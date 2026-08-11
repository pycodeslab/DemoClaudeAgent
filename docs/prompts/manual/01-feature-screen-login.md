# Prompt Manual 1 — Layar Login di `:feature` (tanpa skill)

Sesi **pertama** dari tiga. Versi mandiri dari [`../skills/01-compose-feature-screen-login.md`](../skills/01-compose-feature-screen-login.md):
tidak memanggil `/compose-feature-screen`, jadi semua yang biasanya dibawa skill itu — aturan,
kerangka kode, dan checklist review — ditulis langsung di prompt. Tempel apa adanya di sesi baru
(`/clear` dulu).

---

````text
Bangun layar Login dengan Jetpack Compose di module :feature.

## Langkah 0 — baca dulu, jangan menebak

1. @CLAUDE.md — aturan toolchain dan konvensi. Ini menang kalau bertentangan dengan prompt ini.
2. @gradle/libs.versions.toml — dependency yang boleh dipakai. Jangan menambah koordinat baru.
3. Contoh layar yang SUDAH jadi dan sudah lolos build — baca keenam file ini dan tiru struktur,
   penamaan, gaya KDoc, serta pembagian filenya:
   - feature/src/main/java/com/sample/demo/feature/postlist/PostListUiState.kt
   - feature/src/main/java/com/sample/demo/feature/postlist/PostListViewModel.kt
   - feature/src/main/java/com/sample/demo/feature/postlist/PostListScreen.kt
   - feature/src/main/java/com/sample/demo/feature/postlist/PostListRoute.kt
   - feature/src/main/java/com/sample/demo/feature/postlist/PostListActivity.kt
   - feature/src/test/java/com/sample/demo/feature/postlist/PostListViewModelTest.kt
4. Periksa layer bawah dengan ls, jangan diasumsikan:
   - core/data/src/main/java/com/sample/demo/core/data/repository/
   - core/common/src/main/java/com/sample/demo/core/common/
   Per hari ini belum ada AuthRepository dan :core:common cuma .gitkeep. Kalau ternyata sudah
   ada, BERHENTI dan beri tahu saya — prompt ini ditulis untuk keadaan "layer bawah belum ada".

Laporkan hasil langkah 0 sebelum menulis file pertama.

## File yang dibuat (persis ini, tidak kurang tidak lebih)

    feature/src/main/java/com/sample/demo/feature/login/
    ├── LoginActivity.kt
    ├── LoginRoute.kt
    ├── LoginScreen.kt
    ├── LoginUiState.kt
    └── LoginViewModel.kt
    feature/src/test/java/com/sample/demo/feature/login/LoginViewModelTest.kt
    feature/src/main/AndroidManifest.xml   (tambah satu baris <activity>)

Belum perlu login/components/ — buat hanya kalau ada komponen yang benar-benar dipakai ulang
di layar ini, dan ikuti aturan komponen di bawah kalau membuatnya.

## Kerangka kode

Ini bentuk yang harus diikuti, bukan kode final. Hapus yang tidak dipakai, lengkapi yang perlu.

### LoginUiState.kt

    package com.sample.demo.feature.login

    /** Semua yang dibutuhkan [LoginScreen] untuk render, dalam satu objek. */
    data class LoginUiState(
        val username: String = "",
        val email: String = "",
        val isSubmitting: Boolean = false,
        val session: SessionUiModel? = null,
        val errorMessage: String? = null,
    ) {
        /** Turunan, bukan disimpan — salinan kedua bisa berselisih dengan [session]. */
        val isSignedIn: Boolean get() = session != null
    }

    /** Sesi sebagaimana layar ini menampilkannya: sudah diformat, tanpa tipe domain/wire. */
    data class SessionUiModel(
        val id: Long,
        val displayName: String,
        val email: String,
    )

    /** Setiap niat pengguna di layar ini. Satu onEvent menjaga signature Screen tetap stabil. */
    sealed interface LoginEvent {
        data class UsernameChanged(val value: String) : LoginEvent
        data class EmailChanged(val value: String) : LoginEvent
        data object LoginClicked : LoginEvent
        data object ErrorDismissed : LoginEvent
    }

