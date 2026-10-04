package com.yuecheng.ticket.ui.order

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yuecheng.ticket.data.FlowState
import com.yuecheng.ticket.data.Passenger
import com.yuecheng.ticket.data.Repo
import com.yuecheng.ticket.data.Session
import com.yuecheng.ticket.data.SuitInfo
import com.yuecheng.ticket.data.resultOf
import com.yuecheng.ticket.ui.common.ErrorBox
import com.yuecheng.ticket.ui.common.LoadingBox
import com.yuecheng.ticket.ui.common.YcScaffold
import com.yuecheng.ticket.ui.common.cents
import com.yuecheng.ticket.ui.common.htmlToText
import com.yuecheng.ticket.ui.common.yuan
import com.yuecheng.ticket.ui.pay.PayActivity
import com.yuecheng.ticket.ui.shift.Tag
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@Composable
fun OrderFillScreen(nav: NavController) {
    val shift = FlowState.shift
    var suit by remember { mutableStateOf<SuitInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    // Passenger.tckType 是可变属性、不参与 data class equals,故用引用相等策略:
    // 任何一次列表重建(换票种/移除乘客)都视为变更触发重组(费用明细不缓存,重组即重算)
    var passengers by remember {
        mutableStateOf(FlowState.selectedPassengers.toList(), referentialEqualityPolicy())
    }
    var insure by remember { mutableStateOf(FlowState.insure) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var typePickerFor by remember { mutableStateOf<Passenger?>(null) }
    val scope = rememberCoroutineScope()

    // 从支付页返回后:清掉下单流程状态,跳到订单页(那里会自动轮询确认出票结果)
    val payLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        FlowState.resetBooking()
        nav.popBackStack("home", false)
        nav.navigate("orders") { launchSingleTop = true }
    }

    LaunchedEffect(shift) {
        if (shift == null) { error = "请先选择班次"; loading = false; return@LaunchedEffect }
        resultOf { Repo.suit(FlowState.startCity?.id ?: "", shift.shiftIdJson()) }
            .onSuccess {
                suit = it
                FlowState.suit = it
                insure = it.shiftInfo.insureDefault != "0" && it.shiftInfo.isInsureFlag != "0"
                // 兜底:从乘车人页选择回来的乘客可能没带票种,补默认票种保证票价计算正确
                val defaultTck = it.shiftInfo.tckTypeList.firstOrNull()
                FlowState.selectedPassengers.forEach { p -> if (p.tckType == null) p.tckType = defaultTck }
                passengers = FlowState.selectedPassengers.toList()
                loading = false
            }
            .onFailure { error = it.message ?: "加载失败"; loading = false }
    }

    val info = suit?.shiftInfo
    val scheme = suit?.scheme
    val maxSell = info?.maxSellNum?.toIntOrNull()?.takeIf { it > 0 } ?: 5

    // 费用明细(与 H5 计算一致,单位:分 -> 元)。
    // 刻意不 remember:Passenger.tckType 是可变属性、不参与 data class equals,
    // remember 的键按 equals 比较,缓存会让「换票种」后的明细与合计停留在旧值;
    // 每次重组直接重算(人数少,开销可忽略)
    val priceLines: List<Triple<String, Double, Int>> = run {
        val lines = mutableListOf<Triple<String, Double, Int>>()
        if (info != null && scheme != null) {
            val seen = linkedMapOf<String, Pair<Double, Int>>() // type -> (单价元, 数量)
            var chargeFeeYuan = 0.0
            passengers.forEach { p ->
                val t = p.tckType
                val unit = ((t?.price?.toDoubleOrNull() ?: 0.0) - (scheme.activityPrice?.toDoubleOrNull() ?: 0.0)
                    + (scheme.speedServiceFee?.toDoubleOrNull() ?: 0.0)) / 100
                t?.charge?.chargeFee?.let { chargeFeeYuan += (it.toDoubleOrNull() ?: 0.0) / 100 }
                val key = t?.type ?: "default"
                val cur = seen[key] ?: (unit to 0)
                seen[key] = unit to cur.second + 1
            }
            val activityLabel = scheme.activityTile?.takeIf { it.isNotEmpty() } ?: "普通出票"
            seen.forEach { (key, v) ->
                val name = passengers.firstOrNull { (it.tckType?.type ?: "default") == key }?.tckType?.name ?: "票"
                lines.add(Triple("$activityLabel($name)", v.first, v.second))
            }
            if (chargeFeeYuan > 0) lines.add(Triple("服务费(合)", chargeFeeYuan, 1))
            if (insure && (info.isInsureFlag ?: "1") != "0") {
                lines.add(Triple("交通乘意险", (info.insureFee?.toDoubleOrNull() ?: 0.0) / 100, passengers.size))
            }
        }
        lines
    }
    val totalPrice = priceLines.sumOf { it.second * it.third }

    YcScaffold(title = "订单填写", onBack = { nav.popBackStack() }) { p ->
        when {
            loading -> LoadingBox()
            error.isNotEmpty() -> ErrorBox(error)
            info != null && scheme != null -> {
                Column(Modifier.fillMaxSize().padding(p)) {
                    LazyColumn(
                        Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 班次信息卡
                        item {
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${info.sendDate} ${if (info.flowDelay) info.sendTime + "前" else info.sendTime}",
                                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        if (info.isExpressway == "1") Tag("高速", MaterialTheme.colorScheme.primary)
                                        if (info.flowDelay) Tag("流水班", MaterialTheme.colorScheme.tertiary)
                                        Spacer(Modifier.weight(1f))
                                        Text("余票 ${info.leftSeatNum ?: "--"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text("${info.stationName} → ${info.endPortName ?: info.portName}", fontSize = 14.sp)
                                    info.remark?.let {
                                        Spacer(Modifier.height(4.dp))
                                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        // 乘车人
                        item {
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("乘车人", fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            "添加乘车人",
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                when {
                                                    !Session.isLoggedIn -> nav.navigate("login")
                                                    passengers.size >= maxSell -> msg = "最多选择 $maxSell 人"
                                                    else -> nav.navigate("passengers/1")
                                                }
                                            },
                                        )
                                    }
                                    if (passengers.isEmpty()) {
                                        Spacer(Modifier.height(10.dp))
                                        Text("请添加乘车人(最多 $maxSell 人)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        passengers.forEach { person ->
                                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("${person.name}  ${person.idcardNo ?: ""}", fontSize = 14.sp)
                                                    val t = person.tckType
                                                    Text(
                                                        "${t?.name ?: "票种"}  ¥${cents(t?.price ?: scheme.discountpPrice)}" +
                                                            (t?.charge?.chargeFee?.takeIf { it != "0" }?.let { " +服务费${cents(it)}" } ?: ""),
                                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                Text(
                                                    "换票种",
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.clickable { typePickerFor = person },
                                                )
                                                IconButton(onClick = {
                                                    passengers = passengers - person
                                                    FlowState.selectedPassengers = passengers.toMutableList()
                                                }) { Icon(Icons.Default.Close, "移除", Modifier.width(20.dp)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 保险
                        if ((info.isInsureFlag ?: "1") != "0") {
                            item {
                                Card(shape = RoundedCornerShape(14.dp)) {
                                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("交通乘意险", fontWeight = FontWeight.Medium)
                                            Text("¥${cents(info.insureFee)}/人 × ${passengers.size}人", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = insure, onCheckedChange = { insure = it })
                                    }
                                }
                            }
                        }

                        // 费用明细
                        item {
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("费用明细", fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(8.dp))
                                    priceLines.forEach { (label, price, count) ->
                                        Row(Modifier.padding(vertical = 3.dp)) {
                                            Text("$label × $count", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                            Text(String.format(Locale.US, "¥%.2f", price * count), fontSize = 13.sp)
                                        }
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    Row {
                                        Text("合计", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text(String.format(Locale.US, "¥%.2f", totalPrice), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 18.sp)
                                    }
                                }
                            }
                        }

                        // 温馨提示(纯文本化,空则不显示,避免出现空白卡片)
                        val remarkText = htmlToText(info.remark)
                        if (remarkText.isNotBlank()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                ) {
                                    Text(text = remarkText, modifier = Modifier.padding(14.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // 底部提交栏
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        if (msg.isNotEmpty()) {
                            Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                        }
                        Button(
                            onClick = {
                                if (!Session.isLoggedIn) { nav.navigate("login"); return@Button }
                                scope.launch {
                                    busy = true; msg = ""
                                    resultOf {
                                        val arr = JSONArray()
                                        passengers.forEach { person ->
                                            arr.put(
                                                JSONObject()
                                                    .put("idType", person.idcardType ?: "1")
                                                    .put("idNo", person.idcardNo ?: "")
                                                    .put("name", person.name ?: "")
                                                    .put("mobile", person.mobile ?: "")
                                                    .put("ticketType", person.tckType?.name ?: "")
                                                    .put("insureStatus", if (insure) "1" else "0"),
                                            )
                                        }
                                        Repo.submitOrder(
                                            startId = FlowState.startCity?.id ?: "",
                                            shiftIdJson = info.shiftIdJson(),
                                            insureCompany = info.insureCompany ?: "",
                                            hasActivity = !scheme.activityId.isNullOrEmpty(),
                                            activityId = scheme.activityId,
                                            passengerListJson = arr.toString(),
                                        )
                                    }.onSuccess { payUrl ->
                                        FlowState.selectedPassengers = passengers.toMutableList()
                                        FlowState.insure = insure
                                        payLauncher.launch(PayActivity.intent(nav.context, payUrl))
                                    }.onFailure { msg = it.message ?: "下单失败" }
                                    busy = false
                                }
                            },
                            enabled = !busy && passengers.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) {
                            Text(
                                if (busy) "提交中…"
                                else String.format(Locale.US, "提交订单  ¥%.2f", totalPrice),
                                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    // 票种选择弹窗
    typePickerFor?.let { target ->
        AlertDialog(
            onDismissRequest = { typePickerFor = null },
            title = { Text("选择票种") },
            text = {
                Column {
                    (info?.tckTypeList ?: emptyList()).forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val newList = passengers.map { if (it === target) it.copy().also { c -> c.tckType = t } else it }
                                passengers = newList
                                FlowState.selectedPassengers = newList.toMutableList()
                                typePickerFor = null
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = target.tckType?.type == t.type, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text("${t.name ?: t.type}", Modifier.weight(1f))
                            Text("¥${cents(t.price)}", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { typePickerFor = null }) { Text("取消") } },
        )
    }
}
