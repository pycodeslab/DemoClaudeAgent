# Prompt Manual 3 — Sambungkan Login ke `AuthRepository` (tanpa skill)

Sesi **ketiga** dari tiga. Versi mandiri dari [`../skills/03-wire-feature-to-data-login.md`](../skills/03-wire-feature-to-data-login.md):
tidak memanggil `/wire-feature-to-data`, jadi empat langkah, kerangka kode, aturan batas, dan
checklist-nya ditulis langsung di sini. `/clear` dulu.

Prompt 1 membangun layar sampai berhenti di seam. Prompt 2 membangun repository sampai berhenti
di seam. Keduanya sengaja tidak menutup celah di antaranya — **prompt inilah yang menutupnya.**

---

````text
Sambungkan LoginViewModel di :feature ke AuthRepository di :core:data.

## Langkah 0 — baca dulu, jangan menebak

1. @CLAUDE.md.
2. Contoh join yang SUDAH jadi dan sudah lolos build — baca ketiga file ini dan tiru bentuknya:
   - feature/src/main/java/com/sample/demo/feature/postlist/PostListUiState.kt (mapper domain → UI)
   - feature/src/main/java/com/sample/demo/feature/postlist/PostListViewModel.kt (repository + Factory)
   - feature/src/test/java/com/sample/demo/feature/postlist/PostListViewModelTest.kt (fake tulisan tangan)
3. LIHAT KEDUA SISI yang akan disambung:

       ls core/data/src/main/java/com/sample/demo/core/data/repository/
       ls core/common/src/main/java/com/sample/demo/core/common/

   Aturan keputusannya:

   | AuthRepository ada? | :core:common punya Result? | Lakukan |
   | --- | --- | --- |
   | tidak | — | JANGAN menyambung apa pun. Berhenti, laporkan bahwa repository-nya belum ada. |
   | ya | tidak | Kondisi repo hari ini. Ikuti empat langkah di bawah; try/catch bertahan di satu titik. |
   | ya | ya | Empat langkah yang sama, tapi load() branching di atas Result dan try/catch dihapus. |

Sisi :feature: paket com.sample.demo.feature.login dengan seam
`submitLogin: suspend (String, String) -> SessionUiModel?`.
Sisi :core:data: `suspend fun login(username: String, email: String): Session?` plus factory
function publik `AuthRepository()`.

Laporkan hasil langkah 0 sebelum mengubah file pertama.

## File yang disentuh (persis ini)

    feature/src/main/java/com/sample/demo/feature/login/LoginUiState.kt     (+ mapper)
    feature/src/main/java/com/sample/demo/feature/login/LoginViewModel.kt   (seam → repository)
    feature/src/test/java/com/sample/demo/feature/login/LoginViewModelTest.kt (ditulis ulang)

Tidak ada file baru, tidak ada perubahan build script, tidak ada perubahan di :core:data.

## Empat langkah, urutannya penting

Mapper duluan karena dia yang menyelesaikan ketidakcocokan tipe. Factory paling akhir karena dia
yang membuat wiring ini nyata bagi aplikasi yang berjalan.

### 1. Mapper domain → UI, di :feature

Kedua model memang tidak sejajar — itu justru intinya. Session.userId adalah Int karena begitu
kata domain; SessionUiModel.id adalah Long karena begitu kebutuhan UI. Semua konversi dan
pemformatan tinggal di SATU extension internal, di sebelah UI model yang dihasilkannya:

    // feature/login/LoginUiState.kt
    import com.sample.demo.core.data.model.Session

    /**
     * Satu-satunya tempat bentuk domain dan bentuk layar ini didamaikan.
     *
     * Di sini, bukan di util/ (itu bebas framework DAN bebas layar) dan bukan di
     * :core:data/mapper/ (paket itu khusus Dto → domain; UI model di sana membalik dependency).
     */
    internal fun Session.toUiModel(): SessionUiModel = SessionUiModel(
        id = userId.toLong(),     // domain bilang Int; UI butuh Long
        displayName = username,
        email = email,
    )