### LoginViewModel.kt

    class LoginViewModel(
        // :core:data belum punya AuthRepository, jadi tidak ada yang dipanggil dan tidak ada
        // yang disuplai. Saat repository-nya ada, seam ini diganti repository + mapper.
        private val submitLogin: suspend (String, String) -> SessionUiModel? = { _, _ -> null },
    ) : ViewModel() {

        private val _uiState = MutableStateFlow(LoginUiState())
        val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

        fun onEvent(event: LoginEvent) {
            when (event) {
                is LoginEvent.UsernameChanged -> _uiState.update { it.copy(username = event.value) }
                is LoginEvent.EmailChanged -> _uiState.update { it.copy(email = event.value) }
                LoginEvent.LoginClicked -> login()
                LoginEvent.ErrorDismissed -> _uiState.update { it.copy(errorMessage = null) }
            }
        }

        private fun login() {
            val state = _uiState.value                 // tanpa validasi: kirim apa adanya
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            viewModelScope.launch {
                // try/catch hanya karena :core:common belum punya Result; begitu ada, ini
                // menjadi `when (result)` dan try/catch dihapus.
                try {
                    val session = submitLogin(state.username, state.email)
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            session = session,
                            errorMessage = if (session == null) "<pesan layanan belum tersedia>" else null,
                        )
                    }
                } catch (error: Exception) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            session = null,
                            errorMessage = error.message ?: "Terjadi kesalahan.",
                        )
                    }
                }
            }
        }

        companion object {
            // Tanpa DI framework: default argument + Factory eksplisit.
            val Factory: ViewModelProvider.Factory = viewModelFactory {
                initializer { LoginViewModel() }
            }
        }
    }

### LoginScreen.kt

    @Composable
    fun LoginScreen(
        uiState: LoginUiState,
        onEvent: (LoginEvent) -> Unit,
        modifier: Modifier = Modifier,      // opsional PERTAMA, dipakai sekali di root
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            OutlinedTextField(
                value = uiState.username,
                onValueChange = { onEvent(LoginEvent.UsernameChanged(it)) },
                label = { Text("Username") },
                singleLine = true,
                enabled = !uiState.isSubmitting,
            )
            OutlinedTextField(
                value = uiState.email,
                onValueChange = { onEvent(LoginEvent.EmailChanged(it)) },
                label = { Text("Email") },
                singleLine = true,
                enabled = !uiState.isSubmitting,
            )
            Button(
                onClick = { onEvent(LoginEvent.LoginClicked) },
                enabled = !uiState.isSubmitting,   // HANYA karena sedang mengirim
            ) { Text("Masuk") }

            when {
                uiState.isSubmitting -> CircularProgressIndicator(…)
                uiState.errorMessage != null -> <pesan + aksi coba lagi memanggil LoginClicked
                                                 atau ErrorDismissed>
                uiState.session != null -> <tampilkan displayName dan email>
            }
        }
    }

    // Satu @Preview per kondisi bermakna, semuanya dari state literal:
    // kosong, terisi, isSubmitting, error, sukses. Tanpa ViewModel, tanpa LaunchedEffect.
    @Preview(showBackground = true, name = "Error")
    @Composable
    private fun LoginScreenErrorPreview() {
        DemoTheme(dynamicColor = false) {
            LoginScreen(uiState = LoginUiState(errorMessage = "…"), onEvent = {})
        }
    }

### LoginRoute.kt

    @Composable
    fun LoginRoute(
        modifier: Modifier = Modifier,
        viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
    ) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        LoginScreen(uiState = uiState, onEvent = viewModel::onEvent, modifier = modifier)
    }

