package com.lesicnik.wrench.ui.expenses

import java.math.BigDecimal

/** Accept plain decimal input and pasted numbers with conventional grouping separators. */
fun parseDecimalInput(input: String, maxFractionDigits: Int = 2): BigDecimal? {
    var value = input.trim().replace(" ", "").replace("\u00a0", "").replace("\u202f", "")
    if (value.isEmpty() || value.any { !it.isDigit() && it != '.' && it != ',' }) return null
    if ('.' in value && ',' in value) {
        val decimal = if (value.lastIndexOf('.') > value.lastIndexOf(',')) '.' else ','
        val group = if (decimal == '.') ',' else '.'
        val parts = value.split(decimal)
        if (parts.size != 2 || !parts[0].matches(Regex("\\d{1,3}(\\${group}\\d{3})+"))) return null
        value = value.replace(group.toString(), "").replace(decimal, '.')
    } else {
        value = value.replace(',', '.')
    }
    if (!value.matches(Regex("\\d*(\\.\\d{0,$maxFractionDigits})?")) || value == ".") return null
    return value.toBigDecimalOrNull()
}

fun decimalInputValue(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
