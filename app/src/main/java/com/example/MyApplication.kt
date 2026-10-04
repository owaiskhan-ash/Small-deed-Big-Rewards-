package com.example

import android.app.Application
import android.util.Log

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        // Robust exception handler to log exceptions clearly, then delegate to system default or exit cleanly.
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MyApplication", "CRITICAL UNCAUGHT EXCEPTION on thread ${thread.name}: ${throwable.message}", throwable)
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                java.lang.System.exit(1)
            }
        }

        // Schedule daily Hadith background notifications if enabled in preferences
        try {
            val prefs = getSharedPreferences("smdr_prefs", MODE_PRIVATE)
            val notifEnabled = prefs.getBoolean("smdr_daily_notif", true)
            if (notifEnabled) {
                com.example.worker.DailyHadithWorker.scheduleDailyNotification(this)
            }
        } catch (e: Exception) {
            Log.e("MyApplication", "Failed to schedule DailyHadithWorker: ${e.message}", e)
        }
    }
}
