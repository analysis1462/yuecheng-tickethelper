package com.yuecheng.ticket.ui.order

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yuecheng.ticket.data.DepartureReminder
import com.yuecheng.ticket.data.NeedLoginException
import com.yuecheng.ticket.data.OrderStatus
import com.yuecheng.ticket.data.OrderSummary
import com.yuecheng.ticket.data.ReminderPlan
import com.yuecheng.ticket.data.Repo
import com.yuecheng.ticket.data.Session
import com.yuecheng.ticket.data.departureEpochMillis
import com.yuecheng.ticket.data.resultOf
import com.yuecheng.ticket.data.routeText
import com.yuecheng.ticket.data.statusLabelOf
import com.yuecheng.ticket.data.ticketDetailText
import com.yuecheng.ticket.ui.common.ErrorBox
import com.yuecheng.ticket.ui.common.LoadingBox
import com.yuecheng.ticket.ui.common.EmptyBox
import com.yuecheng.ticket.ui.common.LoginPrompt
import com.yuecheng.ticket.ui.common.YcScaffold
import com.yuecheng.ticket.ui.common.cents
import com.yuecheng.ticket.ui.pay.PayActivity
import com.yuecheng.ticket.ui.shift.Tag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(nav: NavController) {
    var orders by remember { mutableStateOf<List<OrderSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) } // 0全部 1待支付 2待出行
    var busyOrder by remember { mutableStateOf("") }
    var payingOrderId by remember { mutableStateOf("") } // 本次去支付的订单:轮询确认只盯它
    var needLogin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload(showLoading: Boolean = true) {
        scope.launch {
            if (showLoading) loading = true
            error = ""; needLogin = false
            resultOf { Repo.orderList() }
                .onSuccess { orders = it; loading = false; refreshing = false }
                .onFailure {
                    loading = false; refreshing = false
                    if (it is NeedLoginException) {
                        // 服务端会话失效:清掉本地登录态,引导重新登录
                        if (Session.isLoggedIn) Session.logout()
                        needLogin = true
                    } else {
                        error = it.message ?: "加载失败"
                    }
                }
        }
    }
    LaunchedEffect(Unit) { reload() }

    /**
     * 支付返回后自动确认出票:轮询订单状态,直到本次支付的订单离开
     * "待支付"(进入出票中/成功/失败),最多约 50 秒。网关回调有延迟,
     * 只刷新一次常会停留在"待支付",用户得反复手动刷新。
     * 只盯 payingOrderId:历史遗留的待支付订单不会把轮询拖满。
     */
    fun confirmPayments() {
        val before = orders.filter { it.orderId == payingOrderId && it.status in OrderStatus.PENDING_PAY }.map { it.orderId }.toSet()
        if (before.isEmpty()) { reload(false); return }
        scope.launch {
            confirming = true
            repeat(16) {
                delay(3000)
                val list = resultOf { Repo.orderList() }.getOrNull()
                if (list != null) {
                    orders = list
                    if (list.none { it.orderId in before && it.status in OrderStatus.PENDING_PAY }) {
                        confirming = false
                        return@launch
                    }
                }
            }
            confirming = false
        }
    }

    // 从支付页返回(无论是否点"完成")都进入确认轮询
    val payLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { confirmPayments() }

    // ---- 发车提醒:订单卡按钮 → 配置弹窗(提前量多选 + 闹钟开关) ----
    var reminderTick by remember { mutableIntStateOf(0) } // 提醒变更后刷新卡片按钮
    var reminderFor by remember { mutableStateOf<OrderSummary?>(null) }
    var pendingSave by remember { mutableStateOf<PendingReminderSave?>(null) }
    var cancelConfirmFor by remember { mutableStateOf<OrderSummary?>(null) }
    val ctx = LocalContext.current

    // 已设提醒的订单集合:reminderTick 变化时读一次偏好,避免每张卡片重组都查 SharedPreferences
    val remindedOrderIds = remember(reminderTick) {
        DepartureReminder.allPlans(ctx).map { it.orderId }.toSet()
    }

    fun toast(text: String) {
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    }

    /** 统一保存入口:拉一次详情补票面要素(列表页没有检票口/座位),失败留空(触发时还会再拉) */
    fun launchSave(order: OrderSummary, leads: List<Long>, alarm: Boolean) {
        scope.launch {
            val millis = departureEpochMillis(order.sendDate, order.sendTime)
            if (millis <= 0L) { toast("发车时间未知,无法设置提醒"); return@launch }
            val base = ReminderPlan(
                orderId = order.orderId,
                route = routeText(order.startName, order.endPortName, order.sendDate, order.sendTime),
                departureMillis = millis,
                leads = leads,
                alarm = alarm,
            )
            val plan = resultOf { base.copy(detail = ticketDetailText(Repo.orderDetail(order.orderId))) }
                .getOrDefault(base)
            val err = DepartureReminder.save(ctx, plan)
            reminderTick++
            if (err == null) {
                reminderFor = null
                // 全部提前量已过期时 save 等同移除,按实际状态提示
                toast(
                    when {
                        DepartureReminder.load(ctx, order.orderId) == null -> "已移除提醒"
                        alarm -> "已设置闹钟式提醒"
                        else -> "已设置发车提醒"
                    },
                )
            } else {
                // 系统闹钟写入失败已降级为应用内响铃,报错文案交给弹窗展示(此处 toast 兜底)
                Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val save = pendingSave
        pendingSave = null
        if (granted && save != null) launchSave(save.order, save.leads, save.alarm)
        else if (!granted) toast("未授予通知权限,无法设置提醒")
    }

    /** 弹窗保存:同步做权限检查;实际保存走 launchSave,完成后由其关闭弹窗(固定返回 null) */
    fun savePlan(o: OrderSummary, leads: List<Long>, alarm: Boolean): String? {
        val millis = departureEpochMillis(o.sendDate, o.sendTime)
        if (millis <= 0L) return "发车时间未知,无法设置提醒"
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = PendingReminderSave(o, leads, alarm)
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return null
        }
        launchSave(o, leads, alarm)
        return null
    }

    val filtered = when (tab) {
        1 -> orders.filter { it.status in OrderStatus.PENDING_PAY }
        2 -> orders.filter { it.status in OrderStatus.TRAVEL_READY }
        else -> orders
    }

    YcScaffold(title = "我的订单", onBack = { nav.popBackStack() }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            TabRow(selectedTabIndex = tab) {
                listOf("全部", "待支付", "待出行").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; reload(false) },
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (confirming) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LinearProgressIndicator(Modifier.weight(1f))
                            Spacer(Modifier.width(10.dp))
                            Text("正在确认支付结果…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    when {
                        loading -> LoadingBox()
                        needLogin -> com.yuecheng.ticket.ui.common.LoginPrompt { nav.navigate("login") }
                        error.isNotEmpty() -> ErrorBox(error) { reload() }
                        filtered.isEmpty() -> EmptyBox("暂无订单")
                        else -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(
                                filtered,
                                // 列表每一行是一张子票:同一 orderId 会出现多次,key 必须带上子票号与下标
                                key = { i, o -> "${o.orderId}-${o.suborderId ?: "n"}-$i" },
                            ) { i, o ->
                                val rowKey = "${o.orderId}-${o.suborderId ?: "n"}-$i"
                                // 已购票的行程卡上提供发车提醒入口(有计划显示"已设置提醒")
                                val reminderLabel = if (o.status == OrderStatus.SUCCESS) {
                                    if (o.orderId in remindedOrderIds) "已设置提醒" else "设置提醒"
                                } else null
                                OrderCard(
                                    o = o,
                                    busy = busyOrder == rowKey,
                                    reminderLabel = reminderLabel,
                                    onReminderClick = {
                                        val millis = departureEpochMillis(o.sendDate, o.sendTime)
                                        if (millis <= 0L) {
                                            android.widget.Toast.makeText(ctx, "发车时间未知,无法设置提醒", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            reminderFor = o
                                        }
                                    },
                                    onPay = {
                                        payingOrderId = o.orderId
                                        scope.launch {
                                            busyOrder = rowKey
                                            resultOf { Repo.payOrder(o.orderId) }
                                                .onSuccess { payLauncher.launch(PayActivity.intent(nav.context, it)) }
                                                .onFailure { err -> error = err.message ?: "支付失败" }
                                            busyOrder = ""
                                        }
                                    },
                                    onCancel = { cancelConfirmFor = o },
                                    onClick = { nav.navigate("orderDetail/${o.orderId}") },
                                )
                            }
                            item {
                                TextButton(onClick = { reload(false) }, modifier = Modifier.fillMaxWidth()) { Text("刷新") }
                            }
                        }
                    }
                }
            }
        }
    }
    // 发车提醒配置弹窗
    reminderFor?.let { o ->
        val initial = DepartureReminder.load(ctx, o.orderId)
        fun removeAll() {
            // 幂等:移除此票全部提醒计划 + 清掉本应用发布的所有通知
            DepartureReminder.cancel(ctx, o.orderId)
            DepartureReminder.removeAllNotifications(ctx)
            reminderTick++
            reminderFor = null
            toast("已移除此票全部提醒与通知")
        }
        ReminderDialog(
            route = routeText(o.startName, o.endPortName, o.sendDate, o.sendTime),
            departureMillis = departureEpochMillis(o.sendDate, o.sendTime),
            initial = initial,
            onSave = { leads, alarm -> savePlan(o, leads, alarm) },
            onRemove = { removeAll() },
            onDismiss = { reminderFor = null },
        )
    }

    // 取消订单二次确认(服务端操作不可逆,防误触)
    cancelConfirmFor?.let { o ->
        AlertDialog(
            onDismissRequest = { cancelConfirmFor = null },
            title = { Text("取消订单") },
            text = { Text("确定取消 ${o.sendDate ?: ""} ${o.startName ?: ""}→${o.endPortName ?: ""} 的订单吗?取消后需重新下单。") },
            confirmButton = {
                TextButton(onClick = {
                    cancelConfirmFor = null
                    scope.launch {
                        resultOf { Repo.cancelOrder(o.orderId) }
                            .onSuccess { reload(false) }
                            .onFailure { err -> error = err.message ?: "取消失败" }
                    }
                }) { Text("确定取消", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { cancelConfirmFor = null }) { Text("先不了") } },
        )
    }
}

