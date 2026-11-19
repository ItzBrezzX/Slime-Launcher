#include "hook_registry.hpp"
#include "signature_manager.hpp"
#include "slime_log.hpp"

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
    d.reason = "signature belum di-resolve";
    hooks_.push_back(d);
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
        if (h.status == HookStatus::Active) return true;
        if (h.target == 0) {
            h.status = HookStatus::Pending;
            h.reason = "tidak ada alamat target (signature belum ter-resolve)";
            SLOG_W("hook %s: no target address", name.c_str());
            return false;
        }
        // No trampoline backend is linked and stock MC is a separate process.
        // Never claim Active without a real patched function.
        h.status = HookStatus::Failed;
        h.reason = "backend inline-hook belum tersedia; butuh trampoline + co-load in-process";
        SLOG_W("hook %s: install gated (%s)", name.c_str(), h.reason.c_str());
        return false;
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
