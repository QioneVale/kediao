package com.classmate.reminder.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Course(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int = 1,
    val startMin: Int = 8 * 60,
    val endMin: Int = 9 * 60 + 40,
    val weeks: List<Int> = (1..16).toList(),
    val colorIndex: Int = 0
) {
    val startText: String get() = TimeUtil.hm(startMin)
    val endText: String get() = TimeUtil.hm(endMin)
    val weeksText: String get() = TimeUtil.weeksToText(weeks)
    val timeText: String get() = "$startText-$endText"

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("teacher", teacher)
        o.put("location", location)
        o.put("day", dayOfWeek)
        o.put("start", startMin)
        o.put("end", endMin)
        o.put("weeks", JSONArray(weeks))
        o.put("color", colorIndex)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Course {
            val arr = o.optJSONArray("weeks")
            val weeks = ArrayList<Int>()
            if (arr != null) {
                for (i in 0 until arr.length()) weeks.add(arr.optInt(i))
            }
            return Course(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", ""),
                teacher = o.optString("teacher", ""),
                location = o.optString("location", ""),
                dayOfWeek = o.optInt("day", 1).coerceIn(1, 7),
                startMin = o.optInt("start", 8 * 60),
                endMin = o.optInt("end", 9 * 60 + 40),
                weeks = if (weeks.isEmpty()) (1..16).toList() else weeks.distinct().sorted(),
                colorIndex = o.optInt("color", 0)
            )
        }
    }
}
