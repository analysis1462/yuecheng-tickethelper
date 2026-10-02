package com.yuecheng.ticket.ui.pay

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayInputStream

/**
 * 一键生成纸质火车票样票:
 * 1. 拦截编辑器主文档,在 <head> 注入 window.__busPayload(汽车票信息,键=编辑器表单ID);
 * 2. 页面就绪后用 JS 驱动:按 payload 填表 → 切红纸票模板 → 重绘 → 等二维码补绘;
 * 3. canvas.toDataURL 导出 PNG,通过 @JavascriptInterface 桥回传原生显示/保存。
 */
class TicketEditorActivity : ComponentActivity() {

    private val ui = Handler(Looper.getMainLooper())
    private var headless: WebView? = null
    private lateinit var injectedHtml: String

    private var stateBitmap by mutableStateOf<Bitmap?>(null)
    private var generating by mutableStateOf(true)
    private var errorMsg by mutableStateOf("")
    private var outName by mutableStateOf("火车票样票")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val payloadJson = intent.getStringExtra(EXTRA_PAYLOAD) ?: "{}"
        injectedHtml = buildInjectedHtml(payloadJson)
        outName = intent.getStringExtra(EXTRA_NAME) ?: "火车票样票"
        startHeadlessGeneration()

        setContent {
            androidx.compose.material3.MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("纸质火车票样票", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        val bmp = stateBitmap
                        when {
                            generating -> {
                                Spacer(Modifier.height(60.dp))
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text("正在生成票面…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(60.dp))
                            }
                            bmp != null -> {
                                Image(
                                    bmp.asImageBitmap(),
                                    contentDescription = "火车票样票",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.FillWidth,
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    if (errorMsg.isEmpty()) "已根据订单信息自动填充,可保存到相册" else errorMsg,
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { saveToGallery(bmp) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                    ) { Text("保存到相册", maxLines = 1) }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { openInteractiveEditor() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                ) { Text("打开编辑器精修", maxLines = 1) }
                            }
                            else -> {
                                Spacer(Modifier.height(60.dp))
                                Text(
                                    errorMsg.ifEmpty { "生成失败" },
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(20.dp),
                                )
                                Spacer(Modifier.height(60.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    /** 后台 WebView 加载编辑器并驱动导出(不加入视图树,纯 JS 执行) */
    @SuppressLint("SetJavaScriptEnabled")
    private fun startHeadlessGeneration() {
        headless = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(0, 0)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            addJavascriptInterface(YcEditorBridge { onGenerationResult(it) }, "__ycBridge")
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
                    ui.postDelayed({ driveExport(view) }, 1500)
                }
            }
            loadUrl("file:///android_asset/ticket_editor/index.html")
        }
    }

    private fun driveExport(view: WebView) {
        // payload 直接内嵌进驱动脚本(JSON 字符串常量),不依赖注入时序;
        // 编辑器自身初始化会延迟覆盖表单,故用轮询:填表→校验→被覆盖则重填→QR就绪→导出
        val payloadConst = org.json.JSONObject.quote(intent.getStringExtra(EXTRA_PAYLOAD) ?: "{}")
        val driver = """
            (function(){
              var payload = null;
              try { payload = JSON.parse($payloadConst); } catch(e) {}
              if (!payload) payload = window.__busPayload || {};
              var keys = Object.keys(payload);
              function done(v){ if(window.__ycBridge) window.__ycBridge.postResult(String(v)); }
              function fillNow(){
                keys.forEach(function(k){
                  var el = document.getElementById(k);
                  if (!el) return;
                  if (el.type === 'checkbox') { el.checked = !!payload[k]; }
                  else { el.value = payload[k]; }
                  try { el.dispatchEvent(new Event('input', {bubbles:true})); } catch(e2){}
                  try { el.dispatchEvent(new Event('change', {bubbles:true})); } catch(e2){}
                });
                try { if (typeof switchTicketType === 'function') switchTicketType(4); } catch(e2){}
                try { redraw(); } catch(e2){}
              }
              function formOurs(){
                var el = document.getElementById('startStation');
                return el && el.value === (payload.startStation || '');
              }
              function startQRWait(){
                var q = 0;
                var t1 = setInterval(function(){
                  q++;
                  if (!formOurs()) { fillNow(); q = 0; return; }  // 被编辑器初始化覆盖则重填
                  if (window.__qrPainted === true || q > 40) {
                    clearInterval(t1);
                    var c = document.getElementById('canvas');
                    if (!c) { done('ERR:canvas missing'); return; }
                    try { done(c.toDataURL('image/png')); } catch(e) { done('ERR:' + e); }
                  }
                }, 100);
              }
              var tries = 0;
              var t0 = setInterval(function(){
                tries++;
                if (typeof redraw === 'function') {
                  if (!formOurs()) fillNow();
                  if (formOurs()) { clearInterval(t0); startQRWait(); return; }
                }
                if (tries > 80) { clearInterval(t0); done('ERR:编辑器加载超时'); }
              }, 150);
            })();
        """.trimIndent()
        view.evaluateJavascript(driver, null)
    }

    private fun onGenerationResult(value: String) {
        ui.post {
            if (value.startsWith("data:image/png;base64,")) {
                val bytes = Base64.decode(value.substringAfter("base64,"), Base64.DEFAULT)
                stateBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                generating = false
            } else {
                errorMsg = value.removePrefix("ERR:").ifEmpty { "生成失败" }
                generating = false
            }
        }
    }

    private fun openInteractiveEditor() {
        try {
            startActivity(android.content.Intent(this, TicketEditorWebViewActivity::class.java))
        } catch (e: Exception) {
            errorMsg = "打开编辑器失败"
        }
    }

    private fun saveToGallery(bmp: Bitmap) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$outName.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/悦程票样")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            contentResolver.openOutputStream(uri!!)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            errorMsg = ""
        } catch (e: Exception) {
            try {
                val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: throw e
                val f = java.io.File(dir, "$outName.png")
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                errorMsg = ""
            } catch (e2: Exception) {
                errorMsg = "保存失败:${e2.message}"
            }
        }
    }

    private fun buildInjectedHtml(payloadJson: String): String {
        val html = assets.open("ticket_editor/index.html").readBytes().toString(Charsets.UTF_8)
        // 编辑器启动时会把默认表单状态同步到 URL,localStorage 草稿会被忽略;
        // 因此注入全局变量,由驱动脚本在页面就绪后直接填表(实测可靠)
        val injection = "<script>window.__busPayload = $payloadJson;</script>"
        return if (html.contains("<head>", ignoreCase = true)) {
            html.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "<head>" + injection)
        } else {
            injection + html
        }
    }

    override fun onDestroy() {
        headless?.apply {
            loadUrl("about:blank")
            destroy()
        }
        headless = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PAYLOAD = "payload"
        private const val EXTRA_NAME = "name"
        fun start(context: android.content.Context, payloadJson: String, name: String) {
            context.startActivity(
                android.content.Intent(context, TicketEditorActivity::class.java)
                    .putExtra(EXTRA_PAYLOAD, payloadJson)
                    .putExtra(EXTRA_NAME, name),
            )
        }
    }
}

/** JS 桥:编辑器驱动脚本把导出结果回传原生 */
class YcEditorBridge(private val onResult: (String) -> Unit) {
    @JavascriptInterface
    fun postResult(value: String) {
        android.util.Log.d("YcEditor", "result len=${value.length}")
        onResult(value)
    }
}
