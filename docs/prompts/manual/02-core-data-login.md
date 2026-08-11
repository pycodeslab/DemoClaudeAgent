# Prompt Manual 2 — `AuthRepository` di `:core:data` (tanpa skill)

Sesi **kedua** dari tiga. Versi mandiri dari [`../skills/02-core-data-repository-login.md`](../skills/02-core-data-repository-login.md):
tidak memanggil `/core-data-repository`, jadi aturan batas module, kerangka kode, dan checklist
review-nya ditulis langsung di sini. `/clear` dulu — sesi ini tidak perlu tahu apa pun soal Compose.

---

````text
Bangun kontrak data login di module :core:data.

## Langkah 0 — baca dulu, jangan menebak

1. @CLAUDE.md — aturan toolchain dan konvensi. Ini menang kalau bertentangan dengan prompt ini.
2. Contoh yang SUDAH jadi di module ini dan sudah lolos build — baca keempat file ini dan tiru
   struktur, penamaan, gaya KDoc, dan pembagian filenya:
   - core/data/src/main/java/com/sample/demo/core/data/model/Post.kt
   - core/data/src/main/java/com/sample/demo/core/data/repository/PostRepository.kt
   - core/data/src/main/java/com/sample/demo/core/data/repository/PostRepositoryImpl.kt
   - core/data/src/test/java/com/sample/demo/core/data/repository/PostRepositoryImplTest.kt
3. core/data/build.gradle.kts — lihat sendiri bahwa module ini tidak mendeklarasikan library
   produksi apa pun.
4. Periksa layer bawah dengan ls, jangan diasumsikan:
   - core/network/src/main/java/com/sample/demo/core/network/
   - core/common/src/main/java/com/sample/demo/core/common/
   Per hari ini keduanya cuma .gitkeep: tidak ada AuthApi, tidak ada Dto, tidak ada safeApiCall,
   tidak ada Result, tidak ada DispatcherProvider. Kalau ternyata sudah ada, BERHENTI dan beri
   tahu saya — bentuk kontraknya akan berbeda.
5. Periksa juga apakah AuthRepository sudah ada, supaya tidak membuat yang kedua untuk domain
   yang sama.

Laporkan hasil langkah 0 sebelum menulis file pertama.

## Alasan module ini ada

:core:data adalah PENJAGA BATAS. Tugasnya bukan menyimpan data, melainkan memastikan :feature
tidak pernah melihat tipe network. Semua aturan di bawah turun dari situ.

## File yang dibuat (persis ini, tidak kurang tidak lebih)

    core/data/src/main/java/com/sample/demo/core/data/
    ├── model/Session.kt
    └── repository/
        ├── AuthRepository.kt
        └── AuthRepositoryImpl.kt
    core/data/src/test/java/com/sample/demo/core/data/repository/AuthRepositoryImplTest.kt

TIDAK ada mapper/ dan TIDAK ada datasource/ untuk fitur ini: belum ada DTO yang dipetakan dan
belum ada API yang dipanggil. Jangan membuat paket kosong untuk keduanya.

## Kerangka kode

Bentuk yang harus diikuti, bukan kode final.

### model/Session.kt

    package com.sample.demo.core.data.model

    /** Domain model. Sengaja bukan bentuk wire — lihat [com.sample.demo.core.data.mapper]. */
    data class Session(
        val userId: Int,
        val username: String,
        val email: String,
    )

Dibentuk oleh kebutuhan aplikasi, BUKAN oleh JSON: buang field yang tidak pernah dibaca,
selesaikan nullability di sini (sehingga domain model tidak punya `?` yang percuma), normalkan
tipe mentah (string tanggal → tipe terurai). Publik, dan bebas dari setiap tipe network.

### repository/AuthRepository.kt

    package com.sample.demo.core.data.repository

    import com.sample.demo.core.data.model.Session

    /**
     * Kontrak publik untuk login — satu-satunya permukaan yang boleh dipakai :feature.
     *
     * Mengembalikan Session? dan bukan core.common.Result karena :core:common belum ada;
     * kegagalan untuk sementara menyeberang sebagai exception. Saat Result mendarat, ubah
     * menjadi Result<Session> dan hapus jalur exception dari pemanggil.
     */
    interface AuthRepository {
        suspend fun login(username: String, email: String): Session?
    }

    /** Satu-satunya cara publik memperoleh [AuthRepository]. Belum menghasilkan sesi apa pun. */
    fun AuthRepository(): AuthRepository = AuthRepositoryImpl()

Signature di interface ini hanya boleh menyebut tipe :core:common dan model domain module ini.

### repository/AuthRepositoryImpl.kt

    package com.sample.demo.core.data.repository

    import com.sample.demo.core.data.model.Session

    /**
     * [submitLogin] adalah seam, bukan data source. :core:network belum punya AuthApi atau
     * AuthDto, jadi tidak ada yang disuplai dan default-nya tidak menghasilkan sesi. Saat
     * network mendarat: ganti parameter ini dengan AuthRemoteDataSource, tambahkan
     * mapper/SessionMapper.kt, dan map AuthDto -> Session di sini. Sengaja BUKAN API palsu,
     * DTO stub, atau daftar user hardcoded — itu kode :core:network di module yang salah.
     */
    internal class AuthRepositoryImpl(
        private val submitLogin: suspend (String, String) -> Session? = { _, _ -> null },
    ) : AuthRepository {

        override suspend fun login(username: String, email: String): Session? =
            submitLogin(username, email)
    }

Jangan menambahkan `withContext(dispatchers.io)` di varian ini: tidak ada mapping yang perlu
dipindahkan dari main thread, dan DispatcherProvider milik :core:common yang belum ada.

