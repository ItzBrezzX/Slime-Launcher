#include "signature_manager.hpp"
#include "slime_log.hpp"
#include <algorithm>
#include <cctype>

namespace slime {

SignatureManager& SignatureManager::instance() {
    static SignatureManager s;
    return s;
}

void SignatureManager::clear() {
    std::lock_guard<std::recursive_mutex> g(mu_);
    sigs_.clear();
    offsets_.clear();
}

void SignatureManager::addSignature(const std::string& name, const std::string& pattern) {
    std::lock_guard<std::recursive_mutex> g(mu_);
    SigEntry e;
    e.name = name;
    e.pattern = pattern;
    sigs_[name] = e;
}

void SignatureManager::addOffset(const std::string& name, int offset) {
    std::lock_guard<std::recursive_mutex> g(mu_);
    offsets_[name] = offset;
}

int SignatureManager::getOffset(const std::string& name) const {
    std::lock_guard<std::recursive_mutex> g(mu_);
    auto it = offsets_.find(name);
    return it == offsets_.end() ? 0 : it->second;
}

uintptr_t SignatureManager::getAddress(const std::string& name) const {
    std::lock_guard<std::recursive_mutex> g(mu_);
    auto it = sigs_.find(name);
    return it == sigs_.end() ? 0 : it->second.address;
}

bool SignatureManager::isResolved(const std::string& name) const {
    std::lock_guard<std::recursive_mutex> g(mu_);
    auto it = sigs_.find(name);
    return it != sigs_.end() && it->second.resolved;
}

bool SignatureManager::setOverrideSignature(const std::string& name, const std::string& pattern, std::string* err) {
    std::lock_guard<std::recursive_mutex> g(mu_);
    if (name.empty()) {
        if (err) *err = "nama signature kosong";
        return false;
    }
    bool blank = true;
    for (char c : pattern) if (!std::isspace(static_cast<unsigned char>(c))) { blank = false; break; }
    if (blank) {
        sigOverrides_.erase(name);
        return true;
    }
    std::vector<int> bytes;
    if (!parsePattern(pattern, &bytes, err)) return false;
    sigOverrides_[name] = pattern;
    return true;
}

void SignatureManager::setOverrideOffset(const std::string& name, int value) {
    std::lock_guard<std::recursive_mutex> g(mu_);
    if (name.empty()) return;
    offOverrides_[name] = value;
}

void SignatureManager::clearOverrides() {
    std::lock_guard<std::recursive_mutex> g(mu_);
    sigOverrides_.clear();
    offOverrides_.clear();
}

int SignatureManager::overrideCount() const {
    std::lock_guard<std::recursive_mutex> g(mu_);
    return static_cast<int>(sigOverrides_.size() + offOverrides_.size());
}

int SignatureManager::applyOverrides() {
    std::lock_guard<std::recursive_mutex> g(mu_);
    int applied = 0;
    for (const auto& kv : sigOverrides_) {
        auto it = sigs_.find(kv.first);
        if (it == sigs_.end()) continue;
        it->second.pattern = kv.second;
        it->second.address = 0;
        it->second.resolved = false;
        ++applied;
    }
    for (const auto& kv : offOverrides_) {
        auto it = offsets_.find(kv.first);
        if (it == offsets_.end()) continue;
        it->second = kv.second;
        ++applied;
    }
    if (applied > 0) SLOG_I("overrides applied: %d", applied);
    return applied;
}

int SignatureManager::scanAll(uintptr_t moduleBase, size_t moduleSize) {
    if (moduleBase == 0 || moduleSize == 0) {
        SLOG_W("scanAll: invalid module range");
        std::lock_guard<std::recursive_mutex> g(mu_);
        for (auto& kv : sigs_) { kv.second.resolved = false; kv.second.address = 0; }
        return 0;
    }
    return scanRanges({MemRange{moduleBase, moduleSize}});
}

int SignatureManager::scanRanges(const std::vector<MemRange>& ranges) {
    std::lock_guard<std::recursive_mutex> g(mu_);
    int resolved = 0;
    for (auto& kv : sigs_) {
        auto& e = kv.second;
        e.resolved = false;
        e.address = 0;
        if (e.pattern.empty()) continue;

        std::vector<int> bytes;
        std::string err;
        if (!parsePattern(e.pattern, &bytes, &err)) {
            SLOG_W("sig %s: pattern invalid (%s)", e.name.c_str(), err.c_str());
            continue;
        }
        const uintptr_t hit = slime::scanRanges(ranges, bytes);
        if (hit != 0) {
            e.address = hit;
            e.resolved = true;
            ++resolved;
            SLOG_I("sig %s @ %p", e.name.c_str(), reinterpret_cast<void*>(e.address));
        }
    }
    return resolved;
}

int SignatureManager::resolvedCount() const {
    std::lock_guard<std::recursive_mutex> g(mu_);
    int n = 0;
    for (const auto& kv : sigs_) if (kv.second.resolved) ++n;
    return n;
}

int SignatureManager::totalCount() const {
    std::lock_guard<std::recursive_mutex> g(mu_);
    return static_cast<int>(sigs_.size());
}

std::vector<std::string> SignatureManager::unresolvedNames() const {
    std::lock_guard<std::recursive_mutex> g(mu_);
    std::vector<std::string> out;
    for (const auto& kv : sigs_) if (!kv.second.resolved) out.push_back(kv.first);
    std::sort(out.begin(), out.end());
    return out;
}

std::vector<SigEntry> SignatureManager::signatures() const {
    std::lock_guard<std::recursive_mutex> g(mu_);
    std::vector<SigEntry> out;
    out.reserve(sigs_.size());
    for (const auto& kv : sigs_) out.push_back(kv.second);
    std::sort(out.begin(), out.end(), [](const SigEntry& a, const SigEntry& b) { return a.name < b.name; });
    return out;
}

std::vector<std::pair<std::string, int>> SignatureManager::offsets() const {
    std::lock_guard<std::recursive_mutex> g(mu_);
    std::vector<std::pair<std::string, int>> out(offsets_.begin(), offsets_.end());
    std::sort(out.begin(), out.end());
    return out;
}

} // namespace slime
