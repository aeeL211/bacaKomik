# Shinigami Android Client

Aplikasi Android berbasis **Kotlin** dan **Jetpack Compose** yang membungkus situs komik Shinigami di dalam WebView.

## Informasi Aplikasi

- **Package:** `aeeL.shngmWeb`
- **Versi:** `1.8.0`
- **Min SDK:** 24
- **Target SDK:** 37
- **Compile SDK:** 37
- **JDK:** 21

## Fitur

- Tampilan WebView bertema gelap dengan dukungan edge-to-edge.
- Navigasi back, pull-to-refresh, dan popup gambar.
- Upload file melalui pemilih file Android.
- Pemblokiran iklan dan pembersihan elemen halaman.
- Dialog premium yang tampil satu kali.
- Cache gambar untuk mempercepat pemuatan ulang.
- Konfigurasi URL situs yang dapat diperbarui.

## Build

Build lokal membutuhkan JDK 21 dan Android SDK dengan `compileSdk 37`.

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

Build release dijalankan melalui GitHub Actions.

## Izin

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

## Catatan

Build release saat ini menggunakan signing debug agar APK dapat langsung diuji. Ganti dengan keystore produksi sebelum distribusi publik.
