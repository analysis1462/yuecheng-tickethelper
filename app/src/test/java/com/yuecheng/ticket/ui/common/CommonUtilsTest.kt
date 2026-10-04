package com.yuecheng.ticket.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** 通用工具:价格换算、证件号脱敏、HTML 转纯文本、日期计算 */
class CommonUtilsTest {

    // ---- 价格 ----

    @Test
    fun `cents converts to yuan with two decimals`() {
        assertEquals("12.50", cents("1250"))
        assertEquals("0.00", cents("0"))
        assertEquals("0.05", cents("5"))
    }

    @Test
    fun `cents of invalid input is dash`() {
        assertEquals("-", cents(null))
        assertEquals("-", cents("abc"))
        assertEquals("-", cents(""))
    }

    @Test
    fun `yuan keeps integers plain`() {
        assertEquals("12", yuan("12"))
        assertEquals("12.50", yuan("12.50"))
        assertEquals("-", yuan(null))
        assertEquals("-", yuan("abc"))
    }

    // ---- maskId ----

    @Test
    fun `mask keeps first and last four`() {
        assertEquals("1101****002X", maskId("11010519491231002X"))
        assertEquals("--", maskId(null))
        assertEquals("12345", maskId("12345"))
    }

    // ---- htmlToText ----

    @Test
    fun `html paragraphs become single-newline separated text`() {
        // 开标签 <p> 不在块级替换表内,仅闭标签换行,故段间为单换行(与现网行为一致)
        assertEquals("第一段\n第二段", htmlToText("<p>第一段</p><p>第二段</p>"))
    }

    @Test
    fun `br tags become newlines`() {
        assertEquals("第一行\n第二行", htmlToText("第一行<br>第二行<br/>"))
    }

    @Test
    fun `entities are unescaped and tags stripped`() {
        assertEquals("a < b & c", htmlToText("a&nbsp;&lt;&nbsp;b&nbsp;&amp;&nbsp;c"))
    }

    @Test
    fun `plain text passes through`() = assertEquals("纯文本", htmlToText("纯文本"))

    @Test
    fun `null html becomes empty`() = assertEquals("", htmlToText(null))

    // ---- 日期 ----

    @Test
    fun `addDays crosses month boundary`() {
        assertEquals("2026-11-01", addDays("2026-10-31", 1))
        assertEquals("2026-10-03", addDays("2026-10-04", -1))
    }

    @Test
    fun `addDays of invalid date returns input unchanged`() {
        assertEquals("bad", addDays("bad", 1))
    }

    @Test
    fun `dayDiff computes difference in days`() {
        assertEquals(1L, dayDiff("2026-10-05", "2026-10-04"))
        assertEquals(0L, dayDiff("2026-10-04", "2026-10-04"))
        assertEquals(-1L, dayDiff("2026-10-03", "2026-10-04"))
    }

    @Test
    fun `weekLabel maps weekdays`() {
        // 2026-10-04 为周日
        assertEquals("周日", weekLabel("2026-10-04"))
        assertEquals("周一", weekLabel("2026-10-05"))
    }
}
