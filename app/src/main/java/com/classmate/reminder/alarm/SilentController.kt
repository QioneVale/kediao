package com.classmate.reminder.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.classmate.reminder.data.Store

object SilentController {

    private const val PREF = "silent_state"
    private const val KEY_SILENCED = "silenced"
    private const val KEY_PREV = "prev_ringer"

    fun hasAccess(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    /** 上课：保存原铃声状态并切成静音 */
    fun enter(ctx: Context) {
        if (!Store.autoSilent) return
        if (!hasAccess(ctx)) return
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!sp.getBoolean(KEY_SILENCED, false)) {
            sp.edit().putBoolean(KEY_SILENCED, true).putInt(KEY_PREV, am.ringerMode).apply()
        }
        runCatching { am.ringerMode = AudioManager.RINGER_MODE_SILENT }
    }

    /** 下课：如果没有别的课正在进行，恢复原铃声状态 */
    fun exit(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!sp.getBoolean(KEY_SILENCED, false)) return
        if (Store.isInClass()) return
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val prev = sp.getInt(KEY_PREV, AudioManager.RINGER_MODE_NORMAL)
        val target = if (prev == AudioManager.RINGER_MODE_SILENT || prev == AudioManager.RINGER_MODE_VIBRATE) {
            prev
        } else {
            AudioManager.RINGER_MODE_NORMAL
        }
        runCatching { am.ringerMode = target }
        sp.edit().putBoolean(KEY_SILENCED, false).apply()
    }

    /** 打开 App 时对一下状态：正在上课就静音，没在上课就还原 */
    fun syncNow(ctx: Context) {
        if (Store.isInClass()) enter(ctx) else exit(ctx)
    }
}