### 2. Ganti seam dengan repository

    class LoginViewModel(
        // Ambil INTERFACE-nya, default-nya FACTORY FUNCTION publik.
        private val repository: AuthRepository = AuthRepository(),
    ) : ViewModel() {

        private fun login() {
            val state = _uiState.value
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            viewModelScope.launch {
                // try/catch tetap ada HANYA karena :core:common belum punya Result.
                try {
                    val session = repository.login(state.username, state.email)?.toUiModel()
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
    }

Seam lamanya HILANG — jangan menyimpan keduanya. Lambda yang cuma meneruskan ke repository
adalah indireksi tanpa guna. Jangan pernah menyebut AuthRepositoryImpl: dia internal di
:core:data (jadi ini dipaksakan compiler), dan menyebutnya berarti :feature tahu bagaimana
repository dirakit.

### 3. Periksa Factory — langkah yang paling sering dilewati diam-diam

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel() }
        }
    }

Kalau default argument-nya sudah repository asli, baris ini memang tidak perlu diubah — TAPI
BUKA dan pastikan sendiri, lalu laporkan hasil pemeriksaannya. Kalau ViewModel butuh sesuatu
yang tidak bisa disuplai default, Factory-lah tempat merakitnya. Melewatkan langkah ini gagalnya
senyap: semua test hijau lewat fake, sementara aplikasi yang berjalan tidak pernah memanggil
repository.

### 4. Tulis ulang test dengan fake buatan tangan

Lambda yang dulu disuplai test sudah tidak ada. Tulis fake-nya sendiri — TIDAK ADA mocking
library di repo ini dan tidak boleh ditambah:

    private class FakeAuthRepository(
        private val session: Session? = null,
        private val error: Throwable? = null,
    ) : AuthRepository {
        var loginCount = 0
            private set
        var lastUsername: String? = null
            private set
        var lastEmail: String? = null
            private set

        override suspend fun login(username: String, email: String): Session? {
            loginCount++
            lastUsername = username
            lastEmail = email
            error?.let { throw it }
            return session
        }
    }

Pertahankan SEMUA cabang yang sudah ada — sukses, null, exception, perubahan field,
ErrorDismissed — lalu TAMBAHKAN dua test:

    @Test
    fun `session domain dipetakan ke UI model`() = runTest {
        val viewModel = LoginViewModel(FakeAuthRepository(Session(7, "rina", "rina@mail.com")))

        viewModel.onEvent(LoginEvent.LoginClicked)
        val model = viewModel.uiState.value.session!!

        assertEquals(7L, model.id)              // Int -> Long: satu-satunya penjaga konversi ini
        assertEquals("rina", model.displayName)
    }

    @Test
    fun `username dan email dari state diteruskan ke repository`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)

        viewModel.onEvent(LoginEvent.UsernameChanged("rina"))
        viewModel.onEvent(LoginEvent.EmailChanged("rina@mail.com"))
        viewModel.onEvent(LoginEvent.LoginClicked)

        assertEquals("rina", repository.lastUsername)
        assertEquals("rina@mail.com", repository.lastEmail)
    }

:feature sudah punya implementation(project(":core:data")), jadi fake ini ter-compile tanpa
mengubah build sama sekali.

## Batas yang harus tetap utuh setelah wiring

Wiring adalah tempat module graph yang rapi biasanya mulai bocor. Periksa dengan MEMBACA —
Gradle tidak akan menangkap satu pun dari ini:

- :feature hanya menyebut interface repository, factory function-nya, dan model domain. Tidak
  ada Dto, tidak ada tipe Retrofit. Cek IMPORT-nya, bukan sekadar teks:
  `grep -rn "^import com.sample.demo.core.network" feature/src/` harus kosong. (grep polos
  "core.network" juga kena KDoc yang memang kita tulis, jadi hasilnya menyesatkan.)
