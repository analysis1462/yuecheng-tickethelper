package com.yuecheng.ticket.data

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** 发车时间毫秒(sendDate=yyyy-MM-dd, sendTime=HH:mm[:ss]);无法解析返回 0。data 层供通知构建与界面共用 */
fun departureEpochMillis(sendDate: String?, sendTime: String?): Long {
    val date = sendDate?.takeIf { it.length >= 10 } ?: return 0L
    val t = (sendTime ?: "").trim()
    val timePart = when {
        t.length >= 8 -> t.substring(0, 8)
        t.length == 5 -> "$t:00"
        else -> return 0L
    }
    return runCatching {
        java.time.LocalDateTime.parse("${date.substring(0, 10)}T$timePart")
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrDefault(0L)
}

/** 一条订单的发车提醒计划:提前量(分钟,可多个) + 是否闹钟式响铃 + 票面摘要(检票口/座位等) */
data class ReminderPlan(
    val orderId: String,
    val route: String,
    val departureMillis: Long,
    val leads: List<Long>,
    val alarm: Boolean,
    val detail: String = "",
) {
    /** 展示文案,如「提前 2小时/30分钟 · 闹钟」 */
    fun label(): String =
        "提前 " + leads.sortedDescending().joinToString("/") { DepartureReminder.leadLabel(it) } +
            (if (alarm) " · 闹钟" else " · 通知")
}

/** 「出发地→到达地  日期 时间」展示串(提醒计划与配置弹窗共用) */
fun routeText(startName: String?, endPortName: String?, sendDate: String?, sendTime: String?): String =
    "${startName ?: ""}→${endPortName ?: ""}  ${sendDate ?: ""} ${sendTime ?: ""}"

/**
 * 从订单详情提取通知要展示的票面要素:检票口/座位/车牌/发车位。
 * 多人订单座位用 / 连接;字段缺失自动略过。
 */
fun ticketDetailText(d: OrderDetailData): String {
    val parts = mutableListOf<String>()
    d.checkPort?.takeIf { it.isNotBlank() && it != "--" }?.let { parts.add("检票口 $it") }
    val seats = d.tickets.mapNotNull { it.seatNo?.takeIf { s -> s.isNotBlank() && s != "--" } }.distinct()
    if (seats.isNotEmpty()) parts.add("座位 ${seats.joinToString("/")}")
    d.tickets.firstNotNullOfOrNull { it.carNo?.takeIf { c -> c.isNotBlank() && c != "--" && c != "0" } }
        ?.let { parts.add("车牌 $it") }
    d.tickets.firstNotNullOfOrNull { it.sendPort?.takeIf { p -> p.isNotBlank() && p != "--" } }
        ?.let { parts.add("发车位 $it") }
    return parts.joinToString(" · ")
}

/**
 * 发车提醒:
 *  - 仅通知:WorkManager 一次性任务,普通通知;
 *  - 闹钟式:AlarmManager 精确闹钟(31+ 需 SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM,
 *    不可用时降级 setWindow),AlarmReceiver 发响铃通知(闹钟音+循环响,点击或滑动才停)。
 * 计划持久化在 prefs("yc_reminders"),key = plan_<orderId>。
 */
object DepartureReminder {
    const val CHANNEL_NOTIFY = "departure_remind"
    const val CHANNEL_ALARM = "departure_alarm"
    const val CHANNEL_COUNTDOWN = "departure_countdown"
    private const val PREFS = "yc_reminders"
    private val LEAD_OPTIONS = listOf(120L, 60L, 30L, 10L)
    /** 测试模式倒计时通知的固定 id */
    private const val COUNTDOWN_TEST_ID = 192837

    fun leadOptions(): List<Long> = LEAD_OPTIONS

    fun leadLabel(minutes: Long): String =
        if (minutes % 60L == 0L) "${minutes / 60}小时" else "${minutes}分钟"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(
                android.app.NotificationChannel(CHANNEL_NOTIFY, "发车提醒", android.app.NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "班车发车前的到站提醒" },
            )
            if (nm.getNotificationChannel(CHANNEL_ALARM) == null) {
                val alarm = android.app.NotificationChannel(
                    CHANNEL_ALARM, "发车闹钟", android.app.NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "发车提醒的闹钟响铃模式"
                    val attr = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attr)
                    enableVibration(true)
                }
                nm.createNotificationChannel(alarm)
            }
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_COUNTDOWN, "发车倒计时", android.app.NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "临近发车时常驻显示的倒计时(静音)" },
            )
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** AlarmManager 获取:getSystemService(Class) 是 API 23+,21/22 用字符串形式 */
    private fun alarmManagerOf(context: Context): AlarmManager =
        if (Build.VERSION.SDK_INT >= 23) context.getSystemService(AlarmManager::class.java)
        else context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun key(orderId: String) = "plan_$orderId"

    fun load(context: Context, orderId: String): ReminderPlan? {
        val raw = prefs(context).getString(key(orderId), null) ?: return null
        // 解析失败(旧版简单格式)视为无计划
        return runCatching {
            val o = JsonParser.parseString(raw).asJsonObject
            ReminderPlan(
                orderId = orderId,
                route = o.get("route").asString,
                departureMillis = o.get("dep").asLong,
                leads = o.getAsJsonArray("leads").map { it.asLong },
                alarm = o.get("alarm").asBoolean,
                detail = o.get("detail")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
            )
        }.getOrNull()
    }

    /**
     * 保存计划:先清旧,再为每个未过期的提前量排任务/闹钟。
     * 返回 null 表示全部成功;闹钟式写入系统时钟失败时返回报错文案(已自动降级为应用内响铃)。
     */
    fun save(context: Context, plan: ReminderPlan): String? {
        cancel(context, plan.orderId)
        val ctx = context.applicationContext
        val now = System.currentTimeMillis()
        val active = plan.leads.filter { plan.departureMillis - it * 60_000 > now }
        if (active.isEmpty()) {
            // 所有提前量均已过期:等同移除(上方 cancel 已清理旧任务/通知与计划记录),不留空计划
            return null
        }
        var sysError: String? = null
        active.forEach { lead ->
            val triggerAt = plan.departureMillis - lead * 60_000
            when {
                // 闹钟式:优先写入系统时钟App(可见可管理);失败则降级为应用内精确闹钟响铃并报错
                plan.alarm -> {
                    val err = setSystemAlarm(ctx, triggerAt, "发车:${plan.route}")
                    if (err != null) {
                        if (sysError == null) sysError = err
                        setExactAlarm(ctx, plan.orderId, lead, triggerAt, plan.route, timeText(plan.departureMillis), plan.detail)
                    }
                }
                // 仅通知:WorkManager 到点发普通通知;加急执行,避免 Doze 下被推迟到维护窗口
                else -> WorkManager.getInstance(ctx).enqueueUniqueWork(
                    "remind_${plan.orderId}_$lead",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<DepartureWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setInitialDelay(triggerAt - now, TimeUnit.MILLISECONDS)
                        .setInputData(workDataOf(
                            "route" to plan.route, "time" to timeText(plan.departureMillis),
                            "dep" to plan.departureMillis, "orderId" to plan.orderId,
                            "detail" to plan.detail,
                        ))
                        .build(),
                )
            }
        }
        prefs(ctx).edit().putString(
            key(plan.orderId),
            com.google.gson.JsonObject().apply {
                addProperty("route", plan.route)
                addProperty("dep", plan.departureMillis)
                // 必须存成真正的 JsonArray;存字符串会导致 load 解析失败、状态永远丢失
                add("leads", com.google.gson.JsonArray().apply { active.forEach { add(it) } })
                addProperty("alarm", plan.alarm)
                addProperty("detail", plan.detail)
            }.toString(),
        ).apply()

        // 倒计时常驻通知:从最早的提前量起显示,到发车时刻自动撤掉
        active.maxOrNull()?.let { maxLead ->
            val wm = WorkManager.getInstance(ctx)
            wm.enqueueUniqueWork(
                "countdown_${plan.orderId}", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CountdownWorker>()
                    .setInitialDelay(plan.departureMillis - maxLead * 60_000 - now, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(
                        "route" to plan.route, "dep" to plan.departureMillis,
                        "orderId" to plan.orderId, "detail" to plan.detail,
                        "notifId" to countdownId(plan.orderId),
                    ))
                    .build(),
            )
            wm.enqueueUniqueWork(
                "countdownEnd_${plan.orderId}", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CountdownEndWorker>()
                    .setInitialDelay(plan.departureMillis - now, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("notifId" to countdownId(plan.orderId)))
                    .build(),
            )
        }
        return sysError
    }

    /**
     * 写入系统时钟 App 的真实闹钟(用户可见、系统负责响铃)。
     * 注意:系统闹钟无法由本 App 程序化删除,取消提醒时需用户自行在时钟App关闭。
     * 返回 null 表示成功;失败返回含原因的报错文案。
     * 不做 resolveActivity 预检:11+ 包可见性会把时钟应用过滤成 null 造成误判,直接启动并捕获异常。
     */
    private fun setSystemAlarm(context: Context, triggerAt: Long, label: String): String? {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = triggerAt }
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, cal.get(java.util.Calendar.HOUR_OF_DAY))
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, cal.get(java.util.Calendar.MINUTE))
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            "系统闹钟写入失败:${e.message ?: e.javaClass.simpleName};已降级为应用内响铃"
        }
    }

    /** 精确闹钟权限状态文案(测试面板展示) */
    fun exactAlarmStatus(context: Context): String {
        if (Build.VERSION.SDK_INT < 31) return "系统默认支持(API<31)"
        val am = context.getSystemService(AlarmManager::class.java)
        return if (am.canScheduleExactAlarms()) "已授权" else "未授权,闹钟将降级为窗口期送达"
    }

    /** 移除本应用发布的全部通知(含倒计时与响铃中的闹钟通知) */
    fun removeAllNotifications(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancelAll()
    }

    /** 清空全部提醒计划(含任务/闹钟/倒计时) */
    fun clearAllPlans(context: Context) {
        allPlans(context).forEach { cancel(context, it.orderId) }
        prefs(context).edit().clear().apply()
    }

    fun cancel(context: Context, orderId: String) {
        val ctx = context.applicationContext
        val am = alarmManagerOf(ctx)
        val wm = WorkManager.getInstance(ctx)
        load(ctx, orderId)?.leads?.forEach { lead ->
            wm.cancelUniqueWork("remind_${orderId}_$lead")
            am.cancel(alarmPendingIntent(ctx, orderId, lead, "", ""))
        }
        // 撤掉倒计时任务与可能已在显示的常驻通知
        wm.cancelUniqueWork("countdown_$orderId")
        wm.cancelUniqueWork("countdownEnd_$orderId")
        runCatching { NotificationManagerCompat.from(ctx).cancel(countdownId(orderId)) }
        prefs(ctx).edit().remove(key(orderId)).apply()
    }

    /** 清理:发车时间已过(或跨年残留)的计划;旧版 info_ 键直接移除 */
    fun cleanupStale(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        p.all.keys.filter { it.startsWith("info_") }.forEach { p.edit().remove(it).apply() }
        p.all.keys.filter { it.startsWith("plan_") }.forEach { k ->
            val plan = runCatching {
                val o = JsonParser.parseString(p.getString(k, null) ?: "{}").asJsonObject
                o.get("dep")?.takeIf { it.isJsonPrimitive }?.asLong
            }.getOrNull()
            if (plan == null || plan < now) p.edit().remove(k).apply()
        }
    }

    private fun timeText(departureMillis: Long): String =
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(departureMillis))

    private fun requestCode(orderId: String, lead: Long): Int =
        // 带分隔符的字符串哈希:避免 Objects.hash 组合下 (id1,lead1) 与 (id2,lead2) 的别名混同
        "$orderId:$lead".hashCode()

    /** extras 不参与 PendingIntent 匹配,取消时可用空文案构造同 requestCode 的 PI */
    private fun alarmPendingIntent(
        context: Context, orderId: String, lead: Long,
        route: String, timeText: String, detail: String = "",
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra("route", route)
            .putExtra("time", timeText)
            .putExtra("detail", detail)
            .putExtra("orderId", orderId)
        return PendingIntent.getBroadcast(
            context, requestCode(orderId, lead), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setExactAlarm(
        context: Context, orderId: String, lead: Long,
        triggerAt: Long, route: String, timeText: String, detail: String = "",
    ) {
        val am = alarmManagerOf(context)
        val pi = alarmPendingIntent(context, orderId, lead, route, timeText, detail)
        when {
            // 31+ 未授予精确闹钟:窗口期 1 分钟内送达,功能仍可用
            Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms() ->
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000, pi)
            // 23+ 用空闲态也能触发的精确闹钟;21/22 无此 API,退化为 setExact
            Build.VERSION.SDK_INT >= 23 ->
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else ->
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun countdownId(orderId: String): Int = ("countdown$orderId").hashCode()

    /** 全部已设提醒(测试面板展示用) */
    fun allPlans(context: Context): List<ReminderPlan> =
        prefs(context).all.keys.filter { it.startsWith("plan_") }
            .mapNotNull { load(context, it.removePrefix("plan_")) }

    // ---------- 测试模式入口 ----------

    /** 测试通知的数据源:已设提醒的订单 → 最近一张购票成功的订单 */
    private suspend fun pickTestOrder(context: Context): OrderDetailData? = withContext(Dispatchers.IO) {
        resultOf {
            val orderId = allPlans(context).firstOrNull()?.orderId
                ?: Repo.orderList().firstOrNull { it.status == OrderStatus.SUCCESS }?.orderId
            orderId?.takeIf { it.isNotEmpty() }?.let { Repo.orderDetail(it) }
        }.getOrNull()
    }

    /**
     * 测试通知:用真实订单渲染与发车提醒完全一致的格式(含检票口/座位等票面要素);
     * 无网络/无订单时退回纯通道测试内容。
     */
    suspend fun sendTestNotification(context: Context, alarmStyle: Boolean) {
        val ctx = context.applicationContext
        ensureChannels(ctx)
        val channel = if (alarmStyle) CHANNEL_ALARM else CHANNEL_NOTIFY
        val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val d = pickTestOrder(ctx)
        if (d == null) {
            postNotification(
                ctx, channel, "测试:${if (alarmStyle) "闹钟通知" else "普通通知"}",
                "未取到订单数据,仅通道测试 $now", insistent = alarmStyle,
            )
            return
        }
        val detail = ticketDetailText(d)
        val millis = departureEpochMillis(d.order.sendDate, d.order.sendTime)
        val minutes = ((millis - System.currentTimeMillis()) / 60000L).coerceAtLeast(0)
        val left = if (minutes >= 60) "${minutes / 60}小时${(minutes % 60).takeIf { it > 0 }?.let { "${it}分钟" } ?: ""}" else "${minutes}分钟"
        val route = "${d.order.startName ?: ""}→${d.order.endPortName ?: ""}  ${d.order.sendDate ?: ""} ${d.order.sendTime ?: ""}"
        postNotification(
            ctx, channel, "测试 · 班车约${left}后发车",
            detail.ifEmpty { route },
            insistent = alarmStyle,
            bigText = buildString {
                appendLine(route)
                if (detail.isNotEmpty()) appendLine(detail)
                append("此为测试通知,格式与真实发车提醒一致")
            },
        )
    }

    /** 测试倒计时:用真实订单的线路与票面要素 */
    suspend fun sendTestCountdown(context: Context) {
        val ctx = context.applicationContext
        ensureChannels(ctx)
        val d = pickTestOrder(ctx)
        val detail = d?.let { ticketDetailText(it) }.orEmpty()
        val route = d?.let { "${it.order.startName ?: ""}→${it.order.endPortName ?: ""}" } ?: "测试线路"
        postCountdownNotification(ctx, COUNTDOWN_TEST_ID, route, System.currentTimeMillis() + 120_000, detail)
    }

    fun removeTestCountdown(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(COUNTDOWN_TEST_ID)
    }

    /** 写入系统时钟闹钟(2分钟后);返回 null 成功,失败返回报错文案 */
    fun createTestSystemAlarm(context: Context): String? =
        setSystemAlarm(context.applicationContext, System.currentTimeMillis() + 120_000, "悦程测试闹钟")
}

private fun postCountdownNotification(context: Context, id: Int, route: String, departureMillis: Long, detail: String) {
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val tapIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, com.yuecheng.ticket.MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    // chronometer 倒计时:系统自己逐秒刷新,无需应用轮询;部分 ROM 会把这类常驻通知收成胶囊
    val builder = NotificationCompat.Builder(context, DepartureReminder.CHANNEL_COUNTDOWN)
        .setSmallIcon(com.yuecheng.ticket.R.drawable.ic_launcher_foreground)
        .setContentTitle("距发车还有")
        .setContentText(detail.ifEmpty { "$route · 请提前到站取票安检" })
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(buildString {
                    appendLine(route)
                    if (detail.isNotEmpty()) appendLine(detail)
                    append("系统已开始倒计时,请提前到站取票安检")
                }),
        )
        .setWhen(departureMillis)
        .setUsesChronometer(true)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(tapIntent)
    // 倒计时样式的框架 API 是 24+,更早版本忽略(显示为绝对时间,不影响其他内容)
    if (Build.VERSION.SDK_INT >= 24) builder.setChronometerCountDown(true)
    val notification = builder.build()
    runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
}

/** 倒计时常驻通知:发车前(最早提前量)显示,系统 chronometer 自动逐秒倒数 */
class CountdownWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val route = inputData.getString("route") ?: return Result.success()
        val dep = inputData.getLong("dep", 0L)
        val id = inputData.getInt("notifId", 0)
        if (dep <= 0L) return Result.success()
        val orderId = inputData.getString("orderId").orEmpty()
        val detail = freshTicketDetail(orderId, inputData.getString("detail").orEmpty())
        postCountdownNotification(applicationContext, id, route, dep, detail)
        return Result.success()
    }
}

