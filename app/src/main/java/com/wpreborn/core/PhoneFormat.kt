package com.wpreborn.core

/**
 * Brazilian phone numbers as people write them: "+55 (47) 3090-1276" for a landline and
 * "+55 (47) 99999-8888" for a mobile. Handles the national trunk 0 and carrier codes
 * ("0 21 47 ..."), +55 / 55, numbers without the area code, 0800 / 0300 / 4004 service
 * numbers and short codes; other countries keep Android's own formatting.
 */
object PhoneFormat {
    fun br(raw: String): String {
        val number = raw.trim()
        if (number.isEmpty()) return number
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return number
        val international = number.startsWith("+") || number.startsWith("00")
        var d = digits
        // Another country: as Android formats it.
        if (international) {
            d = d.removePrefix("00")
            if (!d.startsWith("55")) {
                return runCatching { android.telephony.PhoneNumberUtils.formatNumber("+$d", "BR") }.getOrNull() ?: number
            }
            d = d.removePrefix("55")
        } else if (d.startsWith("55") && (d.length == 12 || d.length == 13)) {
            d = d.removePrefix("55")
        }
        // 0800 123 4567, 0300 ..., 0500 ..., 0900 ...
        if (d.length == 11 && (d.startsWith("0800") || d.startsWith("0300") || d.startsWith("0500") || d.startsWith("0900"))) {
            return "${d.substring(0, 4)} ${d.substring(4, 7)} ${d.substring(7)}"
        }
        // 4004-1234 / 3003-1234 (capital-wide numbers)
        if (d.length == 8 && (d.startsWith("400") || d.startsWith("300"))) return "${d.substring(0, 4)}-${d.substring(4)}"
        // Trunk 0, with or without a carrier code: 0 47 3090 1276, 0 21 47 99999 8888.
        if (d.startsWith("0")) {
            d = when (d.length) {
                11, 12 -> d.substring(1)
                13, 14 -> d.substring(3)
                else -> d
            }
        }
        return when (d.length) {
            11 -> "+55 (${d.substring(0, 2)}) ${d.substring(2, 7)}-${d.substring(7)}"
            10 -> "+55 (${d.substring(0, 2)}) ${d.substring(2, 6)}-${d.substring(6)}"
            9 -> "${d.substring(0, 5)}-${d.substring(5)}"
            8 -> "${d.substring(0, 4)}-${d.substring(4)}"
            else -> number // short codes, *#, anything else as it came
        }
    }
}
