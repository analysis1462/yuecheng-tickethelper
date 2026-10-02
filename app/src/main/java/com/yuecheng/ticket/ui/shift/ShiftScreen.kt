package com.yuecheng.ticket.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.yuecheng.ticket.ui.common.YcScaffold
import com.yuecheng.ticket.ui.common.addDays
import com.yuecheng.ticket.ui.common.dayDiff
import com.yuecheng.ticket.ui.common.weekLabel
import com.yuecheng.ticket.ui.common.yuan
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ShiftScreen(nav: NavController) {
    val start = FlowState.startCity
    val endName = FlowState.endCityName
    var date by remember { mutableStateOf(FlowState.pickDate) }
    var shifts by remember { mutableStateOf<List<Shift>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    var showFilter by remember { mutableStateOf(false) }
    var sortByTime by remember { mutableStateOf(true) }
    var timeAsc by remember { mutableStateOf(true) }
    var priceAsc by remember { mutableStateOf(true) }
    var timeRange by remember { mutableStateOf(0) }   // 0不限 1上午 2下午 3晚上
    var station by remember { mutableStateOf("") }    // 站点过滤
    var company by remember { mutableStateOf("") }
    var onlyTickets by remember { mutableStateOf(false) }
    var onlyExpress by remember { mutableStateOf(false) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    suspend fun load(d: String) {
        loading = true; error = ""
        runCatching {
            Repo.shifts(start?.id ?: "", start?.name ?: "", endName, d)
        }.onSuccess { shifts = it; loading = false }
            .onFailure { error = it.message ?: "加载失败"; loading = false }
    }
    LaunchedEffect(Unit) { load(date) }

    // 客户端过滤 + 排序(与 H5 行为一致)
    val shown = remember(shifts, timeRange, station, company, onlyTickets, onlyExpress, sortByTime, timeAsc, priceAsc) {
        var list = shifts.asSequence()
        if (timeRange > 0) {
            val range = when (timeRange) { 1 -> 0..11; 2 -> 12..17; else -> 18..23 }
            list = list.filter { it.sendTime.substringBefore(':').toIntOrNull() in range }
        }
        if (station.isNotEmpty()) list = list.filter { it.stationName == station }
        if (company.isNotEmpty()) list = list.filter { it.companyName == company }
        if (onlyTickets) list = list.filter { (it.leftSeatNum?.toIntOrNull() ?: 0) > 0 }
        if (onlyExpress) list = list.filter { it.isExpressway == "1" }
        val r = list.toList()
        when {
            sortByTime -> if (timeAsc) r.sortedBy { it.sendTime } else r.sortedByDescending { it.sendTime }
            else -> if (priceAsc) r.sortedBy { it.price?.toDoubleOrNull() ?: 0.0 } else r.sortedByDescending { it.price?.toDoubleOrNull() ?: 0.0 }
        }
    }

    val maxDate = FlowState.startIndex?.let { addDays(it.today, it.presellDay.toLong()) }

    YcScaffold(
        title = endName,
        onBack = { nav.popBackStack() },
        actions = {
            TextButton(onClick = {
                sortByTime = true; timeAsc = !timeAsc
            }) { Text("时间${if (timeAsc) "↑" else "↓"}") }
            TextButton(onClick = {
                sortByTime = false; priceAsc = !priceAsc
            }) { Text("价格${if (priceAsc) "↑" else "↓"}") }
            IconButton(onClick = { showFilter = true }) {
                Icon(Icons.Default.FilterList, "筛选")
            }
        },
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            // 日期切换
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(enabled = date > (FlowState.startIndex?.today ?: date), onClick = {
                    date = addDays(date, -1); FlowState.pickDate = date; scope.launch { load(date) }
                }) { Text("前一天") }
                Spacer(Modifier.weight(1f))
                val diff = dayDiff(date, FlowState.startIndex?.today ?: date)
                Text(
                    "${date} ${if (diff in 0..2) listOf("今天", "明天", "后天")[diff.toInt()] else weekLabel(date)}",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(enabled = maxDate == null || date < maxDate, onClick = {
                    date = addDays(date, 1); FlowState.pickDate = date; scope.launch { load(date) }
                }) { Text("后一天") }
            }

            when {
                loading -> LoadingBox("正在查询班次…")
                error.isNotEmpty() -> ErrorBox(error) { scope.launch { load(date) } }
                shown.isEmpty() -> {
                    // 醒目的空状态(不再是近似白屏)
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.DirectionsBus, null,
                            modifier = Modifier.size(76.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("未查询到班次", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${start?.name ?: ""} → $endName  $date 暂无班次\n可尝试切换日期或其他线路",
                            fontSize = 14.sp, lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            androidx.compose.material3.OutlinedButton(onClick = {
                                date = addDays(date, -1); FlowState.pickDate = date; scope.launch { load(date) }
                            }) { Text("查前一天") }
                            androidx.compose.material3.Button(onClick = {
                                date = addDays(date, 1); FlowState.pickDate = date; scope.launch { load(date) }
                            }) { Text("查后一天") }
                        }
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.material3.TextButton(onClick = { nav.popBackStack() }) {
                            Text("返回修改线路")
                        }
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { "${it.stationId}-${it.shiftNum}-${it.sendTime}-${it.portName}" }) { s ->
                        ShiftCard(s) {
                            FlowState.resetBooking()
                            FlowState.shift = s
                            nav.navigate("orderFill")
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        ModalBottomSheet(onDismissRequest = { showFilter = false }) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("出发时间", fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    listOf(0 to "不限", 1 to "00-12点", 2 to "12-18点", 3 to "18-24点").forEach { (v, label) ->
                        FilterChip(selected = timeRange == v, onClick = { timeRange = v }, label = { Text(label) })
                    }
                }
                Text("出发车站", fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    listOf("" to "不限").plus(shifts.map { it.stationName }.distinct().map { it to it }).forEach { (v, label) ->
                        FilterChip(selected = station == v, onClick = { station = v }, label = { Text(label) })
                    }
                }
                Text("客运公司", fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    listOf("" to "不限").plus(shifts.mapNotNull { it.companyName }.distinct().map { it to it }).forEach { (v, label) ->
                        FilterChip(selected = company == v, onClick = { company = v }, label = { Text(label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = onlyTickets, onCheckedChange = { onlyTickets = it })
                    Text("只看有票", maxLines = 1)
                    Spacer(Modifier.width(12.dp))
                    Checkbox(checked = onlyExpress, onCheckedChange = { onlyExpress = it })
                    Text("只看高速", maxLines = 1)
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            timeRange = 0; station = ""; company = ""; onlyTickets = false; onlyExpress = false
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("清空") }
                    Button(
                        onClick = { showFilter = false },
                        modifier = Modifier.weight(1f),
                    ) { Text("确定", maxLines = 1) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun IconButtonMock() {}

@Composable
private fun ShiftCard(s: Shift, onClick: () -> Unit) {
    val soldOut = (s.leftSeatNum?.toIntOrNull() ?: 0) <= 0
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !soldOut, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.sendTime, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                if (s.flowDelay) {
                    Tag("流水班 ${s.sendTime}前有效", MaterialTheme.colorScheme.tertiary)
                }
                if (s.isExpressway == "1") Tag("高速", MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "¥${yuan(s.price)}",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = if (soldOut) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        if (soldOut) "无票" else "余票 ${s.leftSeatNum}",
                        fontSize = 12.sp,
                        color = if (soldOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${s.stationName} → ${s.endPortName ?: s.portName}",
                fontSize = 14.sp, fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(s.companyName, if ((s.kilometer ?: "").isNotEmpty()) "${s.kilometer}公里" else null)
                        .joinToString(" · "),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            s.remark?.takeIf { it.length <= 22 }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 11.sp, color = color)
    }
}
