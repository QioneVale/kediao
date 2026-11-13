package com.classmate.reminder

import com.classmate.reminder.data.TimeUtil
import com.classmate.reminder.parser.ScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleParserTest {

    @Test
    fun parseStandardBlocks() {
        val text = """
            课程：高等数学
            星期：1
            开始：08:00
            结束：09:40
            周次：1-8,10-16
            地点：教学楼A101
            教师：张三

            课程：大学英语
            星期：3
            开始：10:00
            结束：11:40
            周次：1-16周(单)
            地点：外语楼B203
            教师：李四
        """.trimIndent()

        val result = ScheduleParser.parse(text, 18)
        assertEquals(2, result.courses.size)

        val math = result.courses[0]
        assertEquals("高等数学", math.name)
        assertEquals(1, math.dayOfWeek)
        assertEquals(480, math.startMin)
        assertEquals(580, math.endMin)
        assertEquals("教学楼A101", math.location)
        assertEquals("张三", math.teacher)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16), math.weeks)

        val english = result.courses[1]
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), english.weeks)
    }

    @Test
    fun parseSectionOnly() {
        val text = """
            课程：大学物理
            星期：2
            节次：3-4节
            周次：1-16
            地点：实验楼C301
            教师：王五
        """.trimIndent()

        val result = ScheduleParser.parse(text, 18)
        assertEquals(1, result.courses.size)
        assertEquals(600, result.courses[0].startMin)
        assertEquals(700, result.courses[0].endMin)
    }

    @Test
    fun parseMarkdownBullets() {
        val text = """
            - 课程：体育
            - 星期：星期三
            - 时间：16:00-17:40
            - 周次：1-18
            - 地点：操场
        """.trimIndent()

        val result = ScheduleParser.parse(text, 18)
        assertEquals(1, result.courses.size)
        assertEquals("体育", result.courses[0].name)
        assertEquals(3, result.courses[0].dayOfWeek)
        assertEquals(960, result.courses[0].startMin)
        assertEquals(1060, result.courses[0].endMin)
    }

    @Test
    fun parseJsonArray() {
        val text = """[{"name":"形势与政策","day":5,"start":"19:00","end":"20:40","weeks":"9-16","location":"报告厅"}]"""
        val result = ScheduleParser.parse(text, 18)
        assertEquals(1, result.courses.size)
        assertEquals("形势与政策", result.courses[0].name)
        assertEquals(5, result.courses[0].dayOfWeek)
        assertEquals(1140, result.courses[0].startMin)
    }

    @Test
    fun mergeSameCourseDifferentWeeks() {
        val text = """
            课程：马克思主义基本原理
            星期：4
            开始：08:00
            结束：09:40
            周次：1-8
            地点：A101

            课程：马克思主义基本原理
            星期：4
            开始：08:00
            结束：09:40
            周次：10-16
            地点：A101
        """.trimIndent()

        val result = ScheduleParser.parse(text, 18)
        assertEquals(1, result.courses.size)
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16),
            result.courses[0].weeks
        )
    }

    @Test
    fun ignoreLeadingChatter() {
        val text = """
            好的，以下是识别结果：

            课程：线性代数
            星期：5
            开始：14:00
            结束：15:40
            周次：1-18
            地点：B204
            教师：未填写
        """.trimIndent()

        val result = ScheduleParser.parse(text, 18)
        assertEquals(1, result.courses.size)
        assertEquals("线性代数", result.courses[0].name)
        assertEquals("B204", result.courses[0].location)
        assertEquals("", result.courses[0].teacher)
    }

    @Test
    fun parseWeekVariants() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), TimeUtil.parseWeeks("1-8周", 18))
        assertEquals(listOf(1, 3, 5), TimeUtil.parseWeeks("1,3,5", 18))
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), TimeUtil.parseWeeks("1-16周(单)", 18))
        assertEquals((1..18).toList(), TimeUtil.parseWeeks("", 18))
        assertEquals(listOf(2, 4, 6, 8), TimeUtil.parseWeeks("2,4,6,8", 18))
    }

    @Test
    fun weekTextCompress() {
        assertEquals("1-8,10-16", TimeUtil.weeksToText((1..8).toList() + (10..16).toList()))
        assertEquals("3", TimeUtil.weeksToText(listOf(3)))
    }

    @Test
    fun emptyInputGivesNoCourse() {
        val result = ScheduleParser.parse("   ", 18)
        assertTrue(result.courses.isEmpty())
    }
}
