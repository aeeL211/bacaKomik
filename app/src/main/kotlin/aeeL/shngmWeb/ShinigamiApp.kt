package aeeL.shngmWeb

import android.app.Application
import android.util.Log
import kotlin.system.exitProcess

class ShinigamiApp : Application() {

  override fun onCreate() {
    super.onCreate()
    Logger.init(this)
    ImageCache.init(this)
    setupCrashHandler()
  }

  private fun setupCrashHandler() {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        Logger.logCrash(throwable)
        Logger.e("ShinigamiApp", throwable) { "Fatal crash in thread: ${thread.name}" }
        Logger.shutdown()
      } catch (e: Exception) {
        Log.e("ShinigamiApp", "Crash handler failed", e)
      } finally {
        if (previousHandler != null) {
          previousHandler.uncaughtException(thread, throwable)
        } else {
          exitProcess(10)
        }
      }
    }
  }
}
