package com.yuecheng.ticket.data

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 服务端响应封装判定(还原自 H5 cm.js 的两种风格:STATUS / CODE) */
class EnvelopeTest {

    private fun obj(vararg pairs: Pair<String, String>): JsonObject =
        JsonObject().apply { pairs.forEach { (k, v) -> addProperty(k, v) } }

    private fun withData(vararg pairs: Pair<String, String>): JsonObject =
        obj(*pairs).apply { add("DATA", obj("payUrl" to "http://pay/x")) }

    // ---- STATUS 风格 ----

    @Test
    fun `needlogin throws NeedLoginException`() {
        assertThrows(NeedLoginException::class.java) { obj("STATUS" to "NEEDLOGIN").envelope() }
    }

    @Test
    fun `success returns DATA`() {
        val data = obj("STATUS" to "SUCCESS").apply { add("DATA", obj("k" to "v")) }.envelope()
        assertEquals("v", data.asJsonObject.get("k").asString)
    }

    @Test
    fun `success without DATA returns empty object`() {
        assertEquals(0, obj("STATUS" to "SUCCESS").envelope().asJsonObject.size())
    }

    @Test
    fun `non-success with code uses code as message`() {
        val e = assertThrows(ApiException::class.java) {
            obj("STATUS" to "FAIL", "CODE" to "票已售完").envelope()
        }
        assertEquals("票已售完", e.message)
    }

    @Test
    fun `non-success without code uses default message`() {
        val e = assertThrows(ApiException::class.java) { obj("STATUS" to "FAIL").envelope() }
        assertEquals("请求失败", e.message)
    }

    @Test
    fun `isSuccess false with code throws code`() {
        val e = assertThrows(ApiException::class.java) {
            obj("CODE" to "验证码错误", "isSuccess" to "false").envelope()
        }
        assertEquals("验证码错误", e.message)
    }

    @Test
    fun `isSuccess false without code throws default`() {
        val e = assertThrows(ApiException::class.java) { obj("isSuccess" to "false").envelope() }
        assertEquals("操作失败", e.message)
    }

    // ---- CODE 风格(下单/支付/改签类响应) ----

    @Test
    fun `code 0000 returns DATA`() {
        val data = withData("CODE" to "0000").envelope()
        assertEquals("http://pay/x", data.asJsonObject.get("payUrl").asString)
    }

    @Test
    fun `empty code falls back to DATA text as message`() {
        val e = assertThrows(ApiException::class.java) { obj("DATA" to "座位已占").envelope() }
        assertEquals("座位已占", e.message)
    }

    @Test
    fun `non-empty code is used as message verbatim`() {
        // 契约现状:CODE 非空时以 CODE 为文案,DATA 文本仅在 CODE 为空时兜底
        val e = assertThrows(ApiException::class.java) { obj("CODE" to "9999", "DATA" to "座位已占").envelope() }
        assertEquals("9999", e.message)
    }

    @Test
    fun `missing code and data throws default`() {
        val e = assertThrows(ApiException::class.java) { JsonObject().envelope() }
        assertEquals("请求失败", e.message)
    }
}
