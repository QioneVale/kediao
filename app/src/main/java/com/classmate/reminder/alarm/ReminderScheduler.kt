package com.classmate.reminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.classmate.reminder.data.Store
import java.time.LocalDate
import java.time.ZoneId

object ReminderScheduler {

    const val TYPE_REMIND = 1
    const val TYPE_START = 2
    const val TYPE_END = 3
    const val TYPE_DAILY = 4

    private const val PREF = "scheduler"
    private const val KEY_CODES = "codes"
    private const val ACTION = "com.classmate.reminder.ALARM"

    private const val DAILY_CODE = 909090

    fun refresh(context: Context) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        // 1. 取消上次排的所有闹钟
        sp.getStringSet(KEY_CODES, emptySet())?.forEach { key ->
            val code = key.substringBefore('|').toIntOrNull() ?: return@forEach
            val pi = buildIntent(ctx, code, null, 0, 0)
            runCatching { am.cancel(pi) }
            pi.cancel()
        }

        val codes = HashSet<String>()
        val now = System.currentTimeMillis()

        // 2. 每天 00:10 滚动补充未来几天的闹钟（防止手机放很久没打开 App）
        val nextMidnight = LocalDate.now().plusDays(1).atTime(0, 10)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (schedule(ctx, am, nextMidnight, DAILY_CODE, null, TYPE_DAILY, 0)) {
            codes.add("$DAILY_CODE|d")
        }

        if (!Store.enabled || Store.courses.isEmpty()) {
            sp.edit().putStringSet(KEY_CODES, codes).apply()
            return
        }

        val remind = Store.remindMinutes

        // 3. 为未来 8 天内的每节课排提醒 / 静音 / 恢复
        for (offset in 0..7) {
            val date = LocalDate.now().plusDays(offset.toLong())
            val week = Store.weekOf(date)
            val dow = date.dayOfWeek.value
            val zone = ZoneId.systemDefault()

            for (c in Store.courses) {
                if (c.dayOfWeek != dow) continue
                if (week !in c.weeks) continue

                val startAt = date.atTime(c.startMin / 60, c.startMin % 60)
                    .atZone(zone).toInstant().toEpochMilli()
                val endAt = date.atTime(c.endMin / 60, c.endMin % 60)
                    .atZone(zone).toInstant().toEpochMilli()
                val remindAt = startAt - remind * 60_000L

                if (remindAt > now + 1000) {
                    val code = codeFor(c.id, date, TYPE_REMIND)
                    if (schedule(ctx, am, remindAt, code, c.id, TYPE_REMIND, remind)) {
                        codes.add("$code|r")
                    }
                }
                if (startAt > now + 1000) {
                    val code = codeFor(c.id, date, TYPE_START)
                    if (schedule(ctx, am, startAt, code, c.id, TYPE_START, 0)) {
                        codes.add("$code|s")
                    }
                }
                if (endAt > now + 1000) {
                    val code = codeFor(c.id, date, TYPE_END)
                    if (schedule(ctx, am, endAt, code, c.id, TYPE_END, 0)) {
                        codes.add("$code|e")
                    }
                }
            }
        }

        sp.edit().putStringSet(KEY_CODES, codes).apply()
    }

    fun hasExactAlarmPermission(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
    }

    private fun codeFor(courseId: String, date: LocalDate, type: Int): Int =
        1_000_000 + (("$courseId#$date#$type").hashCode() and 0x7FFFF)

    private fun schedule(
        ctx: Context,
        am: AlarmManager,
        at: Long,
        code: Int,
        courseId: String?,
        type: Int,
        minutes: Int
    ): Boolean {
        if (at <= System.currentTimeMillis() + 1000) return false
        val pi = buildIntent(ctx, code, courseId, type, minutes)
        return try {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
            true
        } catch (e: SecurityException) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildIntent(
        ctx: Context,
        code: Int,
        courseId: String?,
        type: Int,
        minutes: Int
    ): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION
            putExtra("type", type)
            putExtra("courseId", courseId)
            putExtra("minutes", minutes)
        }
        return PendingIntent.getBroadcast(
            ctx, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
