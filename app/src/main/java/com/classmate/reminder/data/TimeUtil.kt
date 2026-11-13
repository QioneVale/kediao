package com.classmate.reminder.data

import java.util.Locale

object TimeUtil {

    fun hm(minute: Int): String =
        String.format(Locale.CHINA, "%02d:%02d", (minute / 60) % 24, minute % 60)

    fun parseHm(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val m = Regex("(\\d{1,2})\\s*[:：.．]\\s*(\\d{1,2})").find(s) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val mi = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || mi !in 0..59) return null
        return h * 60 + mi
    }

    fun weeksToText(weeks: List<Int>): String {
        if (weeks.isEmpty()) return "—"
        val sorted = weeks.distinct().sorted()
        val sb = StringBuilder()
        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
            if (sb.isNotEmpty()) sb.append(",")
            if (j == i) sb.append(sorted[i]) else sb.append(sorted[i]).append("-").append(sorted[j])
            i = j + 1
        }
        return sb.toString()
    }

    /**
     * 解析周次：支持 "1-16"、"1-8,10-16"、"1,3,5"、"1-16周(单)"、"单周" 等写法
     */
    fun parseWeeks(text: String?, total: Int): List<Int> {
        val max = if (total <= 0) 20 else total
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return (1..max).toList()

        val oddOnly = raw.contains("单")
        val evenOnly = raw.contains("双")

        val result = LinkedHashSet<Int>()
        val rangeRegex = Regex("(\\d{1,2})\\s*[-~至到]\\s*(\\d{1,2})")
        val ranges = rangeRegex.findAll(raw).toList()
        if (ranges.isNotEmpty()) {
            for (m in ranges) {
                val a = m.groupValues[1].toIntOrNull() ?: continue
                val b = m.groupValues[2].toIntOrNull() ?: continue
                for (w in minOf(a, b)..maxOf(a, b)) if (w in 1..max) result.add(w)
            }
        } else {
            Regex("\\d{1,2}").findAll(raw).forEach {
                val w = it.value.toIntOrNull() ?: return@forEach
                if (w in 1..max) result.add(w)
            }
        }

        var list = result.toList().sorted()
        if (oddOnly) list = list.filter { it % 2 == 1 }
        if (evenOnly) list = list.filter { it % 2 == 0 }
        return list.ifEmpty { (1..max).toList() }
    }

    fun dayName(day: Int): String =
        arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            .getOrElse(day - 1) { "周一" }
}
