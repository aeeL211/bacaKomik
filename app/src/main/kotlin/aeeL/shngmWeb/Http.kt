package aeeL.shngmWeb

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One shared client for every request made outside the WebView: page patching, remote config,
 * and image previews. Callers that need a shorter deadline can derive a client with newBuilder().
 */
internal object Http {
  val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(10, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build()
  }
}
