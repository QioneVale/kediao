package com.classmate.reminder.data

import android.content.Context
import android.content.SharedPreferences
import com.classmate.reminder.alarm.ReminderScheduler
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object Store {

    private const val PREF = "classmate_store"
    private const val KEY_TERM = "term_start"
    private const val KEY_WEEKS = "total_weeks"
    private const val KEY_OVERRIDE = "week_override"
    private const val KEY_REMIND = "remind_minutes"
    private const val KEY_SOUND = "sound_alarm"
    private const val KEY_SILENT = "auto_silent"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_COURSES = "courses"

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    val courses = ArrayList<Course>()
    private val listeners = ArrayList<() -> Unit>()

    var termStart: LocalDate = LocalDate.now()
        set(value) {
            field = value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            prefs?.edit()?.putString(KEY_TERM, field.toString())?.apply()
        }

    var totalWeeks: Int = 20
        set(value) {
            field = value.coerceIn(1, 30)
            prefs?.edit()?.putInt(KEY_WEEKS, field)?.apply()
        }

    var weekOverride: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            prefs?.edit()?.putInt(KEY_OVERRIDE, field)?.apply()
        }

    var remindMinutes: Int = 15
        set(value) {
            field = value.coerceIn(0, 120)
            prefs?.edit()?.putInt(KEY_REMIND, field)?.apply()
        }

    var soundAlarm: Boolean = true
        set(value) {
            field = value
            prefs?.edit()?.putBoolean(KEY_SOUND, value)?.apply()
        }

    var autoSilent: Boolean = true
        set(value) {
            field = value
            prefs?.edit()?.putBoolean(KEY_SILENT, value)?.apply()
        }

    var enabled: Boolean = true
        set(value) {
            field = value
            prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        }

    fun init(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        val sp = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = sp
        termStart = parseDate(sp.getString(KEY_TERM, null)) ?: defaultTermStart()
        totalWeeks = sp.getInt(KEY_WEEKS, 20)
        weekOverride = sp.getInt(KEY_OVERRIDE, 0)
        remindMinutes = sp.getInt(KEY_REMIND, 15)
        soundAlarm = sp.getBoolean(KEY_SOUND, true)
        autoSilent = sp.getBoolean(KEY_SILENT, true)
        enabled = sp.getBoolean(KEY_ENABLED, true)
        loadCourses()
    }

    class Upcoming(val course: Course, val start: LocalDateTime, val dayOffset: Int)

    // ---------- 时间与周次 ----------

    fun defaultTermStart(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekOf(date: LocalDate): Int {
        val start = termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = ChronoUnit.DAYS.between(start, date)
        return Math.floorDiv(days, 7L).toInt() + 1
    }

    fun currentWeek(): Int =
        if (weekOverride > 0) weekOverride else weekOf(LocalDate.now()).coerceAtLeast(1)

    fun weekStartOf(week: Int): LocalDate =
        termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(((week - 1) * 7).toLong())

    fun weekRangeText(week: Int): String {
        val s = weekStartOf(week)
        val e = s.plusDays(6)
        val f = DateTimeFormatter.ofPattern("M/d")
        return "${s.format(f)} - ${e.format(f)}"
    }

    fun coursesForWeek(week: Int): List<Course> = courses.filter { week in it.weeks }

    fun isInClass(now: LocalDateTime = LocalDateTime.now()): Boolean {
        val week = weekOf(now.toLocalDate())
        val dow = now.toLocalDate().dayOfWeek.value
        val minute = now.hour * 60 + now.minute
        return courses.any { it.dayOfWeek == dow && week in it.weeks && minute >= it.startMin && minute < it.endMin }
    }

    fun nextUpcoming(from: LocalDateTime = LocalDateTime.now(), maxDays: Int = 14): Upcoming? {
        for (offset in 0..maxDays) {
            val date = from.toLocalDate().plusDays(offset.toLong())
            val week = weekOf(date)
            val dow = date.dayOfWeek.value
            var best: Upcoming? = null
            for (c in courses) {
                if (c.dayOfWeek != dow || week !in c.weeks) continue
                val st = date.atTime(c.startMin / 60, c.startMin % 60)
                if (st.isAfter(from)) {
                    val cur = best
                    if (cur == null || st.isBefore(cur.start)) best = Upcoming(c, st, offset)
                }
            }
            if (best != null) return best
        }
        return null
    }

    // ---------- 课程增删改 ----------

    fun upsert(course: Course) {
        val idx = courses.indexOfFirst { it.id == course.id }
        if (idx >= 0) courses[idx] = course else courses.add(course)
        sortCourses()
        persist()
    }

    fun delete(id: String) {
        courses.removeAll { it.id == id }
        persist()
    }

    fun replaceAll(list: List<Course>) {
        courses.clear()
        courses.addAll(list)
        sortCourses()
        persist()
    }

    fun clear() {
        courses.clear()
        persist()
    }

    private fun sortCourses() {
        courses.sortWith(compareBy({ it.dayOfWeek }, { it.startMin }, { it.name }))
    }

    fun reschedule() {
        appContext?.let { ReminderScheduler.refresh(it) }
        notifyChanged()
    }

    // ---------- 持久化 ----------

    private fun persist() {
        val arr = JSONArray()
        courses.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString(KEY_COURSES, arr.toString())?.apply()
        appContext?.let { ReminderScheduler.refresh(it) }
        notifyChanged()
    }

    private fun loadCourses() {
        courses.clear()
        val raw = prefs?.getString(KEY_COURSES, null)
        if (!raw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { courses.add(Course.fromJson(it)) }
                }
            }
        }
        sortCourses()
    }

    private fun parseDate(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(s) }.getOrNull()
    }

    // ---------- 监听 ----------

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it() }
    }
}
