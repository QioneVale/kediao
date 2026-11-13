package com.classmate.reminder.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.classmate.reminder.data.Course

/**
 * 一周课表网格：横轴 7 天，纵轴时间。课程块按上课时间自动定位，重叠的课自动并排。
 */
class TimetableGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onCourseClick: ((Course) -> Unit)? = null
    var onEmptyClick: ((day: Int, minute: Int) -> Unit)? = null

    private var weekCourses: List<Course> = emptyList()
    private var todayColumn: Int = 0

    private val d = resources.displayMetrics.density
    private val headerH = 40f * d
    private val timeColW = 40f * d
    private val pxPerMin = 1.30f * d
    private val startMinute = 7 * 60 + 30
    private val endMinute = 22 * 60 + 10

    private val dayLabels = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val weekendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFBFBFE.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEFF1F7.toInt()
        strokeWidth = 1f * d
    }
    private val headerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE9ECF5.toInt()
        strokeWidth = 1f * d
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFA6ACBE.toInt()
        textSize = 9f * d
        textAlign = Paint.Align.RIGHT
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5B6178.toInt()
        textSize = 11f * d
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * d
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val todayBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4C6FFF.toInt() }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * d
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEAF0FF.toInt()
        textSize = 9f * d
    }

    private val hitRects = ArrayList<Pair<RectF, Course>>()
    private val rect = RectF()
    private val roundPath = Path()

    private val palette = intArrayOf(
        0xFF4C6FFF.toInt(), 0xFF17A2A2.toInt(), 0xFFF2994A.toInt(), 0xFF9B51E0.toInt(),
        0xFFE05B5B.toInt(), 0xFF2D9CDB.toInt(), 0xFF2FA36B.toInt(), 0xFFC05C9E.toInt()
    )

    fun setData(courses: List<Course>, week: Int, todayDayOfWeek: Int) {
        weekCourses = courses
        todayColumn = if (week == com.classmate.reminder.data.Store.currentWeek()) todayDayOfWeek else 0
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (headerH + (endMinute - startMinute) * pxPerMin).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val colW = (w - timeColW) / 7f

        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawRect(timeColW + colW * 5, headerH, w, h, weekendPaint)

        // 横向时间线 + 时间刻度
        for (hour in 8..22) {
            val y = headerH + (hour * 60 - startMinute) * pxPerMin
            if (y > h) break
            canvas.drawLine(timeColW, y, w, y, linePaint)
            canvas.drawText(String.format("%02d:00", hour), timeColW - 5f * d, y + 3.2f * d, timePaint)
        }

        // 纵向分隔线
        for (i in 0..7) {
            val x = timeColW + colW * i
            canvas.drawLine(x, headerH, x, h, linePaint)
        }
        canvas.drawLine(0f, headerH, w, headerH, headerLinePaint)

        // 表头
        for (i in 0..6) {
            val cx = timeColW + colW * i + colW / 2
            val cy = headerH / 2
            if (i + 1 == todayColumn) {
                val r = 13f * d
                canvas.drawRoundRect(
                    RectF(cx - r * 1.7f, cy - r, cx + r * 1.7f, cy + r), r, r, todayBgPaint
                )
                canvas.drawText(dayLabels[i], cx, cy + 4f * d, todayPaint)
            } else {
                canvas.drawText(dayLabels[i], cx, cy + 4f * d, dayPaint)
            }
        }

        hitRects.clear()

        // 按天分组并处理重叠
        for (day in 1..7) {
            val list = weekCourses.filter { it.dayOfWeek == day }.sortedBy { it.startMin }
            if (list.isEmpty()) continue

            var groupStart = 0
            while (groupStart < list.size) {
                var groupEnd = list[groupStart].endMin
                var j = groupStart + 1
                while (j < list.size && list[j].startMin < groupEnd) {
                    groupEnd = maxOf(groupEnd, list[j].endMin)
                    j++
                }
                val group = list.subList(groupStart, j)

                // 贪心分配泳道
                val lanes = ArrayList<MutableList<Course>>()
                for (c in group) {
                    var placed = false
                    for (lane in lanes) {
                        if (lane.last().endMin <= c.startMin) {
                            lane.add(c); placed = true; break
                        }
                    }
                    if (!placed) lanes.add(mutableListOf(c))
                }
                val laneCount = lanes.size
                for ((laneIndex, lane) in lanes.withIndex()) {
                    for (c in lane) {
                        val left = timeColW + colW * (day - 1) + colW / laneCount * laneIndex
                        val right = left + colW / laneCount
                        drawBlock(canvas, c, left, right)
                    }
                }
                groupStart = j
            }
        }
    }

    private fun drawBlock(canvas: Canvas, c: Course, left: Float, right: Float) {
        val top = headerH + (c.startMin - startMinute) * pxPerMin
        val bottom = headerH + (c.endMin - startMinute) * pxPerMin
        rect.set(left + 1.5f * d, top + 1.5f * d, right - 1.5f * d, bottom - 1.5f * d)
        if (rect.height() < 16f * d) return

        val color = palette[Math.abs(c.colorIndex) % palette.size]
        blockPaint.color = color
        roundPath.reset()
        val radius = 7f * d
        roundPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.drawPath(roundPath, blockPaint)

        hitRects.add(RectF(rect) to c)

        val pad = 5f * d
        val textWidth = rect.width() - pad * 2
        if (textWidth <= 8f * d) return

        var y = rect.top + 14f * d
        val name = TextUtils.ellipsize(c.name, namePaint, textWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(name, rect.left + pad, y, namePaint)

        // 高度够才画第二行
        if (rect.height() > 30f * d && c.location.isNotBlank()) {
            y += 12f * d
            val loc = TextUtils.ellipsize(c.location, subPaint, textWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(loc, rect.left + pad, y, subPaint)
        }
        if (rect.height() > 46f * d) {
            y += 12f * d
            val time = TextUtils.ellipsize(c.startText, subPaint, textWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(time, rect.left + pad, y, subPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            for (i in hitRects.indices.reversed()) {
                val (r, course) = hitRects[i]
                if (r.contains(event.x, event.y)) {
                    onCourseClick?.invoke(course)
                    return true
                }
            }
            if (event.y > headerH && event.x > timeColW) {
                val colW = (width - timeColW) / 7f
                val day = (((event.x - timeColW) / colW).toInt() + 1).coerceIn(1, 7)
                val minute = startMinute + ((event.y - headerH) / pxPerMin).toInt()
                onEmptyClick?.invoke(day, minute / 5 * 5)
            }
            return true
        }
        return true
    }
}
