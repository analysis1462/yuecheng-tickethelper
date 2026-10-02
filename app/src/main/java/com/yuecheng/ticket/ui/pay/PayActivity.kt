package com.yuecheng.ticket.ui.pay

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuecheng.ticket.ui.theme.YcTheme

/**
 * 支付容器:加载官方支付网关返回的 payUrl。
 * 拦截 alipay:// / alipays:// / weixin:// 等scheme跳转到原生支付宝/微信完成支付;
 * intent:// 协议按 Chrome 规则解析降级。
 */
class PayActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        if (url.isEmpty()) { finish(); return }

        setContent {
            YcTheme {
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
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                        handleUrl(view, request.url.toString())

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                                        handleUrl(view, url)
                                }
                                loadUrl(url)
                            }.also { webView = it }
                        },
                    )
                    FinishBar {
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }
        }
    }

    private fun handleUrl(view: WebView, url: String): Boolean {
        val uri = Uri.parse(url)
        return when (uri.scheme) {
            "http", "https", "" -> false // WebView 自己处理
            "alipay", "alipays" -> launchExternal(url)
            "weixin", "wxp" -> launchExternal(url)
            "intent" -> {
                runCatching {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    } else if (fallback != null) {
                        view.loadUrl(fallback)
                    } else {
                        launchExternal("https://uri.amap.com") // 无法跳转时降级
                    }
                }
                true
            }
            else -> launchExternal(url)
        }
    }

    private fun launchExternal(url: String): Boolean {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { e ->
            if (e is ActivityNotFoundException) {
                android.widget.Toast.makeText(this, "未检测到对应支付App,请在网页内继续支付", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        return true
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

    companion object {
        private const val EXTRA_URL = "pay_url"
        fun start(context: android.content.Context, url: String) {
            context.startActivity(Intent(context, PayActivity::class.java).putExtra(EXTRA_URL, url))
        }
    }
}
