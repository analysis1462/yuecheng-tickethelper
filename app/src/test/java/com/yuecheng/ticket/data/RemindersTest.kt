package com.yuecheng.ticket.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 发车时间解析与提醒/通知的文案拼装(均为纯函数) */
class RemindersTest {

    private fun expectedMillis(date: String, time: String): Long =
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ---- departureEpochMillis ----

    @Test
    fun `hh mm time parses to local zone instant`() =
        assertEquals(expectedMillis("2026-10-04", "08:30"), departureEpochMillis("2026-10-04", "08:30"))

    @Test
    fun `hh mm ss time parses to same instant`() =
        assertEquals(
            departureEpochMillis("2026-10-04", "08:30"),
            departureEpochMillis("2026-10-04", "08:30:00"),
        )

    @Test
    fun `missing date returns 0`() = assertEquals(0L, departureEpochMillis(null, "08:30"))

    @Test
    fun `missing time returns 0`() = assertEquals(0L, departureEpochMillis("2026-10-04", null))

    @Test
    fun `short time without seconds returns 0`() =
        assertEquals(0L, departureEpochMillis("2026-10-04", "8:30"))

    @Test
    fun `garbage date returns 0`() = assertEquals(0L, departureEpochMillis("not-a-date", "08:30"))

    // ---- leadLabel / ReminderPlan.label ----

    @Test
    fun `lead labels`() {
        assertEquals("2小时", DepartureReminder.leadLabel(120))
        assertEquals("1小时", DepartureReminder.leadLabel(60))
        assertEquals("30分钟", DepartureReminder.leadLabel(30))
        assertEquals("10分钟", DepartureReminder.leadLabel(10))
    }

    @Test
    fun `plan label sorts leads descending and shows mode`() {
        val alarm = ReminderPlan("o1", "北京→莱芜", 0L, listOf(30, 120), alarm = true)
        assertEquals("提前 2小时/30分钟 · 闹钟", alarm.label())
        val notify = ReminderPlan("o1", "北京→莱芜", 0L, listOf(60), alarm = false)
        assertEquals("提前 1小时 · 通知", notify.label())
    }

    // ---- routeText ----

    @Test
    fun `route text joins fields`() =
        assertEquals("北京→莱芜  2026-10-04 08:30", routeText("北京", "莱芜", "2026-10-04", "08:30"))

    @Test
    fun `route text tolerates nulls`() =
        // 全空时仍保留「→」「日期时间」两处分隔空格(共 3 个)
        assertEquals("→   ", routeText(null, null, null, null))

    // ---- ticketDetailText ----

    private fun detail(
        seats: List<String?>,
        checkPort: String? = null,
        carNo: String? = null,
        sendPort: String? = null,
    ): OrderDetailData {
        val tickets = seats.map { seat ->
            Ticket(
                orderId = null, suborderId = null, seatNo = seat, name = "张三", idcardNo = null,
                idcardType = "1", ticketTypeName = null, price = null, status = "0", qrCode = null,
                checkPort = checkPort, carNo = carNo, sendPort = sendPort, approvePrice = null,
                insurCompany = null, insurFee = null, insurNumber = null,
            )
        }
        val order = OrderSummary(
            orderId = "o1", suborderId = null, billNumber = null, sendDate = null, sendTime = null,
            startName = null, sendStationName = null, endPortName = null, status = "4",
            totalPrice = null, settleAmount = null, sendFlag = null,
        )
        return OrderDetailData(
            order = order, payType = null, payResult = null, isAllowChange = null,
            isAllowRefund = null, shiftNumber = null, ticketPassword = null,
            // ticketDetailText 读订单级 checkPort(非子票字段)
            checkPort = checkPort,
            stationLon = null, stationLat = null, stationAddr = null, stationTel = null,
            kilometer = null, runTime = null, companyName = null, isExpressway = null,
            busType = null, orderTime = null, payTime = null, protocol = null,
            smsRemind = null, tickets = tickets,
        )
    }

    @Test
    fun `detail joins present fields in order`() {
        val d = detail(seats = listOf("12", "13", "--", null), checkPort = "3号", carNo = "鲁A12345", sendPort = "2号位")
        assertEquals("检票口 3号 · 座位 12/13 · 车牌 鲁A12345 · 发车位 2号位", ticketDetailText(d))
    }

    @Test
    fun `detail skips missing fields`() {
        assertEquals("座位 7", ticketDetailText(detail(seats = listOf("7", "--"))))
        assertEquals("", ticketDetailText(detail(seats = listOf(null, "--"))))
    }

    @Test
    fun `detail excludes placeholder car number zero`() {
        assertEquals("", ticketDetailText(detail(seats = listOf(null), carNo = "0")))
    }
}
