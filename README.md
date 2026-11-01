# Ringan Musik

Pemutar musik Android super ringan: streaming + unduhan offline, tampilan ala Spotify.

## Fitur
- Lagu populer dan pencarian (sumber: [Audius](https://audius.co), gratis, tanpa API key)
- Streaming langsung, pemutaran tetap jalan di latar belakang
- Notifikasi media + kontrol di layar kunci (play/pause/next/prev)
- Unduh lagu untuk offline (tombol ↓), tersimpan di penyimpanan internal aplikasi
- Lagu yang sudah diunduh otomatis diputar dari file lokal (hemat kuota)
- Mini player + layar pemutar penuh dengan slider seek

## Kenapa ringan
- Hanya Media3 ExoPlayer inti (tanpa DASH/HLS), Compose, dan Material3
- Tanpa Retrofit/OkHttp/Coil/Room: jaringan pakai `HttpURLConnection`, gambar pakai `ImageCache` mini, data pakai satu file JSON
- Ikon memakai simbol teks, tanpa library ikon
- Build release memakai R8 + penghapusan resource tak terpakai
- Posisi lagu hanya di-refresh saat layar terlihat dan lagu sedang diputar

## Cara build
1. Pasang **Android Studio** (versi terbaru), lalu **Open** folder `RinganMusik`.
2. Tunggu Gradle sync selesai (butuh internet; Android Studio akan mengunduh Gradle 8.9 dan SDK 35 bila belum ada).
3. Sambungkan HP (aktifkan *USB debugging*) lalu klik **Run**, atau bangun APK:
   - Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**, atau
   - Terminal: `./gradlew assembleRelease`, hasilnya di `app/build/outputs/apk/release/`.

Minimal Android 7.0 (API 24).

## Struktur kode
| File | Fungsi |
|---|---|
| `AudiusApi.kt` | Ambil lagu populer, cari lagu, URL stream |
| `PlaybackService.kt` | ExoPlayer + MediaSession (notifikasi & latar belakang) |
| `Library.kt` | Antrean dan penyimpanan unduhan offline |
| `DownloadService.kt` | Foreground service agar unduhan lanjut saat aplikasi ditutup |
| `MainViewModel.kt` | Menghubungkan UI dengan pemutar, pencarian, dan unduhan |
| `MainActivity.kt` | Seluruh UI Compose |

## Ganti sumber musik
Semua akses katalog ada di `AudiusApi.kt`. Untuk memakai layanan lain (mis. Jamendo), pertahankan tiga fungsi
`trending()`, `search()`, dan `streamUrl()` lalu sesuaikan parsing JSON-nya.

## Catatan
- Lagu di Audius diunggah oleh artis independen. Pakai unduhan hanya untuk pemakaian pribadi dan hormati ketentuan Audius serta hak artis. Bila ingin merilis aplikasi ke Play Store, periksa dulu syarat layanan dan lisensi sumber musiknya.
- Android 13+ akan meminta izin notifikasi; izin ini dipakai untuk kontrol pemutar dan progres unduhan.
- Versi library di `build.gradle.kts` sengaja dipilih stabil; boleh dinaikkan lewat Android Studio.
