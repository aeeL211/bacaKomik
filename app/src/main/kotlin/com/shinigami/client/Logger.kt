package com.shinigami.client

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thin wrapper over [Log]. Everything is a no-op unless [AppConfig.DEBUG] is true.
 * Messages are lambdas, so strings are only built when logging is on.
 * In debug builds, lines are also written to a rotating file under the app's files dir.
 */
object Logger {

  private const val TAG = "Logger"
  private const val LOG_DIR = "log"
  private const val LEVEL_CHARS = "VDIWE" // indexed by Log priority - Log.VERBOSE

  private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
  private val queue = Channel<String>(capacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private var file: File? = null
  private var writer: BufferedWriter? = null

  @Volatile private var isReady = false

  fun d(tag: String, err: Throwable? = null, msg: () -> String) = log(Log.DEBUG, tag, err, msg)

  fun i(tag: String, err: Throwable? = null, msg: () -> String) = log(Log.INFO, tag, err, msg)

  fun w(tag: String, err: Throwable? = null, msg: () -> String) = log(Log.WARN, tag, err, msg)

  fun e(tag: String, err: Throwable? = null, msg: () -> String) = log(Log.ERROR, tag, err, msg)

  fun logNetwork(method: String, url: String, code: Int, timeMs: Long) {
    d("Network") { "$method $url → $code (${timeMs}ms)" }
  }

  /** File only. Called from the crash handler. */
  fun logCrash(err: Throwable) {
    if (!AppConfig.DEBUG) return
    val crash = buildString {
      append("\n╔═══ CRASH ═══════════════════════════════════════════════╗\n")
      append("║ ${err.javaClass.simpleName}: ${err.message}\n")
      err.stackTrace.take(15).forEach { append("║   $it\n") }
      append("╚═════════════════════════════════════════════════════════╝\n")
    }
    queue.trySend(crash)
  }

  fun init(context: Context) {
    if (!AppConfig.DEBUG || isReady) return

    try {
      val root = context.getExternalFilesDir(null) ?: context.filesDir
      val dir = File(root, LOG_DIR).apply { mkdirs() }
      val logFile = File(dir, "shngm-log_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.txt")
      file = logFile

      dir.listFiles()
        ?.sortedByDescending { it.lastModified() }
        ?.drop(AppConfig.MAX_LOG_FILES)
        ?.forEach { it.delete() }

      writer = BufferedWriter(FileWriter(logFile, true))
      if (logFile.length() == 0L) {
        write("=== Shinigami v${AppConfig.VERSION_NAME} ===\n")
      }

      isReady = true
      Log.i(TAG, "Logger initialized at: ${logFile.absolutePath}")

      CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        for (line in queue) {
          write(line)
          writer?.flush()
          rotateIfNeeded()
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Initialization failed", e)
    }
  }

  fun shutdown() {
    isReady = false
    queue.close()
    try {
      writer?.flush()
      writer?.close()
      writer = null
    } catch (e: Exception) {
      Log.e(TAG, "Shutdown error", e)
    }
  }

  private fun log(priority: Int, tag: String, err: Throwable?, msg: () -> String) {
    if (!AppConfig.DEBUG) return
    val text = msg()
    Log.println(priority, tag, if (err == null) text else "$text\n${Log.getStackTraceString(err)}")

    if (!isReady) return
    val line = buildString {
      append("${timeFormat.format(Date())} [${LEVEL_CHARS[priority - Log.VERBOSE]}] $tag: $text\n")
      if (err != null) {
        append("  ↳ ${err.javaClass.simpleName}: ${err.message}\n")
        err.stackTrace.take(5).forEach { append("  at $it\n") }
      }
    }
    queue.trySend(line)
  }

  private fun write(text: String) {
    try {
      writer?.write(text)
    } catch (e: Exception) {
      Log.e(TAG, "Write failed", e)
    }
  }

  private fun rotateIfNeeded() {
    val current = file ?: return
    if (current.length() <= AppConfig.MAX_LOG_FILE_SIZE) return

    val backup = File(current.parentFile, "${current.nameWithoutExtension}_${System.currentTimeMillis()}.txt")
    try {
      writer?.close()
    } catch (_: Exception) {}
    current.renameTo(backup)
    try {
      writer = BufferedWriter(FileWriter(current, true))
      write("=== Rotated from ${backup.name} ===\n")
    } catch (e: Exception) {
      Log.e(TAG, "Rotation failed", e)
    }
  }
}