- LoginUiState TIDAK boleh memegang Session. Tetap SessionUiModel. Menaruh domain model di state
  memang menghemat mapper hari ini, dan mengelas layar ke bentuk domain selamanya.
- Session tidak boleh tumbuh field UI (teks terformat, label tampilan). Kebutuhan seperti itu
  adalah keluaran mapper.
- Tidak ada dependency baru di feature/build.gradle.kts. Kalau merasa butuh, berarti mapping
  sedang dikerjakan di module yang salah.
- Mapping tidak boleh masuk ke Composable; Screen menerima UI model yang sudah jadi.
- Tetap TANPA VALIDASI: wiring ini tidak boleh diam-diam menambah cek email atau field kosong.

## Hasil yang jujur — dan jangan "diperbaiki"

Setelah tersambung, menekan tombol Masuk TIDAK menghasilkan sesi: AuthRepositoryImpl punya seam
sendiri yang mengembalikan null selama :core:network belum punya AuthApi. Layar menampilkan
pesan "layanan belum tersedia" milik :feature. Itu ujung yang benar dari rantai yang nyata.

JANGAN membuat repository mengembalikan Session contoh supaya kelihatan berhasil. Itu fabrikasi
yang justru dicegah dua prompt sebelumnya, dan letaknya di :core:data — tempat paling sulit
ditemukan kembali nanti.

## Kalau nanti :core:common punya Result

Yang berubah hanya: fungsi login() menukar try/catch dengan
`when (val result = repository.login(...))`, dan test kegagalan membentuk Result.Error alih-alih
melempar. Yang TIDAK berubah: mapper, Factory, bentuk UiState, sealed event, dan Screen. Kalau
migrasi Result mendorong Anda menyentuh itu semua, berarti wiring-nya salah.

## Verifikasi (jalankan sendiri, jangan tanya saya)

1. .\gradlew.bat testDebugUnitTest
2. .\gradlew.bat lintDebug
3. .\gradlew.bat :app:assembleDebug

Kalau ada emulator/device terhubung DAN Anda menulis test Compose di androidTest:
4. .\gradlew.bat :feature:connectedDebugAndroidTest
Jangan mengklaim test instrumented lolos kalau tidak ada device terpasang.

Tunjukkan output perintahnya sebagai bukti. Kalau gagal, perbaiki akar masalahnya.

## Checklist sebelum menyatakan selesai

Jalankan sendiri dan laporkan butir demi butir:

- [ ] mapper `Session.toUiModel()` ada, `internal`, di feature/login/LoginUiState.kt
- [ ] seam `submitLogin` sudah HILANG dari LoginViewModel; tidak ada dua jalur
- [ ] constructor menyebut interface AuthRepository, default `AuthRepository()`; nama
      AuthRepositoryImpl tidak muncul di :feature
- [ ] Factory sudah dibuka dan diperiksa; hasil pemeriksaan dilaporkan
- [ ] test memakai FakeAuthRepository tulisan tangan; tidak ada mocking library
- [ ] ada test pengunci mapper (Int → Long) dan test username/email diteruskan
- [ ] semua cabang lama masih diuji: sukses, null, exception, perubahan field, ErrorDismissed
- [ ] `grep -rn "^import com.sample.demo.core.network" feature/src/` kosong
- [ ] LoginUiState masih memegang SessionUiModel, bukan Session
- [ ] Session tidak bertambah field UI
- [ ] feature/build.gradle.kts tidak berubah
- [ ] tidak ada validasi yang ikut masuk
- [ ] perintah verifikasi hijau, output ditunjukkan

## Laporan akhir

Sebutkan: apa yang tersambung, mapper yang dibuat, hasil pemeriksaan Factory, hasil grep batas
module, alasan layar masih belum bisa login (seam :core:network), dan hasil checklist di atas.
````

---

**Setelah ini:** rantai `:feature → :core:data` tersambung. Sisa seam ada di
`AuthRepositoryImpl.submitLogin`, menunggu `:core:network` — itu pekerjaan
[`../modules/02-core-network.md`](../modules/02-core-network.md).