Tidak ada isi lain di Route. Itu saja.

### LoginActivity.kt + manifest

    class LoginActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent { DemoTheme { LoginRoute() } }
        }
    }

feature/src/main/AndroidManifest.xml — TANPA android:exported:

    <activity android:name=".login.LoginActivity" />

### LoginViewModelTest.kt

    class LoginViewModelTest {

        @get:Rule
        val mainDispatcherRule = MainDispatcherRule()   // sudah ada di feature/src/test/…/feature/

        @Test
        fun `seam default tidak menghasilkan sesi`() = runTest {
            val viewModel = LoginViewModel()
            viewModel.onEvent(LoginEvent.LoginClicked)
            …
        }
    }

Test menyuplai lambda seam langsung: `LoginViewModel(submitLogin = { _, _ -> session })`,
`{ _, _ -> null }`, `{ _, _ -> throw IOException("…") }`. TIDAK ADA mocking library di repo ini
dan tidak boleh ditambah.

## Aturan layering yang wajib dipatuhi

1. UiState: SATU data class immutable per layar, di-expose sebagai StateFlow. Nilai turunan
   ditulis `val ... get()`. Butuh informasi baru → TAMBAH FIELD, jangan bikin StateFlow kedua.
2. Event: satu sealed interface; Screen hanya menerima satu `onEvent: (LoginEvent) -> Unit`.
3. ViewModel: SATU-SATUNYA tempat business logic. Tanpa tipe framework Android (Context, View)
   di signature. Semua perubahan state lewat `_uiState.update { … }`; kerja suspend di
   `viewModelScope`.
4. Screen stateless: tanpa referensi ViewModel, tanpa `remember` untuk state bisnis.
5. Route: hanya ambil ViewModel, collect, delegasi.

## Aturan komponen Compose (subset panduan AndroidX yang paling sering dilanggar)

- `modifier: Modifier = Modifier` — tepat satu, parameter opsional PERTAMA, dipakai SEKALI di
  layout paling luar. Jangan `rowModifier`/`iconModifier`, jangan diteruskan ke anak, jangan
  default `Modifier.padding(8.dp)`.
- Urutan parameter: wajib → modifier → opsional → trailing @Composable lambda.
- JANGAN menerima `MutableState<T>` atau `State<T>`. Terima nilai + callback.
- Nilai default harus PUBLIK, dikelompokkan di `object <Component>Defaults`.
- `null` berarti "tidak ada", bukan "pakai default".
- Utamakan slot @Composable daripada parameter String/resource untuk konten bebas.
- Komponen yang dibaca sebagai satu unit: `Modifier.semantics(mergeDescendants = true) {}`.
- Preview render dari state literal, tanpa LaunchedEffect atau kerja async.

## Layer bawah belum ada: bangun layarnya, JANGAN fabrikasi

Jangan membuat repository, data source, DTO, API, model domain, atau daftar user — itu kode
module lain yang kalau ditulis di sini akan ke-commit dan harus dibongkar lagi nanti.

Kenapa seam-nya tipe fungsi dan bukan interface yang dideklarasikan di :feature: interface +
implementasi ITU data layer, cuma di module yang salah. Tipe fungsi tidak menambah tipe baru,
semua cabang state machine tetap bisa diuji, dan nanti runtuh jadi satu baris.

Default seam TIDAK menyuplai data (`{ _, _ -> null }`), bukan sesi contoh. SessionUiModel milik
:feature — UI model memang urusan layar, itu bukan fabrikasi domain.

## Tempat kode bersama

- @Composable yang dipakai lebih dari satu layar → feature/ui/components/
- Fungsi non-UI yang dipakai lebih dari satu layar → feature/util/ (murni Kotlin: tanpa
  @Composable, tanpa androidx.compose.*, tanpa android.*, tanpa Context/View)
- Helper sadar-Compose yang tidak meng-emit UI → feature/ui/

