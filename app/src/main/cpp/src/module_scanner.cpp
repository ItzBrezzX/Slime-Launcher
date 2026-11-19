#include "module_scanner.hpp"
#include "slime_log.hpp"
#include <algorithm>
#include <cctype>
#include <cstring>
#include <link.h>

namespace slime {
namespace {

int hexVal(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return 10 + (c - 'a');
    return 10 + (c - 'A');
}

std::string baseName(const std::string& path) {
    auto pos = path.find_last_of('/');
    return pos == std::string::npos ? path : path.substr(pos + 1);
}

int phdrCallback(struct dl_phdr_info* info, size_t, void* data) {
    auto* out = static_cast<std::vector<LoadedModule>*>(data);
    LoadedModule m;
    m.path = info->dlpi_name ? info->dlpi_name : "";
    m.name = baseName(m.path);
    if (m.name.empty()) m.name = "[main]";

    uintptr_t lo = ~static_cast<uintptr_t>(0);
    uintptr_t hi = 0;
    for (int i = 0; i < info->dlpi_phnum; ++i) {
        const auto& ph = info->dlpi_phdr[i];
        if (ph.p_type != PT_LOAD) continue;
        const uintptr_t b = static_cast<uintptr_t>(info->dlpi_addr) + static_cast<uintptr_t>(ph.p_vaddr);
        const size_t sz = static_cast<size_t>(ph.p_memsz);
        lo = std::min(lo, b);
        hi = std::max(hi, b + sz);
        if ((ph.p_flags & PF_R) != 0 && sz > 0) m.readable.push_back({b, sz});
    }
    if (hi == 0 || lo >= hi) return 0;
    m.base = lo;
    m.size = static_cast<size_t>(hi - lo);
    out->push_back(std::move(m));
    return 0;
}

} // namespace

std::vector<LoadedModule> listLoadedModules() {
    std::vector<LoadedModule> out;
    dl_iterate_phdr(phdrCallback, &out);
    return out;
}

bool findLoadedModule(const std::string& needle, LoadedModule* out) {
    if (needle.empty() || !out) return false;
    auto mods = listLoadedModules();
    for (auto& m : mods) {
        if (m.name == needle) { *out = m; return true; }
    }
    for (auto& m : mods) {
        if (m.name.find(needle) != std::string::npos || m.path.find(needle) != std::string::npos) {
            *out = m;
            return true;
        }
    }
    return false;
}

bool parsePattern(const std::string& pattern, std::vector<int>* out, std::string* err) {
    auto fail = [&](const std::string& msg) {
        if (err) *err = msg;
        if (out) out->clear();
        return false;
    };
    if (!out) return false;
    out->clear();

    size_t i = 0;
    const size_t n = pattern.size();
    while (i < n) {
        while (i < n && std::isspace(static_cast<unsigned char>(pattern[i]))) ++i;
        if (i >= n) break;
        size_t j = i;
        while (j < n && !std::isspace(static_cast<unsigned char>(pattern[j]))) ++j;
        const std::string tok = pattern.substr(i, j - i);
        i = j;

        if (tok == "?" || tok == "??") {
            out->push_back(-1);
            continue;
        }
        if (tok.size() != 2 ||
            !std::isxdigit(static_cast<unsigned char>(tok[0])) ||
            !std::isxdigit(static_cast<unsigned char>(tok[1]))) {
            return fail("token tidak valid: '" + tok + "' (pakai 2 digit hex atau wildcard ?)");
        }
        out->push_back(hexVal(tok[0]) * 16 + hexVal(tok[1]));
        if (out->size() > 512) return fail("pattern terlalu panjang (maks 512 byte)");
    }
    if (out->empty()) return fail("pattern kosong");
    const bool anyConcrete = std::any_of(out->begin(), out->end(), [](int v) { return v >= 0; });
    if (!anyConcrete) return fail("pattern hanya wildcard");
    return true;
}

uintptr_t scanRange(uintptr_t begin, size_t size, const std::vector<int>& pat) {
    const size_t m = pat.size();
    if (begin == 0 || m == 0 || size < m) return 0;

    size_t k = 0;
    while (k < m && pat[k] < 0) ++k;
    if (k == m) return 0; // all wildcards: refuse

    const auto* base = reinterpret_cast<const uint8_t*>(begin);
    const uint8_t first = static_cast<uint8_t>(pat[k]);
    const size_t lastStart = size - m;      // highest valid start offset
    const size_t lastProbe = lastStart + k; // highest offset of the first concrete byte
    size_t pos = k;

    while (pos <= lastProbe) {
        const void* hit = std::memchr(base + pos, first, lastProbe - pos + 1);
        if (!hit) return 0;
        const size_t p = static_cast<size_t>(static_cast<const uint8_t*>(hit) - base);
        const size_t start = p - k;
        bool ok = true;
        for (size_t i = 0; i < m; ++i) {
            if (pat[i] >= 0 && base[start + i] != static_cast<uint8_t>(pat[i])) { ok = false; break; }
        }
        if (ok) return begin + start;
        pos = p + 1;
    }
    return 0;
}

uintptr_t scanRanges(const std::vector<MemRange>& ranges, const std::vector<int>& pat) {
    for (const auto& r : ranges) {
        const uintptr_t hit = scanRange(r.begin, r.size, pat);
        if (hit) return hit;
    }
    return 0;
}

} // namespace slime
