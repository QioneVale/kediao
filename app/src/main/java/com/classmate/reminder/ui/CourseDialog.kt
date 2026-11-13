package com.classmate.reminder.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.classmate.reminder.R
import com.classmate.reminder.data.Course
import com.classmate.reminder.data.Store
import com.classmate.reminder.data.TimeUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

object CourseDialog {

    private val days = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun show(
        context: Context,
        existing: Course?,
        day: Int,
        startMin: Int,
        onChanged: () -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_course, null)
        val etName = view.findViewById<TextInputEditText>(R.id.et_name)
        val etLocation = view.findViewById<TextInputEditText>(R.id.et_location)
        val etTeacher = view.findViewById<TextInputEditText>(R.id.et_teacher)
        val etWeeks = view.findViewById<TextInputEditText>(R.id.et_weeks)
        val spinner = view.findViewById<Spinner>(R.id.sp_day)
        val btnStart = view.findViewById<MaterialButton>(R.id.btn_start)
        val btnEnd = view.findViewById<MaterialButton>(R.id.btn_end)

        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, days)

        var start = existing?.startMin ?: (startMin / 5 * 5).coerceIn(0, 23 * 60)
        var end = existing?.endMin ?: (start + 95).coerceAtMost(23 * 60 + 55)
        var dayOfWeek = existing?.dayOfWeek ?: day

        etName.setText(existing?.name.orEmpty())
        etLocation.setText(existing?.location.orEmpty())
        etTeacher.setText(existing?.teacher.orEmpty())
        etWeeks.setText(existing?.weeksText?.replace("—", "") ?: "1-${Store.totalWeeks}")
        spinner.setSelection((dayOfWeek - 1).coerceIn(0, 6))

        fun refreshTimes() {
            btnStart.text = TimeUtil.hm(start)
            btnEnd.text = TimeUtil.hm(end)
        }
        refreshTimes()

        btnStart.setOnClickListener {
            TimePickerDialog(
                context,
                { _, h, m ->
                    start = h * 60 + m
                    if (end <= start) end = (start + 95).coerceAtMost(23 * 60 + 55)
                    refreshTimes()
                },
                start / 60, start % 60, true
            ).show()
        }
        btnEnd.setOnClickListener {
            TimePickerDialog(
                context,
                { _, h, m ->
                    val v = h * 60 + m
                    if (v > start) end = v else Toast.makeText(context, "结束时间要晚于开始时间", Toast.LENGTH_SHORT).show()
                    refreshTimes()
                },
                end / 60, end % 60, true
            ).show()
        }

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) "添加课程" else "编辑课程")
            .setView(view)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)

        if (existing != null) builder.setNeutralButton("删除", null)

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    Toast.makeText(context, "课程名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val location = etLocation.text?.toString()?.trim().orEmpty().ifBlank { "未填写" }
                val teacher = etTeacher.text?.toString()?.trim().orEmpty()
                val course = Course(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name,
                    teacher = teacher,
                    location = location,
                    dayOfWeek = spinner.selectedItemPosition + 1,
                    startMin = start,
                    endMin = end,
                    weeks = TimeUtil.parseWeeks(etWeeks.text?.toString(), Store.totalWeeks),
                    colorIndex = existing?.colorIndex ?: (Math.abs(name.hashCode()) % 8)
                )
                Store.upsert(course)
                onChanged()
                dialog.dismiss()
            }
            if (existing != null) {
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("删除课程")
                        .setMessage("确定删除「${existing.name}」吗？")
                        .setPositiveButton("删除") { _, _ ->
                            Store.delete(existing.id)
                            onChanged()
                            dialog.dismiss()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        }
        dialog.show()

        // 让自定义 View 的宽度撑满对话框
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
