# Shinigami Android Client (Web2APK)

Aplikasi Android berbasis **Kotlin** dan **Jetpack Compose Material 3** yang membungkus situs komik Shinigami di dalam WebView, dengan pemblokiran iklan, patch respons HTML, dan konfigurasi URL remote.

- **Package / Application ID:** `aeeL.shngmWeb`
- **Min SDK:** 24
- **Target SDK:** 36
- **Compile SDK:** 37
- **JDK:** 21

## Fitur

- WebView full-screen dengan navigasi back, popup gambar, file upload, dan dialog JS bergaya Material 3.
- Pull-to-refresh native memakai `SwipeRefreshLayout`; indikator muncul di bawah status bar dan hanya aktif saat halaman berada di paling atas.
- `RequestInterceptor` terpadu:
  - memblokir iklan (`ads.shinigami`), Google Tag Manager, dan Novu;
  - mengganti respons API pengumuman dengan data lokal;
  - mengambil HTML halaman utama lewat OkHttp, menyinkronkan cookie, dan membuka flag `is_premium`;
  - meneruskan request lain langsung ke WebView.
- Konfigurasi URL remote realtime dari `url.txt` di GitHub, dengan fallback ke SharedPreferences / `BASE_URL`.
- Cache gambar disk 256 MB lewat OkHttp: gambar yang sudah pernah dibuka tidak diunduh ulang saat reload, restart, atau membuka preview long-press.
- Splash screen, progress bar, context menu gambar, unduh/bagikan/salin, dan logging debug.

## Build

Build lokal membutuhkan JDK 21 dan Android SDK dengan `compileSdk 37`.

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk

./gradlew test
./gradlew lint
```

Build release utama dijalankan oleh GitHub Actions.

## CI / Release

Workflow `.github/workflows/android_release.yml` berjalan otomatis saat push ke `main` atau lewat `workflow_dispatch`:

1. checkout repo;
2. setup JDK 21 dan Gradle;
3. `./gradlew assembleRelease`;
4. upload artifact `app-release`;
5. membuat GitHub Release dengan tag `vN` (increment dari tag terakhir).

Workflow `.github/workflows/dependamerge.yml` mengaktifkan auto-merge untuk PR Dependabot.

## Signing

Saat ini `app/build.gradle.kts` memakai `signingConfigs.getByName("debug")` pada build release agar APK bisa langsung diuji. Untuk produksi, ganti dengan konfigurasi keystore sendiri. Jangan gunakan signing debug untuk distribusi publik.

## Struktur

```text
app/src/main/kotlin/aeeL/shngmWeb/
├── AppConfig.kt                 # konstanta aplikasi + URL config
├── ConfigRepository.kt          # fetch url.txt realtime, fallback lokal
├── ContextMenuBottomSheet.kt    # menu long-press gambar
├── Dialogs.kt                   # dialog JS Material 3
├── ErudaConsole.kt              # injeksi Eruda (opsional)
├── Http.kt                      # OkHttpClient bersama
├── ImageCache.kt                # cache gambar disk + fallback offline
├── Logger.kt                    # log Android + file rotasi
├── MainActivity.kt              # activity, WebView, file chooser, popup, back
├── MainScreen.kt                # Compose UI, splash, WebView host
├── MainViewModel.kt             # state, config, network, refresh
├── NetworkMonitor.kt            # status konektivitas
├── PullToRefreshWebView.kt      # SwipeRefreshLayout + WebView
├── RequestInterceptor.kt        # blocker + patch HTML
├── ShinigamiApp.kt              # Application + crash handler
└── Theme.kt                     # tema Compose Material 3
```

Aset JS untuk deteksi gambar ada di `app/src/main/assets/js/image_detector.js`.

## Alur WebView

1. **Startup**: splash tampil, `MainViewModel` mengambil `url.txt` dari GitHub dengan cache-buster. Jika gagal, dipakai URL terakhir dari SharedPreferences atau `BASE_URL`.
2. **Load halaman**: `MainScreen` membuat `PullToRefreshWebView`, lalu memuat URL ke WebView.
3. **Interception**: `MainActivity.AppWebViewClient.shouldInterceptRequest` memanggil `RequestInterceptor`, lalu `ImageCache`.
   - Request iklan/GTM/Novu/pengumuman dijawab dengan respons sintetis.
   - Request HTML main-frame untuk host komik diambil lewat OkHttp, cookie disinkronkan, lalu `is_premium:false` diubah menjadi `true`.
   - Request gambar GET dilayani dari cache disk OkHttp; jika belum ada, diunduh sekali lalu disimpan. Saat offline, versi cache dipakai.
   - Request lain dikembalikan `null` agar ditangani WebView.
4. **Pull-to-refresh**: menarik dari posisi paling atas memicu `MainViewModel.refresh`, yang mengambil ulang `url.txt`. Jika origin berubah, WebView membuka URL baru; jika sama, halaman saat ini di-reload.
5. **Long-press gambar**: koordinat sentuhan dikirim ke `image_detector.js`; URL gambar ditampilkan di bottom sheet.

## Konfigurasi URL Remote

Edit `url.txt` di root repo, lalu commit dan push ke `main`:

```text
https://11.shinigami.asia
```

Aplikasi mengambil file mentah dari:

```text
https://raw.githubusercontent.com/aeeL211/bacaKomik/refs/heads/main/url.txt
```

Setiap fetch menambahkan query cache-buster dan header `no-cache`, jadi perubahan `url.txt` langsung terbaca saat aplikasi dibuka, saat koneksi kembali, atau saat pull-to-refresh — tanpa perlu build APK baru.

## Cache Gambar

`ImageCache` memakai `okhttp3.Cache` di `cacheDir/image_cache` dengan batas 256 MB dan eviction LRU otomatis. Header `Cache-Control` dari CDN ditulis ulang menjadi `private, max-age=7 hari` khusus respons gambar, karena banyak CDN komik mengirim `no-store` yang membuat reload mengunduh ulang semua halaman. Permintaan gambar dari WebView dan preview long-press memakai cache yang sama; saat koneksi gagal, `FORCE_CACHE` dipakai sebagai fallback. Cache bertahan setelah aplikasi ditutup dan hanya dibersihkan sistem jika penyimpanan menipis.

## Efisiensi

- **Gambar:** cache disk 256 MB dengan TTL 7 hari; reload, restart, dan preview memakai cache yang sama. Saat offline, cache dipakai langsung tanpa menunggu timeout jaringan.
- **Logging:** mengikuti `BuildConfig.DEBUG`, jadi build release tidak menulis file log atau memformat pesan debug.
- **Progress:** update progress hanya diteruskan selama splash masih tampil, sehingga tidak memicu recomposition setelah halaman terbuka.
- **Preview:** membaca stream dari cache, bukan menyalin seluruh byte gambar ke memori lebih dulu.
- **Tradeoff:** HTML halaman dan data API tetap diambil dari jaringan agar konten terbaru; aksi "Unduh" memakai `DownloadManager` dan mengunduh ulang dari jaringan.

## Catatan

- `AppConfig.DEBUG` mengikuti `BuildConfig.DEBUG`; log ke Logcat dan file di `Android/data/aeeL.shngmWeb/files/log/` hanya aktif di build debug.
- `AppConfig.ENABLE_ERUDA` bisa diaktifkan untuk debugging WebView.
