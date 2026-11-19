# Slime — Minecraft Bedrock Launcher (Android)

**Author:** KingBrezz  
**Version:** 1.5.0  
**Package:** `com.kingbrezz.slime`

## Apa yang dipelajari dari resource

| Resource | Dipakai di Slime |
|----------|------------------|
| **LeviLauncher-Android** | Struktur launcher: versi, isolasi, pustaka konten, intent/launch game, CI APK |
| **dll-oss (Flarial)** | Pola klien: Module registry, kategori Visual/HUD/…, ClickGUI kolom, Config profile |

Implementasi **asli Slime** (bukan salinan file mereka). dll-oss = Windows C++; tidak di-port penuh ke Android di sini.

## Fitur playable

- Beranda + **Mainkan** (buka Minecraft jika ada + sesi klien demo)
- Versi / Modul / Config / Pustaka / Setelan
- **ClickGUI** kolom kategori (toggle modul live)
- HUD demo (FPS, CPS, coords, watermark, …)
- Icon vektor **Slime sendiri** (bukan emoji, bukan pack Material Icons)
- Persistensi SharedPreferences dengan try/catch

## Build APK (GitHub)

Push ke GitHub → Actions → **Build APK** → unduh artifact `slime-debug-apk`.

Lokal: JDK 17 + Android SDK → `./gradlew assembleDebug`

## Batasan

Slime tidak menyuntik ke proses aplikasi Minecraft resmi. Native runtime sekarang dapat memetakan library Minecraft ke proses host milik Slime (`:game`), tetapi belum menjalankan game secara playable karena Java/GameActivity, assets, resource context, dan lifecycle Mojang belum tersedia.

## License

Kode Slime oleh KingBrezz. Pola dipelajari dari proyek terbuka; source mereka tidak digandakan.


## Mod Menu (1.5.0)

Tombol hijau **Mod Menu** melayang di semua layar (bisa digeser) dan ada di Beranda/Setelan.
Isinya, semua terhubung ke native lewat JNI:

| Tab | Fungsi |
|-----|--------|
| Modul | Nyala/mati, slider nilai (zoom, gamma, kabut, jam, skala…), preview efek dari native, simpan config |
| Preloader | Status fase/signature/hook/target, Init, Shutdown, Pipeline inject, buka Minecraft |
| Hook | Daftar hook + alasan status, pasang satu/semua |
| Signature | Isi/ubah pattern IDA per fungsi **tanpa rebuild .so** (disimpan permanen) |
| Offset | Isi/ubah offset (hex/desimal) |
| Alat | Uji pattern pada modul in-process, self-test native, daftar modul proses |
| Log | Log launcher + salin |

## Minecraft APK Preparation

The Versions screen can copy the installed Play Store Minecraft package
(base and split APKs) automatically, or import a user-provided base APK and
matching splits. Slime checks the package/version and requires the installed Google Play
Minecraft package with a matching signing certificate. It chooses the
matching process ABI, validates each extracted ELF library, requires the core native libraries (`libminecraftpe.so`, `libc++_shared.so`,
`libfmod.so`, and `libMediaDecoders_Android.so`), and stores the package and SHA-256 manifest in
app-private storage.

The `MinecraftRuntimeActivity` runs in Slime's `:game` process and can map the imported core native dependency graph. It remains a diagnostic host, not a playable game Activity: Mojang Java/GameActivity compatibility, assets/resources, and game startup lifecycle are still missing. Opening official Minecraft remains an external app launch in a separate process.

## Minecraft Runtime Integration

`MinecraftRuntimeActivity` now runs in the dedicated `:game` process. Its native loader validates the imported ABI, reads ELF `DT_NEEDED` dependencies, maps the core Minecraft libraries into that process, then initializes `libslime_preloader.so` through `MinecraftRuntimeBridge`.

This is process-local loading, not injection into the separately installed Minecraft app. The current `:game` Activity remains diagnostic: the Mojang Java/GameActivity compatibility layer, asset/resource context, and full game lifecycle are not yet implemented. Native hook backend and per-version signatures also remain unfinished.

See [docs/RUNTIME_INTEGRATION.md](docs/RUNTIME_INTEGRATION.md) for the
runtime callback sequence and remaining prerequisites.

## Native Runtime Loader

The launch pipeline detects the device ABI, inventories packaged native libraries,
parses imported ELF `DT_NEEDED` dependencies, and loads the core Minecraft library
set in Slime's dedicated `:game` process. It then loads `libslime_preloader.so`
from the installed APK and checks that `libminecraftpe.so` is mapped in the same
process. A missing or incompatible library is reported as an explicit runtime error.

## Preloader NDK

Native library `libslime_preloader.so`:

- Signature manager + per-version offset tables
- Hook registry (ClientInstance, Actor tick, Gamma, FOV, …)
- JNI bridge + module flags from Java UI
- Real in-process module discovery (`dl_iterate_phdr`) + safe pattern scanner
- Runtime signature/offset overrides, module numeric settings, effect functions
- Init on **Mainkan**, **Setelan → Init preloader**, and **Mod Menu**

See [docs/PRELOADER.md](docs/PRELOADER.md).

**Signatures are placeholders.** Live inject requires RE per version and in-process load with Minecraft (not claimed working out-of-the-box).
