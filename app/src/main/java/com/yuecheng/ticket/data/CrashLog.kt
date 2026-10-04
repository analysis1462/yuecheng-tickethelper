package com.yuecheng.ticket.data

import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志:未捕获异常写入 filesDir/logs,保留最近 5 份;
 * 个人中心可一键导出(诊断信息 + 全部崩溃记录)。
 */
object CrashLog {
    private const val MAX_FILES = 5
    private val dirName = "logs"

    fun install(context: android.content.Context) {
        val dir = File(context.filesDir, dirName)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                dir.mkdirs()
                // 只留最近 MAX_FILES 份,按文件名(含时间戳)降序删除多余
                dir.listFiles()?.sortedByDescending { it.name }?.drop(MAX_FILES - 1)?.forEach { it.delete() }
                val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                File(dir, "crash-$ts.txt").writeText(header() + "\n" + android.util.Log.getStackTraceString(e))
            }
            previous?.uncaughtException(t, e)
        }
    }

    private fun header(): String = buildString {
        appendLine("app: ${com.yuecheng.ticket.BuildConfig.APPLICATION_ID} ${com.yuecheng.ticket.BuildConfig.VERSION_NAME} (${com.yuecheng.ticket.BuildConfig.VERSION_CODE})")
        appendLine("android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
    }

    fun files(context: android.content.Context): List<File> =
        File(context.filesDir, dirName).listFiles()?.filter { it.isFile }?.sortedByDescending { it.name } ?: emptyList()

    /** 导出文本:诊断头 + 每份崩溃日志;无崩溃时也有基础信息 */
    fun exportText(context: android.content.Context): String = buildString {
        appendLine(header())
        val fs = files(context)
        appendLine("crash count: ${fs.size}")
        fs.forEach { f ->
            appendLine()
            appendLine("===== ${f.name} =====")
            runCatching { append(f.readText()) }
        }
    }
}
