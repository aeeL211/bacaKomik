package com.shinigami.client

import android.app.Application
import android.util.Log
import com.google.android.material.color.DynamicColors
import kotlin.system.exitProcess

class ShinigamiApp : Application() {

  override fun onCreate() {
    super.onCreate()
    Logger.init(this)
    DynamicColors.applyToActivitiesIfAvailable(this)
    setupCrashHandler()
  }

  private fun setupCrashHandler() {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        Logger.logCrash(throwable)
        Logger.e("ShinigamiApp", throwable) { "Fatal crash in thread: ${thread.name}" }
        Logger.shutdown()
      } catch (e: Exception) {
        Log.e("ShinigamiApp", "Crash handler failed", e)
      } finally {
        exitProcess(10)
      }
    }
  }
}
