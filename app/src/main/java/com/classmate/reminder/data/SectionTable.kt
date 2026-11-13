package com.classmate.reminder.data

/**
 * 默认作息时间表（智慧职教常见节次时间）。
 * AI 识别时如果只给出节次，就按这张表换算成具体时间。
 */
object SectionTable {

    private val starts = intArrayOf(
        8 * 60,          // 1  08:00
        8 * 60 + 55,     // 2  08:55
        10 * 60,         // 3  10:00
        10 * 60 + 55,    // 4  10:55
        14 * 60,         // 5  14:00
        14 * 60 + 55,    // 6  14:55
        16 * 60,         // 7  16:00
        16 * 60 + 55,    // 8  16:55
        19 * 60,         // 9  19:00
        19 * 60 + 55,    // 10 19:55
        20 * 60 + 50,    // 11 20:50
        21 * 60 + 45     // 12 21:45
    )

    private val ends = intArrayOf(
        8 * 60 + 45,
        9 * 60 + 40,
        10 * 60 + 45,
        11 * 60 + 40,
        14 * 60 + 45,
        15 * 60 + 40,
        16 * 60 + 45,
        17 * 60 + 40,
        19 * 60 + 45,
        20 * 60 + 40,
        21 * 60 + 35,
        22 * 60 + 30
    )

    fun startOf(section: Int): Int = starts.getOrElse(section - 1) { 8 * 60 }

    fun endOf(section: Int): Int = ends.getOrElse(section - 1) { 9 * 60 + 40 }

    val readable: String = (1..12).joinToString("，") { "第${it}节 ${TimeUtil.hm(startOf(it))}-${TimeUtil.hm(endOf(it))}" }
}
