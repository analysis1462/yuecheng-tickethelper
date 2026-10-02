package com.yuecheng.ticket.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yuecheng.ticket.data.Session
import com.yuecheng.ticket.ui.common.YcScaffold

@Composable
fun ProfileScreen(nav: NavController) {
    Session.loginTick.value // 登录态变化时刷新

    YcScaffold(title = "个人中心", onBack = { nav.popBackStack() }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp)) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(if (Session.isLoggedIn) "已登录" else "未登录", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            if (Session.isLoggedIn) "账号:${Session.displayMobile}" else "点击下方登录账号",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Card(shape = RoundedCornerShape(16.dp)) {
                Column {
                    ProfileRow(Icons.Default.Groups, "乘车人管理", "添加/编辑常用乘车人") { nav.navigate("passengers/0") }
                    HorizontalDivider()
                    ProfileRow(Icons.Default.ConfirmationNumber, "我的订单", "支付 / 改签 / 退票") { nav.navigate("orders") }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (Session.isLoggedIn) {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                    Session.logout()
                }) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout, null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("退出登录", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                    nav.navigate("login")
                }) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("去登录", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