/** 发车时刻撤掉倒计时通知 */
class CountdownEndWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getInt("notifId", 0)
        runCatching { NotificationManagerCompat.from(applicationContext).cancel(id) }
        return Result.success()
    }
}

/**
 * 取展示用票面要素:优先拉取服务器最新详情(检票口可能调整),
 * 失败回退到设置提醒时的快照;两者皆无返回空串。
 */
private suspend fun freshTicketDetail(orderId: String, snapshot: String): String =
    resultOf { Repo.orderDetail(orderId) }
        .getOrNull()?.let { ticketDetailText(it) }
        .takeIf { !it.isNullOrEmpty() } ?: snapshot

/** 仅通知模式的触发(闹钟式走 AlarmReceiver) */
class DepartureWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val route = inputData.getString("route") ?: return Result.success()
        val time = inputData.getString("time") ?: ""
        val dep = inputData.getLong("dep", 0L)
        val orderId = inputData.getString("orderId").orEmpty()
        val snapshot = inputData.getString("detail").orEmpty()
        val ctx = applicationContext
        DepartureReminder.ensureChannels(ctx)
        // 触发时拉最新详情:检票口/座位可能调整,取不到再用设置时的快照
        val detail = freshTicketDetail(orderId, snapshot)
        val minutes = ((dep - System.currentTimeMillis()) / 60000L).coerceAtLeast(0)
        val title = when {
            minutes >= 60 -> "班车约${minutes / 60}小时${(minutes % 60).takeIf { it > 0 }?.let { "${it}分钟" } ?: ""}后发车"
            minutes > 0 -> "班车约${minutes}分钟后发车"
            else -> "班车已到发车时间"
        }
        postNotification(
            ctx, DepartureReminder.CHANNEL_NOTIFY, title,
            detail.ifEmpty { "$time · $route" },
            insistent = false,
            bigText = buildString {
                appendLine("$route $time")
                if (detail.isNotEmpty()) appendLine(detail)
                append("请提前到站取票安检")
            },
            notifId = orderNotifyId(orderId),
        )
        return Result.success()
    }
}

