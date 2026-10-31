package aeeL.shngmWeb

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Reads the site URL from url.txt on GitHub.
 *
 * Every fetch is a real network request: the cache-buster and no-cache headers stop GitHub's
 * raw CDN from serving an old copy after url.txt is edited. The last valid value is only used
 * as an offline fallback.
 */
class ConfigRepository(private val prefs: SharedPreferences) {

  private val fetchMutex = Mutex()
  private val client = Http.client.newBuilder()
    .callTimeout(8, TimeUnit.SECONDS)
    .build()

  suspend fun fetchUrl(): String = fetchMutex.withLock {
    withContext(Dispatchers.IO) {
      val fallback = cachedUrl()

      try {
        val request = Request.Builder()
          .url(AppConfig.configUrl())
          .cacheControl(CacheControl.Builder().noCache().build())
          .header("Cache-Control", "no-cache, no-store")
          .header("Pragma", "no-cache")
          .build()

        val fetched = client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@use null

          response.body.string()
            .removePrefix("\uFEFF")
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("http") && it.toHttpUrlOrNull() != null }
        }

        if (fetched == null) {
          Logger.w(TAG) { "Invalid config response, using fallback: $fallback" }
          return@withContext fallback
        }

        if (fetched != fallback) {
          prefs.edit { putString(KEY_URL, fetched) }
          Logger.i(TAG) { "Remote url updated: $fetched" }
        }
        fetched
      } catch (e: Exception) {
        Logger.w(TAG) { "Config fetch failed: ${e.localizedMessage}" }
        fallback
      }
    }
  }

  fun cachedUrl(): String = prefs.getString(KEY_URL, AppConfig.BASE_URL) ?: AppConfig.BASE_URL

  companion object {
    private const val TAG = "ConfigRepository"
    private const val KEY_URL = "remote_url"
  }
}
