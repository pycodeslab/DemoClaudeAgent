# Prompt 3 — Skill `wire-feature-to-data` — Sambungkan Login ke `AuthRepository`

Sesi **ketiga** dari tiga. Menutup celah yang sengaja ditinggalkan dua skill sebelumnya.
Jalankan `/clear` sebelum memakai prompt ini.

Skill ini **tidak terpicu otomatis** — ketik `/wire-feature-to-data` lebih dulu, baru tempel
prompt di bawah.

---

````text
Sambungkan LoginViewModel ke AuthRepository di :core:data.

## Konteks

Baca dulu @CLAUDE.md. Lalu lihat KEDUA sisi sebelum menulis kode, jangan diasumsikan:

    ls core/data/src/main/java/com/sample/demo/core/data/repository/
    ls core/common/src/main/java/com/sample/demo/core/common/

Yang seharusnya Anda temukan: AuthRepository + Session sudah ada, :core:common masih
.gitkeep. Itu baris tengah tabel di skill — ViewModel mengambil repository, dan try/catch
di satu titik pemanggilan itu tetap hidup sampai Result ada. Kalau ternyata AuthRepository
belum ada, JANGAN menyambung apa pun: hentikan dan laporkan.

Sisi :feature: paket com.sample.demo.feature.login dengan seam
`submitLogin: suspend (String, String) -> SessionUiModel?`.
Sisi :core:data: `suspend fun login(username: String, email: String): Session?` plus
factory function AuthRepository().

Contoh join yang sudah jadi di repo ini: PostListViewModel ↔ PostRepository.

## Yang dikerjakan, empat langkah berurutan

1. Mapper domain → UI, di :feature, sebagai extension internal di samping UI model-nya
   (feature/login/LoginUiState.kt):

       internal fun Session.toUiModel(): SessionUiModel = SessionUiModel(
           id = userId.toLong(),      // domain bilang Int; UI butuh Long
           displayName = username,
           email = email,
       )

   Bukan di feature/util/ (util bebas framework DAN bebas screen), bukan di
   :core:data/mapper/ (paket itu hanya untuk Dto → domain; UI model di sana membalik
   arah dependency).

2. Ganti seam dengan repository — satu baris, dan seam lamanya HILANG:

       class LoginViewModel(
           private val repository: AuthRepository = AuthRepository(),
       ) : ViewModel()

   Ambil interface-nya, default-nya factory function publik. Jangan pernah menyebut
   AuthRepositoryImpl. Jangan menyimpan lambda lama sebagai perantara.

3. Periksa companion object Factory. Kalau default argument sudah repository asli,
   initializer { LoginViewModel() } memang tidak perlu diubah — tapi BUKA dan pastikan.
   Melewatkan langkah ini gagalnya diam-diam: semua test hijau lewat fake, sementara
   aplikasi yang berjalan tidak pernah memanggil repository.

4. Tulis ulang test dengan fake buatan tangan (tidak ada mocking library di repo ini,
   dan tidak boleh ditambah):

       private class FakeAuthRepository(
           private val session: Session? = null,
           private val error: Throwable? = null,
       ) : AuthRepository {
           var loginCount = 0
               private set
           override suspend fun login(username: String, email: String): Session? { … }
       }

   Pertahankan semua cabang yang sudah ada — sukses, null, exception, perubahan field,
   ErrorDismissed — dan TAMBAHKAN satu test yang mengunci mapper: Session masuk,
   SessionUiModel yang diharapkan keluar (khususnya konversi Int → Long). Tambahkan juga
   satu test bahwa username dan email dari state benar-benar diteruskan ke repository.

## Hasil yang jujur, dan jangan "diperbaiki"

Setelah tersambung, menekan tombol Masuk TIDAK akan menghasilkan sesi: AuthRepositoryImpl
punya seam sendiri yang mengembalikan null selama :core:network belum punya AuthApi.
Layar akan menampilkan pesan "layanan belum tersedia" milik :feature. Itu ujung yang benar
dari rantai yang nyata. JANGAN membuat repository mengembalikan Session contoh supaya
kelihatan berhasil — itu fabrikasi yang justru dicegah dua skill sebelumnya, dan letaknya
di :core:data, tempat paling sulit ditemukan kembali nanti.

## Batas yang harus tetap utuh setelah wiring

Periksa dengan membaca; Gradle tidak akan menangkap satu pun dari ini:

- :feature hanya menyebut interface repository, factory function-nya, dan domain model.
  Cek import, bukan sekadar teks: grep -rn "^import com.sample.demo.core.network" feature/src/
  harus kosong.
- LoginUiState TIDAK boleh memegang Session (domain). Tetap SessionUiModel.
- Session tidak boleh tumbuh field UI (teks terformat, label tampilan).
- Tidak ada dependency baru di feature/build.gradle.kts — project(":core:data") sudah ada.
- Mapping tidak boleh masuk ke Composable; Screen menerima UI model yang sudah jadi.
- Tetap tanpa validasi: wiring ini tidak boleh diam-diam menambah cek email/field kosong.

## Verifikasi (jalankan sendiri, jangan tanya saya)

1. .\gradlew.bat testDebugUnitTest
2. .\gradlew.bat lintDebug
3. .\gradlew.bat :app:assembleDebug

Kalau ada emulator/device terhubung dan Anda menulis test Compose di androidTest:
4. .\gradlew.bat :feature:connectedDebugAndroidTest
Jangan mengklaim test instrumented lolos kalau tidak ada device yang terpasang.

Tunjukkan output perintahnya sebagai bukti.

## Laporan akhir

Sebutkan: apa yang tersambung, mapper mana yang dibuat, hasil pemeriksaan Factory, hasil
grep batas module, dan alasan layar masih belum bisa login (seam :core:network).
````

---

**Setelah ini:** rantai `:feature → :core:data` sudah tersambung. Sisa seam ada di
`AuthRepositoryImpl.submitLogin`, menunggu `:core:network` — itu pekerjaan
[`../modules/02-core-network.md`](../modules/02-core-network.md), bukan ketiga skill di atas.
