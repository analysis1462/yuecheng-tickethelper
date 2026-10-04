package com.yuecheng.ticket.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** 登录态:保存用户信息(customerId 等),cookie 由 PersistentCookieJar 负责 */
object Session {
    private const val PREFS = "yc_session"
    private const val KEY_USER = "user"

    private lateinit var prefs: android.content.SharedPreferences

    /** Compose 可观察的登录版本号:登录/登出时自增,驱动界面刷新 */
    val loginTick: MutableState<Int> = mutableStateOf(0)

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 清掉旧版本可能残留的明文凭据键
        prefs.edit().remove("sm").remove("sp").apply()
    }

    var user: JsonObject? = null
        private set

    val isLoggedIn: Boolean get() = user != null

    val customerId: String
        get() = user?.let { o ->
            listOf("id", "ID", "customerId", "userId", "uid")
                .firstNotNullOfOrNull { k -> o.get(k)?.takeIf { it.isJsonPrimitive }?.asString }
        } ?: ""

    val displayMobile: String
        get() = user?.get("mobile")?.takeIf { it.isJsonPrimitive }?.asString
            ?: customerId.ifEmpty { "已登录" }

    fun setLogin(data: JsonObject) {
        user = data
        prefs.edit().putString(KEY_USER, data.toString()).apply()
        loginTick.value++
    }

    /** 保存登录凭据用于会话失效时静默重登(Keystore 加密落盘,不存明文) */
    val savedMobile: String get() = SecureStore.decrypt(prefs.getString("sm_enc", null)).orEmpty()
    val savedPassword: String get() = SecureStore.decrypt(prefs.getString("sp_enc", null)).orEmpty()

    fun setCredentials(mobile: String, password: String) {
        prefs.edit()
            .putString("sm_enc", SecureStore.encrypt(mobile).orEmpty())
            .putString("sp_enc", SecureStore.encrypt(password).orEmpty())
            .apply()
    }

    fun logout() {
        user = null
        prefs.edit().remove(KEY_USER).remove("sm_enc").remove("sp_enc").apply()
        runCatching { Api.clearCookies() }
        loginTick.value++
    }

    fun restore() {
        runCatching {
            prefs.getString(KEY_USER, null)?.let { user = JsonParser.parseString(it).asJsonObject }
        }
    }
}

/** 跨页面购票流程状态(对应 H5 的 session 缓存) */
object FlowState {
    var startIndex: IndexInfo? = null
    var startCity: StartCity? = null
    var endCityName: String = ""
    var endCityPinyin: String = ""
    var pickDate: String = ""

    /** 起点城市全量表内存缓存(交换城市用,避免每次点击都请求) */
    var cachedStartPoints: List<StartCity>? = null

    var shift: Shift? = null          // 车次列表页选中的班次
    var suit: SuitInfo? = null        // 下单页加载的套餐+班次详情
    var selectedPassengers: MutableList<Passenger> = mutableListOf()
    var insure: Boolean = false

    /** 改签上下文:原票的子订单号/座位号/原发车日期 */
    var changeOrder: ChangeOrder? = null

    fun resetBooking() {
        shift = null
        suit = null
        selectedPassengers = mutableListOf()
        insure = false
    }

    fun clearAll() {
        resetBooking()
        changeOrder = null
        startCity = null
        endCityName = ""
        endCityPinyin = ""
        pickDate = ""
    }
}

data class ChangeOrder(val subOrderId: String, val seatNo: String, val sendDate: String)

/** 夜间模式开关(持久化) */
object ThemePrefs {
    val dark = mutableStateOf(false)
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("yc_theme", Context.MODE_PRIVATE)
        dark.value = prefs.getBoolean("dark", false)
    }

    fun toggle() {
        dark.value = !dark.value
        prefs.edit().putBoolean("dark", dark.value).apply()
    }
}

/** 查询历史记录(持久化,最近在前,去重,最多8条) */
data class HistoryEntry(val startId: String, val startName: String, val endName: String, val endPinyin: String)

object SearchHistory {
    private const val PREFS = "yc_history"
    private const val KEY = "list"
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun load(): List<HistoryEntry> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        com.google.gson.JsonParser.parseString(raw).asJsonArray.mapNotNull { e ->
            val o = e.asJsonObject
            HistoryEntry(
                o.get("s")?.asString ?: return@mapNotNull null,
                o.get("sn")?.asString ?: return@mapNotNull null,
                o.get("e")?.asString ?: return@mapNotNull null,
                o.get("ep")?.asString ?: "",
            )
        }
    }.getOrDefault(emptyList())

    fun add(startId: String, startName: String, endName: String, endPinyin: String = "") {
        if (startId.isEmpty() || endName.isEmpty()) return
        val rest = load().filter { !(it.startId == startId && it.endName == endName) }
        val next = listOf(HistoryEntry(startId, startName, endName, endPinyin)) + rest
        val capped = next.take(8)
        prefs.edit().putString(KEY, com.google.gson.Gson().toJson(capped.map {
            com.google.gson.JsonObject().apply {
                addProperty("s", it.startId); addProperty("sn", it.startName)
                addProperty("e", it.endName); addProperty("ep", it.endPinyin)
            }
        })).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }
}

/** 收藏线路(持久化,固定常用线路,最多 12 条) */
data class FavLine(val startId: String, val startName: String, val endName: String, val endPinyin: String)

object FavLines {
    private const val PREFS = "yc_favs"
    private const val KEY = "list"
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun load(): List<FavLine> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        com.google.gson.JsonParser.parseString(raw).asJsonArray.mapNotNull { e ->
            val o = e.asJsonObject
            FavLine(
                o.get("s")?.asString ?: return@mapNotNull null,
                o.get("sn")?.asString ?: return@mapNotNull null,
                o.get("e")?.asString ?: return@mapNotNull null,
                o.get("ep")?.asString ?: "",
            )
        }
    }.getOrDefault(emptyList())

    fun add(startId: String, startName: String, endName: String, endPinyin: String = "") {
        if (startId.isEmpty() || endName.isEmpty()) return
        val next = listOf(FavLine(startId, startName, endName, endPinyin)) +
            load().filter { !(it.startId == startId && it.endName == endName) }
        save(next.take(12))
    }

    fun remove(startId: String, endName: String) {
        save(load().filter { !(it.startId == startId && it.endName == endName) })
    }

    fun isFav(startId: String?, endName: String): Boolean =
        startId != null && load().any { it.startId == startId && it.endName == endName }

    private fun save(list: List<FavLine>) {
        prefs.edit().putString(KEY, com.google.gson.Gson().toJson(list.map {
            com.google.gson.JsonObject().apply {
                addProperty("s", it.startId); addProperty("sn", it.startName)
                addProperty("e", it.endName); addProperty("ep", it.endPinyin)
            }
        })).apply()
    }
}
