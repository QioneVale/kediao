package com.classmate.reminder.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.classmate.reminder.MainActivity
import com.classmate.reminder.R
import com.classmate.reminder.data.Course
import com.classmate.reminder.data.Store

object Notifier {

    const val CH_REMIND = "class_remind_high"
    const val CH_INFO = "class_info_low"

    private var seq = 10

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CH_REMIND) == null) {
            val ch = NotificationChannel(CH_REMIND, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "上课前的闹钟式提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 700, 300, 700, 300, 1000)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(CH_INFO) == null) {
            val ch = NotificationChannel(CH_INFO, "上课状态", NotificationManager.IMPORTANCE_LOW).apply {
                description = "上课开始 / 自动静音的提示"
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun showReminder(ctx: Context, course: Course, minutesLeft: Int) {
        if (!canNotify(ctx)) return
        ensureChannels(ctx)

        val title = when {
            minutesLeft <= 0 -> "${course.name} 现在上课"
            else -> "${course.name} · ${minutesLeft} 分钟后上课"
        }
        val body = "${course.timeText} · ${course.location}"

        val builder = NotificationCompat.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (course.teacher.isBlank()) body else "$body\n教师：${course.teacher}"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "打开课表", openApp(ctx))

        if (Store.soundAlarm) {
            builder.setVibrate(longArrayOf(0, 700, 300, 700, 300, 1000))
        } else {
            builder.setSilent(true)
        }

        runCatching {
            NotificationManagerCompat.from(ctx).notify(nextId(), builder.build())
        }
    }

    fun showClassStarted(ctx: Context, course: Course) {
        if (!canNotify(ctx)) return
        ensureChannels(ctx)
        val text = if (Store.autoSilent) {
            "已自动静音，下课自动恢复 · ${course.location}"
        } else {
            "${course.timeText} · ${course.location}"
        }
        val builder = NotificationCompat.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("${course.name} 开始上课")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
        runCatching {
            NotificationManagerCompat.from(ctx).notify(nextId(), builder.build())
        }
    }

    private fun nextId(): Int {
        seq = if (seq > 2000) 10 else seq + 1
        return seq
    }

    private fun openApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, 7701, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
