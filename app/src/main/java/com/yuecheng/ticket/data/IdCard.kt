package com.yuecheng.ticket.data

/**
 * 身份证号校验与信息推算:
 *  - 18 位:GB 11643,校验码 ISO 7064 MOD 11-2(含 X);
 *  - 15 位:旧身份证,全号数字 + 日期段检查;
 *  - 从号码推算出生日期与性别(仅展示用,下单信息以证件号本身为准)。
 */
object IdCard {
    private val WEIGHTS = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    private val CHECK_CODES = "10X98765432"

    /** 校验;返回 null 表示通过,否则为可读错误文案 */
    fun validate(id: String): String? {
        val v = id.trim().uppercase()
        return when (v.length) {
            15 -> when {
                !v.all { it.isDigit() } -> "证件号应为数字"
                isBirthValid("19" + v.substring(6, 12)) -> null
                else -> "证件号出生日期有误"
            }
            18 -> {
                if (!v.take(17).all { it.isDigit() }) return "证件号前 17 位应为数字"
                if (!isBirthValid(v.substring(6, 14))) return "证件号出生日期有误"
                val sum = v.take(17).mapIndexed { i, c -> (c - '0') * WEIGHTS[i] }.sum()
                if (v[17] != CHECK_CODES[sum % 11]) "证件号校验位不符,请核对是否输错" else null
            }
            else -> "身份证号应为 15 或 18 位"
        }
    }

    /** yyyymmdd 是否为合法且不晚于今天的出生日期 */
    private fun isBirthValid(yyyymmdd: String): Boolean = runCatching {
        java.time.LocalDate.parse(
            "${yyyymmdd.substring(0, 4)}-${yyyymmdd.substring(4, 6)}-${yyyymmdd.substring(6, 8)}",
        ).isAfter(java.time.LocalDate.now())
    }.getOrDefault(true) == false

    /** 出生日期 yyyy-MM-dd;非身份证或日期非法返回 null */
    fun birth(id: String?): String? {
        val v = id?.trim()?.uppercase() ?: return null
        val raw = when (v.length) {
            15 -> "19" + v.substring(6, 12)
            18 -> v.substring(6, 14)
            else -> return null
        }
        return runCatching {
            java.time.LocalDate.parse("${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}")
                .toString()
        }.getOrNull()
    }

    /** 性别:男/女;18 位取第 17 位,15 位取第 15 位,奇男偶女 */
    fun gender(id: String?): String? {
        val v = id?.trim()?.uppercase() ?: return null
        val digit = when (v.length) {
            15 -> v[14]
            18 -> v[16]
            else -> return null
        }
        if (!digit.isDigit()) return null
        return if ((digit - '0') % 2 == 1) "男" else "女"
    }
}
