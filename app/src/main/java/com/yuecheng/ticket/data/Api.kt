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
import org.json.JSONObject
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
 * 服务端响应封装(从 H5 cm.js 还原)。internal 供 Repo 的乘客接口复用同一份判定:
 *  - 大多数接口  { STATUS: "SUCCESS" | "NEEDLOGIN" | ..., CODE: msg, DATA: ... }
 *  - 下单/支付/改签用 { CODE: "0000" 表示成功, DATA 为数据或错误文本 }
 */
internal fun JsonObject.envelope(): JsonElement {
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
            (v as? String)?.let { encoded ->
                // 存储格式:SecureStore 加密后的 JSON {n,v,d,p,e};
                // 旧版「|」拼接格式解析失败直接丢弃,会话失效后由保存的凭据静默重登恢复
                val o = SecureStore.decrypt(encoded)?.let { raw ->
                    runCatching { JSONObject(raw) }.getOrNull()
                } ?: return@let
                runCatching {
                    Cookie.Builder()
                        .name(o.optString("n"))
                        .value(o.optString("v"))
                        .domain(o.optString("d"))
                        .path(o.optString("p"))
                        .expiresAt(o.optLong("e"))
                        .build()
                }.getOrNull()?.let { store[key(it)] = it }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { c ->
            store[key(c)] = c
            // 会话 cookie 等同登录凭据,与密码一样加密落盘;加密失败则不持久化(仅本次会话有效)。
            // 用 JSON 而非分隔符拼接:cookie value 本身可能含「|」等合法字符
            val plain = JSONObject()
                .put("n", c.name).put("v", c.value)
                .put("d", c.domain).put("p", c.path)
                .put("e", c.expiresAt)
                .toString()
            SecureStore.encrypt(plain)?.let { prefs.edit().putString(key(c), it).apply() }
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
    /** 首次使用时构建(此时 init() 已注入 cookieJar),使连接池 / Dispatcher 全程复用 */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

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
        try {
            client.newCall(request).execute().use { resp: Response ->
                val body = resp.body?.string() ?: throw ApiException("服务器无响应")
                // 网关错误页/代理返回 HTML 时给出可读提示,不让 JSON 解析异常直接冒泡
                runCatching { JsonParser.parseString(body) }.getOrNull()
                    ?: throw ApiException(if (resp.isSuccessful) "返回数据格式异常" else "服务器错误(${resp.code})")
            }
        } catch (e: Exception) {
            // 网络层异常统一转可读文案(CancellationException 等非 IO 异常原样抛出)
            throw when (e) {
                is java.net.UnknownHostException -> ApiException("网络连接失败,请检查网络后重试")
                is java.net.SocketTimeoutException -> ApiException("连接超时,请稍后重试")
                is java.io.IOException -> ApiException("网络异常,请检查网络后重试")
                else -> e
            }
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
        client.newCall(newBuilder().url(url).get().build()).execute().use { it.body?.bytes() ?: ByteArray(0) }
    }

    /** 校验图形验证码,返回 true/false */
    suspend fun checkCaptcha(code: String): Boolean = runCatching {
        val r = getRaw("/servlet/validate", mapOf("validateCode" to code, "t" to System.currentTimeMillis().toString()))
        r.isJsonPrimitive && r.asBoolean
    }.getOrDefault(false)
}
