# Prompt per Skill — Contoh: Layar Login

Tiga prompt untuk membangun satu layar Login (input **username**, input **email**, tombol
**Masuk**, **tanpa validasi**) memakai tiga skill di [`.claude/skills/`](../../../.claude/skills/).

Bedanya dengan [`../modules/`](../modules/): folder itu menghasilkan layar XML + view binding
lewat prompt biasa. Folder ini memakai skill, yang sudah memuat layering Compose, aturan batas
module, dan pola test-nya — jadi prompt-nya bisa lebih pendek dan lebih sulit disalahartikan.

## Cara memanggil

Ketiga skill dikonfigurasi `disable-model-invocation: true`, jadi **hanya Anda yang bisa
memanggilnya, lewat `/`**:

```
/compose-feature-screen
/core-data-repository
/wire-feature-to-data
```

Claude tidak akan memuatnya sendiri hanya karena Anda menulis "buatkan screen X" — itu
disengaja: skill ini mengubah banyak file, jadi keputusan memakainya ada di tangan Anda.
Konsekuensinya, kalau Anda lupa mengetik `/`, Claude akan mengerjakannya tanpa aturan
layering di skill tersebut. Ketik `/` di prompt untuk melihat daftarnya.

## Urutan eksekusi

**Satu skill = satu sesi.** Jalankan `/clear` di antaranya: setiap skill memuat referensinya
sendiri, dan sesi `:core:data` tidak perlu terbebani detail Compose.

| # | Sesi | Prompt | Panggil dengan | Berhenti di |
| --- | --- | --- | --- | --- |
| 1 | Layar | [`01-compose-feature-screen-login.md`](01-compose-feature-screen-login.md) | `/compose-feature-screen` | seam `submitLogin` di ViewModel |
| 2 | Data | [`02-core-data-repository-login.md`](02-core-data-repository-login.md) | `/core-data-repository` | seam `submitLogin` di `AuthRepositoryImpl` |
| 3 | Sambung | [`03-wire-feature-to-data-login.md`](03-wire-feature-to-data-login.md) | `/wire-feature-to-data` | rantai tersambung; sisa seam menunggu `:core:network` |

```
1. compose-feature-screen ──► LoginScreen + LoginViewModel(seam)
                                        │
2. core-data-repository   ──► AuthRepository + Session          │ celah sengaja dibiarkan
                                        │                       │
3. wire-feature-to-data   ──► mapper Session → SessionUiModel ◄─┘ celah ditutup
```

### Kenapa layar dulu, bukan data dulu

Urutan sebaliknya (`:core:data` dulu) juga menghasilkan kode yang benar, tapi mengaburkan
batas skill #1 dan #3: begitu repository sudah ada, prompt layar akan langsung ingin memakainya,
padahal mapper dan `Factory` adalah milik skill #3. Urutan 1 → 2 → 3 membuat titik berhenti
tiap skill tetap terlihat — dan itu urutan yang dipakai contoh `postlist` di repo ini
(commit `b39a4ae` → `f10f23d` → `b7213d8`).

## Yang membuat ketiga prompt ini bekerja

- **Menyebut apa yang harus dicek, bukan apa yang harus dipercaya.** Tiap prompt menyuruh
  `ls` layer di bawahnya lebih dulu; kalau keadaan repo berubah, Claude memilih varian yang
  benar sendiri.
- **"Tanpa validasi" ditulis sebagai larangan eksplisit.** Tanpa itu, cek format email dan
  tombol yang disabled saat field kosong hampir pasti muncul "sekalian".
- **Larangan fabrikasi diulang di tiap layer.** Ini satu-satunya cara layar yang belum punya
  backend tidak berubah jadi daftar user hardcoded di `:core:data`.
- **Verifikasi berupa perintah Gradle konkret**, dan permintaan menunjukkan output-nya.

## Hasil akhir yang benar

Setelah ketiganya, tombol **Masuk** menampilkan pesan "layanan belum tersedia", bukan sesi.
Itu bukan bug: `AuthRepositoryImpl.submitLogin` masih seam karena `:core:network` belum punya
`AuthApi`. Menutup seam terakhir itu pekerjaan [`../modules/02-core-network.md`](../modules/02-core-network.md).

## Melihatnya di emulator

`LoginActivity` dideklarasikan `:feature` **tanpa** `android:exported`, jadi tidak bisa
dijalankan lewat `adb shell am start` — itu konvensi yang bekerja, bukan kesalahan. Dua cara
melihatnya:

- jadikan sementara entry point lewat intent-filter `MAIN`/`LAUNCHER` di `:app`
  ([`../modules/05-app.md`](../modules/05-app.md)), lalu `.\gradlew.bat :app:installDebug`;
- atau render `LoginScreen` di test `androidTest` dan jalankan
  `.\gradlew.bat :feature:connectedDebugAndroidTest`.

## Memakai ulang untuk fitur lain

Ganti nama domainnya (`Login`/`Auth`/`Session`) dan bagian "Yang dibangun". Sisanya —
konteks, larangan fabrikasi, batas module, verifikasi — berlaku apa adanya untuk fitur mana pun
di repo ini.
