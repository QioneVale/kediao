package com.classmate.reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.classmate.reminder.data.Store

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Store.init(app)

        val type = intent.getIntExtra("type", 0)
        val courseId = intent.getStringExtra("courseId")
        val minutes = intent.getIntExtra("minutes", 0)

        when (type) {
            ReminderScheduler.TYPE_REMIND -> {
                val course = Store.courses.firstOrNull { it.id == courseId }
                if (course != null && Store.enabled) {
                    Notifier.showReminder(app, course, minutes)
                }
            }
            ReminderScheduler.TYPE_START -> {
                val course = Store.courses.firstOrNull { it.id == courseId }
                if (course != null) {
                    if (Store.autoSilent) SilentController.enter(app)
                    Notifier.showClassStarted(app, course)
                }
            }
            ReminderScheduler.TYPE_END -> {
                SilentController.exit(app)
            }
            ReminderScheduler.TYPE_DAILY -> {
                // 只是用来滚动刷新，下面统一重排
            }
        }

        // 每次触发后重排，保证后续提醒继续存在
        ReminderScheduler.refresh(app)
    }
}
