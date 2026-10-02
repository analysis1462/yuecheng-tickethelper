package com.yuecheng.ticket.ui.order

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer
import com.yuecheng.ticket.data.OrderDetailData
import com.yuecheng.ticket.data.Repo
import com.yuecheng.ticket.data.Ticket
import com.yuecheng.ticket.data.statusLabelOf
import com.yuecheng.ticket.data.ticketStatusLabel
import com.yuecheng.ticket.ui.common.ErrorBox
import com.yuecheng.ticket.ui.common.LoadingBox
import com.yuecheng.ticket.ui.common.YcScaffold
import com.yuecheng.ticket.ui.common.cents
import com.yuecheng.ticket.ui.common.yuan
import com.yuecheng.ticket.ui.pay.PayActivity
import com.yuecheng.ticket.ui.shift.Tag
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true; error = ""; needLogin = false
            runCatching { Repo.orderDetail(orderId) }
                .onSuccess { data = it; loading = false }
                .onFailure {
                    loading = false
                    if (it is com.yuecheng.ticket.data.NeedLoginException) {
                        if (com.yuecheng.ticket.data.Session.isLoggedIn) com.yuecheng.ticket.data.Session.logout()
                        needLogin = true
                    } else {
                        error = it.message ?: "加载失败"
                    }
                }
        }
    }
    LaunchedEffect(orderId) { reload() }

    val d = data
    YcScaffold(title = "订单详情", onBack = { nav.popBackStack() }) { p ->
        when {
            loading -> LoadingBox()
            needLogin -> com.yuecheng.ticket.ui.common.LoginPrompt { nav.navigate("login") }
            error.isNotEmpty() -> ErrorBox(error) { reload() }
            d != null -> {
                Column(Modifier.fillMaxSize().padding(p)) {
                    LazyColumn(
                        Modifier.fillMaxSize().weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
                                d, d.tickets[i],
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

                        // 退改签规则 / 使用须知 / 票样制作
                        item {
                            Card(shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            val first = d.tickets.firstOrNull()
                                            if (first != null) {
                                                com.yuecheng.ticket.ui.pay.TicketEditorActivity.start(
                                                    nav.context,
                                                    buildTicketEditorPayload(d, first),
                                                    "火车票_${d.shiftNumber?.takeIf { it.isNotEmpty() } ?: "样票"}_${d.order.sendDate ?: ""}",
                                                )
                                            }
                                        }.padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("生成火车票样票", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Text("一键出图 ›", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val canPay = d.order.status == "0" || d.order.status == "2"
                    val refundableTickets = d.order.status == "4" && d.tickets.any { it.status == "0" || it.status == "5" }
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
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            runCatching { Repo.cancelOrder(orderId) }
                                                .onSuccess { reload() }
                                                .onFailure { error = it.message ?: "取消失败" }
                                            busy = false
                                        }
                                    },
                                ) { Text("取消订单", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            runCatching { Repo.payOrder(orderId) }
                                                .onSuccess { PayActivity.start(nav.context, it) }
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
                                            com.yuecheng.ticket.data.FlowState.changeOrder =
                                                com.yuecheng.ticket.data.ChangeOrder(
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
    showQrFor?.let { code ->
        val bmp = remember(code) { qrBitmap(code, 720) }
        CodeDialog(title = "扫码时请调亮屏幕", bitmap = bmp, code = code, onDismiss = { showQrFor = null })
    }
    showBarcodeFor?.let { code ->
        val bmp = remember(code) { barcodeBitmap(code, width = 1000, height = 260) }
        CodeDialog(title = "扫码时请调亮屏幕", bitmap = bmp, code = code, onDismiss = { showBarcodeFor = null })
    }
}

/** 道路客运电子客票电子凭证卡片(版式与 H5 详情页一致) */
@Composable
private fun TicketVoucherCard(
    d: OrderDetailData,
    t: Ticket,
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
                                val bmp = remember(code) { barcodeBitmap(code) }
                                val qbmp = remember(code) { qrBitmap(code) }
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
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
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

private fun htmlToText(html: String?): String = html
    ?.replace(Regex("(?is)<\\s*(br|/p|/div|/li|/h[1-6])[^>]*>"), "\n")
    ?.replace(Regex("<[^>]*>"), "")
    ?.replace("&nbsp;", " ")
    ?.replace("&lt;", "<")
    ?.replace("&gt;", ">")
    ?.replace("&amp;", "&")
    ?.replace(Regex("[ \\t]+"), " ")
    ?.replace(Regex("\n\\s*\n+"), "\n\n")
    ?.trim()
    ?: ""

private fun maskId(id: String?): String =
    when {
        id == null -> "--"
        id.length > 8 -> id.take(4) + "****" + id.takeLast(4)
        else -> id
    }

/**
 * 把汽车票信息映射为票样编辑器的草稿 JSON(键 = 编辑器表单控件 ID)。
 * 参考 12306-train-ticket-editor 的 collectForm() 字段结构。
 * 版面要求:站名只显示城市名;只显示座位号;不显示个人信息;
 * 附加信息行显示「限乘当日当次车」(infoline1="2"),不显示「仅供报销使用」(infoline2="1")。
 */
private fun buildTicketEditorPayload(d: OrderDetailData, t: com.yuecheng.ticket.data.Ticket): String {
    val payload = org.json.JSONObject().apply {
        put("name", "")
        put("identity", "")
        put("startStation", d.order.startName ?: d.order.sendStationName ?: "")
        put("endStation", d.order.endPortName ?: "")
        put("trainNo", d.shiftNumber?.takeIf { it.isNotEmpty() } ?: "汽车")
        put("date", d.order.sendDate ?: "")
        put("time", d.order.sendTime ?: "")
        put("seatClass", t.ticketTypeName ?: "全票")
        put("seatNo", t.seatNo ?: "")
        put("carriageNo", "")
        put("price", t.price?.toDoubleOrNull()?.let { String.format("%.1f", it / 100) } ?: "")
        put("checkinRoom", d.checkPort ?: "")
        put("ticketNo", t.qrCode ?: "")
        put("infoline1", "2")
        put("infoline2", "1")
        put("infoline3", "0")
        put("qrcodeString", t.qrCode ?: "")
        put("isChild", 0); put("isStudent", 0); put("isOnline", 0); put("isDiscount", 0)
    }
    return payload.toString()
}

private class RefundItem(val ticket: Ticket) {
    var selected: Boolean = false
    var fee: Double? = null
}

@Composable
private fun RefundDialog(data: OrderDetailData, onDismiss: () -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var tickets by remember { mutableStateOf(data.tickets.map { RefundItem(it) }) }
    var busy by remember { mutableStateOf(false) }

    fun refreshFee(item: RefundItem) {
        scope.launch {
            runCatching {
                Repo.bounceFee(data.order.orderId, item.ticket.seatNo ?: "")
            }.onSuccess {
                item.fee = it
                tickets = tickets.toList() // 触发重组刷新手续费显示
            }.onFailure { onError(it.message ?: "查询手续费失败") }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("申请退票") },
        text = {
            Column {
                Text("退票将按规则收取手续费,请确认。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            item.fee?.let { Text("手续费 ¥${"%.2f".format(it)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
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
                        tickets.filter { it.selected }.forEach { item ->
                            runCatching { Repo.bounce(data.order.orderId, item.ticket.seatNo ?: "") }
                                .onFailure { onError(it.message ?: "退票失败") }
                        }
                        busy = false
                        onDone()
                    }
                },
            ) { Text(if (busy) "退票中…" else "确认退票") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
