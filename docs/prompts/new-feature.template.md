# Template Prompt — Menambah Feature Baru (Multi-Module)

Template prompt untuk meminta Claude Code membangun satu feature end-to-end di repo ini,
mengikuti [best practices Claude Code](https://code.claude.com/docs/en/best-practices#provide-specific-context-in-your-prompts).

## Cara pakai

1. Copy blok **PROMPT** di bawah ke sesi Claude Code.
2. Ganti semua placeholder `<...>`. Placeholder yang tidak diisi = tebakan Claude = revisi.
3. Jalankan `/clear` dulu kalau sesi sebelumnya membahas hal lain.
4. Untuk feature yang menyentuh 4–5 module (hampir semua di repo ini), masuk **plan mode**
   (`Shift+Tab` sampai `⏸ plan mode on`) untuk 2 langkah pertama, baru approve untuk implementasi.

## Kenapa strukturnya seperti ini

Setiap bagian template memetakan satu praktik dari dokumen best practices:

| Bagian template | Praktik | Alasan |
| --- | --- | --- |
| `## Konteks` | *Point to sources* | Menunjuk `CLAUDE.md` + `libs.versions.toml` daripada membiarkan Claude menebak toolchain AGP 9 yang tidak biasa. |
| `## Yang dibangun` per module | *Scope the task* | Batas module sudah jadi batas task — Claude tahu file mana milik layer mana. |
| `## Ikuti pattern ini` | *Reference existing patterns* | Konvensi repo (Result, `safeApiCall`, satu state object) ditulis eksplisit, bukan diasumsikan. |
| `## Di luar scope` | *Scope the task* | Mencegah Claude menambah Hilt/Compose/kotlinx.serialization "sekalian". |
| `## Verifikasi` | *Give Claude a way to verify its work* | Perintah Gradle yang bisa dijalankan sendiri → loop selesai tanpa Anda jadi verifikatornya. |
| `## Urutan kerja` | *Explore first, then plan, then code* | Memaksa eksplorasi dan rencana sebelum menulis file. |

---

## PROMPT

````text
Bangun feature <NAMA_FEATURE> di project multi-module ini.

## Konteks

Baca dulu @CLAUDE.md — di situ ada aturan toolchain dan konvensi yang WAJIB diikuti.
Dependency yang boleh dipakai hanya yang sudah ada di @gradle/libs.versions.toml;
jangan menambah koordinat baru.

Rantai module: :app → :feature → :core:data → :core:network → :core:common
Semua source set masih kosong, jadi ini implementasi pertama — tidak ada kode lama
untuk dicontoh. Ikuti bagian "Conventions" di CLAUDE.md sebagai gantinya.

Sumber data: <URL endpoint / deskripsi API, contoh: GET https://jsonplaceholder.typicode.com/posts>
Contoh response JSON:
<tempel 1 item response di sini>

## Yang dibangun, per module

:core:common  — <mis. Result<T> sealed class, NetworkException, DispatcherProvider>
:core:network — <mis. wire model <X>Dto, <X>Api dengan suspend fun ...: Response<...>,
                 NetworkModule (Retrofit + OkHttp + Gson + logging interceptor)>
:core:data    — <mis. domain model <X>, mapper <X>Dto.toDomain(), <X>Repository interface
                 + <X>RepositoryImpl>
:feature      — <mis. <X>Activity, <X>ViewModel, <X>UiState, <X>Adapter (ListAdapter +
                 DiffUtil), layout activity_<x>.xml + item_<x>.xml>
:app          — <mis. merge intent-filter MAIN/LAUNCHER untuk <X>Activity>

UI yang diharapkan: <deskripsi layar + state loading / error / empty / success>

## Ikuti pattern ini

- :core:data menyembunyikan :core:network (`implementation`, bukan `api`).
  Tipe Retrofit dan wire model TIDAK BOLEH muncul di public API :core:data —
  buat constructor yang menerima tipe network sebagai `internal`.
- Error menyeberang layer sebagai value, bukan exception. Helper `safeApiCall`
  melipat semua outcome jadi `Result`, kegagalan diklasifikasi jadi `NetworkException`.
  Repository dan ViewModel branch di atas `Result`, tidak pakai try/catch.
- Endpoint = `suspend fun` yang return `Response<T>` di `core/network/api/`.
- Mapping wire → domain di `core/data/mapper/`.
- Tanpa DI framework: default constructor argument + `ViewModelProvider.Factory` eksplisit.
- Views + XML (view binding), bukan Compose. Layout :feature pakai `?attr/...`
  karena `Theme.DemoClaudeAgent` ada di :app.
- Satu state object per layar, di-expose sebagai `StateFlow`, dikoleksi dengan
  `repeatOnLifecycle(STARTED)`. Tambah field ke state itu, jangan bikin stream kedua.
- Feature mendeklarasikan activity-nya sendiri tanpa `android:exported`; :app yang
  menambahkan intent-filter MAIN/LAUNCHER.

## Di luar scope — jangan dikerjakan

- Jangan menambah plugin Kotlin, Hilt/Koin, Compose, atau kotlinx.serialization.
- Jangan mengubah versi AGP/Gradle/compileSdk atau "memodernkan" DSL
  (`compileSdk { version = release(37) }` dan `optimization { enable = false }` memang benar).
- Jangan menambah module baru <atau: buat module baru :feature:<nama> dengan cara di CLAUDE.md>.
- Jangan menyentuh <area lain yang tidak boleh berubah>.

## Verifikasi (jalankan sendiri, jangan tanya saya)

1. .\gradlew.bat :app:assembleDebug
2. .\gradlew.bat testDebugUnitTest
3. .\gradlew.bat lintDebug

Tulis unit test untuk: <mis. mapper (field mapping + field null),
<X>RepositoryImpl (sukses, HTTP error, IOException), <X>ViewModel (urutan state
loading → success, loading → error) pakai fake repository, tanpa mocking library>.

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

## Varian prompt yang lebih pendek

Untuk perubahan kecil pada feature yang sudah ada, template lengkap ini berlebihan.
Sesuai catatan di best practices — *"if you could describe the diff in one sentence, skip the plan"* —
pakai bentuk singkat yang tetap menyebut file, pattern, dan verifikasi:

````text
Tambahkan pull-to-refresh di PostsActivity.

- Pakai SwipeRefreshLayout <atau: kalau belum ada di libs.versions.toml, pakai
  tombol retry di layout yang sudah ada — jangan tambah dependency baru>.
- Refresh memanggil ulang jalur yang sama dengan initial load di PostsViewModel.
- Tambah field `isRefreshing` ke PostsUiState yang sudah ada — jangan bikin
  StateFlow kedua (lihat konvensi "One state object per screen" di CLAUDE.md).
- Tambah unit test di PostsViewModelTest untuk urutan state saat refresh gagal.
- Jalankan: .\gradlew.bat :feature:testDebugUnitTest --tests "*PostsViewModelTest"
  lalu .\gradlew.bat :app:assembleDebug
````

## Anti-pattern yang sering muncul di repo ini

Hal-hal yang membuat prompt gagal di project ini secara spesifik — sebutkan larangannya
di prompt kalau Claude pernah melakukannya:

| Gejala | Sebab | Tulis di prompt |
| --- | --- | --- |
| Menambah `org.jetbrains.kotlin.android` saat ada error compile Kotlin | AGP 9 sudah punya Kotlin bawaan | "Jangan tambah Kotlin Gradle plugin — AGP 9 sudah punya dukungan Kotlin bawaan." |
| Menulis `compileSdk = 37` | DSL AGP 9 berbeda dari kebanyakan dokumentasi | "DSL-nya `compileSdk { version = release(37) }`." |
| Mengedit `proguard-rules.pro` / `isMinifyEnabled` | Tidak ada di setup ini | "Keep rules R8 ada di `app/src/main/keepRules/`." |
| Hardcode koordinat dependency di `build.gradle.kts` | — | "Dependency hanya dari `gradle/libs.versions.toml`." |
| Wire model / tipe Retrofit bocor ke public API `:core:data` | `implementation` vs `api` | "Constructor yang menerima tipe network harus `internal`." |
| Menambah `repositories { }` di `build.gradle.kts` module | `FAIL_ON_PROJECT_REPOS` | "Repository hanya di `settings.gradle.kts`." |

## Lihat juga

- [`example-post-list.md`](example-post-list.md) — template ini dalam bentuk terisi penuh.
- [`modules/`](modules/) — versi terpisah per module, satu file per layer. Pakai itu
  kalau feature-nya besar atau Anda ingin mengunci desain tiap layer sebelum lanjut;
  satu module = satu sesi = context window yang bersih.
- [`README.md`](README.md) — panduan memilih antara satu prompt vs per module.
