#pragma once
#include <cstdint>
#include <string>
#include <vector>

namespace slime {

enum class HookStatus {
    Idle,           // target resolved, ready to install
    Pending,        // signature missing / unresolved
    Active,         // installed
    Failed
};

struct HookDesc {
    std::string name;
    std::string sigName;      // key in SignatureManager
    HookStatus status = HookStatus::Pending;
    uintptr_t target = 0;
    std::string reason;       // human-readable why (shown in the Mod Menu)
};

const char* hookStatusName(HookStatus s);

/**
 * Hook registry — architecture similar to Flarial Client/Hook/ directory.
 * On Android, true inline hooks require process co-location (Levi-style
 * System.load into game) plus a trampoline backend. The registry tracks intent +
 * resolution state and never reports Active without a real installed hook.
 */
class HookRegistry {
public:
    static HookRegistry& instance();

    void registerHook(const std::string& name, const std::string& sigName);
    void resolveFromSignatures();
    HookStatus status(const std::string& name) const;
    int activeCount() const;
    int pendingCount() const;
    std::vector<HookDesc> all() const;

    /** Attempt install — returns true only if target resolved and the backend allows it. */
    bool tryInstall(const std::string& name);
    /** tryInstall for every hook; returns how many ended Active. */
    int installAll();

    void reset();

private:
    HookRegistry() = default;
    std::vector<HookDesc> hooks_;
};

void registerDefaultHooks();

} // namespace slime
