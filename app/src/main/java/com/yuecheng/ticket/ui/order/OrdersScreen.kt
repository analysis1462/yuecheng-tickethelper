package com.yuecheng.ticket.ui.order

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yuecheng.ticket.data.OrderSummary
import com.yuecheng.ticket.data.Repo
import com.yuecheng.ticket.data.Session
import com.yuecheng.ticket.data.statusLabelOf
import com.yuecheng.ticket.ui.common.ErrorBox
import com.yuecheng.ticket.ui.common.LoadingBox
import com.yuecheng.ticket.ui.common.EmptyBox
import com.yuecheng.ticket.ui.common.YcScaffold
import com.yuecheng.ticket.ui.common.cents
import com.yuecheng.ticket.ui.pay.PayActivity
import com.yuecheng.ticket.ui.shift.Tag
import kotlinx.coroutines.launch

@Composable
fun OrdersScreen(nav: NavController) {
    var orders by remember { mutableStateOf<List<OrderSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) } // 0全部 1待支付 2待出行
    var busyOrder by remember { mutableStateOf("") }
    var needLogin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true; error = ""; needLogin = false
            runCatching { Repo.orderList() }
                .onSuccess { orders = it; loading = false }
                .onFailure {
                    loading = false
                    if (it is com.yuecheng.ticket.data.NeedLoginException) {
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

    val filtered = when (tab) {
        1 -> orders.filter { it.status == "0" || it.status == "2" }
        2 -> orders.filter { it.status == "4" || it.status == "1" || it.status == "3" }
        else -> orders
    }

    YcScaffold(title = "我的订单", onBack = { nav.popBackStack() }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            TabRow(selectedTabIndex = tab) {
                listOf("全部", "待支付", "待出行").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when {
                loading -> LoadingBox()
                needLogin -> com.yuecheng.ticket.ui.common.LoginPrompt { nav.navigate("login") }
                error.isNotEmpty() -> ErrorBox(error) { reload() }
                filtered.isEmpty() -> EmptyBox("暂无订单")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.orderId }) { o ->
                        OrderCard(
                            o = o,
                            busy = busyOrder == o.orderId,
                            onPay = {
                                scope.launch {
                                    busyOrder = o.orderId
                                    runCatching { Repo.payOrder(o.orderId) }
                                        .onSuccess { PayActivity.start(nav.context, it) }
                                        .onFailure { err -> error = err.message ?: "支付失败" }
                                    busyOrder = ""
                                }
                            },
                            onCancel = {
                                scope.launch {
                                    busyOrder = o.orderId
                                    runCatching { Repo.cancelOrder(o.orderId) }
                                        .onSuccess { reload() }
                                        .onFailure { err -> error = err.message ?: "取消失败" }
                                    busyOrder = ""
                                }
                            },
                            onClick = { nav.navigate("orderDetail/${o.orderId}") },
                        )
                    }
                    item {
                        TextButton(onClick = { reload() }, modifier = Modifier.fillMaxWidth()) { Text("刷新") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(o: OrderSummary, busy: Boolean, onPay: () -> Unit, onCancel: () -> Unit, onClick: () -> Unit) {
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
                    when (o.status) {
                        "4" -> MaterialTheme.colorScheme.tertiary
                        "0", "2" -> MaterialTheme.colorScheme.secondary
                        "6", "7" -> MaterialTheme.colorScheme.error
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
                if (o.status == "0" || o.status == "2") {
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
