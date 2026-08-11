# Contoh Prompt Terisi — Feature "Post List"

Ini [`new-feature.template.md`](new-feature.template.md) dengan semua placeholder terisi,
untuk feature daftar post dari JSONPlaceholder. Copy blok di bawah apa adanya untuk
mencobanya, atau pakai sebagai acuan seberapa spesifik isian tiap bagian sebaiknya.

---

````text
Bangun feature Post List di project multi-module ini.

## Konteks

Baca dulu @CLAUDE.md — di situ ada aturan toolchain dan konvensi yang WAJIB diikuti.
Dependency yang boleh dipakai hanya yang sudah ada di @gradle/libs.versions.toml
(Retrofit 2.11, OkHttp 4.12, Gson 2.11, coroutines 1.8.1, lifecycle 2.7, recyclerview,
material, constraintlayout sudah tersedia); jangan menambah koordinat baru.

Rantai module: :app → :feature → :core:data → :core:network → :core:common
Semua source set masih kosong, jadi ini implementasi pertama — tidak ada kode lama
untuk dicontoh. Ikuti bagian "Conventions" di CLAUDE.md sebagai gantinya.

Sumber data: GET https://jsonplaceholder.typicode.com/posts
Contoh response JSON (array of):

    {
      "userId": 1,
      "id": 1,
      "title": "sunt aut facere repellat provident",
      "body": "quia et suscipit\nsuscipit recusandae"
    }

## Yang dibangun, per module

:core:common  — `Result<out T>` sealed class (Success<T> / Failure(NetworkException)),
                `NetworkException` (subtipe: Connectivity, Http(code), Serialization, Unknown),
                `DispatcherProvider` interface + implementasi default (Dispatchers.IO/Main).

:core:network — `PostDto` (userId, id, title, body),
                `PostApi` dengan `@GET("posts") suspend fun getPosts(): Response<List<PostDto>>`,
                `NetworkModule` yang menyusun OkHttp (+ HttpLoggingInterceptor hanya saat
                BuildConfig.DEBUG) dan Retrofit dengan GsonConverterFactory,
                base URL https://jsonplaceholder.typicode.com/,
                `safeApiCall` helper yang melipat Response/exception jadi Result.

:core:data    — domain model `Post(id, title, body)`,
                mapper `core/data/mapper/PostMapper.kt` → `PostDto.toDomain()`,
                `PostRepository` interface dengan `suspend fun getPosts(): Result<List<Post>>`,
                `PostRepositoryImpl` — constructor yang menerima `PostApi` harus `internal`,
                plus factory/constructor publik tanpa argumen tipe network.

:feature      — `PostsActivity`, `PostsViewModel`, `PostsUiState`,
                `PostsAdapter` (ListAdapter + DiffUtil, item menampilkan title dan body),
                layout `activity_posts.xml` (RecyclerView + ProgressBar + view error dengan
                tombol Retry) dan `item_post.xml`.

:app          — merge intent-filter MAIN/LAUNCHER untuk PostsActivity di
                app/src/main/AndroidManifest.xml.

UI yang diharapkan: satu layar berisi daftar post.
- loading  → ProgressBar tampil, list dan error disembunyikan
- success  → list tampil; kalau kosong tampilkan teks "Belum ada post"
- error    → pesan error + tombol Retry yang memanggil ulang load
String hardcoded tidak boleh — taruh di app/src/main/res/values/strings.xml
<atau res milik :feature kalau lebih tepat>.

## Ikuti pattern ini

- :core:data menyembunyikan :core:network (`implementation`, bukan `api`).
  `PostDto`, Retrofit, dan OkHttp TIDAK BOLEH muncul di public API :core:data —
  `PostRepositoryImpl(api: PostApi)` harus `internal`.
- Error menyeberang layer sebagai value. `safeApiCall` melipat semua outcome jadi
  `Result`; kegagalan diklasifikasi jadi `NetworkException`. `PostRepositoryImpl` dan
  `PostsViewModel` branch di atas `Result`, tidak pakai try/catch.
