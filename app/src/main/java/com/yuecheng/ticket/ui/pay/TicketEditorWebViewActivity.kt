package com.yuecheng.ticket.ui.pay

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/** 交互式票样编辑器(全功能 WebView 版) */
class TicketEditorWebViewActivity : ComponentActivity() {

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val payloadJson = intent.getStringExtra("payload") ?: "{}"
        val injectedHtml = buildInjectedHtml(payloadJson)

        setContent {
            androidx.compose.material3.MaterialTheme {
                var canGoBack by remember { mutableStateOf(false) }
                val webView = remember {
                    WebView(this).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                if (request.url.lastPathSegment == "index.html") {
                                    return WebResourceResponse(
                                        "text/html",
                                        "utf-8",
                                        ByteArrayInputStream(injectedHtml.toByteArray(Charsets.UTF_8)),
                                    )
                                }
                                return null
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                canGoBack = view.canGoBack()
                            }
                        }
                        loadUrl("file:///android_asset/ticket_editor/index.html")
                    }
                }

                BackHandler(enabled = true) {
                    if (webView.canGoBack()) webView.goBack() else finish()
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("票样编辑器") },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (webView.canGoBack()) webView.goBack() else finish()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { webView },
                        )
                    }
                }
            }
        }
    }

    private fun buildInjectedHtml(payloadJson: String): String {
        val html = assets.open("ticket_editor/index.html").readBytes().toString(Charsets.UTF_8)
        // 注入全局变量,编辑器就绪后自动填表(其自身初始化会覆盖 localStorage 草稿,故用延时填表)
        val injection = "<script>window.__busPayload = $payloadJson;" +
            "window.addEventListener('load',function(){" +
            "var n=0;var t=setInterval(function(){n++;" +
            "if(typeof redraw==='function'&&document.getElementById('startStation')){clearInterval(t);" +
            "var p=window.__busPayload||{};Object.keys(p).forEach(function(k){var el=document.getElementById(k);if(!el)return;" +
            "if(el.type==='checkbox'){el.checked=!!p[k];}else{el.value=p[k];}" +
            "try{el.dispatchEvent(new Event('input',{bubbles:true}));}catch(e){}" +
            "try{el.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}});" +
            "try{if(typeof switchTicketType==='function')switchTicketType(4);}catch(e){}" +
            "try{redraw();}catch(e){}}},150);});</script>"
        return if (html.contains("<head>", ignoreCase = true)) {
            html.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "<head>" + injection)
        } else {
            injection + html
        }
    }
}