Komponen yang sudah generik dan dibutuhkan layar kedua DIPINDAHKAN ke ui/components/, bukan
disalin, dan teks default yang khas satu layar digeneralkan saat dipindah.

## Toolchain (jangan "dimodernkan")

- Compose sudah aktif di :feature. JANGAN menambah plugin Kotlin apa pun, termasuk
  org.jetbrains.kotlin.android — AGP 9 sudah menyediakan Kotlin.
- Artefak Compose ambil versi dari BOM — jangan menambah version.ref pada entry compose-*.
- Material 3. Jangan mengimpor androidx.compose.material.*.
- minSdk 24: pakai DemoTheme yang sudah ada (sudah menjaga dynamicColor API 31+); jangan bikin
  theme kedua.
- Jangan menyentuh DSL AGP 9 dan jangan menambah blok repositories {} di module.

## Di luar scope — jangan dikerjakan

- JANGAN menambahkan validasi apa pun: tidak ada cek format email, tidak ada cek field kosong,
  tidak ada pesan error per-field, tombol tidak pernah disabled karena isi form. Ini permintaan
  eksplisit, bukan kelalaian.
- Jangan membuat repository/data source/DTO/API/model domain.
- Jangan memberi data contoh supaya layar "kelihatan jadi".
- Jangan menambah field password, "ingat saya", navigasi setelah sukses, penyimpanan token,
  DataStore, atau enkripsi.
- Jangan menambah dependency di feature/build.gradle.kts.
- Jangan mengubah layar XML/view binding yang sudah ada.

## Verifikasi (jalankan sendiri, jangan tanya saya)

1. .\gradlew.bat :feature:testDebugUnitTest
2. .\gradlew.bat :feature:lintDebug
3. .\gradlew.bat :app:assembleDebug

LoginViewModelTest wajib menutup: state awal; UsernameChanged dan EmailChanged mengubah state;
LoginClicked saat seam mengembalikan SessionUiModel; LoginClicked saat seam mengembalikan null
(errorMessage terisi, isSubmitting kembali false); seam melempar exception (pesannya muncul di
state); ErrorDismissed membersihkan error TANPA memanggil ulang seam; username dan email dari
state benar-benar diteruskan ke seam.

Tunjukkan output ketiga perintah sebagai bukti, jangan cuma bilang "sudah berhasil". Kalau
gagal, perbaiki akar masalahnya — jangan melonggarkan test atau men-suppress lint.

## Checklist sebelum menyatakan selesai

Jalankan sendiri daftar ini dan laporkan hasilnya butir demi butir:

- [ ] enam file di atas dibuat, tidak ada file lain di luar daftar
- [ ] tidak ada repository/DTO/API/model domain yang dibuat di :feature
- [ ] seam default `{ _, _ -> null }`, tanpa data contoh
- [ ] LoginUiState satu-satunya sumber kebenaran; tidak ada StateFlow kedua
- [ ] LoginScreen tanpa referensi ViewModel; semua interaksi lewat onEvent
- [ ] tepat satu `modifier: Modifier = Modifier` per Composable publik, opsional pertama,
      dipakai sekali di layout root
- [ ] tidak ada parameter MutableState/State
- [ ] @Preview ada untuk kosong, terisi, submitting, error, sukses — semuanya state literal
- [ ] LoginActivity dideklarasikan di manifest :feature tanpa android:exported
- [ ] tidak ada validasi di mana pun
- [ ] feature/build.gradle.kts tidak berubah
- [ ] ketiga perintah verifikasi hijau, output ditunjukkan

## Laporan akhir

Sebutkan: file yang dibuat, layer mana yang absen dan apa yang karena itu TIDAK dibangun,
posisi persis seam-nya, keputusan desain yang diambil, dan hasil checklist di atas.
````

---

**Setelah ini:** `/clear`, lalu [`02-core-data-login.md`](02-core-data-login.md).
