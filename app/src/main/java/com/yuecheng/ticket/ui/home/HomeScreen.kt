package com.yuecheng.ticket.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.widget.Toast
import kotlinx.coroutines.launch
import com.yuecheng.ticket.data.FavLines
import com.yuecheng.ticket.data.FlowState
import com.yuecheng.ticket.data.IndexInfo
import com.yuecheng.ticket.data.Repo
import com.yuecheng.ticket.data.SearchHistory
import com.yuecheng.ticket.data.Session
import com.yuecheng.ticket.data.StartCity
import com.yuecheng.ticket.data.ThemePrefs
import com.yuecheng.ticket.data.resultOf
import com.yuecheng.ticket.ui.common.ErrorBox
import com.yuecheng.ticket.ui.common.LoadingBox
import com.yuecheng.ticket.ui.common.addDays
import com.yuecheng.ticket.ui.common.dayDiff
import com.yuecheng.ticket.ui.common.weekLabel
import com.yuecheng.ticket.ui.debug.TestPanelDialog

@Composable
fun HomeScreen(nav: NavController) {
    Session.loginTick.value // 订阅登录态变化
    var info by remember { mutableStateOf<IndexInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val uiScope = rememberCoroutineScope()

    suspend fun load() {
        loading = true; error = ""
        resultOf { Repo.index() }
            .onSuccess {
                info = it
                FlowState.startIndex = it
                if (FlowState.startCity == null) FlowState.startCity = it.defaultFrom
                if (FlowState.pickDate.isEmpty()) FlowState.pickDate = it.defaultDay
                loading = false
            }
            .onFailure { error = it.message ?: "加载失败"; loading = false }
    }

    LaunchedEffect(Unit) { load() }

    val idx = info
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        // 顶部渐变头部
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF1B6FE8))))
                .padding(top = 60.dp, bottom = 28.dp, start = 20.dp, end = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("悦程购票", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    // 副标题:"退票"二字是隐藏测试模式的入口(长按5秒)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("汽车票查询 · 购票 · 改签 · ", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                        TestModeEntryText()
                    }
                }
                IconButton(onClick = { ThemePrefs.toggle() }) {
                    Icon(
                        if (ThemePrefs.dark.value) Icons.Default.LightMode
                        else Icons.Default.DarkMode,
                        "夜间模式", tint = Color.White,
                    )
                }
                IconButton(onClick = { nav.navigate(if (Session.isLoggedIn) "profile" else "login") }) {
                    Icon(Icons.Default.Person, "个人中心", tint = Color.White)
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                loading -> LoadingBox()
                error.isNotEmpty() -> ErrorBox(error) { uiScope.launch { load() } }
                idx != null -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        SearchCard(nav, idx)
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EntryCard(
                                Modifier.weight(1f),
                                icon = { Icon(Icons.Default.ConfirmationNumber, null, tint = Color(0xFF0B57D0)) },
                                title = "我的订单",
                                sub = "支付 / 改签 / 退票",
                            ) { nav.navigate("orders") }
                            EntryCard(
                                Modifier.weight(1f),
                                icon = {
                                    Icon(Icons.Default.Person, null, tint = Color(0xFF1E8E3E))
                                },
                                title = if (Session.isLoggedIn) "已登录" else "未登录",
                                sub = if (Session.isLoggedIn) Session.displayMobile else "点击登录账号",
                            ) {
                                nav.navigate(if (Session.isLoggedIn) "profile" else "login")
                            }
                        }
                    }
                }
            }
        }

        // 免责声明(主页底部)
        Text(
            "免责声明:本应用仅为购票辅助工具,不提供也不支持任何抢票、占票、倒卖加价等行为;" +
                "请遵守客运站规定合理购票,班次、票价及车票信息以客运站官方发布为准。",
            fontSize = 10.sp,
            lineHeight = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
private fun SearchCard(nav: NavController, idx: IndexInfo) {
    // uiTick 变化驱动重组(交换城市/清除历史后刷新界面)
    var uiTick by remember { mutableStateOf(0) }
    val start = remember(uiTick) { FlowState.startCity }
    val end = remember(uiTick) { FlowState.endCityName }
    var date by remember { mutableStateOf(FlowState.pickDate.ifEmpty { idx.defaultDay }.ifEmpty { idx.today }) }
    var history by remember(uiTick) { mutableStateOf(SearchHistory.load()) }
    var favs by remember(uiTick) { mutableStateOf(FavLines.load()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            // 出发 / 到达
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).clickable { nav.navigate("startPick") },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (start == null) "选择出发地" else start.name,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text("出发地", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier
                        .size(50.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .clickable {
                            // 交换:出发地与到达地互换。到达地可能是"莱芜(高速直达)"这类
                            // 带后缀的名字,起点表里不一定同名,做三级匹配:精确→去括号→前缀
                            if (start != null && end.isNotEmpty()) {
                                val oldStart = start
                                scope.launch {
                                    val all = FlowState.cachedStartPoints
                                        ?: resultOf { Repo.startPoints().flatMap { it.second } }.getOrNull()
                                    val baseEnd = end.substringBefore("(").trim()
                                    val matched = all?.firstOrNull { it.name == end }
                                        ?: all?.firstOrNull { it.name == baseEnd }
                                        ?: all?.firstOrNull { baseEnd.startsWith(it.name) }
                                    if (matched != null) {
                                        FlowState.startCity = matched
                                        FlowState.endCityName = oldStart.name
                                        // 到达地拼音优先从城市表取(历史进入时出发地可能没拼音)
                                        FlowState.endCityPinyin =
                                            all?.firstOrNull { it.name == oldStart.name }?.pinyin
                                                ?: oldStart.pinyin
                                        uiTick++
                                    } else {
                                        Toast.makeText(
                                            nav.context, "「$end」暂不支持作为出发地",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.SwapVert, "交换",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.rotate(45f),
                    )
                }
                Column(
                    Modifier.weight(1f).clickable { nav.navigate("endPick") },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (end.isEmpty()) "选择到达地" else end,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = if (end.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text("目的地", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(18.dp))

            // 日期行(点击弹出日历选择)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val minDate = idx.today
                val maxDate = addDays(idx.today, idx.presellDay.toLong())
                DateChip("前一天", date > minDate) { date = addDays(date, -1) }
                Column(
                    Modifier.weight(1f).clickable { showDatePicker = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val diff = dayDiff(date, idx.today)
                    val label = when (diff) {
                        0L -> "今天"
                        1L -> "明天"
                        2L -> "后天"
                        else -> weekLabel(date)
                    }
                    Text(
                        if (date.length >= 10) "${date.substring(5).replace('-', '月')}日 · $label" else label,
                        fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            Icons.Default.CalendarMonth, "选择日期",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DateChip("后一天", date < maxDate) { date = addDays(date, 1) }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    if (start != null && end.isNotEmpty()) {
                        FlowState.pickDate = date
                        SearchHistory.add(start.id, start.name, end, FlowState.endCityPinyin)
                        history = SearchHistory.load()
                        nav.navigate("shifts")
                    }
                },
                enabled = start != null && end.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                Text("查询车票", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // 收藏线路:点击直接查询;长按移除;出发到达齐备时可一键收藏
            if (favs.isNotEmpty() || (start != null && end.isNotEmpty())) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "收藏线路",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        favs.forEach { f ->
                            Text(
                                "★ ${f.startName}—${f.endName}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                    .combinedClickable(
                                        onClick = {
                                            FlowState.startCity = StartCity(f.startId, f.startName, "", "", 0)
                                            FlowState.endCityName = f.endName
                                            FlowState.endCityPinyin = f.endPinyin
                                            FlowState.pickDate = date
                                            nav.navigate("shifts")
                                        },
                                        onLongClick = {
                                            FavLines.remove(f.startId, f.endName)
                                            favs = FavLines.load()
                                            Toast.makeText(nav.context, "已移除「${f.startName}—${f.endName}」", Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        if (start != null && end.isNotEmpty()) {
                            val isFav = FavLines.isFav(start.id, end)
                            Text(
                                if (isFav) "★ 已收藏" else "☆ 收藏此线路",
                                fontSize = 13.sp,
                                color = if (isFav) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondary.copy(alpha = if (isFav) 0.08f else 0.12f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .clickable {
                                        if (isFav) {
                                            FavLines.remove(start.id, end)
                                        } else {
                                            FavLines.add(start.id, start.name, end, FlowState.endCityPinyin)
                                        }
                                        favs = FavLines.load()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            // 历史记录:点击直接进入该车次列表
            if (history.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "历史记录",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        history.forEach { h ->
                            Text(
                                "${h.startName}—${h.endName}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                    .clickable {
                                        FlowState.startCity = StartCity(h.startId, h.startName, "", "", 0)
                                        FlowState.endCityName = h.endName
                                        FlowState.endCityPinyin = h.endPinyin
                                        FlowState.pickDate = date
                                        nav.navigate("shifts")
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        // 清除历史记录
                        Text(
                            "清除",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                .clickable {
                                    SearchHistory.clear()
                                    history = emptyList()
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }

    // 日期选择日历(限预售期内可选)
    if (showDatePicker) {
        val zone = java.time.ZoneOffset.UTC
        val todayDate = java.time.LocalDate.parse(idx.today)
        val lastDate = todayDate.plusDays(idx.presellDay.toLong())
        val initMillis = runCatching {
            java.time.LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initMillis,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(zone).toLocalDate()
                    return !d.isBefore(todayDate) && !d.isAfter(lastDate)
                }
            },
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        var d = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()
                        if (d < idx.today) d = idx.today
                        val maxD = addDays(idx.today, idx.presellDay.toLong())
                        if (d > maxD) d = maxD
                        date = d
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            androidx.compose.material3.DatePicker(
                state = pickerState,
                title = { Text("选择出发日期", modifier = Modifier.padding(start = 20.dp, top = 16.dp)) },
                showModeToggle = false,
            )
        }
    }
}

@Composable
private fun DateChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 14.sp,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun EntryCard(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    title: String,
    sub: String,
    onClick: () -> Unit,
) {
    Card(modifier = modifier.clickable { onClick() }, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** 副标题里的"退票":长按满 5 秒进入隐藏测试模式,按住时下方有进度条反馈 */
@Composable
private fun TestModeEntryText() {
    var holding by remember { mutableStateOf(false) }
    var showTestPanel by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current

    // 进度由动画时钟驱动(按住 5 秒线性推满),不再用定时轮询刷重组;松手快速归零
    val holdProgress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = if (holding) tween(5000, easing = LinearEasing) else tween(150),
        label = "holdProgress",
    )

    Box {
        Text(
            "退票",
            color = Color(0xCCFFFFFF),
            fontSize = 13.sp,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val start = System.currentTimeMillis()
                        holding = true
                        tryAwaitRelease()
                        holding = false
                        if (System.currentTimeMillis() - start >= 5000) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            showTestPanel = true
                        }
                    },
                )
            },
        )
        if (holdProgress > 0f) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { holdProgress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
                trackColor = androidx.compose.ui.graphics.Color.Transparent,
            )
        }
    }
    if (showTestPanel) {
        TestPanelDialog { showTestPanel = false }
    }
}
