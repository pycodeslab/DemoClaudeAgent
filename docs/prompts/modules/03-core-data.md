# Prompt — `:core:data`

**Module penjaga batas.** Tugas utamanya bukan menyimpan data, melainkan memastikan
`:feature` tidak pernah melihat tipe network.

| | |
| --- | --- |
| Namespace | `com.sample.demo.core.data` |
| Source dir | `core/data/src/main/java/com/sample/demo/core/data/` |
| Depends on | `api(:core:common)`, `implementation(:core:network)` |
| Library tersedia | tidak ada library langsung — Retrofit terlihat lewat `:core:network`, coroutines lewat `:core:common` |
| Verifikasi | `.\gradlew.bat :core:data:testDebugUnitTest` |

`implementation(project(":core:network"))` berarti module ini boleh memakai Retrofit
dan `<X>Dto` di dalam dirinya sendiri, tapi konsumennya (`:feature`) tidak.
Gradle **tidak** akan menolak `internal` yang salah jadi `public`, karena `:feature`
tetap bisa dikompilasi selama tipe network tidak benar-benar disebut. Jadi aturan
"constructor yang menerima tipe network harus `internal`" harus dijaga saat menulis,
bukan diandalkan ke compiler.

---

## PROMPT

````text
Implementasikan lapisan data untuk <NAMA_FEATURE> di module :core:data.

## Konteks

Baca @CLAUDE.md — bagian "Conventions". Prasyarat:
- :core:common sudah punya `Result`, `NetworkException`, `DispatcherProvider`.
- :core:network sudah punya `<X>Dto`, `<X>Api`, `safeApiCall`, `NetworkModule`.

Module ini memakai :core:network dengan `implementation`, bukan `api` — itu disengaja.
Jangan mengubahnya jadi `api` untuk "memudahkan" :feature.

## Yang dibangun

com.sample.demo.core.data:

1. `model/<X>` — model domain. Bentuknya ditentukan kebutuhan UI, bukan bentuk JSON:
   field yang tidak dipakai UI tidak perlu dibawa, field nullable dari wire
   diselesaikan di sini (default value), tipe mentah dinormalkan
   (mis. string tanggal → tipe yang sudah diparse).

2. `mapper/<X>Mapper.kt` — extension `internal fun <X>Dto.toDomain(): <X>`.
   Semua penanganan null dan normalisasi terjadi di sini, sehingga model domain
   tidak punya field nullable yang tidak perlu.
   Mapper harus `internal` — signature-nya menyebut `<X>Dto`.

3. `repository/<X>Repository` — interface PUBLIK. Ini satu-satunya permukaan yang
   dilihat :feature:

       interface <X>Repository {
           suspend fun get<X>s(): Result<List<<X>>>
       }

   Signature-nya hanya boleh menyebut tipe dari :core:common dan model domain
   module ini. Tidak boleh ada Retrofit, `Response`, atau `<X>Dto` di sini.

4. `repository/<X>RepositoryImpl` — implementasi.
   - Constructor yang menerima `<X>Api` WAJIB `internal`.
   - Sediakan cara publik untuk membuatnya tanpa menyebut tipe network — mis.
     `fun <X>Repository(): <X>Repository = <X>RepositoryImpl(NetworkModule.create<X>Api())`
     atau constructor sekunder publik tanpa argumen. Pilih satu, konsisten.
   - Panggil `safeApiCall`, lalu `map` hasilnya ke model domain. Repository
     BRANCH di atas `Result` — tidak ada try/catch di sini.
   - Kerjakan mapping di dispatcher IO lewat `DispatcherProvider`.

## Aturan — ini inti module ini

- Public API :core:data tidak boleh menyebut tipe apa pun dari :core:network.
  Sebelum selesai, cek ulang setiap deklarasi `public` di module ini dan pastikan
  tidak ada `<X>Dto`, `Response`, `Retrofit`, atau `OkHttp` di signature-nya.
- Error tetap jadi value. Jangan melempar exception ke :feature, jangan menulis
  try/catch — `safeApiCall` di :core:network sudah menangani itu.
- Tanpa DI framework: default constructor argument, bukan singleton global
  yang menyimpan state.
- Jangan menambah dependency ke core/data/build.gradle.kts.

## Verifikasi (jalankan sendiri)

.\gradlew.bat :core:data:testDebugUnitTest

Tulis unit test di core/data/src/test/java/com/sample/demo/core/data/:
- `<X>MapperTest` — Dto lengkap ter-map benar; Dto dengan field null menghasilkan
  default yang masuk akal, bukan crash.
- `<X>RepositoryImplTest` — pakai fake `<X>Api` (implementasi interface langsung,
  bukan mocking library) yang mengembalikan:
  Response sukses → `Result.Success` berisi model domain;
  Response 500    → `Failure(NetworkException.Http(500, ...))`, diteruskan apa adanya;
  IOException     → `Failure(NetworkException.Connectivity)`.
  Suntik dispatcher test lewat `DispatcherProvider` dan pakai `runTest`.

Tunjukkan output test sebagai bukti. Kalau gagal, perbaiki akar masalahnya.

## Di luar scope

- Jangan menambah database, cache, atau DataStore.
- Jangan membuat ViewModel atau apa pun yang berbau UI.
- Jangan mengubah :core:network jadi `api`.
- Jangan menyentuh module lain.
````

---

## Setelah selesai

Lanjut ke [`04-feature.md`](04-feature.md).
