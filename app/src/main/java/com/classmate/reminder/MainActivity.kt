package com.classmate.reminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.classmate.reminder.alarm.Notifier
import com.classmate.reminder.alarm.ReminderScheduler
import com.classmate.reminder.alarm.SilentController
import com.classmate.reminder.data.Store
import com.classmate.reminder.ui.ImportFragment
import com.classmate.reminder.ui.SettingsFragment
import com.classmate.reminder.ui.TimetableFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var nav: BottomNavigationView
    private var fragments: List<Fragment> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        setContentView(R.layout.activity_main)
        nav = findViewById(R.id.bottom_nav)

        val fm = supportFragmentManager
        if (savedInstanceState == null) {
            fm.beginTransaction()
                .add(R.id.container, TimetableFragment(), "f0")
                .add(R.id.container, ImportFragment(), "f1")
                .add(R.id.container, SettingsFragment(), "f2")
                .commitNow()
        }

        fragments = listOfNotNull(
            fm.findFragmentByTag("f0"),
            fm.findFragmentByTag("f1"),
            fm.findFragmentByTag("f2")
        )
        select(0)

        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_timetable -> {
                    select(0)
                    (fragments.getOrNull(0) as? TimetableFragment)?.refreshAll()
                }
                R.id.nav_import -> select(1)
                R.id.nav_settings -> select(2)
                else -> return@setOnItemSelectedListener false
            }
            true
        }

        askNotificationPermission()
        ReminderScheduler.refresh(this)
        SilentController.syncNow(this)
    }

    override fun onResume() {
        super.onResume()
        SilentController.syncNow(this)
    }

    private fun select(index: Int) {
        if (fragments.size < 3) return
        val ft = supportFragmentManager.beginTransaction()
        fragments.forEachIndexed { i, f ->
            if (i == index) ft.show(f) else ft.hide(f)
        }
        ft.commitAllowingStateLoss()
    }

    fun switchToTimetable() {
        nav.selectedItemId = R.id.nav_timetable
        select(0)
        (fragments.getOrNull(0) as? TimetableFragment)?.refreshAll()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
        }
    }
}
