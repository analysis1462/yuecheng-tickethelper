package com.yuecheng.ticket.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Base64

private fun JsonObject.s(key: String): String? =
    get(key)?.let { if (it.isJsonNull) null else it.asString }

private fun JsonObject.o(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.a(key: String): List<JsonObject> =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { e ->
        (e as? JsonObject)?.takeIf { !it.isJsonNull }
    } ?: emptyList()

private fun JsonElement.s(key: String): String? = (this as? JsonObject)?.s(key)

// ---------- 模型 ----------

data class StartCity(val id: String, val name: String, val pinyin: String, val jianpin: String, val hot: Int)

data class EndCity(val name: String, val jianpin: String, val pinyin: String)

data class IndexInfo(
    val today: String,
    val presellDay: Int,
    val defaultDay: String,
    val defaultTo: String?,
    val defaultFrom: StartCity?,
)

data class TckCharge(val chargeFee: String?)

data class TckType(val type: String?, val name: String?, val price: String?, val charge: TckCharge?)

data class Port(val id: String?, val portName: String?)

data class Shift(
    val id: String?,
    val sendDate: String,
    val sendTime: String,
    val stationId: String,
    val stationName: String,
    val stationLon: String?,
    val stationLat: String?,
    val stationAddr: String?,
    val stationTel: String?,
    val portName: String,
    val endPortName: String?,
    val shiftNum: String,
    val price: String?,
    val leftSeatNum: String?,
    val isExpressway: String?,
    val isFlow: String?,
    val kilometer: String?,
    val runTime: String?,
    val companyName: String?,
    val lineName: String?,
    val memo: String?,
    val maxSellNum: String?,
    val tckTypeList: List<TckType>,
    val isInsureFlag: String?,
    val insureDefault: String?,
    val insureFee: String?,
    val insureCompany: String?,
    val insureProtocol: String?,
    val protocol: String?,
    val remind: String?,
) {
    val flowDelay: Boolean get() = isFlow == "1" && stationId.startsWith("37")

    /** 备注 JSON 里的 remark 文本 */
    val remark: String? get() = runCatching {
        memo?.let { com.google.gson.JsonParser.parseString(it).asJsonObject.s("remark") }
    }.getOrNull()

    /** 下单用的 shiftId 复合串(与 H5 完全一致) */
    fun shiftIdJson(): String =
        """{"stationId":"$stationId","sendDate":"$sendDate","sendTime":"$sendTime","shiftNum":"$shiftNum","portName":"$portName"}"""
}

data class Scheme(
    val activityId: String?,
    val activityTile: String?,
    val discountpPrice: String?,
    val speedServiceFee: String?,
    val activityPrice: String?,
)

data class SuitInfo(val shiftInfo: Shift, val scheme: Scheme)

data class Passenger(
    val id: String?,
    val customerId: String?,
    val name: String?,
    val mobile: String?,
    val idcardNo: String?,
    val idcardType: String?,
) {
    var selected: Boolean = false
    var tckType: TckType? = null
    var refundFee: Double = 0.0
}

data class CardType(val value: String?, val label: String?)

data class OrderSummary(
    val orderId: String,
    val suborderId: String?,
    val billNumber: String?,
    val sendDate: String?,
    val sendTime: String?,
    val startName: String?,
    val sendStationName: String?,
    val endPortName: String?,
    val status: String?,
    val totalPrice: String?,
    val settleAmount: String?,
    val sendFlag: String?,
) {
    val statusLabel: String get() = statusLabelOf(status)
}

data class OrderDetailData(
    val order: OrderSummary,
    val payType: String?,
    val payResult: String?,
    val isAllowChange: String?,
    val isAllowRefund: String?,
    val shiftNumber: String?,
    val ticketPassword: String?,
    val checkPort: String?,
    val stationLon: String?,
    val stationLat: String?,
    val stationAddr: String?,
    val stationTel: String?,
    val kilometer: String?,
    val runTime: String?,
    val companyName: String?,
    val isExpressway: String?,
    val busType: String?,
    val orderTime: String?,
    val payTime: String?,
    val protocol: String?,
    val smsRemind: String?,
    val tickets: List<Ticket>,
)

data class Ticket(
    val orderId: String?,
    val suborderId: String?,
    val seatNo: String?,
    val name: String?,
    val idcardNo: String?,
    val idcardType: String?,
    val ticketTypeName: String?,
    val price: String?,
    val status: String?,
    val qrCode: String?,
    val checkPort: String?,
    // 电子凭证扩展字段(缺失显示 --)
    val carNo: String?,
    val sendPort: String?,
    val approvePrice: String?,
    val insurCompany: String?,
    val insurFee: String?,
    val insurNumber: String?,
)

fun statusLabelOf(status: String?): String = when (status) {
    "0", "2" -> "待支付"
    "1", "3" -> "正在出票"
    "4" -> "购票成功"
    "5" -> "已关闭"
    "6", "7" -> "出票失败"
    else -> "状态 $status"
}

/** 子票状态(与订单状态表不同):0=购票成功 */
fun ticketStatusLabel(status: String?): String = when (status) {
    "0" -> "购票成功"
    else -> statusLabelOf(status)
}

// ---------- 解析 ----------

fun parseStartCity(o: JsonObject): StartCity =
    StartCity(o.s("id") ?: "", o.s("name") ?: "", o.s("pinyin") ?: "", o.s("jianpin") ?: "", o.get("hot")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0)

fun parseShift(o: JsonObject): Shift = Shift(
    id = o.s("id"),
    sendDate = o.s("sendDate") ?: "",
    sendTime = o.s("sendTime") ?: "",
    stationId = o.s("stationId") ?: "",
    stationName = o.s("stationName") ?: "",
    stationLon = o.s("stationLon"),
    stationLat = o.s("stationLat"),
    stationAddr = o.s("stationAddr"),
    stationTel = o.s("stationTel"),
    portName = o.s("portName") ?: "",
    endPortName = o.s("endPortName"),
    shiftNum = o.s("shiftNum") ?: "",
    price = o.s("price"),
    leftSeatNum = o.s("leftSeatNum"),
    isExpressway = o.s("isExpressway"),
    isFlow = o.s("isFlow"),
    kilometer = o.s("kilometer"),
    runTime = o.s("runTime"),
    companyName = o.s("companyName"),
    lineName = o.s("lineName"),
    memo = o.s("memo"),
    maxSellNum = o.s("maxSellNum"),
    tckTypeList = o.a("tckTypeList").map { t ->
        TckType(
            type = t.s("type"), name = t.s("name"), price = t.s("price"),
            charge = t.o("charge")?.let { TckCharge(it.s("chargeFee")) },
        )
    },
    isInsureFlag = o.s("isInsureFlag"),
    insureDefault = o.s("insureDefault"),
    insureFee = o.s("insureFee"),
    insureCompany = o.s("insureCompany"),
    insureProtocol = o.s("insureProtocol"),
    protocol = o.s("protocol"),
    remind = o.s("remind"),
)

// ---------- 接口仓库 ----------

object Repo {
    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
    private fun ttsId(): String = Config.ttsId

    /**
     * 会话失效自动恢复:官网服务端会话有效期较短,遇 NEEDLOGIN 时
     * 用已保存的密码静默重登一次再重试原请求;无凭据则抛给界面引导登录。
     */
    private suspend fun <T> authed(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: NeedLoginException) {
            val m = Session.savedMobile
            val p = Session.savedPassword
            if (m.isEmpty() || p.isEmpty()) throw e
            val user = loginByPassword(m, p)
            Session.setLogin(user)
            block()
        }
    }

    suspend fun index(): IndexInfo {
        val d = Api.getEnvelope("/book/index", mapOf("ttsId" to ttsId())).asJsonObjectOrNull()
        return IndexInfo(
            today = d.s("today") ?: "",
            presellDay = d.get("presellDay")?.takeIf { it.isJsonPrimitive }?.asInt ?: 15,
            defaultDay = d.s("defaultDay") ?: "",
            defaultTo = d.s("to"),
            defaultFrom = d.o("from")?.let { parseStartCity(it) },
        )
    }

    /** 起点城市:DATA 按首字母分组 { "S": [...], ... } */
    suspend fun startPoints(): List<Pair<String, List<StartCity>>> {
        val d = Api.getEnvelope("/book/startPoint", mapOf("ttsId" to ttsId())).asJsonObjectOrNull()
        return d.entrySet()
            .filter { it.value.isJsonArray }
            .map { it.key to it.value.asJsonArray.mapNotNull { e -> (e as? JsonObject)?.let(::parseStartCity) } }
            .sortedBy { it.first }
    }

    /** 终点地:DATA 按首字母分组 { "B": [ ["北京","bj","beijing"], ... ] } */
    suspend fun endPoints(departure: String): List<Pair<String, List<EndCity>>> {
        val d = Api.getEnvelope("/book/endPoint", mapOf("departure" to departure, "ttsId" to ttsId())).asJsonObjectOrNull()
        val out = mutableListOf<Pair<String, List<EndCity>>>()
        for ((letter, value) in d.entrySet()) {
            val arr = value as? com.google.gson.JsonArray ?: continue
            val list = mutableListOf<EndCity>()
            for (item in arr) {
                val a2 = item as? com.google.gson.JsonArray ?: continue
                if (a2.size() == 0) continue
                val name = a2.get(0).takeIf { it.isJsonPrimitive }?.asString ?: continue
                val jp = if (a2.size() > 1) a2.get(1).takeIf { it.isJsonPrimitive }?.asString ?: "" else ""
                val py = if (a2.size() > 2) a2.get(2).takeIf { it.isJsonPrimitive }?.asString ?: "" else ""
                list.add(EndCity(name, jp, py))
            }
            out.add(letter to list)
        }
        return out.sortedBy { it.first }
    }

    suspend fun shifts(startCityId: String, startCityName: String, endCityName: String, date: String): List<Shift> {
        val d = Api.getEnvelope(
            "/book/shifts",
            mapOf(
                "ttsId" to ttsId(),
                "startCityId" to startCityId,
                "startCityName" to startCityName,
                "endCityName" to endCityName,
                "startDate" to date,
                "lon" to "0", "lat" to "0",
            ),
        )
        return (d.takeIf { it.isJsonArray }?.asJsonArray ?: emptyList())
            .mapNotNull { (it as? JsonObject)?.let(::parseShift) }
    }

    suspend fun suit(startId: String, shiftIdJson: String): SuitInfo {
        val d = Api.getEnvelope(
            "/book/suit",
            mapOf(
                "ttsId" to ttsId(),
                "startId" to startId,
                "shiftId" to shiftIdJson,
                "isWeixin" to "0",
            ),
        ).asJsonObjectOrNull()
        val shiftInfo = d.o("shiftInfo")?.let(::parseShift)
            ?: throw ApiException("未获取到班次信息")
        val s = d.o("normalScheme") ?: JsonObject()
        return SuitInfo(
            shiftInfo = shiftInfo,
            scheme = Scheme(
                activityId = s.s("activityId"),
                activityTile = s.s("activityTile"),
                discountpPrice = s.s("discountpPrice"),
                speedServiceFee = s.s("speedServiceFee"),
                activityPrice = s.s("activityPrice"),
            ),
        )
    }

    /** 图形验证码预检查(已注册返回 0000) */
    suspend fun verification(mobile: String): Pair<Boolean, String> {
        val r = Api.get("/login/verification", mapOf("mobile" to mobile))
        val status = r.s("STATUS") ?: ""
        val code = r.s("CODE") ?: ""
        return Pair(status == "0000", code)
    }

    suspend fun sendSmsCode(mobile: String, captcha: String) {
        val r = Api.post("/login/sendSMSCode", mapOf("mobile" to mobile, "validateCode" to captcha))
        if (r.s("STATUS") != "SUCCESS") throw ApiException(r.s("CODE") ?: "验证码发送失败")
    }

    /** 密码登录;成功返回用户信息 */
    suspend fun loginByPassword(mobile: String, password: String): JsonObject {
        val r = Api.get(
            "/login/login",
            mapOf("mobile" to b64(mobile), "password" to b64(password), "openid" to "", "ttsId" to ttsId()),
        )
        return handleLoginResp(r)
    }

    suspend fun loginBySms(mobile: String, smsCode: String): JsonObject {
        val r = Api.get(
            "/login/login",
            mapOf("mobile" to b64(mobile), "SMSCode" to b64(smsCode), "openid" to ""),
        )
        return handleLoginResp(r)
    }

    private fun handleLoginResp(r: JsonObject): JsonObject {
        val status = r.s("STATUS") ?: ""
        return when {
            status == "0000" -> r.o("DATA") ?: JsonObject()
            status == "0002" -> {
                val data = r.get("DATA")
                val token = when {
                    data == null || data.isJsonNull -> ""
                    data.isJsonPrimitive -> data.asString
                    else -> data.toString()
                }
                throw RegisterRequiredException(token)
            }
            else -> throw ApiException(r.s("CODE") ?: "登录失败")
        }
    }

    /**
     * 设置密码完成注册(对应 H5 #/password 页):
     * GET /login/resetPassword?mobile=b64&password=b64&validateCode=<登录0002返回的令牌>
     * 实测响应为 STATUS:"0000" 风格,DATA 直接就是登录用户信息(无需再调登录)。
     */
    suspend fun resetPassword(mobile: String, password: String, token: String): JsonObject {
        val r = Api.get(
            "/login/resetPassword",
            mapOf(
                "mobile" to b64(mobile),
                "password" to b64(password),
                "validateCode" to token,
            ),
        )
        val status = r.s("STATUS") ?: ""
        return when {
            status == "0000" -> r.o("DATA") ?: JsonObject()
            else -> throw ApiException(r.s("CODE")?.takeIf { it.isNotEmpty() } ?: "设置密码失败,请稍后重试")
        }
    }

    suspend fun passengers(stationId: String, customerId: String): Pair<List<Passenger>, List<CardType>> = authed {
        val r = Api.get("/login/passengers", mapOf("stationId" to stationId, "customerId" to customerId, "ttsId" to ttsId()))
        if (r.s("STATUS") == "NEEDLOGIN") throw NeedLoginException()
        if (r.s("STATUS") != "SUCCESS") throw ApiException(r.s("CODE") ?: "获取乘车人失败")
        val list = r.a("DATA").map { p ->
            Passenger(
                id = p.s("id"), customerId = p.s("customerId"), name = p.s("name"),
                mobile = p.s("mobile"), idcardNo = p.s("idcardNo"), idcardType = p.s("idcardType"),
            )
        }
        val cards = r.a("CARDTYPE").map { c -> CardType(c.s("idcardType"), c.s("idcardTypeName")) }
            .ifEmpty { listOf(CardType("1", "身份证")) }
        return@authed list to cards
    }

    suspend fun addPassenger(customerId: String, name: String, idcardType: String, idcardNo: String, mobile: String): JsonObject {
        val r = Api.post(
            "/login/addPassenger",
            mapOf(
                "customerId" to b64(customerId), "name" to b64(name), "idcardType" to b64(idcardType),
                "idcardNo" to b64(idcardNo), "mobile" to b64(mobile), "ttsId" to ttsId(),
            ),
        )
        return r.envelopeData()
    }

    suspend fun editPassenger(customerId: String, id: String, name: String, idcardType: String, idcardNo: String, mobile: String) {
        Api.post(
            "/login/editPassenger",
            mapOf(
                "customerId" to customerId, "id" to id, "name" to name, "idcardType" to idcardType,
                "idcardNo" to idcardNo, "mobile" to mobile, "ttsId" to ttsId(),
            ),
        )
    }

    suspend fun delPassenger(customerId: String, id: String) {
        Api.post("/login/delPassenger", mapOf("customerId" to customerId, "id" to id, "ttsId" to ttsId()))
    }

    /** 提交订单,成功返回 payUrl;0004 = 有未完成订单 */
    suspend fun submitOrder(
        startId: String, shiftIdJson: String, insureCompany: String,
        hasActivity: Boolean, activityId: String?,
        passengerListJson: String,
    ): String = authed {
        val r = Api.post(
            "/book/submitOrder",
            mapOf(
                "ttsId" to ttsId(),
                "startId" to startId,
                "shiftId" to shiftIdJson,
                "openId" to "",
                "insureCompany" to insureCompany,
                "speedServiceFlag" to if (hasActivity) "1" else "0",
                "activityId" to (activityId ?: ""),
                "passengerList" to passengerListJson,
            ),
        )
        val code = r.s("CODE") ?: ""
        if (code == "0004") throw ApiException("您有未完成的订单,请到「我的订单」处理")
        if (code != "0000") throw ApiException(r.s("DATA") ?: code.ifEmpty { "下单失败" })
        r.o("DATA")?.s("payUrl") ?: throw ApiException("未返回支付地址")
    }

    suspend fun orderList(): List<OrderSummary> = authed {
        val r = Api.get("/order/orderList", mapOf("openId" to "", "ttsId" to ttsId()))
        val status = r.s("STATUS")
        if (status == "NEEDLOGIN") throw NeedLoginException()
        if (status != "SUCCESS") throw ApiException(r.s("CODE") ?: "获取订单失败")
        val d = r.o("DATA") ?: return@authed emptyList()
        // 实测:列表为扁平结构,每一行就是一张子票(自带 suborderId/status),无 orders[] 子数组
        return@authed d.a("data").map { o ->
            OrderSummary(
                orderId = o.s("orderId") ?: "",
                suborderId = o.s("suborderId"),
                billNumber = o.s("billNumber"),
                sendDate = o.s("sendDate"),
                sendTime = o.s("sendTime"),
                startName = o.s("startName"),
                sendStationName = o.s("sendStationName"),
                endPortName = o.s("endPortName"),
                status = o.s("status"),
                totalPrice = o.s("totalPrice"),
                settleAmount = o.s("settleAmount"),
                sendFlag = o.s("sendFlag"),
            )
        }
    }

    suspend fun orderDetail(orderId: String): OrderDetailData = authed {
        val r = Api.get("/order/orderDetail", mapOf("openId" to "", "orderId" to orderId, "ttsId" to ttsId()))
        if (r.s("STATUS") == "NEEDLOGIN") throw NeedLoginException()
        if (r.s("STATUS") != "SUCCESS") throw ApiException(r.s("CODE") ?: "获取订单详情失败")
        val d = r.o("DATA") ?: throw ApiException("订单数据为空")
        val o = d.o("order") ?: JsonObject()
        val order = OrderSummary(
            orderId = o.s("orderId") ?: orderId,
            suborderId = o.s("suborderId"),
            billNumber = o.s("billNumber"),
            sendDate = o.s("sendDate"), sendTime = o.s("sendTime"),
            startName = o.s("startName"), sendStationName = o.s("sendStationName") ?: o.s("stationName"),
            endPortName = o.s("endPortName") ?: o.s("portName"),
            status = o.s("status"), totalPrice = o.s("totalPrice"),
            settleAmount = o.s("settleAmount"), sendFlag = o.s("sendFlag"),
        )
        val tickets = d.a("detail").map { t ->
            Ticket(
                orderId = t.s("orderId"), suborderId = t.s("suborderId") ?: t.s("id"),
                seatNo = t.s("seatNo"), name = t.s("name"), idcardNo = t.s("idcardNo"),
                idcardType = t.s("idcardType"),
                ticketTypeName = t.s("ticketTypeName") ?: t.s("ticketType") ?: t.s("tckTypeName"),
                price = t.s("price") ?: t.s("discountPrice"),
                status = t.s("status"), qrCode = t.s("qrCode"), checkPort = t.s("checkPort"),
                carNo = t.s("carNo"), sendPort = t.s("sendPort"), approvePrice = t.s("approvePrice"),
                insurCompany = t.s("insurCompany"), insurFee = t.s("insurFee"), insurNumber = t.s("insurNumber"),
            )
        }
        return@authed OrderDetailData(
            order = order,
            payType = o.s("payType"),
            payResult = o.s("payResult"),
            isAllowChange = o.s("isAllowChange"),
            isAllowRefund = o.s("isAllowRefund"),
            shiftNumber = o.s("shiftNumber"),
            ticketPassword = o.s("ticketPassword"),
            checkPort = tickets.firstNotNullOfOrNull { it.checkPort } ?: o.s("checkPort"),
            stationLon = o.s("stationLon"), stationLat = o.s("stationLat"),
            stationAddr = o.s("stationAddr"), stationTel = o.s("stationTel"),
            kilometer = o.s("kilometer"), runTime = o.s("runTime"),
            companyName = o.s("companyName"), isExpressway = o.s("isExpressway"),
            busType = o.s("busType"),
            orderTime = o.s("orderTime"), payTime = o.s("payTime"),
            protocol = o.s("protocol"), smsRemind = o.s("smsRemind"),
            tickets = tickets,
        )
    }

    suspend fun payOrder(orderId: String): String = authed {
        val r = Api.get("/order/payOrder", mapOf("openId" to "", "orderId" to orderId))
        val code = r.s("CODE") ?: ""
        if (code != "0000") throw ApiException(r.s("DATA") ?: code.ifEmpty { "获取支付信息失败" })
        r.o("DATA")?.s("payUrl") ?: throw ApiException("未返回支付地址")
    }

    suspend fun cancelOrder(orderId: String) = authed {
        val r = Api.post("/order/cancelOrder", mapOf("orderId" to orderId))
        if (r.s("STATUS") != "SUCCESS") throw ApiException(r.s("CODE") ?: "取消失败")
    }

    suspend fun bounceFee(orderNo: String, seat: String): Double = authed {
        val r = Api.get("/order/bounceFee", mapOf("orderNo" to orderNo, "seat" to seat, "openId" to ""))
        if (r.s("STATUS") != "SUCCESS") throw ApiException(r.s("CODE") ?: "查询退票费失败")
        val v = r.get("DATA")?.takeIf { it.isJsonPrimitive }?.asString ?: "0"
        (v.toDoubleOrNull() ?: 0.0) / 100
    }

    suspend fun bounce(orderNo: String, seat: String) = authed {
        val r = Api.get("/order/bounce", mapOf("orderNo" to orderNo, "seat" to seat, "openId" to ""))
        if (r.s("STATUS") != "SUCCESS") throw ApiException(r.s("CODE") ?: "退票失败")
    }

    suspend fun changeShifts(orderNo: String, subOrderId: String, sendDate: String, seatNo: String): List<Shift> = authed {
        val r = Api.get(
            "/order/changeShifts",
            mapOf("orderNo" to orderNo, "subOrderId" to subOrderId, "sendDate" to sendDate, "seatNo" to seatNo, "openId" to ""),
        )
        if (r.s("STATUS") == "NEEDLOGIN") throw NeedLoginException()
        if (r.s("STATUS") != "SUCCESS") throw ApiException(r.s("CODE") ?: "查询可改班次失败")
        val d = r.get("DATA") ?: return@authed emptyList()
        return@authed (d.takeIf { it.isJsonArray }?.asJsonArray ?: emptyList())
            .mapNotNull { (it as? JsonObject)?.let(::parseShift) }
    }

    suspend fun change(orderNo: String, subOrderId: String, seat: String, sendDate: String, shift: Shift) = authed {
        val r = Api.get(
            "/order/change",
            mapOf(
                "orderNo" to orderNo, "subOrderId" to subOrderId, "seat" to seat,
                "sendDate" to sendDate, "sendTime" to shift.sendTime, "shiftNum" to shift.shiftNum,
                "openId" to "",
            ),
        )
        val code = r.s("CODE") ?: ""
        if (code != "0000") throw ApiException(r.s("DATA") ?: code.ifEmpty { "改签失败" })
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject =
        (this as? JsonObject) ?: JsonObject()

    private fun JsonObject.envelopeData(): JsonObject {
        val status = s("STATUS")
        if (status == "NEEDLOGIN") throw NeedLoginException()
        if (status != null && status != "SUCCESS") throw ApiException(s("CODE") ?: "操作失败")
        if (get("isSuccess")?.takeIf { it.isJsonPrimitive }?.asString == "false") throw ApiException("操作失败")
        return o("DATA") ?: JsonObject()
    }
}

/** 可调参数 */
object Config {
    /** 租户 ID:H5 用 URL ?ttsId= 传入,留空使用网关默认 */
    var ttsId: String = ""
}
