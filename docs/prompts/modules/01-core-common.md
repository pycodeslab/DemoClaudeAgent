# Prompt — `:core:common`

**Layer terdalam. Tidak bergantung pada module lain.** Kerjakan ini pertama — empat prompt
lainnya mengasumsikan tipe di sini sudah ada.

| | |
| --- | --- |
| Namespace | `com.sample.demo.core.common` |
| Source dir | `core/common/src/main/java/com/sample/demo/core/common/` |
| Depends on | *(tidak ada module)* |
| Library tersedia | `kotlinx-coroutines-core` (`api`), `androidx-core-ktx` |
| Test | `junit`, `kotlinx-coroutines-test` |
| Verifikasi | `.\gradlew.bat :core:common:testDebugUnitTest` |

`kotlinx-coroutines-core` dideklarasikan `api`, jadi tipe coroutines ikut terlihat oleh
semua module di atasnya. Ini satu-satunya module yang tipenya boleh muncul di public API
setiap layer lain — apa pun yang ditaruh di sini otomatis jadi bahasa bersama antar module.

---

## PROMPT

````text
Implementasikan primitif bersama di module :core:common.

## Konteks

Baca @CLAUDE.md — bagian "Conventions" dan "Toolchain". Module ini masih kosong
(hanya .gitkeep). Library yang boleh dipakai hanya yang sudah ada di
core/common/build.gradle.kts: kotlinx-coroutines-core dan androidx-core-ktx.
Jangan menambah dependency ke gradle/libs.versions.toml.

Module ini tidak boleh bergantung pada module lain, dan tidak boleh menyebut
Retrofit, OkHttp, Gson, atau tipe Android UI apa pun.

## Yang dibangun

com.sample.demo.core.common:

1. `Result<out T>` — sealed class/interface:
   - `Success<T>(val data: T)`
   - `Failure(val error: NetworkException)`
   Lengkapi dengan helper inline yang benar-benar dipakai layer di atas:
   `map`, `getOrNull`, `fold`. Jangan menambah helper spekulatif.

2. `NetworkException` — sealed hierarchy (subclass dari Exception):
   - `Connectivity(cause: Throwable?)`   — tidak ada koneksi / timeout / IOException
   - `Http(val code: Int, val body: String?)` — response tidak sukses
   - `Serialization(cause: Throwable?)`  — gagal parsing body
   - `Unknown(cause: Throwable?)`        — sisanya
   Ini value yang menyeberang boundary, jadi harus stabil dan tidak membawa
   tipe apa pun dari layer network.

3. `DispatcherProvider` — interface dengan `io`, `default`, `main`
   (`CoroutineDispatcher`), plus implementasi default berbasis `Dispatchers`.
   Tujuannya supaya unit test bisa menyuntik `StandardTestDispatcher` tanpa
   Robolectric atau aturan JUnit khusus.

## Aturan

- Semua tipe di module ini `public` dan stabil — ini bahasa bersama antar layer.
- Tanpa DI framework. Implementasi default dipasang lewat default constructor
  argument di layer yang memakainya, bukan lewat singleton/service locator.
- Jangan menambah util yang belum ada pemakainya. Extension yang tidak dipanggil
  siapa pun jangan dibuat.

## Verifikasi (jalankan sendiri)

.\gradlew.bat :core:common:testDebugUnitTest

Tulis unit test di core/common/src/test/java/com/sample/demo/core/common/:
- `Result.map` mentransformasi Success dan meneruskan Failure apa adanya.
- `fold` memanggil cabang yang benar untuk masing-masing kasus.
- Setiap subtipe `NetworkException` membawa datanya (`Http` menyimpan code).

Tunjukkan output test sebagai bukti. Kalau gagal, perbaiki akar masalahnya.

## Di luar scope

- Jangan membuat safeApiCall di sini — helper itu milik :core:network karena
  menyentuh tipe Retrofit `Response`.
- Jangan menambah plugin Kotlin, DI framework, atau library serialization.
- Jangan menyentuh module lain.
````

---

## Setelah selesai

Lanjut ke [`02-core-network.md`](02-core-network.md).
