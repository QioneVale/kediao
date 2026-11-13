package com.classmate.reminder

import android.app.Application
import com.classmate.reminder.alarm.Notifier
import com.classmate.reminder.data.Store

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.ensureChannels(this)
    }
}
