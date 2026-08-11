# Prompt — `:core:network`

**Batas luar aplikasi.** Semua yang tahu soal HTTP, JSON, dan bentuk response server
berhenti di module ini.

| | |
| --- | --- |
| Namespace | `com.sample.demo.core.network` |
| Source dir | `core/network/src/main/java/com/sample/demo/core/network/` |
| Depends on | `api(:core:common)` |
| Library tersedia | `retrofit` (`api`), `retrofit-converter-gson`, `okhttp`, `okhttp-logging-interceptor`, `gson` |
| Khusus module ini | `buildConfig = true` → `BuildConfig.DEBUG` bisa dipakai |
| Manifest | sudah mendeklarasikan `INTERNET` dan `ACCESS_NETWORK_STATE` — jangan diduplikasi |
| Verifikasi | `.\gradlew.bat :core:network:testDebugUnitTest` |

Retrofit dideklarasikan `api` di sini, tapi `:core:data` memakai module ini dengan
`implementation` — jadi Retrofit terlihat oleh `:core:data` sendiri, tapi **tidak** oleh
`:feature` maupun `:app`. Batas itu yang dijaga prompt berikutnya.

---

## PROMPT

````text
Implementasikan lapisan network untuk <NAMA_FEATURE> di module :core:network.

## Konteks

Baca @CLAUDE.md — bagian "Conventions". Prasyarat: `Result` dan `NetworkException`
sudah ada di :core:common. Library yang boleh dipakai hanya yang sudah ada di
core/network/build.gradle.kts (Retrofit, Gson converter, OkHttp, logging interceptor).
Jangan menambah dependency.

Gson dipakai sebagai converter — BUKAN kotlinx.serialization — karena project ini
tidak punya Kotlin Gradle plugin sama sekali, sehingga library berbasis compiler
plugin tidak bisa dipakai. Jangan mencoba menggantinya.

Endpoint: <METHOD> <URL lengkap>
Contoh response JSON:
<tempel response asli di sini>

## Yang dibangun

com.sample.demo.core.network:

1. `model/<X>Dto` — wire model, mengikuti nama field JSON persis apa adanya.
   Pakai `@SerializedName` bila nama Kotlin berbeda dari nama JSON.
   Semua field yang boleh hilang dari response dibuat nullable — server yang
   menentukan bentuknya, bukan kita.

2. `api/<X>Api` — interface Retrofit.
   Setiap endpoint `suspend fun` yang return `Response<T>`, bukan T langsung.
   Ini disengaja: `Response` membawa status code, dan safeApiCall butuh itu untuk
   membedakan Http(code) dari Connectivity.

3. `safeApiCall` — helper yang membungkus satu panggilan API dan melipat SEMUA
   outcome jadi `Result` dari :core:common:
   - response.isSuccessful && body != null  → Result.Success(body)
   - response tidak sukses                  → Failure(NetworkException.Http(code, errorBody))
   - IOException / SocketTimeoutException   → Failure(NetworkException.Connectivity)
   - JsonSyntaxException / body null padahal sukses → Failure(NetworkException.Serialization)
   - sisanya                                → Failure(NetworkException.Unknown)
   Tidak ada exception yang boleh lolos keluar dari helper ini.

4. `NetworkModule` — perakitan OkHttp + Retrofit:
   - HttpLoggingInterceptor level BODY hanya saat `BuildConfig.DEBUG`, selain itu NONE
     (`buildConfig = true` sudah aktif di module ini).
   - timeout connect/read/write yang eksplisit.
   - `GsonConverterFactory`.
   - base URL <BASE_URL> dengan trailing slash.
   - expose factory untuk membuat `<X>Api`.

## Aturan

- Semua yang berbau HTTP berhenti di module ini. `<X>Dto` tidak boleh dipakai
  sebagai model domain.
- Jangan mendeklarasikan permission apa pun — INTERNET dan ACCESS_NETWORK_STATE
  sudah ada di core/network/src/main/AndroidManifest.xml.
- Tanpa DI framework: `NetworkModule` cukup object/factory sederhana yang dipanggil
  lewat default constructor argument di :core:data.
- Jangan menambah `repositories { }` di build.gradle.kts — setelan
  FAIL_ON_PROJECT_REPOS membuat repository hanya boleh di settings.gradle.kts.

## Verifikasi (jalankan sendiri)

.\gradlew.bat :core:network:testDebugUnitTest

Tulis unit test di core/network/src/test/java/com/sample/demo/core/network/ untuk
`safeApiCall` — panggil helper dengan lambda yang masing-masing menghasilkan:
Response sukses, Response sukses tapi body null, Response error 500 (pakai
`Response.error(...)`), IOException yang dilempar, dan exception generik.
Assert klasifikasi `NetworkException`-nya benar untuk tiap kasus.

Jangan pakai mocking library — belum ada di libs.versions.toml dan tidak perlu:
`Response.success(...)` / `Response.error(...)` bisa dibuat langsung.

Tunjukkan output test sebagai bukti. Kalau gagal, perbaiki akar masalahnya.

## Di luar scope

- Jangan membuat repository atau model domain — itu milik :core:data.
- Jangan menambah caching, interceptor auth, atau retry policy kecuali diminta.
- Jangan menambah kotlinx.serialization / Moshi / plugin Kotlin.
- Jangan menyentuh module lain.
````

---

## Setelah selesai

Lanjut ke [`03-core-data.md`](03-core-data.md).
