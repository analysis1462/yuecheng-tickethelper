package com.yuecheng.ticket.ui.order

import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer
import com.yuecheng.ticket.data.DepartureReminder
import com.yuecheng.ticket.data.NeedLoginException
import com.yuecheng.ticket.data.OrderDetailData
import com.yuecheng.ticket.data.OrderStatus
import com.yuecheng.ticket.data.ReminderPlan
import com.yuecheng.ticket.data.Repo
import com.yuecheng.ticket.data.Session
import com.yuecheng.ticket.data.Ticket
import com.yuecheng.ticket.data.ChangeOrder
import com.yuecheng.ticket.data.FlowState
import com.yuecheng.ticket.data.departureEpochMillis
import com.yuecheng.ticket.data.resultOf
import com.yuecheng.ticket.data.routeText
import com.yuecheng.ticket.data.statusLabelOf
import com.yuecheng.ticket.data.ticketDetailText
import com.yuecheng.ticket.data.ticketStatusLabel
import com.yuecheng.ticket.ui.common.ErrorBox
import com.yuecheng.ticket.ui.common.LoadingBox
import com.yuecheng.ticket.ui.common.LoginPrompt
import com.yuecheng.ticket.ui.common.YcScaffold
import com.yuecheng.ticket.ui.common.cents
import com.yuecheng.ticket.ui.common.htmlToText
import com.yuecheng.ticket.ui.common.maskId
import com.yuecheng.ticket.ui.common.yuan
import com.yuecheng.ticket.ui.pay.PayActivity
import com.yuecheng.ticket.ui.shift.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun OrderDetailScreen(nav: NavController, orderId: String) {
    var data by remember { mutableStateOf<OrderDetailData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showRefund by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var showNotice by remember { mutableStateOf(false) }
    var showQrFor by remember { mutableStateOf<String?>(null) }
    var showBarcodeFor by remember { mutableStateOf<String?>(null) }
    var needLogin by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // 票码位图缓存:同码只生成一次,列表内嵌小图与全屏亮码共用
    val codeBitmaps = remember { CodeBitmapCache() }

    fun reload() {
        scope.launch {
            loading = true; error = ""; needLogin = false
            resultOf { Repo.orderDetail(orderId) }
                .onSuccess { data = it; loading = false }
                .onFailure {
                    loading = false
                    if (it is NeedLoginException) {
                        if (Session.isLoggedIn) Session.logout()
                        needLogin = true
                    } else {
                        error = it.message ?: "加载失败"
                    }
                }
        }
    }
    LaunchedEffect(orderId) { reload() }

    /**
     * 支付返回后轮询确认:网关回调有延迟,只刷一次常停留在"待支付"。
     * 每 3 秒拉一次详情,直到状态离开"待支付"(出票中/成功/失败),最多约 50 秒。
     */
    fun confirmPayment() {
        scope.launch {
            confirming = true
            repeat(16) {
                kotlinx.coroutines.delay(3000)
                resultOf { Repo.orderDetail(orderId) }.onSuccess {
                    data = it
                    val st = it.order.status
                    if (st !in OrderStatus.PENDING_PAY) { confirming = false; return@launch }
                }
            }
            confirming = false
        }
    }

    // 支付返回后进入确认轮询
    val payLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { confirmPayment() }

    // ---- 发车提醒:配置弹窗(提前量多选 + 闹钟开关),状态存 DepartureReminder ----
    var reminderPlan by remember { mutableStateOf<ReminderPlan?>(null) }
    var showReminder by remember { mutableStateOf(false) }
    var pendingPlan by remember { mutableStateOf<ReminderPlan?>(null) }

    /** 保存计划并刷新界面;成功(null)关弹窗并按实际状态提示,失败返回报错文案 */
    fun persistPlan(plan: ReminderPlan): String? {
        val err = DepartureReminder.save(ctx, plan)
        reminderPlan = DepartureReminder.load(ctx, orderId)
        if (err == null) {
            showReminder = false
            // 全部提前量已过期时 save 等同移除,按实际状态提示
            Toast.makeText(
                ctx,
                when {
                    reminderPlan == null -> "已移除提醒"
                    plan.alarm -> "已设置闹钟式提醒"
                    else -> "已设置发车提醒"
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
        return err
    }

    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val plan = pendingPlan
        pendingPlan = null
        if (granted && plan != null) {
            persistPlan(plan)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
        } else if (!granted) android.widget.Toast.makeText(ctx, "未授予通知权限,无法设置提醒", android.widget.Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(orderId) {
        // 清掉已过期的残留提醒记录,再载入本单的提醒计划
        DepartureReminder.cleanupStale(ctx)
        reminderPlan = DepartureReminder.load(ctx, orderId)
    }

    /** 返回 null 表示成功(关弹窗);返回文案为红色报错(弹窗内展示) */
    fun saveReminderPlan(d: OrderDetailData, leads: List<Long>, alarm: Boolean): String? {
        val millis = departureEpochMillis(d.order.sendDate, d.order.sendTime)
        if (millis <= 0L) return "发车时间未知,无法设置提醒"
        val plan = ReminderPlan(
            orderId = orderId,
            route = routeText(d.order.startName, d.order.endPortName, d.order.sendDate, d.order.sendTime),
            departureMillis = millis,
            leads = leads,
            alarm = alarm,
            // 票面要素快照(检票口/座位/车牌/发车位),通知触发时还会优先拉服务器最新值
            detail = ticketDetailText(d),
        )
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingPlan = plan
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return null
        }
        return persistPlan(plan)
    }

    val d = data
    YcScaffold(title = "订单详情", onBack = { nav.popBackStack() }) { p ->
        when {
            loading -> LoadingBox()
            needLogin -> LoginPrompt { nav.navigate("login") }
            error.isNotEmpty() -> ErrorBox(error) { reload() }
            d != null -> {
                Column(Modifier.fillMaxSize().padding(p)) {
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
                    LazyColumn(
                        Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 状态 + 订单号 + 取票密码
                        item {
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(statusLabelOf(d.order.status), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                        Spacer(Modifier.weight(1f))
                                        val payLabel = d.payResult?.takeIf { it.isNotEmpty() } ?: d.payType?.takeIf { it.isNotEmpty() }
                                        payLabel?.let { Tag(it, MaterialTheme.colorScheme.primary) }
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                                    VoucherField("订单号", d.order.suborderId)
                                    d.ticketPassword?.takeIf { it.isNotEmpty() }?.let {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("取票密码", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
                                            Text(it, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 6.sp,
                                                color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }

                        // 电子客票凭证(每张票一张卡)
                        items(d.tickets.size) { i ->
                            TicketVoucherCard(
                                d, d.tickets[i], codeBitmaps,
                                onEnlargeBarcode = { showBarcodeFor = it },
                                onEnlargeQr = { showQrFor = it },
                            )
                        }

                        // 车站 / 班次信息
                        item {
                            Card(shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    InfoRow("客运公司", d.companyName)
                                    InfoRow("里程/时长", listOfNotNull(
                                        d.kilometer?.takeIf { it != "0" }?.let { "$it 公里" },
                                        d.runTime?.takeIf { it != "0" }?.let { "$it 小时" },
                                    ).joinToString(" · ").ifEmpty { null })
                                    InfoRow("车站地址", d.stationAddr)
                                    InfoRow("车站电话", d.stationTel)
                                    InfoRow("下单时间", d.orderTime)
                                    InfoRow("支付时间", d.payTime)
                                }
                            }
                        }

                        // 退改签规则 / 使用须知
                        item {
                            Card(shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            if (departureEpochMillis(d.order.sendDate, d.order.sendTime) > 0L) {
                                                showReminder = true
                                            } else {
                                                android.widget.Toast.makeText(ctx, "发车时间未知,无法设置提醒", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }.padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("发车提醒", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Text(
                                            reminderPlan?.label() ?: "发车前2小时 · 点击设置",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(2f),
                                        )
                                        Text(
                                            if (reminderPlan != null) "修改" else "开启",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                    HorizontalDivider()
                                    Row(
                                        Modifier.fillMaxWidth().clickable { showRules = true }.padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("退改签规则", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    HorizontalDivider()
                                    Row(
                                        Modifier.fillMaxWidth().clickable { showNotice = !showNotice }.padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("使用须知", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Text(if (showNotice) "收起↑" else "展开↓", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (showNotice) {
                                        val notice = listOfNotNull(d.smsRemind?.takeIf { it.isNotBlank() })
                                            .joinToString("\n\n").ifEmpty { "暂无" }
                                        Text(
                                            notice,
                                            fontSize = 13.sp, lineHeight = 20.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 12.dp),
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    }

                    // 底部操作栏
                    val canPay = d.order.status in OrderStatus.PENDING_PAY
                    // 子票状态码与主订单是另一套:"0"=购票成功,"5"=已退/关闭,二者可发起退票
                    val refundableTickets = d.order.status == OrderStatus.SUCCESS &&
                        d.tickets.any { it.status == "0" || it.status == "5" }
                    val canRefund = refundableTickets && d.isAllowRefund != "0"
                    val canChange = refundableTickets && d.isAllowChange == "1"
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        if (error.isNotEmpty()) {
                            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (canPay) {
                                TextButton(
                                    enabled = !busy,
                                    onClick = { showCancelConfirm = true },
                                ) { Text("取消订单", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            resultOf { Repo.payOrder(orderId) }
                                                .onSuccess { payLauncher.launch(PayActivity.intent(nav.context, it)) }
                                                .onFailure { error = it.message ?: "支付失败" }
                                            busy = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                ) { Text("去支付", fontWeight = FontWeight.Bold) }
                            }
                            if (canChange) {
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        val first = d.tickets.firstOrNull { it.seatNo != null } ?: d.tickets.firstOrNull()
                                        if (first != null) {
                                            FlowState.changeOrder =
                                                ChangeOrder(
                                                    // suborderId 在订单对象上(实测),不是子票的 id
                                                    subOrderId = d.order.suborderId ?: first.suborderId ?: "",
                                                    seatNo = first.seatNo ?: "",
                                                    sendDate = d.order.sendDate ?: "",
                                                )
                                            nav.navigate("change/$orderId")
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) { Text("改签") }
                            }
                            if (canRefund) {
                                Button(
                                    enabled = !busy,
                                    onClick = { showRefund = true },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                ) { Text("退票") }
                            }
                        }
                    }
                }
            }
        }
    }

    // 退票弹窗:选择车票,查询手续费
    if (showRefund && d != null) {
        RefundDialog(
            data = d,
            onDismiss = { showRefund = false },
            onDone = { showRefund = false; reload() },
            onError = { error = it },
        )
    }

    // 取消订单二次确认(服务端操作不可逆,防误触)
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("取消订单") },
            text = { Text("确定取消该订单吗?取消后需重新下单。") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    scope.launch {
                        busy = true
                        resultOf { Repo.cancelOrder(orderId) }
                            .onSuccess { reload() }
                            .onFailure { error = it.message ?: "取消失败" }
                        busy = false
                    }
                }) { Text("确定取消", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text("先不了") } },
        )
    }

    // 发车提醒配置弹窗(提前量多选 + 闹钟开关)
    if (showReminder && d != null) {
        ReminderDialog(
            route = routeText(d.order.startName, d.order.endPortName, d.order.sendDate, d.order.sendTime),
            departureMillis = departureEpochMillis(d.order.sendDate, d.order.sendTime),
            initial = reminderPlan,
            onSave = { leads, alarm -> saveReminderPlan(d, leads, alarm) },
            onRemove = {
                // 幂等:移除此票全部提醒计划 + 清掉本应用发布的所有通知
                DepartureReminder.cancel(ctx, orderId)
                DepartureReminder.removeAllNotifications(ctx)
                reminderPlan = null
                showReminder = false
                Toast.makeText(ctx, "已移除此票全部提醒与通知", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showReminder = false },
        )
    }

    // 退改签规则弹窗(与 H5 一致,展示购票协议全文)
    if (showRules && d != null) {
        val rulesText = htmlToText(d.protocol).ifEmpty { "暂无规则内容" }
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("退改签规则") },
            text = {
                LazyColumn {
                    item {
                        Text(rulesText, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text("我知道了") } },
        )
    }

    // 全屏亮码:乘车二维码 / 上车条码(与 H5 enlargeQRCode / enlargeCode 一致)
    // 码图在后台线程生成并缓存:同码只算一次,列表内嵌小图与全屏亮码共用
    showQrFor?.let { code ->
        val bmp by produceState<Bitmap?>(null, code) {
            value = withContext(Dispatchers.Default) { codeBitmaps.qr(code) }
        }
        CodeDialog(title = "扫码时请调亮屏幕", bitmap = bmp, code = code, onDismiss = { showQrFor = null })
    }
    showBarcodeFor?.let { code ->
        val bmp by produceState<Bitmap?>(null, code) {
            value = withContext(Dispatchers.Default) { codeBitmaps.barcode(code) }
        }
        CodeDialog(title = "扫码时请调亮屏幕", bitmap = bmp, code = code, onDismiss = { showBarcodeFor = null })
    }
}

/** 道路客运电子客票电子凭证卡片(版式与 H5 详情页一致) */
@Composable
private fun TicketVoucherCard(
    d: OrderDetailData,
    t: Ticket,
    codeBitmaps: CodeBitmapCache,
    onEnlargeBarcode: (String) -> Unit,
    onEnlargeQr: (String) -> Unit,
) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            // 标题
            Text(
                "道路客运电子客票电子凭证",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )

            // 电子客票号 + 条码 + 乘车二维码(点击放大亮码)
            t.qrCode?.takeIf { it.isNotEmpty() }?.let { code ->
                Spacer(Modifier.height(10.dp))
                Text("电子客票号:$code", fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val bmpState = produceState<Bitmap?>(null, code) {
                        value = withContext(Dispatchers.Default) { codeBitmaps.barcode(code) }
                    }
                    val qbmpState = produceState<Bitmap?>(null, code) {
                        value = withContext(Dispatchers.Default) { codeBitmaps.qr(code) }
                    }
                    val bmp = bmpState.value
                    val qbmp = qbmpState.value
                    Box(
                        Modifier.weight(1f).height(72.dp).clickable { onEnlargeBarcode(code) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), "上车条码", modifier = Modifier.fillMaxWidth().height(60.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    if (qbmp != null) {
                        Image(
                            qbmp.asImageBitmap(), "乘车二维码",
                            modifier = Modifier.size(88.dp).clickable { onEnlargeQr(code) },
                        )
                    }
                }
                Text(
                    "(仅限乘当日当次车 · 点击码图放大亮码)",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(12.dp))

            // 出发站 - 班次 - 到达站
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.2f)) {
                    Text(d.order.startName ?: "--", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(d.order.sendStationName ?: "--", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    d.shiftNumber?.takeIf { it.isNotEmpty() }?.let {
                        Text("${it}次", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    } ?: Text("班车", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("——→", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                    Text(d.order.endPortName ?: "--", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("", fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "乘客:${t.name ?: "--"}(${maskId(t.idcardNo)})",
                fontSize = 14.sp, fontWeight = FontWeight.Medium,
            )
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            // 信息栏(与 H5 字段一致,2 列)
            val sendDateShow = d.order.sendDate?.replace("-", "/")
            VoucherGrid(listOf(
                "乘车日期" to (sendDateShow ?: "--"),
                "检票口" to (t.checkPort ?: "--"),
                "发车时间" to (d.order.sendTime ?: "--"),
                "发车位" to (t.sendPort ?: "--"),
                "座位号" to (t.seatNo ?: "--"),
                "车票类型" to (t.ticketTypeName ?: "--"),
                "车牌号" to (t.carNo ?: "--"),
                "车辆类型" to (d.busType ?: "--"),
                "票价" to (t.price?.let { cents(it) } ?: "--"),
                "上限票价" to (t.approvePrice?.let { yuan(it) } ?: "--"),
                "保费" to (t.insurFee?.takeIf { it != "0" && it.isNotEmpty() }?.let { cents(it) } ?: "--"),
                "保险号" to (t.insurNumber?.takeIf { it != "0" && it.isNotEmpty() } ?: "--"),
                "保险公司" to (t.insurCompany ?: "--"),
                "车票状态" to ticketStatusLabel(t.status),
            ))
        }
    }
}

@Composable
private fun VoucherGrid(fields: List<Pair<String, String>>) {
    Column {
        fields.chunked(2).forEach { rowFields ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                rowFields.forEach { (label, value) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
                if (rowFields.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VoucherField(label: String, value: String?) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
        Text(value ?: "--", fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrEmpty() || value == "--") return
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp))
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

/** 全屏亮码弹窗 */
@Composable
private fun CodeDialog(title: String, bitmap: Bitmap?, code: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = RoundedCornerShape(16.dp)) {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), code, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                Text(code, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(14.dp))
                Text(
                    "关闭",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onDismiss() }.padding(6.dp),
                )
            }
        }
    }
}

/** 票码位图缓存:同一票码的条码/二维码只生成一次,列表内嵌与放大亮码共用(生成失败不缓存,下次重试) */
private class CodeBitmapCache {
    private val map = mutableMapOf<String, Bitmap?>()
    fun barcode(code: String): Bitmap? = map.getOrPut("bc:$code") { barcodeBitmap(code, width = 1000, height = 260) }
    fun qr(code: String): Bitmap? = map.getOrPut("qr:$code") { qrBitmap(code, 720) }
}

/** Code128 条码(与 H5 JsBarcode 一致) */
private fun barcodeBitmap(content: String, width: Int = 900, height: Int = 150): Bitmap? = runCatching {
    val matrix = Code128Writer().encode(content, BarcodeFormat.CODE_128, width, height)
    matrixToBitmap(matrix, width, height)
}.getOrNull()

/** 乘车二维码(与 H5 qrcode.js 一致,内容同为 qrCode) */
private fun qrBitmap(content: String, size: Int = 600): Bitmap? = runCatching {
    val matrix = com.google.zxing.qrcode.QRCodeWriter()
        .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
    matrixToBitmap(matrix, size, size)
}.getOrNull()

private fun matrixToBitmap(matrix: com.google.zxing.common.BitMatrix, width: Int, height: Int): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    bmp.setPixels(pixels, 0, width, 0, 0, width, height)
    return bmp
}

private class RefundItem(val ticket: Ticket) {
    var selected: Boolean = false
    var fee: Double? = null
}

@Composable
private fun RefundDialog(data: OrderDetailData, onDismiss: () -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    // RefundItem 是可变对象、无 equals,故用引用相等策略:重建列表即触发刷新
    var tickets by remember {
        mutableStateOf(data.tickets.map { RefundItem(it) }, referentialEqualityPolicy())
    }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    fun refreshFee(item: RefundItem) {
        scope.launch {
            resultOf {
                Repo.bounceFee(data.order.orderId, item.ticket.seatNo ?: "")
            }.onSuccess {
                item.fee = it
                tickets = tickets.toList() // 触发重组刷新手续费显示
            }.onFailure { onError(it.message ?: "查询手续费失败") }
        }
    }

    AlertDialog(
        // 退票进行中禁止关闭:对话框销毁会取消协程,顺序退票会中断成"部分退票"且无提示
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("申请退票") },
        text = {
            Column {
                Text("退票将按规则收取手续费,请确认。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                tickets.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.selected,
                            onCheckedChange = {
                                item.selected = it
                                if (it && item.fee == null) refreshFee(item)
                                tickets = tickets.toList()
                            },
                        )
                        Column {
                            Text("${item.ticket.name ?: ""}  座位 ${item.ticket.seatNo ?: "-"}")
                            item.fee?.let { Text(String.format(Locale.US, "手续费 ¥%.2f", it), fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && tickets.any { it.selected },
                onClick = {
                    scope.launch {
                        busy = true
                        msg = ""
                        var failed: String? = null
                        tickets.filter { it.selected }.forEach { item ->
                            resultOf { Repo.bounce(data.order.orderId, item.ticket.seatNo ?: "") }
                                .onFailure { if (failed == null) failed = it.message ?: "退票失败" }
                        }
                        busy = false
                        if (failed == null) {
                            onDone()
                        } else {
                            // 有失败则保留弹窗并明确提示,不再无条件关闭成"成功"样子
                            msg = failed ?: "退票失败"
                            onError(msg)
                        }
                    }
                },
            ) { Text(if (busy) "退票中…" else "确认退票") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } },
    )
}
