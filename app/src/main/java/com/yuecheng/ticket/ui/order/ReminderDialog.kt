package com.yuecheng.ticket.ui.order

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuecheng.ticket.data.DepartureReminder
import com.yuecheng.ticket.data.ReminderPlan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 发车提醒配置弹窗:
 *  - 多选提前量(已过期的选项禁用);
 *  - 闹钟开关:开=写入系统时钟真实闹钟,关=仅普通通知;
 *  - 保存失败(如系统闹钟写入出错)在弹窗内红色报错;
 *  - "移除提醒"=移除此票全部提醒计划并清除本应用通知(幂等);全不勾选保存等同移除。
 */
@Composable
fun ReminderDialog(
    route: String,
    departureMillis: Long,
    initial: ReminderPlan?,
    onSave: (leads: List<Long>, alarm: Boolean) -> String?,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial?.leads?.toSet() ?: setOf(120L, 60L)) }
    var alarm by remember { mutableStateOf(initial?.alarm ?: false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发车提醒") },
        text = {
            Column {
                Text(route, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "发车时间:${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(departureMillis))}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("提前多久提醒(可多选)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                DepartureReminder.leadOptions().forEach { minutes ->
                    val expired = departureMillis - minutes * 60_000 <= System.currentTimeMillis()
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = minutes in selected,
                            enabled = !expired,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + minutes else selected - minutes
                            },
                        )
                        Text(
                            DepartureReminder.leadLabel(minutes) + "前" + if (expired) "(已过)" else "",
                            fontSize = 15.sp,
                            color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("闹钟响铃", fontWeight = FontWeight.Medium)
                        Text(
                            "开启会在系统时钟App创建真实闹钟(系统负责响铃,取消提醒后需在时钟App自行删除);关闭则仅发送应用通知",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = alarm, onCheckedChange = { alarm = it })
                }
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            // 全不勾选时保存 = 移除此票的全部提醒(幂等,无需报错)
            TextButton(
                onClick = {
                    if (selected.isEmpty()) {
                        onRemove()
                    } else {
                        val err = onSave(selected.toList(), alarm)
                        if (err != null) error = err
                    }
                },
            ) {
                Text(
                    when {
                        selected.isEmpty() -> "移除提醒"
                        initial == null -> "设置"
                        else -> "保存"
                    },
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) {
                    Text("移除提醒", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}
