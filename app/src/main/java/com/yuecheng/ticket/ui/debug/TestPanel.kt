package com.yuecheng.ticket.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuecheng.ticket.BuildConfig
import com.yuecheng.ticket.data.CrashLog
import com.yuecheng.ticket.data.DepartureReminder
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * 隐藏测试面板(长按首页副标题"退票"5秒进入):
 * 验证各通知通道、倒计时胶囊、系统闹钟写入与崩溃日志,并展示设备/权限/提醒状态。
 */
@Composable
fun TestPanelDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var confirmCrash by remember { mutableStateOf(false) }
    var infoTick by remember { mutableStateOf(0) }

    fun note(text: String) { msg = text }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("测试模式") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                infoTick // 刷新操作后重建状态区
                val plans = DepartureReminder.allPlans(ctx)
                Text(
                    buildString {
                        appendLine("悦程购票 ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
                        appendLine("Android ${android.os.Build.VERSION.RELEASE}(SDK ${android.os.Build.VERSION.SDK_INT})")
                        appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        appendLine("崩溃记录:${CrashLog.files(ctx).size} 条")
                        appendLine("精确闹钟:${DepartureReminder.exactAlarmStatus(ctx)}")
                        appendLine("已设提醒:${plans.size} 项")
                        plans.forEach { appendLine(" · ${it.label()} ${it.route}") }
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text("通知", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                TestAction("发送普通通知(真实格式)") {
                    scope.launch {
                        DepartureReminder.sendTestNotification(ctx, false)
                        note("已发送:用已设提醒/最近订单渲染,含检票口座位")
                    }
                }
                TestAction("发送闹钟通知(响铃循环)") {
                    scope.launch {
                        DepartureReminder.sendTestNotification(ctx, true)
                        note("闹钟通知已发送,点开或划掉才会停")
                    }
                }
                TestAction("倒计时通知(2分钟胶囊)") {
                    scope.launch {
                        DepartureReminder.sendTestCountdown(ctx)
                        note("倒计时已发送,看通知栏/状态栏胶囊")
                    }
                }
                TestAction("移除所有通知") {
                    DepartureReminder.removeAllNotifications(ctx)
                    note("已移除本应用全部通知")
                }
                HorizontalDivider()
                Text("闹钟与权限", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                TestAction("写入系统时钟闹钟(2分钟后)") {
                    val err = DepartureReminder.createTestSystemAlarm(ctx)
                    note(err ?: "已写入,打开时钟App查看")
                }
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    TestAction("打开精确闹钟权限设置") {
                        runCatching {
                            ctx.startActivity(android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:${ctx.packageName}"),
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            note("已打开设置页")
                        }.onFailure { note("打开失败:${it.message}") }
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    TestAction("打开应用通知设置") {
                        runCatching {
                            ctx.startActivity(android.content.Intent(
                                android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                            ).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            note("已打开设置页")
                        }.onFailure { note("打开失败:${it.message}") }
                    }
                }
                TestAction("长震动(0.8秒)") {
                    val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
                        val vm = ctx.getSystemService(android.os.VibratorManager::class.java)
                        vm?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    }
                    if (vibrator != null) {
                        // 个别 ROM 权限校验异常时不让它带崩界面;VibrationEffect 是 26+,更早用时长重载
                        runCatching {
                            if (android.os.Build.VERSION.SDK_INT >= 26) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(800, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(800L)
                            }
                            note("已触发长震动")
                        }.onFailure { note("震动失败:${it.message}") }
                    } else note("设备无震动器")
                }
                HorizontalDivider()
                Text("清理", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                TestAction("清空全部提醒计划") {
                    DepartureReminder.clearAllPlans(ctx)
                    infoTick++
                    note("已清空全部提醒计划(系统时钟里的闹钟需自行删除)")
                }
                TestAction("取消所有后台任务") {
                    androidx.work.WorkManager.getInstance(ctx).cancelAllWork()
                    infoTick++
                    note("已取消 WorkManager 全部任务")
                }
                HorizontalDivider()
                TestAction("触发测试崩溃(验证崩溃日志)", danger = true) {
                    confirmCrash = true
                }
                if (msg.isNotEmpty()) {
                    Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("退出测试模式") }
        },
    )

    if (confirmCrash) {
        AlertDialog(
            onDismissRequest = { confirmCrash = false },
            title = { Text("确认触发崩溃?") },
            text = { Text("应用会立即闪退,崩溃堆栈写入本地日志;重开后可在个人中心导出验证。") },
            confirmButton = {
                TextButton(onClick = {
                    throw RuntimeException("YcTestCrash: 测试模式主动触发的崩溃")
                }) { Text("崩溃", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCrash = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TestAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}
