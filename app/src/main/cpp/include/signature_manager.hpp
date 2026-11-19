#pragma once
#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>
#include "module_scanner.hpp"

namespace slime {

struct SigEntry {
    std::string name;
    std::string pattern;   // IDA-style: "48 8B ?? ?? 90" or empty if unresolved
    uintptr_t address = 0;
    bool resolved = false;
};

class SignatureManager {
public:
    static SignatureManager& instance();

    /** Clears the per-version table. Runtime overrides are kept. */
    void clear();
    void addSignature(const std::string& name, const std::string& pattern);
    void addOffset(const std::string& name, int offset);
    int getOffset(const std::string& name) const;
    uintptr_t getAddress(const std::string& name) const;
    bool isResolved(const std::string& name) const;

    /**
     * Runtime overrides (set from the Mod Menu, persisted by Java).
     * They survive clear() and are re-applied by applyOverrides() after a table load,
     * so patterns can be filled without rebuilding the .so.
     * Empty pattern removes the override. Returns false + err when the pattern is malformed.
     */
    bool setOverrideSignature(const std::string& name, const std::string& pattern, std::string* err);
    void setOverrideOffset(const std::string& name, int value);
    void clearOverrides();
    int overrideCount() const;
    /** Applies overrides to known names; returns how many were applied. */
    int applyOverrides();

    /** Scan one contiguous range the caller guarantees to be readable. Returns resolved count. */
    int scanAll(uintptr_t moduleBase, size_t moduleSize);
    /** Scan the readable segments of a loaded module. Returns resolved count. */
    int scanRanges(const std::vector<MemRange>& ranges);

    int resolvedCount() const;
    int totalCount() const;
    std::vector<std::string> unresolvedNames() const;
    std::vector<SigEntry> signatures() const;                   // sorted by name
    std::vector<std::pair<std::string, int>> offsets() const;   // sorted by name

private:
    SignatureManager() = default;
    mutable std::recursive_mutex mu_;
    std::unordered_map<std::string, SigEntry> sigs_;
    std::unordered_map<std::string, int> offsets_;
    std::unordered_map<std::string, std::string> sigOverrides_;
    std::unordered_map<std::string, int> offOverrides_;
};

} // namespace slime