### AuthRepositoryImplTest.kt

    class AuthRepositoryImplTest {

        @Test
        fun `meneruskan apa yang dihasilkan seam`() = runTest {
            val session = Session(1, "rina", "rina@mail.com")
            val repository = AuthRepositoryImpl(submitLogin = { _, _ -> session })

            assertEquals(session, repository.login("rina", "rina@mail.com"))
        }

        @Test
        fun `default tidak menghasilkan sesi selama core network kosong`() = runTest {
            assertNull(AuthRepository().login("rina", "rina@mail.com"))
        }

        @Test
        fun `seam yang gagal melempar exception-nya sampai Result ada`() = runTest {
            val repository = AuthRepositoryImpl(submitLogin = { _, _ -> throw IOException("offline") })

            val error = runCatching { repository.login("a", "b") }.exceptionOrNull()

            assertTrue(error is IOException)
        }
    }

Test unit berada di module yang sama sehingga MELIHAT deklarasi internal — konstruksi
AuthRepositoryImpl langsung, tanpa melonggarkan visibility apa pun. Tidak ada mocking library di
repo ini dan tidak boleh ditambah: seam-nya tipe fungsi, jadi cukup berikan lambda. Assert pada
nilai, bukan pada jumlah pemanggilan.

## Aturan batas module — periksa sebelum selesai

:core:data memakai `implementation(project(":core:network"))`, bukan `api`. Itu menjaga Retrofit
keluar dari compile classpath :feature, TAPI Gradle tidak akan menangkap kebocoran: sesuatu yang
seharusnya `internal` tetap ter-compile selama :feature belum menyebutnya. Aturan ini dijaga saat
menulis, bukan oleh compiler.

Telusuri SETIAP deklarasi public di module ini dan pastikan tidak satu pun dari ini muncul di
signature-nya (parameter, return type, atau type argument):

    <X>Dto · Response · Retrofit · OkHttp* · Call · @SerializedName ·
    apa pun dari com.sample.demo.core.network

Kalau ada, perbaikannya `internal` + factory publik — BUKAN mengubah :core:network jadi api().
Factory publik yang di dalam TUBUHNYA menyebut tipe internal itu legal di Kotlin; yang dilarang
adalah tipe internal muncul di SIGNATURE.

## Layer bawah belum ada: bangun kontraknya, JANGAN fabrikasi

- JANGAN membuat AuthApi, AuthDto, LoginRequest, NetworkModule, atau Retrofit interface —
  itu milik :core:network. Termasuk versi "sementara".
- JANGAN mendefinisikan Result, NetworkException, atau DispatcherProvider di sini. Itu kode
  :core:common di module yang salah, persis kesalahan yang aturan ini cegah.
- JANGAN menaruh daftar user hardcoded, map in-memory, atau Impl yang pura-pura jadi cache
  supaya login "berhasil". Seam yang mengembalikan null adalah hasil yang benar.
- Domain model tetap milik Anda — Session memang urusan module ini, itu bukan fabrikasi.
- Sumber lokal/offline (Room, DataStore) butuh dependency baru, jadi di luar scope. Katakan
  begitu, jangan dipalsukan dengan map in-memory.

## Di luar scope — jangan dikerjakan

- Jangan menambah dependency di core/data/build.gradle.kts. Module ini sengaja tidak punya
  library produksi sendiri: coroutines lewat api(:core:common), Retrofit lewat
  implementation(:core:network); yang dideklarasikan hanya junit dan kotlinx-coroutines-test.
  Kalau merasa butuh yang lain, HENTIKAN dan tanya saya dulu.
- Jangan ada Compose, tipe android.* UI, atau ViewModel di module ini.
- Jangan ada validasi: repository tidak memeriksa format email dan tidak menolak string kosong.
- Jangan ada DI framework. Default constructor argument, bukan singleton global.
- Jangan menyentuh :feature, :app, atau mapper domain → UI.

## Verifikasi (jalankan sendiri, jangan tanya saya)

1. .\gradlew.bat :core:data:testDebugUnitTest
2. .\gradlew.bat :core:data:lintDebug
3. .\gradlew.bat :app:assembleDebug

Tunjukkan output ketiga perintah sebagai bukti. Kalau gagal, perbaiki akar masalahnya.

## Checklist sebelum menyatakan selesai

Jalankan sendiri dan laporkan butir demi butir:

- [ ] empat file di atas dibuat; tidak ada mapper/ atau datasource/ yang dibuat
- [ ] tidak ada AuthApi/AuthDto/NetworkModule/Retrofit di mana pun
- [ ] tidak ada Result/NetworkException/DispatcherProvider yang didefinisikan di module ini
- [ ] tidak ada data contoh, map in-memory, atau daftar user hardcoded
- [ ] AuthRepositoryImpl `internal`; AuthRepository dan factory function `public`
- [ ] penelusuran deklarasi public selesai: tidak ada tipe network di signature mana pun
- [ ] core/data/build.gradle.kts tidak berubah
- [ ] tiga test di atas ada dan hijau; tanpa mocking library
- [ ] ketiga perintah verifikasi hijau, output ditunjukkan

## Laporan akhir

Sebutkan: file yang dibuat, SIGNATURE PUBLIK persisnya (sesi berikutnya butuh itu), layer mana
yang absen, apa yang karena itu tidak dibangun (mapper dan data source), hasil penelusuran batas
module, dan hasil checklist di atas.
````

---

**Setelah ini:** `/clear`, lalu [`03-wire-login.md`](03-wire-login.md).
