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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import com.yuecheng.ticket.data.CrashLog
import com.yuecheng.ticket.data.Session
import com.yuecheng.ticket.ui.common.YcScaffold

@Composable
fun ProfileScreen(nav: NavController) {
    Session.loginTick.value // 登录态变化时刷新
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 诊断日志导出(SAF 存文件,免存储权限)
    val logExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                nav.context.contentResolver.openOutputStream(uri)?.use {
                    it.write(CrashLog.exportText(nav.context).toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("无法写入文件")
            }.onSuccess {
                Toast.makeText(nav.context, "日志已导出", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(nav.context, "导出失败:${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val crashCount = CrashLog.files(nav.context).size

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
                    HorizontalDivider()
                    ProfileRow(
                        Icons.Default.FolderOpen, "诊断日志",
                        if (crashCount > 0) "检测到 $crashCount 条崩溃记录,点击导出" else "无崩溃记录,可导出基础信息",
                    ) { logExporter.launch("yuecheng-diag.txt") }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (Session.isLoggedIn) {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                    showLogoutConfirm = true
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

            // 底部:项目地址(GitHub,地址待补充)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Code, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text("项目地址:GitHub", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("开源地址待补充", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
    }

    // 退出登录二次确认(会清掉保存的凭据,防误触)
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("确定退出当前账号吗?退出后需重新登录。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    Session.logout()
                }) { Text("退出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") } },
        )
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
