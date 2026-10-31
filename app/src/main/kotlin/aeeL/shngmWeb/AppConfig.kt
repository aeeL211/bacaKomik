package aeeL.shngmWeb

object AppConfig {
  val DEBUG = BuildConfig.DEBUG
  const val ENABLE_ERUDA = false

  const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024L
  const val MAX_LOG_FILES = 3

  val VERSION_NAME = BuildConfig.VERSION_NAME

  const val BASE_URL = "https://shinigami.to"
  const val CONFIG_URL = "https://raw.githubusercontent.com/aeeL211/bacaKomik/refs/heads/main/url.txt"

  /** Adds a cache-buster so an edit to url.txt is visible immediately, not after GitHub's CDN TTL. */
  fun configUrl(): String {
    val separator = if ('?' in CONFIG_URL) '&' else '?'
    return "$CONFIG_URL${separator}t=${System.currentTimeMillis()}"
  }
}
