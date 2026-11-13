package com.classmate.reminder.parser

import com.classmate.reminder.data.Course
import com.classmate.reminder.data.SectionTable
import com.classmate.reminder.data.TimeUtil
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把 AI 输出的课表文本解析成课程。
 * 兼容：标准七行格式、Markdown 列表、JSON 数组、只给节次没给时间、单双周等。
 */
object ScheduleParser {

    data class Result(val courses: List<Course>, val errors: List<String>)

    fun parse(raw: String, totalWeeks: Int): Result {
        val text = normalize(raw)
        if (text.isBlank()) return Result(emptyList(), listOf("内容是空的"))

        val fromJson = tryParseJson(text, totalWeeks)
        if (fromJson.isNotEmpty()) return Result(fromJson, emptyList())

        val blocks = splitBlocks(text)
        val list = ArrayList<Course>()
        val errors = ArrayList<String>()

        blocks.forEachIndexed { index, block ->
            val course = parseBlock(block, totalWeeks)
            if (course == null) {
                // 只有一两行的通常是 AI 的寒暄/标题，不算错误
                if (block.lines().count { it.isNotBlank() } >= 3) {
                    val preview = block.replace("\n", " ").trim().take(22)
                    errors.add("第 ${index + 1} 段没认出来：$preview…")
                }
            } else {
                list.add(course)
            }
        }

        return Result(merge(list), errors)
    }

    // ---------------- 预处理 ----------------

    private fun normalize(s: String): String {
        var t = s.replace("\r\n", "\n").replace("\r", "\n")
        t = t.replace("```json", "").replace("```JSON", "").replace("```", "")
        t = t.replace('：', ':')
        t = t.replace('，', ',')
        t = t.replace('；', ';')
        t = t.replace('－', '-')
        t = t.replace('—', '-')
        t = t.replace('–', '-')
        t = t.replace('～', '-')
        t = t.replace('\u00A0', ' ')
        return t.trim()
    }

    private val startKey = Regex("^[\\s*\\-•·#>\\d.、)）]*?(课程名称|课程|科目|课名)\\s*:")

    private fun splitBlocks(text: String): List<String> {
        val result = ArrayList<StringBuilder>()
        var current: StringBuilder? = null
        for (line in text.lines()) {
            val l = line.trim()
            if (l.isEmpty()) {
                current = null
                continue
            }
            if (startKey.containsMatchIn(l)) {
                current = StringBuilder(l)
                result.add(current)
            } else if (current == null) {
                current = StringBuilder(l)
                result.add(current)
            } else {
                current.append("\n").append(l)
            }
        }
        return result.map { it.toString() }
    }

    // ---------------- 单段解析 ----------------

    private fun parseBlock(block: String, totalWeeks: Int): Course? {
        val map = LinkedHashMap<String, String>()
        for (line in block.lines()) {
            val l = line.trim().trimStart('-', '*', '•', '·', ' ', '#', '|')
            if (l.isEmpty()) continue
            val idx = l.indexOf(':')
            if (idx <= 0) continue
            var key = l.substring(0, idx).trim().trim('*', '【', '】', ' ', '#')
            key = key.replace(Regex("^\\d+[.、)）]\\s*"), "").trim()
            val value = l.substring(idx + 1).trim().trim('*', '`', ' ', '|')
            if (key.isNotEmpty() && value.isNotEmpty()) map[key] = value
        }

        fun pick(vararg keys: String): String? {
            for (k in keys) {
                for ((mk, mv) in map) if (mk == k) return mv
            }
            for (k in keys) {
                for ((mk, mv) in map) if (mk.contains(k) || k.contains(mk)) return mv
            }
            return null
        }

        val name = pick("课程名称", "课程", "科目", "课名", "名称")?.trim()
        if (name.isNullOrBlank()) return null

        val day = parseDay(pick("星期", "周几", "上课日", "日期")) ?: return null

        var start: Int? = null
        var end: Int? = null

        val timeText = pick("时间", "时段", "上课时间")
        if (timeText != null) {
            val r = parseRange(timeText)
            if (r != null) {
                start = r.first
                end = r.second
            }
        }
        if (start == null) start = TimeUtil.parseHm(pick("开始", "起始"))
        if (end == null) end = TimeUtil.parseHm(pick("结束", "下课"))

        if (start == null || end == null) {
            val sectionPair = parseSection(pick("节次", "节数", "节")) ?: parseSectionFromBlock(block)
            if (sectionPair != null) {
                start = SectionTable.startOf(sectionPair.first)
                end = SectionTable.endOf(sectionPair.second)
            }
        }

        if (start == null || end == null) {
            val r = parseRange(block)
            if (r != null) {
                start = r.first
                end = r.second
            }
        }

        if (start == null || end == null || start == end) return null
        if (end!! < start!!) {
            val tmp = start!!
            start = end
            end = tmp
        }
        if (end!! - start!! < 20) end = start!! + 45

        val weeksText = pick("周次", "周数", "上课周", "周") ?: "1-$totalWeeks"
        val weeks = TimeUtil.parseWeeks(weeksText, totalWeeks)

        var location = pick("地点", "教室", "场地", "上课地点")?.trim().orEmpty()
        if (location.isBlank() || location == "无") location = "未填写"

        var teacher = pick("教师", "老师", "授课")?.trim().orEmpty()
        if (teacher.isBlank() || teacher == "无") teacher = ""

        return Course(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = day,
            startMin = start!!,
            endMin = end!!,
            weeks = weeks,
            colorIndex = Math.abs(name.hashCode()) % 8
        )
    }

