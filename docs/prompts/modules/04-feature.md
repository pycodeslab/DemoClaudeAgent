# Prompt — `:feature`

**Satu-satunya module dengan UI.** Activity, ViewModel, state, adapter, dan layout.

| | |
| --- | --- |
| Namespace | `com.sample.demo.feature` |
| Source dir | `feature/src/main/java/com/sample/demo/feature/` |
| Depends on | `implementation(:core:data)` → transitif melihat `:core:common` |
| Library tersedia | `appcompat`, `core-ktx`, `activity-ktx`, `constraintlayout`, `recyclerview`, `lifecycle-viewmodel-ktx`, `lifecycle-runtime-ktx`, `coroutines-android`, `material` |
| Khusus module ini | `viewBinding = true` sudah aktif |
| Verifikasi | `.\gradlew.bat :feature:testDebugUnitTest` lalu `.\gradlew.bat :app:assembleDebug` |

Retrofit dan Gson **tidak** ada di classpath module ini — itu memang tujuannya.
Kalau muncul error "unresolved reference" ke tipe network di sini, jawabannya bukan
menambah dependency, melainkan memperbaiki public API `:core:data`.

Module ini belum punya folder `res/`; layout pertama akan membuatnya. Theme
`Theme.DemoClaudeAgent` ada di `:app` dan **tidak** terlihat dari sini — karena itu
layout harus mereferensikan atribut theme (`?attr/colorPrimary`), bukan style konkret.

---

## PROMPT

````text
Implementasikan layar <NAMA_LAYAR> di module :feature.

## Konteks

Baca @CLAUDE.md — bagian "Conventions". Prasyarat: :core:data sudah meng-expose
`<X>Repository` dengan `suspend fun get<X>s(): Result<List<<X>>>` dan model domain `<X>`.

Module ini TIDAK punya Retrofit/Gson di classpath, dan itu disengaja. Kalau butuh
sesuatu dari network, ambil lewat `<X>Repository` — jangan menambah dependency.

`viewBinding = true` sudah aktif, jadi prompt ini menghasilkan layar berbasis XML.

> **Catatan:** Compose sekarang **sudah aktif** di `:feature` (BOM, Material 3, dan plugin
> compiler-nya sudah terpasang). Kalau layarnya mau dibuat dengan Compose, jangan pakai file
> ini — pakai skill `compose-feature-screen`, yang sudah memuat layering Route/Screen/component
> dan panduan API guidelines-nya. File ini tetap dipakai untuk layar XML.

## Yang dibangun

com.sample.demo.feature.<paket>:

1. `<X>UiState` — SATU data class untuk seluruh layar:

       data class <X>UiState(
           val isLoading: Boolean = false,
           val items: List<<X>> = emptyList(),
           val errorMessage: String? = null,   // atau resource id / tipe error
       )

   Kalau nanti butuh state baru (refreshing, selectedId, dsb.), TAMBAHKAN FIELD
   di sini — jangan membuat StateFlow kedua.

2. `<X>ViewModel` —
   - `private val _uiState = MutableStateFlow(<X>UiState())` dan
     `val uiState: StateFlow<<X>UiState> = _uiState.asStateFlow()`.
   - Constructor: `class <X>ViewModel(private val repository: <X>Repository = <X>Repository())`
     — default argument, tanpa DI framework.
   - Muat data di `init` atau lewat fungsi `load()` publik yang bisa dipanggil ulang
     oleh tombol Retry.
   - BRANCH di atas `Result` (`fold`/`when`), bukan try/catch.
   - Sediakan `<X>ViewModel.Factory` (`ViewModelProvider.Factory`) eksplisit.

3. `<X>Activity` —
   - `AppCompatActivity` dengan view binding.
   - Koleksi state di `lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { ... } }`.
     Bukan `collect` langsung di `onCreate` tanpa repeatOnLifecycle.
   - Render satu state object jadi visibilitas view: loading / success / empty / error.
   - Tombol Retry memanggil `viewModel.load()`.

4. `<X>Adapter` — `ListAdapter` + `DiffUtil.ItemCallback`, view binding di ViewHolder.

5. Layout di feature/src/main/res/layout/ (folder res belum ada, buat baru):
   `activity_<x>.xml` dan `item_<x>.xml`.
   - Pakai ConstraintLayout dan komponen Material yang sudah tersedia.
   - Warna dan style HARUS lewat atribut theme (`?attr/colorPrimary`,
     `?attr/textAppearanceBodyMedium`, dst.) karena Theme.DemoClaudeAgent ada di :app
     dan tidak terlihat dari module ini.
   - Tanpa string hardcoded — taruh di feature/src/main/res/values/strings.xml.

6. feature/src/main/AndroidManifest.xml — deklarasikan `<X>Activity`
   TANPA `android:exported` dan TANPA intent-filter. Yang menjadikannya launcher
   adalah :app, bukan module ini.

## Aturan

- Satu state object per layar, di-expose sebagai StateFlow, dikoleksi dengan
  repeatOnLifecycle(STARTED).
- Views + XML dengan view binding. Tanpa Compose, tanpa data binding.
- Tanpa DI framework: default constructor argument + ViewModelProvider.Factory eksplisit.
- Jangan mendeklarasikan permission INTERNET — sudah ada di :core:network dan akan
  ter-merge sendiri.
- Jangan menambah dependency ke feature/build.gradle.kts.

## Verifikasi (jalankan sendiri)

1. .\gradlew.bat :feature:testDebugUnitTest
2. .\gradlew.bat :app:assembleDebug     ← wajib; ini yang membuktikan layout dan
                                          manifest ikut ter-merge dengan benar

Tulis unit test di feature/src/test/java/com/sample/demo/feature/ untuk
`<X>ViewModelTest` — pakai fake `<X>Repository` (implementasi interface langsung,
tanpa mocking library) dan `kotlinx-coroutines-test`:
- urutan state loading → success (items terisi, errorMessage null)
- urutan state loading → error (errorMessage terisi, isLoading false)
- `load()` yang dipanggil ulang setelah gagal menghasilkan success

Suntik dispatcher lewat DispatcherProvider dari :core:common. ViewModel tidak boleh
menyentuh Android framework di jalur yang diuji, supaya test tetap JVM murni
(tanpa Robolectric — belum ada di libs.versions.toml).

Tunjukkan output kedua perintah sebagai bukti. Kalau gagal, perbaiki akar masalahnya.

## Di luar scope

- Jangan menambah Compose, Hilt/Koin, Navigation component, atau Glide/Coil.
- Jangan menambah intent-filter MAIN/LAUNCHER di sini — itu tugas :app.
- Jangan memindahkan Theme.DemoClaudeAgent ke module ini.
- Jangan menyentuh module lain.
````

---

## Setelah selesai

Lanjut ke [`05-app.md`](05-app.md).
