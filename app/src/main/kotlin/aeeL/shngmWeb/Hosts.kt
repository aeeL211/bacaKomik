package aeeL.shngmWeb

import androidx.core.net.toUri
import java.util.Locale

/** Hosts that are known to belong to the wrapped comic site. */
internal val COMIC_HOSTS = setOf(
  "shinigami.asia",
  "shngm.io",
  "shinigami.to",
  "shngm.id",
)

/**
 * The wrapped site moves between unofficial mirrors, so a fixed host list is not enough.
 *
 * The active host is learned from the remote config, while known host families stay valid even
 * when a mirror uses a new subdomain or TLD. This is used both for in-app navigation and for
 * response patching so a new mirror does not silently lose ad blocking or premium handling.
 */
internal object SiteHosts {

  @Volatile
  private var activeHost: String? = null

  fun updateFromUrl(url: String?) {
    activeHost = runCatching { url?.toUri()?.host }.getOrNull()
      ?.lowercase(Locale.ROOT)
      ?.takeIf { it.isNotBlank() }
  }

  fun matches(host: String?): Boolean {
    val normalized = host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return false
    val active = activeHost

    if (active != null && (normalized == active || normalized.endsWith(".$active"))) {
      return true
    }

    if (COMIC_HOSTS.any { normalized == it || normalized.endsWith(".$it") }) {
      return true
    }

    // Unofficial mirrors commonly use shngm/shinigami as a domain label.
    return normalized.split('.').any { label ->
      label.startsWith("shngm") || label.contains("shinigami")
    }
  }
}

internal fun isComicHost(host: String?): Boolean = SiteHosts.matches(host)
