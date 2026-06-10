package com.example

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CatholicMeditationsApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Dynamic Crash Interception & Logging moved to Application level
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sharedPrefs = getSharedPreferences("daily_catholic_prefs", Context.MODE_PRIVATE)
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTraceString = sw.toString()
                sharedPrefs.edit().putString("last_crash_trace", stackTraceString).commit()
            } catch (e: Exception) {
                // Silent catch
            }
            originalHandler?.uncaughtException(thread, throwable)
        }
    }
}
