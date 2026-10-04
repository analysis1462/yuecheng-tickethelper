package com.yuecheng.ticket.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 主订单/子票状态码与展示文案的映射(契约见 README) */
class StatusLabelTest {

    @Test
    fun `pending pay statuses`() {
        assertEquals("待支付", statusLabelOf("0"))
        assertEquals("待支付", statusLabelOf("2"))
    }

    @Test
    fun `issuing statuses`() {
        assertEquals("正在出票", statusLabelOf("1"))
        assertEquals("正在出票", statusLabelOf("3"))
    }

    @Test
    fun `success status`() = assertEquals("购票成功", statusLabelOf("4"))

    @Test
    fun `closed status`() = assertEquals("已关闭", statusLabelOf("5"))

    @Test
    fun `failed statuses`() {
        assertEquals("出票失败", statusLabelOf("6"))
        assertEquals("出票失败", statusLabelOf("7"))
    }

    @Test
    fun `unknown status keeps raw value`() {
        assertEquals("状态 99", statusLabelOf("99"))
        assertEquals("状态 null", statusLabelOf(null))
    }

    @Test
    fun `ticket status 0 is purchased success`() {
        // 子票状态码是另一套:"0"=购票成功
        assertEquals("购票成功", ticketStatusLabel("0"))
        assertEquals("已关闭", ticketStatusLabel("5"))
    }

    @Test
    fun `order status sets stay consistent with labels`() {
        OrderStatus.PENDING_PAY.forEach { assertEquals("待支付", statusLabelOf(it)) }
        OrderStatus.ISSUING.forEach { assertEquals("正在出票", statusLabelOf(it)) }
        OrderStatus.FAILED.forEach { assertEquals("出票失败", statusLabelOf(it)) }
        assertEquals("购票成功", statusLabelOf(OrderStatus.SUCCESS))
        assertEquals(OrderStatus.ISSUING + OrderStatus.SUCCESS, OrderStatus.TRAVEL_READY)
    }
}
