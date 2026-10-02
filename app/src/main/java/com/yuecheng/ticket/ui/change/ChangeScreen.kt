package com.yuecheng.ticket.ui.change

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yuecheng.ticket.data.FlowState
import com.yuecheng.ticket.data.Repo
import com.yuecheng.ticket.data.Shift
import com.yuecheng.ticket.ui.common.ErrorBox
import com.yuecheng.ticket.ui.common.LoadingBox
import com.yuecheng.ticket.ui.common.EmptyBox
import com.yuecheng.ticket.ui.common.YcScaffold
import com.yuecheng.ticket.ui.common.addDays
import com.yuecheng.ticket.ui.common.weekLabel
import com.yuecheng.ticket.ui.common.yuan
import com.yuecheng.ticket.ui.shift.Tag
import kotlinx.coroutines.launch

/** 改签:从订单详情进入,携带订单号;原票信息由 OrderDetailScreen 存入 FlowState */
@Composable
fun ChangeScreen(nav: NavController, orderId: String) {
    val order = FlowState.changeOrder
    var date by remember { mutableStateOf(order?.sendDate ?: FlowState.pickDate) }
    var shifts by remember { mutableStateOf<List<Shift>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<Shift?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(orderId) {
        val o = order ?: run { error = "缺少订单信息"; loading = false; return@LaunchedEffect }
        loading = true
        runCatching {
            Repo.changeShifts(orderId, o.subOrderId, date, o.seatNo)
        }.onSuccess { shifts = it; loading = false }
            .onFailure { error = it.message ?: "加载失败"; loading = false }
    }

    val maxDate = FlowState.startIndex?.let { addDays(it.today, it.presellDay.toLong()) }

    YcScaffold(title = "改签", onBack = { nav.popBackStack() }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(enabled = date > (FlowState.startIndex?.today ?: date), onClick = {
                    date = addDays(date, -1)
                    val o = order
                    scope.launch {
                        loading = true
                        if (o != null) {
                            runCatching { Repo.changeShifts(orderId, o.subOrderId, date, o.seatNo) }
                                .onSuccess { shifts = it; loading = false }
                                .onFailure { error = it.message ?: "加载失败"; loading = false }
                        } else loading = false
                    }
                }) { Text("前一天") }
                Spacer(Modifier.weight(1f))
                Text("$date ${weekLabel(date)}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(enabled = maxDate == null || date < maxDate, onClick = {
                    date = addDays(date, 1)
                    val o = order ?: return@TextButton
                    scope.launch {
                        loading = true
                        runCatching { Repo.changeShifts(orderId, o.subOrderId, date, o.seatNo) }
                            .onSuccess { shifts = it; loading = false }
                            .onFailure { error = it.message ?: "加载失败"; loading = false }
                    }
                }) { Text("后一天") }
            }

            when {
                loading -> LoadingBox()
                error.isNotEmpty() -> ErrorBox(error)
                shifts.isEmpty() -> EmptyBox("该日期无可改签班次")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shifts, key = { "${it.stationId}-${it.shiftNum}-${it.sendTime}" }) { s ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clickable { target = s },
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(s.sendTime, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.padding(start = 8.dp))
                                        if (s.isExpressway == "1") Tag("高速", MaterialTheme.colorScheme.primary)
                                        if (s.isFlow == "1") Tag("流水班", MaterialTheme.colorScheme.tertiary)
                                    }
                                    Text(
                                        "${s.stationName} → ${s.endPortName ?: s.portName}   余票${s.leftSeatNum}",
                                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("¥${yuan(s.price)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    target?.let { s ->
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("确认改签") },
            text = { Text("改签至 ${s.sendDate} ${s.sendTime} ${s.stationName} → ${s.endPortName ?: s.portName}?\n改签规则以车站公示为准,可能产生差价。") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching {
                                Repo.change(orderId, order!!.subOrderId, order.seatNo, s.sendDate, s)
                            }.onSuccess {
                                target = null
                                FlowState.changeOrder = null
                                nav.popBackStack()
                            }.onFailure { error = it.message ?: "改签失败"; target = null }
                            busy = false
                        }
                    }
                ) { Text(if (busy) "改签中…" else "确认") }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("取消") } },
        )
    }
}
