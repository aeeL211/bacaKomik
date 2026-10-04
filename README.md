# Shinigami Android Client (Web2APK)

Aplikasi Android berbasis **Kotlin** dan **Jetpack Compose Material 3** yang membungkus WebView dengan modifikasi respons kustom, pemblokiran iklan, dan ekstensi bawaan.

## 🚀 Cara Build

- **Debug Build**:
  ```bash
  ./gradlew assembleDebug
  ```
  Output APK: `app/build/outputs/apk/debug/app-debug.apk`

- **Release Build**:
  ```bash
  ./gradlew assembleRelease
  ```
  Output APK: `app/build/outputs/apk/release/app-release.apk`

- **Menjalankan Unit Test**:
  ```bash
  ./gradlew test
  ```

- **Menjalankan Lint**:
  ```bash
  ./gradlew lint
  ```

## 🔑 Setup Signing

Untuk build release, konfigurasi signing default di `app/build.gradle.kts` menggunakan signing config `debug` untuk mempermudah pengujian lokal. Untuk konfigurasi keystore produksi, tambahkan properti keystore di `local.properties` atau `gradle.properties`:
```properties
RELEASE_STORE_FILE=path/to/keystore.jks
RELEASE_STORE_PASSWORD=password
RELEASE_KEY_ALIAS=alias
RELEASE_KEY_PASSWORD=password
```

## 📁 Struktur File Utama (`com.shinigami.client`)

Semua source code Kotlin berada di `app/src/main/kotlin/com/shinigami/client/`:

- **`MainActivity.kt`**: Activity utama yang mengelola windowing, permissions, file chooser, back navigation, dan WebChromeClient/WebViewClient default.
- **`MainScreen.kt`**: UI Composable utama yang berisi WebView (`AndroidView`), Compose M3 `pullToRefresh`, splash screen, serta dialog.
- **`MainViewModel.kt`**: ViewModel yang mengelola state halaman (loading, URL remote, splash, reload).
- **`WebExtension.kt`**: Interceptor OkHttp dan pengelola cache HTML untuk menyuntikkan patch/ekstensi kustom ke dalam respons web.
- **`RequestInterceptor.kt`**: Pengaggal/interceptor request kustom untuk memblokir iklan, mensimulasikan pengumuman API, dan me-redirect URL tertentu.
- **`ConfigRepository.kt`**: Repository untuk mengambil URL remote dari GitHub dengan fallback lokal.
- **`ShinigamiApp.kt`**: Class Application yang menginisialisasi Logger dan global crash handler.
- **`Theme.kt`**: Tema Jetpack Compose Material 3 dan Skema Warna.
- **`Dialogs.kt`**: Component dialog Compose M3 (Info, Confirm, Prompt).
- **`ContextMenuBottomSheet.kt`**: Bottom sheet Material 3 untuk menu konteks saat long-press pada gambar di WebView.
- **`ErudaConsole.kt`**: Injektor Eruda dev console untuk WebView debug.
- **`Logger.kt` & `AppConfig.kt`**: Utility logging ke file dan konfigurasi konstanta aplikasi.

## 🔄 Alur WebView & Modifikasi Respons

1. **Inisialisasi**: `MainActivity` memuat `MainScreen`, yang merender `WebView` di dalam Compose Material 3 `pullToRefresh`.
2. **Interception Request (`RequestInterceptor`)**:
   - `shouldInterceptRequest` memeriksa URL yang diminta. Jika cocok dengan domain iklan (`ads.shinigami`), API pengumuman (`api.shngm.io`), Google Tag Manager, atau Novu, respons kustom langsung dikembalikan tanpa menghubungi server eksternal.
3. **Ekstensi & Patch Respons (`WebExtension`)**:
   - Request HTML yang lolos akan diproses oleh `WebExtension`.
   - `WebExtension` menggunakan `OkHttpClient` dengan disk cache kustom (`html_http_cache`).
   - Konten HTML yang diterima dipatch secara langsung (misal: mengganti `is_premium:false` menjadi `is_premium:true`) sebelum dikembalikan ke WebView.
4. **Deteksi Gambar (`js/image_detector.js`)**:
   - Saat pengguna melakukan long-press di WebView, koordinat sentuhan dikirim ke `image_detector.js` (asset UTF-8 yang dimuat secara lazy).
   - Script JS mengembalikan URL gambar (termasuk srcset, shadow root, picture, canvas, bg-image) untuk ditampilkan di `ContextMenuBottomSheet`.
