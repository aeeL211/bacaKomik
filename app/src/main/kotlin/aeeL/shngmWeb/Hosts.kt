package aeeL.shngmWeb

import java.util.Locale

/** Hosts that belong to the wrapped comic site. */
internal val COMIC_HOSTS = setOf(
  "shinigami.asia",
  "shngm.io",
  "shinigami.to",
  "shngm.id",
)

/** Login and OAuth hosts that must stay inside the WebView. */
private val AUTH_HOSTS = setOf(
  // Supabase auth gateway used by the Google login flow.
  "kratos.shinigami.io",
  // Google OAuth pages.
  "accounts.google.com",
)

/** Comic and auth hosts that must stay inside the WebView. */
internal val INTERNAL_HOSTS = COMIC_HOSTS + AUTH_HOSTS

internal fun isComicHost(host: String?): Boolean = matchesHost(host, COMIC_HOSTS)

internal fun isInternalHost(host: String?): Boolean = matchesHost(host, INTERNAL_HOSTS)

private fun matchesHost(host: String?, hosts: Set<String>): Boolean {
  val normalized = host?.lowercase(Locale.ROOT) ?: return false
  return hosts.any { normalized == it || normalized.endsWith(".$it") }
}
