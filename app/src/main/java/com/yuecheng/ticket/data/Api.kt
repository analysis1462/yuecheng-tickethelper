package com.yuecheng.ticket.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

const val BASE_URL = "https://m.xintuyun.cn"

/** 会话失效:STATUS == NEEDLOGIN */
class NeedLoginException : Exception("请先登录")

/** 业务错误:STATUS != SUCCESS 或 CODE != 0000,CODE/DATA 为提示文本 */
class ApiException(message: String) : Exception(message)

/**
 * 账号未注册:H5 的做法是携带登录接口(0002)返回的令牌跳"设置密码"页完成注册。
 * token 即登录响应的 DATA,作为 /login/resetPassword 的 validateCode 参数。
 */
class RegisterRequiredException(val token: String) :
    Exception("该手机号尚未注册,请设置密码完成注册")

/**
 * 服务端响应封装(从 H5 cm.js 还原):
 *  - 大多数接口  { STATUS: "SUCCESS" | "NEEDLOGIN" | ..., CODE: msg, DATA: ... }
 *  - 下单/支付/改签用 { CODE: "0000" 表示成功, DATA 为数据或错误文本 }
 */
private fun JsonObject.envelope(): JsonElement {
    val status = get("STATUS")?.takeIf { it.isJsonPrimitive }?.asString
    val code = get("CODE")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
    if (status == "NEEDLOGIN") throw NeedLoginException()
    if (status != null && status != "SUCCESS") throw ApiException(if (code.isNotEmpty()) code else "请求失败")
    if (get("isSuccess")?.takeIf { it.isJsonPrimitive }?.asString == "false") throw ApiException(code.ifEmpty { "操作失败" })
    if (status == null) {
        // CODE 风格:0000 成功
        if (code != "0000") {
            val dataMsg = get("DATA")?.takeIf { it.isJsonPrimitive }?.asString
            throw ApiException(code.ifEmpty { dataMsg ?: "请求失败" })
        }
    }
    return get("DATA") ?: JsonObject()
}

/** 持久化 CookieJar:短信验证码、图形验证码、登录态都绑定 JSESSIONID 会话 */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("yc_cookies", Context.MODE_PRIVATE)
    private val store = ConcurrentHashMap<String, Cookie>()

    private fun key(c: Cookie): String = "${c.name};${c.domain};${c.path}"

    init {
        prefs.all.forEach { (_, v) ->
            (v as? String)?.let { raw ->
                // 格式:name|value|domain|path|expiresAt
                val parts = raw.split("|")
                if (parts.size >= 5) {
                    runCatching {
                        Cookie.Builder()
                            .name(parts[0])
                            .value(parts[1])
                            .domain(parts[2])
                            .path(parts[3])
                            .expiresAt(parts[4].toLongOrNull() ?: 0L)
                            .build()
                    }.getOrNull()?.let { store[key(it)] = it }
                }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { c ->
            store[key(c)] = c
            prefs.edit().putString(
                key(c),
                listOf(c.name, c.value, c.domain, c.path, c.expiresAt.toString()).joinToString("|"),
            ).apply()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.values.filter { it.matches(url) && it.expiresAt >= System.currentTimeMillis() }

    fun clear() {
        store.clear()
        prefs.edit().clear().apply()
    }
}

object Api {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    lateinit var cookieJar: PersistentCookieJar

    fun init(context: Context) {
        if (!::cookieJar.isInitialized) cookieJar = PersistentCookieJar(context.applicationContext)
    }

    /** 退出登录时清空会话 cookie */
    fun clearCookies() {
        if (::cookieJar.isInitialized) cookieJar.clear()
    }

    private fun newBuilder(): Request.Builder =
        Request.Builder()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "$BASE_URL/html/index.html")

    private suspend fun call(request: Request): JsonElement = withContext(Dispatchers.IO) {
        client.newBuilder().cookieJar(cookieJar).build().newCall(request).execute().use { resp: Response ->
            val body = resp.body?.string() ?: throw ApiException("服务器无响应")
            JsonParser.parseString(body)
        }
    }

    suspend fun getRaw(path: String, params: Map<String, String> = emptyMap()): JsonElement {
        val url = "$BASE_URL$path".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        return call(newBuilder().url(url).get().build())
    }

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject =
        getRaw(path, params).let { if (it.isJsonObject) it.asJsonObject else JsonObject() }

    suspend fun post(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val body = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()
        return call(newBuilder().url("$BASE_URL$path").post(body).build())
            .let { if (it.isJsonObject) it.asJsonObject else JsonObject() }
    }

    suspend fun getEnvelope(path: String, params: Map<String, String> = emptyMap()): JsonElement =
        get(path, params).envelope()

    suspend fun postEnvelope(path: String, params: Map<String, String> = emptyMap()): JsonElement =
        post(path, params).envelope()

    /** 获取图形验证码(字节流,与会话 cookie 绑定)。用 H5 原生尺寸,字相对更大 */
    suspend fun captchaImage(): ByteArray = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/servlet/validate?width=92&height=38&t=${System.currentTimeMillis()}"
        client.newBuilder().cookieJar(cookieJar).build()
            .newCall(newBuilder().url(url).get().build()).execute().use { it.body?.bytes() ?: ByteArray(0) }
    }

    /** 校验图形验证码,返回 true/false */
    suspend fun checkCaptcha(code: String): Boolean = runCatching {
        val r = getRaw("/servlet/validate", mapOf("validateCode" to code, "t" to System.currentTimeMillis().toString()))
        r.isJsonPrimitive && r.asBoolean
    }.getOrDefault(false)
}
