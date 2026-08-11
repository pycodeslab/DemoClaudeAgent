# Prompt 2 — Skill `core-data-repository` — `AuthRepository`

Sesi **kedua** dari tiga. Membangun kontrak data untuk login di `:core:data`, berhenti di
seam ke `:core:network`. Jalankan `/clear` sebelum memakai prompt ini — sesi ini tidak perlu
tahu apa-apa soal Compose.

Skill ini **tidak terpicu otomatis** — ketik `/core-data-repository` lebih dulu, baru tempel
prompt di bawah.

---

````text
Buatkan repository AuthRepository di :core:data untuk login.

## Konteks

Baca dulu @CLAUDE.md — aturan toolchain dan konvensi di situ WAJIB diikuti.

Cek sendiri isi core/network/src/main/java/com/sample/demo/core/network/ dan
core/common/src/main/java/com/sample/demo/core/common/ sebelum menulis kode. Per hari ini
keduanya cuma berisi .gitkeep: tidak ada AuthApi, tidak ada Dto, tidak ada safeApiCall,
tidak ada Result. Jadi ini varian "seam" — bangun kontraknya, jangan fabrikasi layer
di bawah.

Contoh yang sudah jadi di module ini: Post + PostRepository + PostRepositoryImpl.
Ikuti bentuk yang sama persis, termasuk gaya factory function publiknya.

:feature sudah punya layar Login (paket com.sample.demo.feature.login) yang menunggu
kontrak ini. Layar itu memakai username dan email, dan menampilkan sesi yang berhasil.
JANGAN menyentuh :feature di sesi ini — penyambungannya sesi berikutnya.

## Yang dibangun

core/data/src/main/java/com/sample/demo/core/data/

- model/Session.kt — domain model publik, dibentuk oleh kebutuhan aplikasi:

      data class Session(
          val userId: Int,
          val username: String,
          val email: String,
      )

- repository/AuthRepository.kt — SELURUH permukaan publik module untuk fitur ini:

      interface AuthRepository {
          suspend fun login(username: String, email: String): Session?
      }

      fun AuthRepository(): AuthRepository = AuthRepositoryImpl()

  Return-nya Session? dan bukan core.common.Result karena :core:common belum ada; null
  berarti "belum ada sesi" — itu bentuk jujur dari "tidak menyuplai apa-apa". Tulis di
  KDoc bahwa ini berubah jadi Result<Session> begitu :core:common punya Result, dan bahwa
  kegagalan sementara ini menyeberang sebagai exception.

- repository/AuthRepositoryImpl.kt — internal, dengan seam sebagai parameter constructor:

      internal class AuthRepositoryImpl(
          // :core:network belum punya AuthApi/AuthDto, jadi tidak ada yang dipanggil dan
          // tidak ada yang disuplai. Saat network landing: ganti parameter ini dengan
          // AuthRemoteDataSource, tambahkan mapper/SessionMapper.kt, dan map DTO → Session
          // di sini.
          private val submitLogin: suspend (String, String) -> Session? = { _, _ -> null },
      ) : AuthRepository

- Test: core/data/src/test/.../repository/AuthRepositoryImplTest.kt

## Di luar scope — jangan dikerjakan

- JANGAN membuat AuthApi, AuthDto, LoginRequest wire model, NetworkModule, atau apa pun
  yang sebenarnya milik :core:network. Termasuk versi "sementara".
- JANGAN mendefinisikan Result, NetworkException, atau DispatcherProvider di sini — itu
  kode :core:common di module yang salah, persis kesalahan yang aturan ini cegah.
- JANGAN menaruh daftar user hardcoded, map in-memory, atau Impl yang pura-pura jadi cache
  supaya login "berhasil". Seam yang mengembalikan null adalah hasil yang benar.
- Jangan menambah dependency di core/data/build.gradle.kts (Room, DataStore, mocking
  library, crypto). Kalau merasa butuh, hentikan dan tanya dulu.
- Tanpa validasi: repository tidak memeriksa format email, tidak menolak string kosong.
- Jangan ada Compose, android.* UI type, atau ViewModel di module ini.
- Jangan menyentuh :feature, :app, atau mapper domain → UI.

## Batas module yang harus tetap utuh

Sebelum selesai, telusuri setiap deklarasi public di :core:data dan pastikan tidak ada
Dto, Response, Retrofit, OkHttp, Call, @SerializedName, atau apa pun dari
com.sample.demo.core.network di signature-nya. Kalau ada, perbaikannya `internal` +
factory publik, BUKAN mengubah :core:network jadi api().

## Verifikasi (jalankan sendiri, jangan tanya saya)

1. .\gradlew.bat :core:data:testDebugUnitTest
2. .\gradlew.bat :core:data:lintDebug
3. .\gradlew.bat :app:assembleDebug

AuthRepositoryImplTest wajib menutup: seam mengembalikan Session → diteruskan apa adanya,
default AuthRepository() mengembalikan null selama :core:network kosong, dan seam yang
melempar exception → exception-nya lolos ke pemanggil selama Result belum ada. Test unit
melihat deklarasi internal di module yang sama, jadi tidak perlu melonggarkan visibility.

Tunjukkan output perintahnya sebagai bukti.

## Laporan akhir

Sebutkan layer mana yang absen, apa yang karena itu tidak dibangun (mapper dan data source),
dan signature publik persis yang dihasilkan — sesi berikutnya butuh itu.
````

---

**Setelah ini:** `/clear`, lalu lanjut ke [`03-wire-feature-to-data-login.md`](03-wire-feature-to-data-login.md).
