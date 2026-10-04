package com.yuecheng.ticket.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 下单 shiftId 复合串、memo 备注 JSON 解析与流水班规则 */
class ShiftTest {

    private fun shift() = Shift(
        id = "1", sendDate = "2026-10-04", sendTime = "08:30", stationId = "S1",
        stationName = "济南广场汽车站", stationLon = null, stationLat = null,
        stationAddr = null, stationTel = null, portName = "P1", endPortName = null,
        shiftNum = "C1", price = "5000", leftSeatNum = "10", isExpressway = "1",
        isFlow = "0", kilometer = "60", runTime = "1.5", companyName = "公司",
        lineName = "线路", memo = null, maxSellNum = "5", tckTypeList = emptyList(),
        isInsureFlag = "0", insureDefault = "0", insureFee = null, insureCompany = null,
        insureProtocol = null, protocol = null, remind = null,
    )

    @Test
    fun `shiftIdJson contains exactly the five contract fields`() {
        val o = JSONObject(shift().shiftIdJson())
        assertEquals(5, o.length())
        assertEquals("S1", o.getString("stationId"))
        assertEquals("2026-10-04", o.getString("sendDate"))
        assertEquals("08:30", o.getString("sendTime"))
        assertEquals("C1", o.getString("shiftNum"))
        assertEquals("P1", o.getString("portName"))
    }

    @Test
    fun `remark extracts text from memo json`() {
        val s = shift().copy(memo = """{"remark":"提前30分钟检票"}""")
        assertEquals("提前30分钟检票", s.remark)
    }

    @Test
    fun `remark of plain-text memo is null`() {
        assertEquals(null, shift().copy(memo = "普通备注").remark)
        assertEquals(null, shift().copy(memo = null).remark)
    }

    @Test
    fun `flowDelay requires flow flag and shandong station prefix`() {
        assertTrue(shift().copy(isFlow = "1", stationId = "37010001").flowDelay)
        // 非山东区段(37)不按流水班延迟展示
        assertFalse(shift().copy(isFlow = "1", stationId = "11010001").flowDelay)
        assertFalse(shift().copy(isFlow = "0", stationId = "37010001").flowDelay)
    }
}
