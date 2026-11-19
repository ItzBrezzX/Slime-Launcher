#include "preloader.hpp"
#include "effects.hpp"
#include "module_bridge.hpp"
#include "module_scanner.hpp"
#include "signature_manager.hpp"
#include <cmath>
#include <cstring>

namespace slime {
namespace {

// Lives in this module's .rodata → lets us prove the in-process scanner finds real bytes.
const unsigned char kMarker[] = {0x53, 0x4C, 0x49, 0x4D, 0x45, 0x5F, 0x4D, 0x4B,
                                 0x52, 0xA7, 0x3C, 0x19, 0xE4, 0x88, 0x0D, 0x6B};

void report(std::vector<std::string>& out, const char* name, bool ok, const std::string& detail = "") {
    out.push_back(std::string(ok ? "PASS " : "FAIL ") + name + (detail.empty() ? "" : ": " + detail));
}

} // namespace

std::vector<std::string> runSelfTest() {
    std::vector<std::string> out;

    // 1. parser
    std::vector<int> p;
    std::string err;
    report(out, "pattern valid", parsePattern("DE AD ?? EF 12", &p, &err) && p.size() == 5 && p[2] == -1 && p[0] == 0xDE);
    report(out, "pattern invalid ditolak (tanpa crash)",
           !parsePattern("ZZ 12", &p, &err) && !parsePattern("", &p, &err) && !parsePattern("?? ??", &p, &err) &&
               !parsePattern("123 45", &p, &err));

    // 2. scanner on a synthetic buffer
    unsigned char buf[64];
    std::memset(buf, 0x90, sizeof(buf));
    const unsigned char needle[] = {0xDE, 0xAD, 0xBE, 0xEF, 0x12, 0x34};
    std::memcpy(buf + 37, needle, sizeof(needle));
    std::vector<int> pat;
    parsePattern("DE AD ?? EF 12", &pat, &err);
    const uintptr_t hit = scanRange(reinterpret_cast<uintptr_t>(buf), sizeof(buf), pat);
    report(out, "scan buffer sintetis", hit == reinterpret_cast<uintptr_t>(buf) + 37);
    parsePattern("DE AD ?? EE", &pat, &err);
    report(out, "scan tidak cocok → 0", scanRange(reinterpret_cast<uintptr_t>(buf), sizeof(buf), pat) == 0);
    parsePattern("34", &pat, &err);
    report(out, "scan byte terakhir (batas)", scanRange(reinterpret_cast<uintptr_t>(buf), 43, pat) ==
                                                  reinterpret_cast<uintptr_t>(buf) + 42);

    // 3. process modules + scan our own image
    auto mods = listLoadedModules();
    LoadedModule self;
    const bool haveSelf = findLoadedModule("slime_preloader", &self);
    report(out, "daftar modul proses", !mods.empty(), std::to_string(mods.size()) + " modul");
    report(out, "modul sendiri terdeteksi", haveSelf);
    if (haveSelf) {
        std::string hex;
        static const char* kHex = "0123456789ABCDEF";
        for (unsigned char b : kMarker) { hex += kHex[b >> 4]; hex += kHex[b & 15]; hex += ' '; }
        parsePattern(hex, &pat, &err);
        const uintptr_t found = scanRanges(self.readable, pat);
        report(out, "scan modul in-process (marker)",
               found >= self.base && found < self.base + self.size && std::memcmp(reinterpret_cast<const void*>(found), kMarker, sizeof(kMarker)) == 0);
    }

    // 4. overrides: invalid rejected, valid accepted, cleanup
    auto& sm = SignatureManager::instance();
    report(out, "override pattern invalid ditolak", !sm.setOverrideSignature("__selftest__", "XX", &err));
    report(out, "override pattern valid diterima", sm.setOverrideSignature("__selftest__", "AA BB ??", &err));
    sm.setOverrideSignature("__selftest__", "", &err); // remove

    // 5. bridge + effects (state restored afterwards)
    auto& mb = ModuleBridge::instance();
    const bool zOn = mb.isEnabled("zoom");
    const double zLv = mb.getValue("zoom", "level", 4.0);
    mb.setEnabled("zoom", true);
    mb.setValue("zoom", "level", 4.0);
    const double f = effects::fov(70.0, true);
    report(out, "efek zoom 70°/4× = 17.5°", std::fabs(f - 17.5) < 1e-9, std::to_string(f));
    mb.setEnabled("zoom", false);
    report(out, "efek zoom mati → FOV asli", effects::fov(70.0, true) == 70.0);
    mb.setEnabled("zoom", zOn);
    mb.setValue("zoom", "level", zLv);

    const bool tOn = mb.isEnabled("time");
    const double tH = mb.getValue("time", "hour", 12.0);
    mb.setEnabled("time", true);
    mb.setValue("time", "hour", 6.0);
    const int t6 = effects::timeTicks(0);
    mb.setValue("time", "hour", 12.0);
    const int t12 = effects::timeTicks(0);
    report(out, "efek waktu (06:00→0, 12:00→6000)", t6 == 0 && t12 == 6000);
    mb.setEnabled("time", tOn);
    mb.setValue("time", "hour", tH);

    return out;
}

} // namespace slime
