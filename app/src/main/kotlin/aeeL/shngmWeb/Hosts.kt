package aeeL.shngmWeb

import java.util.Locale

/** Hosts that belong to the wrapped comic site. */
internal val COMIC_HOSTS = setOf(
  "shinigami.asia",
  "shngm.io",
  "shinigami.to",
  "shngm.id",
)

internal fun isComicHost(host: String?): Boolean {
  val normalized = host?.lowercase(Locale.ROOT) ?: return false
  return COMIC_HOSTS.any { normalized == it || normalized.endsWith(".$it") }
}
