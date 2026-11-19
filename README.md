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

Tidak menyuntik native SO ke proses Minecraft. Native inject butuh preloader + offset per versi (skala Levi/Flarial penuh).

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

## Native Runtime Loader

The launch pipeline detects the device ABI, inventories packaged native libraries,
checks the preloader ELF ABI, and loads `libslime_preloader.so` from the installed APK.
Android's linker resolves declared shared-library dependencies. A missing or
incompatible library is reported as an explicit runtime error.

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