@Composable
private fun OrderCard(
    o: OrderSummary,
    busy: Boolean,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onClick: () -> Unit,
    reminderLabel: String? = null,
    onReminderClick: () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${o.sendDate ?: ""} ${o.sendTime ?: ""}",
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                )
                Spacer(Modifier.weight(1f))
                Tag(
                    o.statusLabel,
                    when {
                        o.status == OrderStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
                        o.status in OrderStatus.PENDING_PAY -> MaterialTheme.colorScheme.secondary
                        o.status in OrderStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("${o.startName ?: ""}  →  ${o.endPortName ?: ""}", fontSize = 14.sp)
            Text(o.sendStationName ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¥${cents(o.totalPrice)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                if (o.status == "4") {
                    TextButton(onClick = onReminderClick) {
                        Icon(
                            Icons.Default.Alarm, null,
                            modifier = Modifier.size(15.dp),
                            tint = if (reminderLabel != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            reminderLabel ?: "设置提醒",
                            fontSize = 13.sp,
                            color = if (reminderLabel != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (o.status in OrderStatus.PENDING_PAY) {
                    if (busy) {
                        Text("处理中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TextButton(onClick = onCancel) { Text("取消订单", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = onPay) { Text("去支付", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

/** 通知权限弹窗期间暂存的保存请求 */
private data class PendingReminderSave(val order: OrderSummary, val leads: List<Long>, val alarm: Boolean)
