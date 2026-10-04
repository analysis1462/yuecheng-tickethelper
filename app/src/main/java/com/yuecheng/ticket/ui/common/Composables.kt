package com.yuecheng.ticket.ui.common

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 价格:分 -> 元字符串(Locale.US 固定小数点,避免随设备区域变化) */
fun cents(cents: String?): String {
    val v = cents?.toDoubleOrNull() ?: return "-"
    return String.format(Locale.US, "%.2f", v / 100)
}

/** 价格:接口已给元的值,规整显示 */
fun yuan(v: String?): String {
    val d = v?.toDoubleOrNull() ?: return "-"
    return if (d == d.toLong().toDouble()) d.toLong().toString() else String.format(Locale.US, "%.2f", d)
}

/** 简易 HTML 转纯文本(备注/规则字段常带内联样式);退改签规则与班次备注共用 */
fun htmlToText(html: String?): String = html
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

/** 证件号脱敏展示:保留前 4 后 4,其余打码 */
fun maskId(id: String?): String = when {
    id == null -> "--"
    id.length > 8 -> id.take(4) + "****" + id.takeLast(4)
    else -> id
}

/** 日期统一按 yyyy-MM-dd 解析;接口偶发空值时不做崩溃处理,交由各页面回退 */
private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

fun addDays(date: String, days: Long): String =
    parseDate(date)?.plusDays(days)?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: date

fun dayDiff(a: String, b: String): Long {
    val da = parseDate(a) ?: return 0
    val db = parseDate(b) ?: return 0
    return da.toEpochDay() - db.toEpochDay()
}

fun weekLabel(date: String): String {
    val d = parseDate(date) ?: return ""
    return arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1]
}

// 发车时间解析已移至 data 包:com.yuecheng.ticket.data.departureEpochMillis(通知构建也要用)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YcScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        bottomBar = bottomBar,
    ) { padding ->
        content(padding)
    }
}

@Composable
fun LoadingBox(text: String = "加载中…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onRetry != null) {
                    Button(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyBox(text: String = "暂无数据") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 未登录提示:"登录"二字蓝色下划线,点击任意位置跳转登录页 */
@Composable
fun LoginPrompt(onLogin: () -> Unit) {
    Box(
        Modifier.fillMaxSize().clickable { onLogin() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Person, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    ) { append("您还未登录,请先") }
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) { append("登录") }
                },
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text("点击此处前往登录", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
