#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace slime {

struct MemRange {
    uintptr_t begin = 0;
    size_t size = 0;
};

struct LoadedModule {
    std::string name;               // basename, e.g. "libminecraftpe.so"
    std::string path;
    uintptr_t base = 0;             // lowest mapped address
    size_t size = 0;                // span lowest..highest mapped byte
    std::vector<MemRange> readable; // PT_LOAD segments with PF_R (safe to scan)
};

/** Modules mapped in *this* process (dl_iterate_phdr). */
std::vector<LoadedModule> listLoadedModules();

/** Exact basename match first, then substring on name/path. */
bool findLoadedModule(const std::string& nameOrSubstring, LoadedModule* out);

/**
 * IDA-style pattern "48 8B ?? 90" → bytes, -1 = wildcard.
 * Exactly two hex digits per byte, "?" / "??" wildcard. Never throws / aborts.
 */
bool parsePattern(const std::string& pattern, std::vector<int>* out, std::string* err);

/** First match inside [begin, begin+size). Caller guarantees the range is readable. 0 = none. */
uintptr_t scanRange(uintptr_t begin, size_t size, const std::vector<int>& pat);
uintptr_t scanRanges(const std::vector<MemRange>& ranges, const std::vector<int>& pat);

} // namespace slime
