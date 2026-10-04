package com.yuecheng.ticket.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 身份证号校验。
 * 样例号 11010519491231002X:GB 11643 校验位(MOD 11-2)验证通过的经典测试号;
 * 15 位样例 110105491231002 为旧身份证格式(仅做格式与日期检查)。
 */
class IdCardTest {

    // ---- validate: 18 位 ----

    @Test
    fun `valid 18-digit id passes`() = assertNull(IdCard.validate("11010519491231002X"))

    @Test
    fun `lowercase x is normalized`() = assertNull(IdCard.validate("11010519491231002x"))

    @Test
    fun `surrounding whitespace is trimmed`() = assertNull(IdCard.validate(" 11010519491231002X "))

    @Test
    fun `wrong checksum is rejected`() =
        assertEquals("证件号校验位不符,请核对是否输错", IdCard.validate("110105194912310021"))

    @Test
    fun `non-digit in first 17 chars is rejected`() =
        assertEquals("证件号前 17 位应为数字", IdCard.validate("1101051949123100AX"))

    @Test
    fun `invalid birth month is rejected`() =
        assertEquals("证件号出生日期有误", IdCard.validate("110105194913310021"))

    @Test
    fun `future birth date is rejected`() =
        assertEquals("证件号出生日期有误", IdCard.validate("110105209912310021"))

    // ---- validate: 15 位 ----

    @Test
    fun `valid 15-digit id passes`() = assertNull(IdCard.validate("110105491231002"))

    @Test
    fun `15-digit invalid birth is rejected`() =
        assertEquals("证件号出生日期有误", IdCard.validate("110105491331002"))

    @Test
    fun `15-digit non-digit region is rejected`() =
        assertEquals("证件号应为数字", IdCard.validate("11010A491231002"))

    @Test
    fun `15-digit non-digit tail is rejected`() =
        assertEquals("证件号应为数字", IdCard.validate("1101054912310X2"))

    // ---- validate: 长度 ----

    @Test
    fun `wrong length is rejected`() =
        assertEquals("身份证号应为 15 或 18 位", IdCard.validate("1101054912310"))

    @Test
    fun `empty id is rejected`() =
        assertEquals("身份证号应为 15 或 18 位", IdCard.validate(""))

    // ---- birth ----

    @Test
    fun `birth extracted from 18-digit id`() =
        assertEquals("1949-12-31", IdCard.birth("11010519491231002X"))

    @Test
    fun `birth extracted from 15-digit id`() =
        assertEquals("1949-12-31", IdCard.birth("110105491231002"))

    @Test
    fun `birth of malformed id is null`() = assertNull(IdCard.birth("12345"))

    @Test
    fun `birth of null is null`() = assertNull(IdCard.birth(null))

    @Test
    fun `birth with impossible date is null`() = assertNull(IdCard.birth("110105194913310021"))

    // ---- gender ----

    @Test
    fun `even 17th digit is female`() = assertEquals("女", IdCard.gender("11010519491231002X"))

    @Test
    fun `odd 17th digit is male`() = assertEquals("男", IdCard.gender("11010519491231001X"))

    @Test
    fun `gender from 15-digit id uses last digit`() {
        assertEquals("女", IdCard.gender("110105491231002"))
        assertEquals("男", IdCard.gender("110105491231001"))
    }

    @Test
    fun `gender of null is null`() = assertNull(IdCard.gender(null))
}
