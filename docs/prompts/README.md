# Prompt Library

Kumpulan prompt siap pakai untuk membangun feature di repo ini bersama Claude Code,
disusun mengikuti [best practices Claude Code](https://code.claude.com/docs/en/best-practices#provide-specific-context-in-your-prompts).

## Pilih yang mana

| Kalau Anda… | Pakai |
| --- | --- |
| ingin satu prompt untuk seluruh feature end-to-end | [`new-feature.template.md`](new-feature.template.md) |
| ingin melihat template itu dalam bentuk terisi | [`example-post-list.md`](example-post-list.md) |
| ingin mengerjakan per module, satu sesi per layer | [`modules/`](modules/) — lihat di bawah |

### Satu prompt vs per module

**Satu prompt** (`new-feature.template.md`) cocok untuk feature kecil–menengah:
lebih sedikit overhead, dan Claude melihat seluruh rantai sekaligus sehingga
konsistensi antar layer terjaga sendiri.

**Per module** (`modules/`) lebih baik saat feature-nya besar atau saat Anda mau
mengunci desain tiap layer sebelum lanjut. Keuntungannya sesuai dengan kendala utama
Claude Code: satu module = satu sesi = context window yang bersih, jadi saat menulis
`:feature` context tidak lagi penuh detail Retrofit. Jalankan `/clear` di antara
module, dan mulai sesi berikutnya dengan file prompt yang sesuai.

Biayanya: Anda perlu meneruskan signature publik hasil layer sebelumnya ke prompt
berikutnya. Tiap file punya bagian **Konteks** yang menyebutkan prasyarat itu — isi
dengan signature yang benar-benar dihasilkan, jangan disalin mentah.

## Urutan per module

Kerjakan dari layer terdalam ke luar. Arah dependency hanya ke bawah, jadi urutan ini
memastikan setiap layer sudah punya yang dibutuhkannya.

```
:app  ──►  :feature  ──►  :core:data  ──►  :core:network  ──►  :core:common
  5           4               3                  2                  1
```

| # | File | Isi | Verifikasi |
| --- | --- | --- | --- |
| 1 | [`modules/01-core-common.md`](modules/01-core-common.md) | `Result`, `NetworkException`, `DispatcherProvider` | `:core:common:testDebugUnitTest` |
| 2 | [`modules/02-core-network.md`](modules/02-core-network.md) | wire model, API interface, `safeApiCall`, Retrofit/OkHttp | `:core:network:testDebugUnitTest` |
| 3 | [`modules/03-core-data.md`](modules/03-core-data.md) | model domain, mapper, repository | `:core:data:testDebugUnitTest` |
| 4 | [`modules/04-feature.md`](modules/04-feature.md) | Activity, ViewModel, UiState, adapter, layout | `:feature:testDebugUnitTest` + `:app:assembleDebug` |
| 5 | [`modules/05-app.md`](modules/05-app.md) | manifest merge, intent-filter MAIN/LAUNCHER | `:app:assembleDebug` |

Setelah kelimanya selesai:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat :app:assembleDebug
```

## Yang membuat prompt di sini berhasil

Empat hal yang paling menentukan hasil, dan paling sering dilewatkan:

1. **Verifikasi yang bisa dijalankan Claude sendiri.** Setiap file berisi perintah
   Gradle konkret dan daftar test yang harus ditulis. Tanpa ini, Anda yang jadi loop
   verifikasinya dan setiap kesalahan menunggu Anda menyadarinya.
2. **Batas scope yang eksplisit.** Bagian "Di luar scope" ada di setiap file. Tanpa itu
   Claude cenderung menambah Hilt atau kotlinx.serialization "sekalian" — yang justru
   merusak setup toolchain repo ini. (Compose adalah pengecualian yang sudah disiapkan:
   lihat catatan di bawah.)
3. **Menunjuk sumber, bukan menjelaskan ulang.** Prompt menyebut `@CLAUDE.md` dan
   `@gradle/libs.versions.toml` alih-alih menyalin isinya, sehingga tidak ada dua
   versi kebenaran yang bisa berbeda.
4. **Bukti, bukan klaim.** Setiap file meminta output perintah ditampilkan, bukan
   kesimpulan "sudah berhasil".

## Compose: pakai skill, bukan prompt di sini

Prompt library ini menghasilkan layar **XML + view binding**. Untuk layar **Jetpack Compose**
di `:feature`, pakai skill `compose-feature-screen`
([`.claude/skills/compose-feature-screen/`](../../.claude/skills/compose-feature-screen/)):

```
buatkan screen <X> dengan Compose di :feature
```

Skill itu memuat layering Route → Screen (stateless) → component, panduan
[Compose component API guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md),
dan wiring build-nya. Contoh jadi: paket `com.sample.demo.feature.postlist`.

## Catatan toolchain

Repo ini memakai AGP 9.3 dengan DSL yang berbeda dari kebanyakan dokumentasi Android,
dan **tidak punya Kotlin Gradle plugin** meski sumbernya `.kt`. Semua prompt di sini
sudah memuat rambu-rambunya, tapi kalau Anda menulis prompt sendiri, baca dulu bagian
"Toolchain — do not 'modernize' these" di [`CLAUDE.md`](../../CLAUDE.md).