    private fun parseDay(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val text = s.trim()
        val cn = listOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
        for ((ch, v) in cn) {
            if (text.contains("周$ch") || text.contains("星期$ch") || text.contains("礼拜$ch")) return v
        }
        if (text == "一" || text == "二" || text == "三" || text == "四" || text == "五" || text == "六" || text == "日") {
            return cn.first { it.first == text }.second
        }
        val n = Regex("\\d").find(text)?.value?.toIntOrNull()
        if (n != null && n in 1..7) return n
        if (text.contains("一")) return 1
        if (text.contains("二")) return 2
        if (text.contains("三")) return 3
        if (text.contains("四")) return 4
        if (text.contains("五")) return 5
        if (text.contains("六")) return 6
        return null
    }

    /** 解析 "08:00-09:40" / "8:00~9:40" / "0800-0940" / "08:00 至 09:40" */
    private fun parseRange(s: String?): Pair<Int, Int>? {
        if (s.isNullOrBlank()) return null

        val full = Regex(
            "(\\d{1,2})\\s*[:.．]\\s*(\\d{1,2})\\s*[-~至到]\\s*(\\d{1,2})\\s*[:.．]\\s*(\\d{1,2})"
        ).find(s)
        if (full != null) {
            val h1 = full.groupValues[1].toIntOrNull()
            val m1 = full.groupValues[2].toIntOrNull()
            val h2 = full.groupValues[3].toIntOrNull()
            val m2 = full.groupValues[4].toIntOrNull()
            if (h1 != null && m1 != null && h2 != null && m2 != null &&
                h1 in 0..23 && h2 in 0..23 && m1 in 0..59 && m2 in 0..59
            ) {
                return (h1 * 60 + m1) to (h2 * 60 + m2)
            }
        }

        val times = Regex("(\\d{1,2})\\s*[:.．]\\s*(\\d{2})").findAll(s).mapNotNull {
            val h = it.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val mi = it.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            if (h in 0..23 && mi in 0..59) h * 60 + mi else null
        }.toList()
        if (times.size >= 2) return times[0] to times[1]

        val compact = Regex("(\\d{3,4})\\s*[-~至到]\\s*(\\d{3,4})").find(s)
        if (compact != null) {
            val a = compact.groupValues[1].toIntOrNull()
            val b = compact.groupValues[2].toIntOrNull()
            if (a != null && b != null) {
                val h1 = a / 100
                val m1 = a % 100
                val h2 = b / 100
                val m2 = b % 100
                if (h1 in 0..23 && h2 in 0..23 && m1 in 0..59 && m2 in 0..59) {
                    return (h1 * 60 + m1) to (h2 * 60 + m2)
                }
            }
        }
        return null
    }

    /** "1-2节" / "第1-2节" / "第3节" */
    private fun parseSection(s: String?): Pair<Int, Int>? {
        if (s.isNullOrBlank()) return null
        val range = Regex("(\\d{1,2})\\s*[-~至]\\s*(\\d{1,2})").find(s)
        if (range != null) {
            val a = range.groupValues[1].toIntOrNull()
            val b = range.groupValues[2].toIntOrNull()
            if (a != null && b != null && a in 1..12 && b in 1..12 && a <= b) return a to b
        }
        val single = Regex("(\\d{1,2})").find(s)?.value?.toIntOrNull()
        if (single != null && single in 1..12) return single to single
        return null
    }