- Endpoint = `suspend fun` yang return `Response<T>` di `core/network/api/`.
- Mapping wire → domain di `core/data/mapper/`.
- Tanpa DI framework: `PostsViewModel(repository: PostRepository = PostRepository())`
  gaya default argument, plus `ViewModelProvider.Factory` eksplisit di :feature.
- Views + XML dengan view binding (`viewBinding = true` sudah aktif di :feature),
  bukan Compose. Layout pakai `?attr/colorPrimary` dsb. karena `Theme.DemoClaudeAgent`
  ada di :app.
- Satu `PostsUiState` untuk layar ini (isLoading, posts, errorMessage), di-expose
  sebagai `StateFlow`, dikoleksi dengan `repeatOnLifecycle(STARTED)`. Tambah field ke
  state itu, jangan bikin StateFlow kedua.
- `PostsActivity` dideklarasikan di feature/src/main/AndroidManifest.xml tanpa
  `android:exported`; :app yang menambahkan intent-filter MAIN/LAUNCHER.
  `INTERNET` sudah dideklarasikan :core:network — jangan diduplikasi.

## Di luar scope — jangan dikerjakan

- Jangan menambah plugin Kotlin, Hilt/Koin, Compose, atau kotlinx.serialization.
- Jangan mengubah versi AGP/Gradle/compileSdk atau "memodernkan" DSL
  (`compileSdk { version = release(37) }` dan `optimization { enable = false }` memang benar).
- Jangan membuat module baru — lima module yang ada sudah cukup.
- Jangan menambah caching, database, paging, atau layar detail. Hanya daftar + refresh via Retry.
- Jangan mengubah theme, ikon, atau resource :app selain manifest dan strings.

## Verifikasi (jalankan sendiri, jangan tanya saya)

1. .\gradlew.bat :app:assembleDebug
2. .\gradlew.bat testDebugUnitTest
3. .\gradlew.bat lintDebug

Tulis unit test untuk:
- `PostMapperTest` — PostDto lengkap ter-map benar; field kosong/null jadi string kosong.
- `PostRepositoryImplTest` — Response sukses → Result.Success berisi domain model;
  HTTP 500 → Failure(NetworkException.Http(500)); IOException → Failure(Connectivity).
  Pakai fake `PostApi` (implementasi interface langsung), tanpa mocking library.
- `PostsViewModelTest` — urutan state loading → success dan loading → error, pakai fake
  `PostRepository` dan `kotlinx-coroutines-test` (StandardTestDispatcher via DispatcherProvider).

Iterasi sampai ketiga perintah di atas hijau. Tunjukkan output perintahnya sebagai
bukti, jangan cuma bilang "sudah berhasil". Kalau ada yang gagal, perbaiki akar
masalahnya — jangan disuppress atau test-nya dilonggarkan.

## Urutan kerja

1. Baca CLAUDE.md, libs.versions.toml, dan build.gradle.kts tiap module.
   Laporkan library apa saja yang sudah tersedia per layer.
2. Buat rencana: daftar file yang akan dibuat, per module, beserta signature publiknya.
   Tunggu saya approve sebelum menulis file.
3. Implementasikan dari layer terdalam ke luar: :core:common → :core:network →
   :core:data → :feature → :app.
4. Jalankan verifikasi di atas dan iterasi sampai lolos.
5. Ringkas: file yang dibuat, keputusan desain yang diambil, dan yang sengaja
   tidak dikerjakan.
````

---

## Catatan

- Bagian **Verifikasi** dan **Di luar scope** yang paling sering dilewatkan, padahal keduanya
  yang paling menentukan hasil: tanpa verifikasi Anda jadi loop verifikasinya, dan tanpa
  batas scope Claude cenderung menambah hal yang "sekalian berguna".
- Kalau belum yakin bentuk feature-nya, jangan pakai template ini. Mulai dengan:
  `Saya mau bangun <deskripsi singkat>. Interview saya detail pakai AskUserQuestion,
  lalu tulis spec lengkap ke SPEC.md.` — baru buka sesi baru untuk mengeksekusi SPEC.md.
- Setelah implementasi selesai, jalankan `/code-review` di sesi yang sama untuk review
  diff di context yang bersih.
