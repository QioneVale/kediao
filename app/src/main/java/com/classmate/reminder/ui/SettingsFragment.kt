package com.classmate.reminder.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.classmate.reminder.R
import com.classmate.reminder.alarm.Notifier
import com.classmate.reminder.alarm.ReminderScheduler
import com.classmate.reminder.alarm.SilentController
import com.classmate.reminder.data.Store
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val slider = view.findViewById<Slider>(R.id.slider_remind)
        val tvValue = view.findViewById<TextView>(R.id.tv_remind_value)
        val tvHint = view.findViewById<TextView>(R.id.tv_remind_hint)

        slider.value = Store.remindMinutes.toFloat().coerceIn(0f, 60f)
        updateRemindLabel(tvValue, tvHint, Store.remindMinutes)
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) updateRemindLabel(tvValue, tvHint, value.toInt())
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                Store.remindMinutes = slider.value.toInt()
                Store.reschedule()
            }
        })

        val swSound = view.findViewById<MaterialSwitch>(R.id.switch_sound)
        swSound.isChecked = Store.soundAlarm
        swSound.setOnCheckedChangeListener { _, checked ->
            Store.soundAlarm = checked
        }

        val swEnabled = view.findViewById<MaterialSwitch>(R.id.switch_enabled)
        swEnabled.isChecked = Store.enabled
        swEnabled.setOnCheckedChangeListener { _, checked ->
            Store.enabled = checked
            Store.reschedule()
        }

        val swSilent = view.findViewById<MaterialSwitch>(R.id.switch_silent)
        swSilent.isChecked = Store.autoSilent
        swSilent.setOnCheckedChangeListener { _, checked ->
            Store.autoSilent = checked
            Store.reschedule()
            if (checked && !SilentController.hasAccess(requireContext())) {
                toast("还需授权「免打扰」，自动静音才会生效")
            }
        }

        view.findViewById<View>(R.id.row_term_start).setOnClickListener { pickTermStart(view) }
        view.findViewById<View>(R.id.btn_weeks_minus).setOnClickListener {
            Store.totalWeeks = Store.totalWeeks - 1
            bind(view)
        }
        view.findViewById<View>(R.id.btn_weeks_plus).setOnClickListener {
            Store.totalWeeks = Store.totalWeeks + 1
            bind(view)
        }
        view.findViewById<View>(R.id.row_current_week).setOnClickListener { pickWeek(view) }

        view.findViewById<View>(R.id.row_notify).setOnClickListener { openNotifySettings() }
        view.findViewById<View>(R.id.row_alarm).setOnClickListener { openExactAlarm() }
        view.findViewById<View>(R.id.row_battery).setOnClickListener { openBattery() }
        view.findViewById<View>(R.id.btn_dnd).setOnClickListener {
            openIntent(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }

        view.findViewById<View>(R.id.btn_refresh).setOnClickListener {
            Store.reschedule()
            toast("已重新安排未来 8 天的提醒")
        }
        view.findViewById<View>(R.id.btn_clear).setOnClickListener { confirmClear() }

        bind(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { bind(it) }
    }

    private fun updateRemindLabel(tvValue: TextView, tvHint: TextView, minutes: Int) {
        if (minutes <= 0) {
            tvValue.text = "上课时"
            tvHint.text = "正好上课时提醒"
        } else {
            tvValue.text = "$minutes 分钟"
            tvHint.text = "上课前 $minutes 分钟提醒（可拖动调整）"
        }
    }

    private fun bind(view: View) {
        view.findViewById<TextView>(R.id.tv_term_start).text =
            Store.termStart.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
        view.findViewById<TextView>(R.id.tv_total_weeks).text = Store.totalWeeks.toString()
        view.findViewById<TextView>(R.id.tv_current_week).text =
            if (Store.weekOverride > 0) "第 ${Store.weekOverride} 周（手动）"
            else "第 ${Store.currentWeek()} 周（自动）"

        view.findViewById<TextView>(R.id.tv_status_notify).text =
            if (Notifier.canNotify(requireContext())) "已开启" else "未开启 · 点此开启"
        view.findViewById<TextView>(R.id.tv_status_alarm).text =
            if (ReminderScheduler.hasExactAlarmPermission(requireContext())) "已允许" else "未允许 · 点此允许"

        val pm = requireContext().getSystemService(PowerManager::class.java)
        val ignoring = pm?.isIgnoringBatteryOptimizations(requireContext().packageName) == true
        view.findViewById<TextView>(R.id.tv_status_battery).text =
            if (ignoring) "不受限制" else "可能被限制 · 点此设置"

        view.findViewById<TextView>(R.id.tv_dnd_status).text =
            if (SilentController.hasAccess(requireContext())) "免打扰权限：已授权 ✓"
            else "免打扰权限：未授权，上课不会自动静音"
    }

    private fun pickTermStart(view: View) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = Store.termStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                Store.termStart = LocalDate.of(y, m + 1, d)
                Store.reschedule()
                bind(view)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickWeek(view: View) {
        val density = resources.displayMetrics.density
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "第几周（1-30）"
        input.setText((if (Store.weekOverride > 0) Store.weekOverride else Store.currentWeek()).toString())
        val box = FrameLayout(requireContext())
        val pad = (20 * density).toInt()
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(input)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("当前是第几周")
            .setMessage("默认按「开学第一周」自动推算；手动指定后不再自动变化。")
            .setView(box)
            .setPositiveButton("确定") { _, _ ->
                val n = input.text.toString().trim().toIntOrNull() ?: 0
                Store.weekOverride = if (n in 1..30) n else 0
                Store.reschedule()
                bind(view)
            }
            .setNeutralButton("恢复自动") { _, _ ->
                Store.weekOverride = 0
                Store.reschedule()
                bind(view)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openNotifySettings() {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1003
            )
            return
        }
        openIntent(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        )
    }

    private fun openExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openIntent(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${requireContext().packageName}")
                )
            ) { openAppDetails() }
        } else {
            toast("当前系统版本不需要额外授权")
        }
    }

    private fun openBattery() {
        openIntent(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) { openAppDetails() }
    }

    private fun openAppDetails() {
        openIntent(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${requireContext().packageName}")
            )
        )
    }

    private fun openIntent(intent: Intent, fallback: (() -> Unit)? = null) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            if (fallback != null) fallback() else toast("这台手机没有该设置页，请到系统设置里手动找一下")
        }
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清空所有课程")
            .setMessage("会删除全部课程和已排的提醒，确定吗？")
            .setPositiveButton("清空") { _, _ ->
                Store.clear()
                toast("已清空")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
