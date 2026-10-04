package com.yuecheng.ticket.ui.pay

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import com.yuecheng.ticket.data.ThemePrefs
import com.yuecheng.ticket.ui.theme.YcTheme
import com.yuecheng.ticket.ui.theme.applyWindowBackground

/**
 * 支付容器:加载官方支付网关返回的 payUrl。
 * 拦截 alipay:// / alipays:// / weixin:// 等scheme跳转到原生支付宝/微信完成支付;
 * intent:// 协议按 Chrome 规则解析降级;
 * 顶层导航一律 HTTPS(网关与支付宝等收银台均已实测支持;服务端 302 在 shouldInterceptRequest 兜底重定向);
 * https 跨站跳转需用户确认,防劫持页导去钓鱼站。
 */
class PayActivity : ComponentActivity() {

    private lateinit var webView: WebView

    /** 支付网关主机:顶层导航跳到其他站点时需用户确认(网关走明文 HTTP,跨站跳转是劫持页的主要出路) */
    private var payHost: String? = null

    /** 待确认的跨站跳转 URL(非 null 时弹确认对话框) */
    private var pendingCrossSiteUrl by mutableStateOf<String?>(null)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyWindowBackground(ThemePrefs.dark.value)
        // 服务端返回的 payUrl 是 http://,网关已实测支持 HTTPS:统一升级,页面内容走加密通道
        val url = upgradePayUrl(intent.getStringExtra(EXTRA_URL).orEmpty())
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
        // 只接受 http(s) 支付地址,拒绝 file:// javascript: 等危险 scheme
        if (scheme != "http" && scheme != "https") { finish(); return }
        payHost = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()

        setContent {
            // 跟随应用内夜间开关,而非系统配置(原 YcTheme{} 跟随系统,应用深色+系统浅色时会整页变白)
            YcTheme(ThemePrefs.dark.value) {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        "正在打开支付…",
                        Modifier.padding(16.dp),
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                // 页面内容只需网络加载,禁掉 file:// / content:// 访问面
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                // 支付网关是老页面,https 主体里常嵌 http 资源,默认策略会整页拦掉
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                // Compose 互嵌下防止渲染进程被节流导致滚动卡顿
                                if (android.os.Build.VERSION.SDK_INT >= 29) {
                                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
                                }
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                        handleUrl(view, request.url.toString(), request.isForMainFrame)

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                                        handleUrl(view, url, true)

                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): WebResourceResponse? {
                                        // 服务端 302 重定向不经过 shouldOverrideUrlLoading:
                                        // 顶层 http 请求在此返回 307,把支付流程强制引到 https
                                        if (request.isForMainFrame &&
                                            request.url.scheme?.equals("http", ignoreCase = true) == true
                                        ) {
                                            return WebResourceResponse(
                                                "text/html", "utf-8", 307, "Temporary Redirect",
                                                mapOf("Location" to request.url.buildUpon().scheme("https").toString()),
                                                ByteArrayInputStream(ByteArray(0)),
                                            )
                                        }
                                        return null
                                    }
                                }
                                loadUrl(url)
                            }.also { webView = it }
                        },
                    )
                    FinishBar {
                        setResult(RESULT_OK)
                        finish()
                    }
                    CrossSiteConfirmDialog()
                }
            }
        }
    }

    /**
     * 返回 true 表示已拦截。已知支付 scheme 放行到外部 App,未知 scheme 一律拦截;
     * 顶层导航跳往支付网关以外的站点时先弹用户确认,确认后才加载(子框架不受影响)。
     */
    /** 支付 WebView 顶层导航一律走 HTTPS:网关/支付宝/微信收银台均已实测支持,个别站升级失败会显式报错而非回落明文 */
    private fun upgradePayUrl(url: String): String {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        return if (u.scheme?.lowercase() == "http") u.buildUpon().scheme("https").toString() else url
    }

    private fun handleUrl(view: WebView, url: String, mainFrame: Boolean): Boolean {
        val uri = Uri.parse(url)
        return when (uri.scheme?.lowercase()) {
            null, "http", "https" -> {
                when {
                    // 顶层 http 一律升级为 https(点击/JS 跳转;服务端 302 由 shouldInterceptRequest 兜底)
                    mainFrame && uri.scheme?.lowercase() == "http" -> {
                        view.loadUrl(upgradePayUrl(url))
                        true
                    }
                    // https 跨站跳转需用户确认(收银台/回跳页常在此列),防劫持页导去钓鱼站
                    mainFrame && payHost != null && !uri.host.equals(payHost, ignoreCase = true) -> {
                        pendingCrossSiteUrl = url
                        true
                    }
                    else -> false // WebView 自己处理
                }
            }
            "alipay", "alipays" -> launchExternal(url)
            "weixin", "wxp" -> launchExternal(url)
            "unionpay", "uppay" -> launchExternal(url)
            "intent" -> { openIntentUrl(view, url); true }
            else -> true
        }
    }

    /** intent:// 按 Chrome 规则解析:仅允许拉起可浏览(BROWSABLE)组件,忽略 URL 指定的 component */
    private fun openIntentUrl(view: WebView, url: String) {
        runCatching {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.selector = null
            val fallback = intent.getStringExtra("browser_fallback_url")?.takeIf { it.startsWith("http") }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else if (fallback != null) {
                view.loadUrl(fallback)
            } else {
                toast("未检测到对应支付App,请在网页内继续支付")
            }
        }.onFailure { toast("无法打开外部应用,请在网页内继续支付") }
    }

    private fun launchExternal(url: String): Boolean {
        val ok = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
        if (!ok) toast("未检测到对应支付App,请在网页内继续支付")
        return true
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        webView.takeIf { ::webView.isInitialized }?.apply {
            loadUrl("about:blank")
            destroy()
        }
        super.onDestroy()
    }

    @Composable
    private fun FinishBar(onFinish: () -> Unit) {
        androidx.compose.material3.Surface(shadowElevation = 8.dp) {
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(46.dp),
            ) { Text("我已完成支付", fontWeight = FontWeight.Bold) }
        }
    }

    /** 跨站跳转确认:用户点头后才把目标 URL 交给 WebView 加载 */
    @Composable
    private fun CrossSiteConfirmDialog() {
        val target = pendingCrossSiteUrl ?: return
        AlertDialog(
            onDismissRequest = { pendingCrossSiteUrl = null },
            title = { Text("即将离开支付网关") },
            text = { Text("页面要跳转到:${Uri.parse(target).host ?: "未知站点"}\n支付过程中如非您主动操作,请选择取消。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingCrossSiteUrl = null
                    if (::webView.isInitialized) webView.loadUrl(target)
                }) { Text("继续打开") }
            },
            dismissButton = { TextButton(onClick = { pendingCrossSiteUrl = null }) { Text("取消") } },
        )
    }

    companion object {
        private const val EXTRA_URL = "pay_url"

        /** 供 registerForActivityResult 使用,便于支付返回后刷新订单状态 */
        fun intent(context: android.content.Context, url: String): Intent =
            Intent(context, PayActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
