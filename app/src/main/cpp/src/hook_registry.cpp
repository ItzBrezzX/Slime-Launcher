#include "hook_registry.hpp"
#include "signature_manager.hpp"
#include "slime_log.hpp"
#include <mutex>
#ifdef __ANDROID__
#include <shadowhook.h>
#endif

namespace slime {

const char* hookStatusName(HookStatus s) {
    switch (s) {
        case HookStatus::Idle: return "IDLE";
        case HookStatus::Pending: return "PENDING";
        case HookStatus::Active: return "ACTIVE";
        case HookStatus::Failed: return "FAILED";
    }
    return "IDLE";
}

HookRegistry& HookRegistry::instance() {
    static HookRegistry s;
    return s;
}

void HookRegistry::registerHook(const std::string& name, const std::string& sigName) {
    HookDesc d;
    d.name = name;
    d.sigName = sigName;
    d.status = HookStatus::Pending;
    d.target = 0;
    d.replacement = nullptr;
    d.original = nullptr;
    d.stub = nullptr;
    d.reason = "signature belum di-resolve";
    hooks_.push_back(d);
}


void HookRegistry::registerDetour(const std::string& name, void* replacement) {
    for (auto& h : hooks_) {
        if (h.name != name) continue;
        h.replacement = replacement;
        h.reason = replacement ? "detour terdaftar" : "detour belum terdaftar";
        if (h.target == 0) h.status = HookStatus::Pending;
        else if (h.status != HookStatus::Active) h.status = HookStatus::Idle;
        return;
    }
    SLOG_W("detour %s ignored: hook not registered", name.c_str());
}

void* HookRegistry::originalFor(const std::string& name) const {
    for (const auto& h : hooks_) if (h.name == name) return h.original;
    return nullptr;
}

bool HookRegistry::initializeBackend() {
#ifdef __ANDROID__
    static std::once_flag once;
    static bool ready = false;
    std::call_once(once, [] {
        const int result = shadowhook_init(SHADOWHOOK_MODE_UNIQUE, false);
        ready = result == 0;
        if (ready) {
            SLOG_I("ShadowHook %s initialized", shadowhook_get_version());
        } else {
            // The packaged ShadowHook 2.0.0 library does not export
            // shadowhook_get_init_errno on all supported ABIs.
            SLOG_E("ShadowHook init failed with code %d", result);
        }
    });
    return ready;
#else
    SLOG_W("ShadowHook is Android-only; native host tests do not install hooks");
    return false;
#endif
}

void HookRegistry::resolveFromSignatures() {
    auto& sigs = SignatureManager::instance();
    for (auto& h : hooks_) {
        if (sigs.isResolved(h.sigName)) {
            h.target = sigs.getAddress(h.sigName);
            h.status = HookStatus::Idle;
            h.reason = "target ditemukan, siap dipasang";
        } else {
            h.target = 0;
            h.status = HookStatus::Pending;
            h.reason = "signature belum diisi / tidak ditemukan di modul target";
        }
    }
}

HookStatus HookRegistry::status(const std::string& name) const {
    for (const auto& h : hooks_) if (h.name == name) return h.status;
    return HookStatus::Idle;
}

int HookRegistry::activeCount() const {
    int n = 0;
    for (const auto& h : hooks_) if (h.status == HookStatus::Active) ++n;
    return n;
}

int HookRegistry::pendingCount() const {
    int n = 0;
    for (const auto& h : hooks_) if (h.status == HookStatus::Pending) ++n;
    return n;
}

std::vector<HookDesc> HookRegistry::all() const {
    return hooks_;
}

bool HookRegistry::tryInstall(const std::string& name) {
    for (auto& h : hooks_) {
        if (h.name != name) continue;
        if (h.status == HookStatus::Active && h.stub != nullptr) return true;
        if (h.target == 0) {
            h.status = HookStatus::Pending;
            h.reason = "target signature belum ter-resolve";
            SLOG_W("hook %s: no target address", name.c_str());
            return false;
        }
        if (h.replacement == nullptr) {
            h.status = HookStatus::Failed;
            h.reason = "detour function belum didaftarkan";
            SLOG_W("hook %s: detour function missing", name.c_str());
            return false;
        }
        if (!initializeBackend()) {
            h.status = HookStatus::Failed;
            h.reason = "ShadowHook backend belum siap";
            return false;
        }
#ifdef __ANDROID__
        h.original = nullptr;
        h.stub = shadowhook_hook_func_addr(
            reinterpret_cast<void*>(h.target),
            h.replacement,
            &h.original
        );
        if (h.stub == nullptr || h.original == nullptr) {
            const int error = shadowhook_get_errno();
            h.status = HookStatus::Failed;
            h.reason = std::string("ShadowHook gagal: ") + shadowhook_to_errmsg(error);
            SLOG_W("hook %s install failed: %s", name.c_str(), h.reason.c_str());
            if (h.stub != nullptr) {
                shadowhook_unhook(h.stub);
                h.stub = nullptr;
            }
            h.original = nullptr;
            return false;
        }
#else
        h.status = HookStatus::Failed;
        h.reason = "ShadowHook hanya tersedia pada Android";
        return false;
#endif
        h.status = HookStatus::Active;
        h.reason = "ShadowHook inline hook active";
        SLOG_I("hook %s installed at %p", name.c_str(), reinterpret_cast<void*>(h.target));
        return true;
    }
    return false;
}

int HookRegistry::installAll() {
    int ok = 0;
    for (const auto& h : hooks_) {
        const std::string n = h.name; // tryInstall mutates the vector entries, not the vector itself
        if (tryInstall(n)) ++ok;
    }
    return ok;
}

void HookRegistry::reset() {
    for (auto& h : hooks_) {
        if (h.stub != nullptr) {
#ifdef __ANDROID__
            const int result = shadowhook_unhook(h.stub);
            if (result != 0) {
                const int error = shadowhook_get_errno();
                SLOG_W("hook %s unhook failed: %s", h.name.c_str(), shadowhook_to_errmsg(error));
            }
#endif
            h.stub = nullptr;
            h.original = nullptr;
        }
    }
    hooks_.clear();
}

void registerDefaultHooks() {
    auto& hr = HookRegistry::instance();
    hr.reset();
    hr.registerHook("ClientInstanceUpdate", "ClientInstance::update");
    hr.registerHook("ActorBaseTick", "Actor::baseTick");
    hr.registerHook("GameModeAttack", "GameMode::attack");
    hr.registerHook("DisplayClientMessage", "GuiData::displayClientMessage");
    hr.registerHook("CameraFov", "Camera::getFov");
    hr.registerHook("OptionsGamma", "Options::getGamma");
    hr.registerHook("FogDistance", "FogManager::getFogDistance");
    hr.registerHook("RakNetTick", "RakNetInstance::tick");
    SLOG_I("default hooks registered: %zu", hr.all().size());
}

} // namespace slime
