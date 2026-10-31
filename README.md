# Shinigami Android Client (Web2APK)

Aplikasi Android berbasis **Kotlin** dan **Jetpack Compose Material 3** yang membungkus situs komik Shinigami di dalam WebView, dengan pemblokiran iklan, patch respons HTML, dan konfigurasi URL remote.

- **Package / Application ID:** `aeeL.shngmWeb`
- **Version name:** `1.7.0`
- **Min SDK:** 24
- **Target SDK:** 36
- **Compile SDK:** 37
- **JDK:** 21

## Fitur

- WebView edge-to-edge dengan tema gelap, navigasi back, popup gambar, upload file lewat SAF, dan dialog JS bergaya Material 3.
- Dialog premium sekali saja: arahan login Google untuk membuka fitur premium ditampilkan sebagai dialog setelah splash, bukan toast. Preferensi disimpan saat dialog ditutup.
- Login Google tetap di dalam WebView: `kratos.shinigami.io` (gateway auth Supabase) dan `accounts.google.com` masuk daftar host internal, jadi tidak dilempar ke browser. Tautan non-komik/non-auth tetap dibuka di aplikasi eksternal.
- Status bar disembunyikan di activity dan dialog; swipe dari atas hanya menampilkannya sementara.
- Pull-to-refresh native memakai `SwipeRefreshLayout`; indikator muncul di bagian atas layar dan hanya aktif saat halaman berada di paling atas.
- `RequestInterceptor` terpadu:
  - memblokir iklan (`ads.shinigami`), Google Tag Manager, dan Novu;
  - mengganti respons API pengumuman dengan data lokal;
  - mengambil HTML halaman utama lewat OkHttp, menyinkronkan cookie, dan membuka flag `is_premium`;
  - meneruskan request lain langsung ke WebView.
- Konfigurasi URL remote realtime dari `url.txt` di GitHub, dengan fallback ke SharedPreferences / `BASE_URL`.
- Cache gambar disk 256 MB lewat OkHttp: gambar yang sudah pernah dibuka tidak diunduh ulang saat reload, restart, atau membuka preview long-press.
- Preview long-press memakai bitmap yang di-downsample (maksimal 1280 px) agar tidak membebani memori.
- Splash screen, progress bar, context menu gambar, unduh/bagikan/salin, dan logging debug.

## Build

Build lokal membutuhkan JDK 21 dan Android SDK dengan `compileSdk 37`.

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk

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
├── ContextMenuBottomSheet.kt    # menu long-press gambar + preview
├── Dialogs.kt                   # dialog JS dan dialog premium sekali saja
├── ErudaConsole.kt              # injeksi Eruda (opsional)
├── Hosts.kt                     # host komik + host login yang dipakai bersama
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
└── Theme.kt                     # tema Compose Material 3 (dark-only)
```

Aset JS untuk deteksi gambar ada di `app/src/main/assets/js/image_detector.js`.

## Alur WebView

1. **Startup**: splash tampil, `MainViewModel` mengambil `url.txt` dari GitHub dengan cache-buster. Jika gagal, dipakai URL terakhir dari SharedPreferences atau `BASE_URL`. Hasilnya dikirim ke UI sebagai `PageCommand`, bukan callback yang disimpan lintas layer.
2. **Load halaman**: `MainScreen` membuat `PullToRefreshWebView` satu kali lalu menyimpannya dengan `remember`. `LaunchedEffect` menerapkan `PageCommand`: reload jika origin sama, atau buka URL baru jika origin berubah.
3. **Interception**: `MainActivity.AppWebViewClient.shouldInterceptRequest` memanggil `RequestInterceptor`, lalu `ImageCache`.
   - Request iklan/GTM/Novu/pengumuman dijawab dengan respons sintetis.
   - Request HTML main-frame untuk host komik diambil lewat OkHttp, cookie disinkronkan, lalu `is_premium:false` diubah menjadi `true`.
   - Request gambar GET dilayani dari cache disk OkHttp; jika belum ada, diunduh sekali lalu disimpan. Saat offline, versi cache dipakai.
   - Request lain dikembalikan `null` agar ditangani WebView.
4. **Pull-to-refresh**: menarik dari posisi paling atas memicu `MainViewModel.refresh`, yang mengambil ulang `url.txt`. Jika origin berubah, WebView membuka URL baru; jika sama, halaman saat ini di-reload. Timeout 15 detik menghentikan spinner jika halaman tidak pernah selesai dimuat.
5. **Long-press gambar**: koordinat sentuhan dikirim ke `image_detector.js`; URL gambar ditampilkan di bottom sheet bersama preview yang sudah di-downsample.
6. **Upload file**: WebView membuka file picker SAF (`ACTION_GET_CONTENT` / `ACTION_OPEN_DOCUMENT`), jadi aplikasi tidak meminta izin penyimpanan.
7. **Dialog premium**: setelah splash hilang, dialog arahan login Google ditampilkan satu kali dan preferensinya disimpan saat ditutup.
8. **Login Google**: tautan login menuju `kratos.shinigami.io`, lalu Google OAuth di `accounts.google.com`, dan kembali ke `kratos.shinigami.io`. Kedua host tetap dimuat di WebView; host lain dibuka lewat browser.

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

## Izin

Aplikasi hanya meminta:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

Upload file memakai Storage Access Framework, sehingga tidak memerlukan izin baca media. Izin kamera, Wi-Fi state, dan `androidx.startup` yang tidak terpakai sudah dihapus.

## Efisiensi

- **Gambar:** cache disk 256 MB dengan TTL 7 hari; reload, restart, dan preview memakai cache yang sama. Saat offline, cache dipakai langsung tanpa menunggu timeout jaringan.
- **Preview:** byte gambar dibaca dari cache, lalu didecode dengan `inSampleSize` agar bitmap yang disimpan di memori maksimal 1280 px.
- **Logging:** mengikuti `BuildConfig.DEBUG`, jadi build release tidak menulis file log atau memformat pesan debug.
- **Progress:** update progress hanya diteruskan selama splash masih tampil, sehingga tidak memicu recomposition setelah halaman terbuka.
- **WebView host:** host dibuat sekali dan tidak dibuat ulang pada setiap recomposition; perintah navigasi dikirim lewat `StateFlow` dan diakui setelah diterapkan.
- **Jaringan:** `NetworkMonitor` memakai `registerDefaultNetworkCallback` dan perubahan kapabilitas, bukan polling.
- **Tradeoff:** HTML halaman dan data API tetap diambil dari jaringan agar konten terbaru; aksi "Unduh" memakai `DownloadManager` dan mengunduh ulang dari jaringan.

## Catatan

- `AppConfig.DEBUG` mengikuti `BuildConfig.DEBUG`; log ke Logcat dan file di `Android/data/aeeL.shngmWeb/files/log/` hanya aktif di build debug.
- `AppConfig.ENABLE_ERUDA` bisa diaktifkan untuk debugging WebView.
- `AppConfig.VERSION_NAME` membaca `BuildConfig.VERSION_NAME`, jadi versi log selalu sama dengan versi APK.
- Pada Android 9 ke bawah, unduhan gambar disimpan di penyimpanan khusus aplikasi (`Android/data/aeeL.shngmWeb/files/Pictures/Shinigami`) agar tidak memerlukan izin tulis. Pada Android 10 ke atas, unduhan masuk ke folder publik `Pictures/Shinigami`.
