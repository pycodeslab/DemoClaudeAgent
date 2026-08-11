# Prompt 1 — Skill `compose-feature-screen` — Layar Login

Sesi **pertama** dari tiga. Membangun layar Login lengkap di `:feature` sampai berhenti di
seam, tanpa menyentuh `:core:data`. Jalankan `/clear` sebelum memakai prompt ini.

Skill terpicu sendiri oleh kalimat pembuka di bawah; kalau tidak, sebut namanya:
`pakai skill compose-feature-screen`.

---

````text
Buatkan screen Login dengan Compose di :feature.

## Konteks

Baca dulu @CLAUDE.md — di situ ada aturan toolchain dan konvensi yang WAJIB diikuti.
Dependency hanya yang sudah ada di @gradle/libs.versions.toml; jangan tambah koordinat baru.

Cek sendiri isi core/data/src/main/java/com/sample/demo/core/data/repository/ dan
core/common/src/main/java/com/sample/demo/core/common/ sebelum menulis kode. Per hari ini
belum ada AuthRepository dan :core:common masih .gitkeep — jadi ini kasus "Missing layers":
bangun layarnya utuh, jangan fabrikasi layer di bawahnya.

Contoh layar yang sudah jadi di repo ini: paket com.sample.demo.feature.postlist. Ikuti
bentuknya (Route → Screen stateless → components, satu UiState, satu sealed event).

## Yang dibangun

Paket com.sample.demo.feature.login:

- LoginUiState.kt
    data class LoginUiState(
        val username: String = "",
        val email: String = "",
        val isSubmitting: Boolean = false,
        val session: SessionUiModel? = null,
        val errorMessage: String? = null,
    )
    data class SessionUiModel(val id: Long, val displayName: String, val email: String)
    sealed interface LoginEvent:
        data class UsernameChanged(val value: String)
        data class EmailChanged(val value: String)
        data object LoginClicked
        data object ErrorDismissed

- LoginViewModel.kt — seluruh business logic + companion object Factory.
  Dependensinya seam, karena :core:data belum punya repository:

      class LoginViewModel(
          // :core:data belum punya AuthRepository, jadi tidak ada yang dipanggil dan
          // tidak ada yang disuplai. Saat repository-nya ada, ganti seam ini lewat skill
          // wire-feature-to-data (skill itu juga yang punya mapper domain → UI).
          private val submitLogin: suspend (String, String) -> SessionUiModel? = { _, _ -> null },
      ) : ViewModel()

  Perilaku LoginClicked: isSubmitting = true → panggil submitLogin(username, email) →
  hasil non-null masuk ke state.session; hasil null artinya layanan login belum tersedia,
  jadi isi errorMessage (teks Indonesia, milik :feature, bukan :core:data). Exception dari
  seam ditangkap di satu titik itu saja — try/catch ini hidup hanya sampai :core:common
  punya Result; tulis catatan itu sebagai komentar.

- LoginScreen.kt — @Composable stateless (uiState, onEvent, modifier). Isinya:
  OutlinedTextField username, OutlinedTextField email, Button "Masuk".
  Tombol SELALU enabled kecuali saat isSubmitting. Saat isSubmitting tampilkan indikator
  loading. Saat errorMessage != null tampilkan pesan + aksi coba lagi. Saat session != null
  tampilkan displayName dan email yang masuk.
  Tambahkan @Preview per kondisi bermakna: kosong, isi, submitting, error, sukses.

- LoginRoute.kt — stateful tipis: viewModel(factory = LoginViewModel.Factory),
  collectAsStateWithLifecycle(), delegasi ke LoginScreen. Tidak ada isi lain.

- LoginActivity.kt — setContent { DemoTheme { LoginRoute() } }, dan dideklarasikan di
  feature/src/main/AndroidManifest.xml TANPA android:exported.

- Test: feature/src/test/.../login/LoginViewModelTest.kt.

Kalau butuh komponen error/empty yang sudah generik (ErrorState/EmptyState hari ini masih
screen-private di postlist/components/), ikuti aturan promosi di skill: PINDAHKAN ke
feature/ui/components/ dan sesuaikan teks default-nya, jangan menyalin salinan kedua.

## Di luar scope — jangan dikerjakan

- JANGAN menambahkan validasi apa pun: tidak ada cek format email, tidak ada cek field
  kosong, tidak ada pesan error per-field, tombol tidak pernah disabled karena isi form.
  Ini permintaan eksplisit, bukan kelalaian.
- Jangan membuat AuthRepository, data source, domain model, DTO, atau API — itu module
  lain. Seam-nya dibiarkan mengembalikan null.
- Jangan pakai data contoh supaya layar "kelihatan jadi". Layar yang belum bisa login
  memang hasil yang jujur di tahap ini.
- Jangan menambah field password, "ingat saya", navigasi setelah sukses, atau penyimpanan
  token/DataStore.
- Jangan menambah dependency di feature/build.gradle.kts, jangan menambah plugin Kotlin,
  jangan mengubah DSL AGP 9, jangan menyentuh XML/view binding yang sudah ada.

## Verifikasi (jalankan sendiri, jangan tanya saya)

1. .\gradlew.bat :feature:testDebugUnitTest
2. .\gradlew.bat :feature:lintDebug
3. .\gradlew.bat :app:assembleDebug

LoginViewModelTest wajib menutup: state awal, LoginClicked saat seam mengembalikan session,
LoginClicked saat seam mengembalikan null (errorMessage terisi, isSubmitting kembali false),
seam yang melempar exception, UsernameChanged/EmailChanged mengubah state, dan ErrorDismissed
membersihkan error tanpa memanggil ulang seam. Test menyuplai lambda langsung — tanpa
mocking library, karena repo ini tidak punya satu pun.

Tunjukkan output perintahnya sebagai bukti, jangan cuma bilang "sudah berhasil".

## Laporan akhir

Sebutkan layer mana yang absen dan apa yang karena itu TIDAK dibangun, plus di mana
persis seam-nya berada supaya sesi berikutnya bisa menutupnya.
````

---

**Setelah ini:** `/clear`, lalu lanjut ke [`02-core-data-repository-login.md`](02-core-data-repository-login.md).
