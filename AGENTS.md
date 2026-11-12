# AGENTS.md — Shinigami Android Client

## Tujuan proyek

Aplikasi Android pembungkus situs komik Shinigami. Satu modul `:app`, Kotlin + Jetpack Compose,
WebView sebagai inti pengalaman baca. Package `aeeL.shngmWeb`.

## Stack dan batasan

- minSdk 24, target/compileSdk 37, JDK 21, AGP 9.x, Kotlin 2.4.x.
- Compose BOM + Material 3. Tidak ada navigation library: WebView mengelola histori sendiri.
- OkHttp dipakai untuk request di luar WebView (config, patching respons, cache gambar, avatar).
- Gaya kode: 2 spasi, LF, UTF-8, tanpa baris > tidak dibatasi; jalankan `./gradlew spotlessApply`.
- Jangan menambah dependency baru tanpa alasan performa/keamanan yang jelas.

## Arsitektur aktual

- `MainActivity` memiliki lifecycle WebView, file picker, dialog JS, popup WebView, dan avatar flow.
- `MainScreen` membuat satu host `PullToRefreshWebView` lewat `remember`; `AndroidView` hanya
  memasang host ke hierarchy. Jangan membuat WebView baru saat recomposition.
- `MainViewModel` menyimpan `MainState` dan mengirim `PageCommand` satu kali. Host wajib memanggil
  `onPageCommandHandled(id)` setelah perintah dijalankan.
- `ConfigRepository` mengambil URL situs dari `url.txt` GitHub; URL cache dipakai sebagai fallback offline.
- `RequestInterceptor` memblokir iklan/announcement dan menambal flag `is_premium` pada HTML/JSON.
- `ImageCache` adalah cache disk OkHttp 256 MB untuk gambar; jangan menambah cache gambar kedua.
- `NetworkMonitor` memberi sinyal online/offline ke ViewModel dan `ImageCache`.
- `Hosts.kt` adalah sumber kebenaran domain komik. Gunakan `isComicHost()` untuk keputusan domain.
  Domain aktif dari config selalu diterima, ditambah keluarga mirror yang mengandung `shngm`/`shinigami`.
- Tema ada di `Theme.kt`; palet situs: base-dark `#09090B`, base-bg `#18181B`,
  base-card `#27272A`, primary-500 `#6F39EE`, primary-300 `#9F8AFB`, aksen magenta `#AE14B0`.
  Skema light/dark diturunkan dari token ini; jangan menambah warna hardcoded di composable.

## Aturan produk

- Zoom WebView hanya aktif bila URL main frame (huruf kecil) mengandung `chapter`, di mana pun
  letaknya (path, query, atau fragment). Deteksi harus mengikuti URL main frame, termasuk redirect,
  back/forward, dan navigasi SPA. Gunakan `WebViewZoom`; popup gambar tidak boleh ikut zoom.
- Jangan mengubah perilaku fitur yang sudah benar: pull-to-refresh, popup gambar, file upload,
  dialog JS, pemblokiran iklan, patch premium, dan avatar.
- WebView tidak boleh mengakses file lokal (`allowFileAccess = false`). Pertahankan sandbox ini.
- Konten yang dibuka di WebView hanya domain komik dan alur login Google; selain itu buka browser.

## Verifikasi

- Build: `./gradlew assembleDebug` (butuh Android SDK 37).
- Format: `./gradlew spotlessCheck` atau `spotlessApply`.
- Uji manual minimal: buka home, buka series, buka chapter (pinch zoom), back, redirect/login,
  putar layar, matikan/nyalakan jaringan, long-press gambar, upload file, dialog JS.
- Jangan commit/ubah artefak `build/`, `app/build/`, atau file workflow yang sedang diubah user.
