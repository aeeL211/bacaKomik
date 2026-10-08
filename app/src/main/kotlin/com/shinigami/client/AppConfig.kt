package com.shinigami.client

object AppConfig {
  const val DEBUG = true
  const val ENABLE_ERUDA = false

  const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024L
  const val MAX_LOG_FILES = 3

  // Pull-to-refresh tuning, in dp. Finger travel needed to refresh is about
  // PULL_START_DP + PULL_THRESHOLD_DP / 0.5 (0.5 = Material's built-in drag factor, from memory).
  const val PULL_START_DP = 48 // dead zone: a pull shorter than this does nothing at all
  const val PULL_THRESHOLD_DP = 140 // how far the indicator must go to refresh (Material default: 80)

  const val VERSION_NAME = "1.7.0"

  const val BASE_URL = "https://shinigami.to"
  const val CONFIG_URL = "https://raw.githubusercontent.com/aeeL211/bacaKomik/refs/heads/main/url.txt"
}
