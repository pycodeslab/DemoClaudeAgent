# Prompt Manual — Layar Login tanpa Skill

Tiga prompt untuk membangun layar Login (input **username**, input **email**, tombol **Masuk**,
**tanpa validasi**) **tanpa memanggil skill apa pun**. Ekspektasi hasilnya sama persis dengan
versi skill di [`../skills/`](../skills/) — bedanya, semua aturan yang biasanya disuntikkan skill
ditulis langsung di dalam prompt.

Tiga prompt, tiga sesi terpisah — **bukan satu prompt gabungan**:

| # | Sesi | Prompt manual | Padanan skill |
| --- | --- | --- | --- |
| 1 | `:feature` — layar | [`01-feature-screen-login.md`](01-feature-screen-login.md) | `/compose-feature-screen` |
| 2 | `:core:data` — repository | [`02-core-data-login.md`](02-core-data-login.md) | `/core-data-repository` |
| 3 | Penggabungan keduanya | [`03-wire-login.md`](03-wire-login.md) | `/wire-feature-to-data` |

## Urutan eksekusi

**Satu prompt = satu sesi.** Jalankan `/clear` di antaranya: sesi `:core:data` tidak perlu
terbebani detail Compose, dan sebaliknya.

```
1. 01-feature-screen-login.md ──► LoginScreen + LoginViewModel(seam submitLogin)
                                            │
2. 02-core-data-login.md      ──► AuthRepository + Session (seam ke :core:network)
                                            │  ← celah sengaja dibiarkan terbuka
3. 03-wire-login.md           ──► mapper Session → SessionUiModel, seam #1 diganti repository
```

Urutan sebaliknya (`:core:data` dulu) juga menghasilkan kode benar, tapi mengaburkan batas
prompt 1 dan 3: begitu repository sudah ada, prompt layar akan langsung ingin memakainya,
padahal mapper dan `Factory` adalah pekerjaan prompt 3. Urutan 1 → 2 → 3 membuat titik berhenti
tiap prompt tetap terlihat, dan itu urutan yang dipakai contoh `postlist` di repo ini.

## Apa yang membuat versi manual setara

Skill tidak hanya membawa aturan — ia juga membawa `references/templates.md` (kerangka kode) dan
checklist review. Prosa saja membuat hasilnya melenceng di detail yang tidak tertulis, jadi tiap
prompt manual di sini membawa tiga hal itu sendiri:

1. **Langkah 0 — daftar file yang wajib dibaca dulu.** Repo ini sudah punya contoh `postlist`
   yang lolos build; menyuruh membacanya lebih akurat daripada mendeskripsikan ulang gayanya.
2. **Kerangka kode per file**, lengkap dengan komentar yang menjelaskan kenapa seam-nya seperti
   itu — sama seperti yang disuntikkan skill.
3. **Checklist "sebelum menyatakan selesai"** yang harus dijawab butir demi butir, plus daftar
   file yang boleh dibuat ("tidak kurang tidak lebih") supaya tidak ada file liar.

## Manual atau skill?

| | Manual (folder ini) | Skill ([`../skills/`](../skills/)) |
| --- | --- | --- |
| Cara mulai | tempel isi blok ke sesi baru | ketik `/compose-feature-screen` dst. |
| Aturan layering | ada di dalam prompt, ikut terbaca | dimuat skill, prompt jadi pendek |
| Kalau aturan berubah | edit tiap prompt yang memuatnya | cukup edit `SKILL.md` |
| Cocok untuk | sesi tanpa akses skill repo ini (Claude web, rekan yang belum clone `.claude/`), atau saat ingin melihat semua aturannya sekaligus | pekerjaan sehari-hari di repo ini |

Keduanya menuntut hal yang sama: seam alih-alih layer palsu, batas module yang tidak bocor,
test berbasis fake tulisan tangan, dan verifikasi Gradle yang outputnya ditunjukkan.

Kalau isi skill dan prompt manual ini berselisih, **`.claude/skills/` dan `CLAUDE.md` yang
menang** — perbarui prompt di sini agar ikut.

## Hasil akhir yang benar

Setelah ketiganya, tombol **Masuk** menampilkan pesan "layanan belum tersedia", bukan sesi.
Itu bukan bug: `AuthRepositoryImpl.submitLogin` masih seam karena `:core:network` belum punya
`AuthApi`. Menutup seam terakhir itu pekerjaan
[`../modules/02-core-network.md`](../modules/02-core-network.md).

## Melihatnya di emulator

`LoginActivity` dideklarasikan `:feature` **tanpa** `android:exported`, jadi tidak bisa
dijalankan lewat `adb shell am start` — itu konvensi yang bekerja, bukan kesalahan. Dua cara:

- jadikan sementara entry point lewat intent-filter `MAIN`/`LAUNCHER` di `:app`
  ([`../modules/05-app.md`](../modules/05-app.md)), lalu `.\gradlew.bat :app:installDebug`;
- atau render `LoginScreen` di test `androidTest` dan jalankan
  `.\gradlew.bat :feature:connectedDebugAndroidTest`.
