package com.shinigami.client

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class ConfigRepository(private val prefs: SharedPreferences) {

  companion object {
    private const val TAG = "ConfigRepository"
    private const val KEY_URL = "remote_url"
  }

  suspend fun fetchUrl(): String = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url(AppConfig.CONFIG_URL)
        .build()

      val fetchedUrl = WebExtension.httpClient.newCall(request).execute().use { response ->
        response.body.string().trim().takeIf { it.startsWith("http") }
      }

      if (fetchedUrl != null) {
        Logger.i(TAG) { "Fetched remote url: $fetchedUrl" }
        prefs.edit().putString(KEY_URL, fetchedUrl).apply()
        fetchedUrl
      } else {
        Logger.w(TAG) { "Empty or invalid response from config URL, falling back to cache" }
        cachedUrl()
      }
    } catch (e: Exception) {
      Logger.w(TAG) { "Network fetch failed: ${e.localizedMessage}" }
      cachedUrl()
    }
  }

  private fun cachedUrl(): String = prefs.getString(KEY_URL, AppConfig.BASE_URL) ?: AppConfig.BASE_URL
}