/** 闹钟式提醒触发点:发闹钟铃声通知,循环响直到用户处理 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val route = intent.getStringExtra("route") ?: return
        val time = intent.getStringExtra("time") ?: ""
        val snapshot = intent.getStringExtra("detail").orEmpty()
        val orderId = intent.getStringExtra("orderId").orEmpty()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 短超时拉最新票面(检票口可能调整):广播存活窗口有限,超时即用设置时的快照
                val detail = withTimeoutOrNull(4_000) { freshTicketDetail(orderId, snapshot) } ?: snapshot
                DepartureReminder.ensureChannels(context)
                postNotification(
                    context, DepartureReminder.CHANNEL_ALARM, "班车就要发车啦",
                    detail.ifEmpty { "$time · $route" },
                    insistent = true,
                    bigText = buildString {
                        appendLine("$route $time")
                        if (detail.isNotEmpty()) appendLine(detail)
                        append("请立即前往检票口")
                    },
                    notifId = orderNotifyId(orderId),
                )
            } finally {
                pending.finish()
            }
        }
    }
}

/** 发车提醒通知 id:同一订单的后续提醒覆盖前一条,避免通知堆积 */
private fun orderNotifyId(orderId: String): Int = orderId.hashCode()

private fun postNotification(
    context: Context, channelId: String, title: String, text: String,
    insistent: Boolean, bigText: String = "", notifId: Int = text.hashCode(),
) {
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        // 无通知权限时静默放弃(设置提醒时会先申请权限,正常不会走到这里)
        return
    }
    val tapIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, com.yuecheng.ticket.MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(com.yuecheng.ticket.R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(bigText.ifEmpty { text }))
        .setCategory(if (insistent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setContentIntent(tapIntent)
        .build()
    // 闹钟式:循环响铃直到用户点开/划掉(NotificationCompat 无对应 API,用 flags)
    if (insistent) notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
    runCatching { NotificationManagerCompat.from(context).notify(notifId, notification) }
}
