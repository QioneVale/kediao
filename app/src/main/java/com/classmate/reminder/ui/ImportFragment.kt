package com.classmate.reminder.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.classmate.reminder.MainActivity
import com.classmate.reminder.R
import com.classmate.reminder.data.Course
import com.classmate.reminder.data.Store
import com.classmate.reminder.data.TimeUtil
import com.classmate.reminder.parser.PromptText
import com.classmate.reminder.parser.ScheduleParser
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ImportFragment : Fragment(R.layout.fragment_import) {

    private var parsed: List<Course> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tv_prompt).text = PromptText.MAIN

        view.findViewById<View>(R.id.btn_copy_prompt).setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("课表识别提示词", PromptText.MAIN))
            toast("提示词已复制，去 AI 对话框里粘贴吧")
        }

        val etInput = view.findViewById<EditText>(R.id.et_input)

        view.findViewById<View>(R.id.btn_paste).setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(requireContext())?.toString()
            if (text.isNullOrBlank()) {
                toast("剪贴板里没有文字")
            } else {
                etInput.setText(text)
                toast("已粘贴，点「识别预览」看看")
            }
        }

        view.findViewById<View>(R.id.btn_parse).setOnClickListener {
            doParse(etInput.text.toString())
        }

        view.findViewById<View>(R.id.btn_confirm_import).setOnClickListener {
            confirmImport()
        }
    }

    private fun doParse(text: String) {
        val root = view ?: return
        val card = root.findViewById<View>(R.id.card_preview)
        val container = root.findViewById<LinearLayout>(R.id.preview_container)

        if (text.isBlank()) {
            toast("先把 AI 的输出粘贴进来")
            return
        }

        val result = ScheduleParser.parse(text, Store.totalWeeks)
        parsed = result.courses
        container.removeAllViews()

        if (parsed.isEmpty()) {
            card.visibility = View.GONE
            toast("没识别出课程，检查一下格式，或者让 AI 重新输出一次")
            return
        }

        root.findViewById<TextView>(R.id.tv_preview_title).text =
            "③ 确认导入（共 ${parsed.size} 门课）"
        parsed.forEach { container.addView(buildPreviewItem(it)) }
        card.visibility = View.VISIBLE

        if (result.errors.isNotEmpty()) {
            toast("有 ${result.errors.size} 段没认出来，已跳过")
        }
    }

    private fun buildPreviewItem(c: Course): View {
        val tv = TextView(requireContext())
        val teacher = if (c.teacher.isBlank()) "" else " · ${c.teacher}"
        tv.text = "${c.name}\n${TimeUtil.dayName(c.dayOfWeek)} ${c.startText}-${c.endText} · ${c.location}$teacher · 第 ${c.weeksText} 周"
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main))
        tv.textSize = 12.5f
        tv.setLineSpacing(5f, 1f)
        tv.setPadding(dp(12), dp(10), dp(12), dp(10))
        tv.setBackgroundResource(R.drawable.bg_preview_item)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(6)
        tv.layoutParams = lp
        return tv
    }

    private fun confirmImport() {
        if (parsed.isEmpty()) return
        val hasExisting = Store.courses.isNotEmpty()
        val options = if (hasExisting) {
            arrayOf("覆盖现有课表（推荐）", "追加到现有课表")
        } else {
            arrayOf("导入")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("导入 ${parsed.size} 门课程")
            .setItems(options) { _, which ->
                if (which == 1) {
                    val merged = Store.courses + parsed
                    Store.replaceAll(merged.distinctBy { "${it.name}#${it.dayOfWeek}#${it.startMin}#${it.endMin}" })
                } else {
                    Store.replaceAll(parsed)
                }
                toast("已导入 ${parsed.size} 门课程")
                parsed = emptyList()
                view?.findViewById<View>(R.id.card_preview)?.visibility = View.GONE
                (activity as? MainActivity)?.switchToTimetable()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
