package com.classmate.reminder.ui

import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.classmate.reminder.R
import com.classmate.reminder.data.Store
import com.classmate.reminder.data.TimeUtil
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TimetableFragment : Fragment(R.layout.fragment_timetable) {

    private var week: Int = 1
    private lateinit var grid: TimetableGridView
    private lateinit var tvWeek: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var scroll: ScrollView

    private val listener: () -> Unit = { render() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        week = Store.currentWeek()
        grid = view.findViewById(R.id.grid)
        tvWeek = view.findViewById(R.id.tv_week)
        tvEmpty = view.findViewById(R.id.tv_empty)
        scroll = view.findViewById(R.id.scroll)

        view.findViewById<TextView>(R.id.tv_today).text = todayLabel()

        grid.onCourseClick = { course ->
            CourseDialog.show(requireContext(), course, course.dayOfWeek, course.startMin) { render() }
        }
        grid.onEmptyClick = { day, minute ->
            CourseDialog.show(requireContext(), null, day, minute) { render() }
        }

        view.findViewById<View>(R.id.btn_prev_week).setOnClickListener {
            if (week > 1) {
                week--
                render()
            }
        }
        view.findViewById<View>(R.id.btn_next_week).setOnClickListener {
            week++
            render()
        }
        view.findViewById<View>(R.id.btn_this_week).setOnClickListener {
            week = Store.currentWeek()
            render()
            scrollToNow()
        }
        view.findViewById<View>(R.id.fab_add).setOnClickListener {
            val now = LocalTime.now()
            CourseDialog.show(
                requireContext(), null,
                LocalDate.now().dayOfWeek.value,
                now.hour * 60 / 5 * 5
            ) { render() }
        }

        Store.addListener(listener)
        render()
        scroll.post { scrollToNow() }
    }

    override fun onResume() {
        super.onResume()
        if (::grid.isInitialized) render()
    }

    override fun onDestroyView() {
        Store.removeListener(listener)
        super.onDestroyView()
    }

    fun refreshAll() {
        if (::grid.isInitialized) {
            week = Store.currentWeek()
            render()
        }
    }

    private fun render() {
        if (!isAdded || view == null) return
        val maxWeek = maxOf(
            Store.totalWeeks,
            Store.currentWeek(),
            Store.courses.maxOfOrNull { it.weeks.maxOrNull() ?: 1 } ?: 1
        )
        if (week < 1) week = 1
        if (week > maxWeek) week = maxWeek

        val list = Store.coursesForWeek(week)
        tvWeek.text = "第 $week 周 · ${Store.weekRangeText(week)}"
        grid.setData(list, week, LocalDate.now().dayOfWeek.value)
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        if (list.isEmpty() && week != Store.currentWeek()) {
            tvEmpty.text = "这一周没有课程 🎉"
        } else if (list.isEmpty()) {
            tvEmpty.text = "还没有课程，点右下角 + 添加，或到「导入」用 AI 识别课表"
        }
        renderNext()
    }

    private fun renderNext() {
        val v = view ?: return
        val label = v.findViewById<TextView>(R.id.tv_next_label)
        val title = v.findViewById<TextView>(R.id.tv_next_title)
        val detail = v.findViewById<TextView>(R.id.tv_next_detail)

        val upcoming = Store.nextUpcoming()
        if (upcoming == null) {
            label.text = "下一节课"
            title.text = "最近没有课"
            detail.text = if (Store.courses.isEmpty()) {
                "点右下角 + 手动添加，或到「导入」用 AI 一键识别课表"
            } else {
                "接下来的课都上完啦，好好休息"
            }
            return
        }

        val c = upcoming.course
        val minutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), upcoming.start)
        label.text = when (upcoming.dayOffset) {
            0 -> "下一节课 · 今天"
            1 -> "下一节课 · 明天"
            else -> "下一节课 · ${TimeUtil.dayName(c.dayOfWeek)}"
        }
        title.text = "${c.name} · ${c.startText}-${c.endText}"
        detail.text = buildString {
            append(c.location)
            if (c.teacher.isNotBlank()) append(" · ").append(c.teacher)
            append(" · ")
            append(if (minutes <= 1) "马上上课" else "还有 $minutes 分钟")
        }
    }

    private fun scrollToNow() {
        val now = LocalTime.now()
        val minute = now.hour * 60 + now.minute
        val y = ((minute.coerceAtLeast(8 * 60) - 8 * 60) * 1.30f * resources.displayMetrics.density).toInt()
        scroll.scrollTo(0, y)
    }

    private fun todayLabel(): String {
        val now = LocalDate.now()
        return now.format(DateTimeFormatter.ofPattern("M月d日")) + " " + TimeUtil.dayName(now.dayOfWeek.value)
    }
}