    private fun parseSectionFromBlock(block: String): Pair<Int, Int>? {
        val range = Regex("第?\\s*(\\d{1,2})\\s*[-~至]\\s*(\\d{1,2})\\s*节").find(block)
        if (range != null) {
            val a = range.groupValues[1].toIntOrNull()
            val b = range.groupValues[2].toIntOrNull()
            if (a != null && b != null && a in 1..12 && b in 1..12) return a to b
        }
        val single = Regex("第\\s*(\\d{1,2})\\s*节").find(block)?.groupValues?.get(1)?.toIntOrNull()
        if (single != null && single in 1..12) return single to single
        return null
    }

    // ---------------- JSON ----------------

    private fun tryParseJson(text: String, totalWeeks: Int): List<Course> {
        val bracket = text.indexOf('[')
        val brace = text.indexOf('{')
        val candidates = listOf(bracket, brace).filter { it >= 0 }
        if (candidates.isEmpty()) return emptyList()
        val body = text.substring(candidates.minOrNull() ?: 0)

        val array = runCatching { JSONArray(body) }.getOrNull()
            ?: runCatching { JSONObject(body).optJSONArray("courses") }.getOrNull()
            ?: runCatching { JSONObject(body).optJSONArray("data") }.getOrNull()
            ?: return emptyList()

        val out = ArrayList<Course>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val name = firstNonBlank(
                o.optString("name"), o.optString("course"), o.optString("课程"), o.optString("课程名称")
            ) ?: continue
            val day = parseDay(firstNonBlank(o.optString("day"), o.optString("weekday"), o.optString("星期")))
                ?: continue

            var start = TimeUtil.parseHm(firstNonBlank(o.optString("start"), o.optString("开始")))
            var end = TimeUtil.parseHm(firstNonBlank(o.optString("end"), o.optString("结束")))
            if (start == null || end == null) {
                val r = parseRange(firstNonBlank(o.optString("time"), o.optString("时间")))
                if (r != null) {
                    start = r.first
                    end = r.second
                }
            }
            if (start == null || end == null) continue

            val weeksRaw = when {
                o.has("weeks") && o.opt("weeks") is JSONArray -> {
                    val arr = o.optJSONArray("weeks")!!
                    (0 until arr.length()).mapNotNull { arr.optInt(it, -1).takeIf { w -> w > 0 } }.joinToString(",")
                }
                o.has("weeks") -> o.opt("weeks")?.toString().orEmpty()
                else -> firstNonBlank(o.optString("周次"), o.optString("weekRange")).orEmpty()
            }
            val weeks = TimeUtil.parseWeeks(weeksRaw.ifBlank { "1-$totalWeeks" }, totalWeeks)

            val location = firstNonBlank(o.optString("location"), o.optString("地点"), o.optString("教室")) ?: "未填写"
            val teacher = firstNonBlank(o.optString("teacher"), o.optString("教师"), o.optString("老师")) ?: ""

            out.add(
                Course(
                    name = name,
                    teacher = teacher,
                    location = location,
                    dayOfWeek = day,
                    startMin = minOf(start, end),
                    endMin = maxOf(start, end),
                    weeks = weeks,
                    colorIndex = Math.abs(name.hashCode()) % 8
                )
            )
        }
        return out
    }

    private fun firstNonBlank(vararg values: String): String? =
        values.firstOrNull { it.isNotBlank() && it != "null" }

    // ---------------- 合并同名同时间的课 ----------------

    private fun merge(list: List<Course>): List<Course> {
        val result = ArrayList<Course>()
        for (c in list) {
            val exist = result.firstOrNull {
                it.name == c.name &&
                    it.dayOfWeek == c.dayOfWeek &&
                    it.startMin == c.startMin &&
                    it.endMin == c.endMin &&
                    it.location == c.location
            }
            if (exist == null) {
                result.add(c)
            } else {
                val idx = result.indexOf(exist)
                result[idx] = exist.copy(weeks = (exist.weeks + c.weeks).distinct().sorted())
            }
        }
        return result
    }
}
