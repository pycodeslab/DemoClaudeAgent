# Prompt — `:app`

**Module perakit.** Tidak punya kode sendiri — hanya manifest, theme, ikon, dan
keputusan "layar mana yang jadi entry point".

| | |
| --- | --- |
| Namespace / applicationId | `com.sample.demo` |
| Depends on | keempat module lainnya |
| Library tersedia | `appcompat`, `core-ktx`, `material` |
| Theme | `Theme.DemoClaudeAgent` di `app/src/main/res/values/themes.xml` (+ `values-night/`) |
| Verifikasi | `.\gradlew.bat :app:assembleDebug` lalu `.\gradlew.bat :app:installDebug` |

Ini langkah terakhir, dan biasanya diff-nya kecil — sering hanya satu blok
`<activity>` di manifest. Prompt sebesar ini berlebihan untuk itu; pakai bagian
**Prompt singkat** di bawah kecuali memang ada perubahan theme atau build type.

---

## PROMPT

````text
Sambungkan <X>Activity dari :feature sebagai entry point aplikasi di module :app.

## Konteks

Baca @CLAUDE.md — bagian "Toolchain" dan "Conventions". Prasyarat: :feature sudah
mendeklarasikan `<X>Activity` di feature/src/main/AndroidManifest.xml tanpa
`android:exported` dan tanpa intent-filter.

:app tidak punya kode Kotlin sendiri selain template ExampleUnitTest /
ExampleInstrumentedTest. Jangan menambah Activity atau kelas apa pun di sini —
tugas module ini hanya merakit.

## Yang dibangun

1. app/src/main/AndroidManifest.xml — tambahkan di dalam `<application>`:

       <activity
           android:name="com.sample.demo.feature.<paket>.<X>Activity"
           android:exported="true">
           <intent-filter>
               <action android:name="android.intent.action.MAIN" />
               <category android:name="android.intent.category.LAUNCHER" />
           </intent-filter>
       </activity>

   `android:exported="true"` ditulis DI SINI, bukan di :feature. Manifest merger
   menggabungkan deklarasi dari kedua module; :feature mendefinisikan activity-nya,
   :app yang memutuskan bahwa activity itu adalah launcher dan karenanya exported.
   Hapus komentar placeholder "No components yet" yang ada sekarang.

2. app/src/main/res/values/strings.xml — sesuaikan `app_name` bila perlu.

## Aturan

- Jangan menambah kode Kotlin di :app.
- Jangan memindahkan Theme.DemoClaudeAgent ke module lain — layout :feature
  sengaja mereferensikan `?attr/...` supaya theme tetap tinggal di sini.
- Jangan menambah `<uses-permission>` — INTERNET dan ACCESS_NETWORK_STATE datang
  dari :core:network lewat manifest merger.
- Toolchain jangan diubah: `compileSdk { version = release(37) }` dan
  `buildTypes.release { optimization { enable = false } }` memang bentuk yang benar
  untuk AGP 9. Tidak ada `isMinifyEnabled`/`proguardFiles` untuk diedit; keep rules
  R8 ada di app/src/main/keepRules/.

## Verifikasi (jalankan sendiri)

1. .\gradlew.bat :app:assembleDebug
2. .\gradlew.bat lintDebug
3. Cek manifest hasil merge dan konfirmasi bahwa <X>Activity punya
   intent-filter MAIN/LAUNCHER, `android:exported="true"`, dan permission INTERNET
   ikut ter-merge:
   app/build/intermediates/merged_manifests/debug/**/AndroidManifest.xml
   (tunjukkan isinya sebagai bukti, jangan hanya menyimpulkan)

Kalau ada device/emulator terhubung, jalankan juga:
   .\gradlew.bat :app:installDebug

Kalau gagal, perbaiki akar masalahnya — jangan menambah `tools:replace` atau
`tools:node="remove"` untuk menutupi konflik merger tanpa memahami sebabnya.

## Di luar scope

- Jangan menambah build type, product flavor, atau signing config.
- Jangan mengaktifkan minifikasi.
- Jangan mengubah ikon launcher atau warna theme kecuali diminta.
- Jangan menyentuh module lain.
````

---

## Prompt singkat

Untuk kasus biasa — satu activity, satu intent-filter — ini sudah cukup:

````text
Jadikan <X>Activity dari :feature sebagai launcher activity.

Tambahkan blok <activity> dengan intent-filter MAIN/LAUNCHER dan
android:exported="true" di app/src/main/AndroidManifest.xml (bukan di :feature —
lihat konvensi "Each module declares its own manifest components" di CLAUDE.md),
lalu hapus komentar placeholder di situ.

Verifikasi dengan .\gradlew.bat :app:assembleDebug dan tunjukkan hasil merged
manifest-nya.
````

---

## Setelah selesai

Seluruh rantai module sudah tersambung. Jalankan verifikasi menyeluruh:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat :app:assembleDebug
```

Lalu `/code-review` untuk mereview diff gabungan di context yang bersih.
