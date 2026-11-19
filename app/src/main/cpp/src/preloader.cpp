#include "preloader.hpp"
#include "offset_tables.hpp"
#include "signature_manager.hpp"
#include "hook_registry.hpp"
#include "module_bridge.hpp"
#include "module_scanner.hpp"
#include "slime_log.hpp"

namespace slime {

Preloader& Preloader::instance() {
    static Preloader s;
    return s;
}

bool Preloader::initialize(const std::string& versionId) {
    st_ = PreloaderStatus{};
    st_.versionId = versionId;
    st_.phase = PreloaderPhase::InitNative;
    st_.message = "native init";
    SLOG_I("preloader init version=%s", versionId.c_str());

    st_.phase = PreloaderPhase::LoadOffsets;
    st_.message = "loading offset table";
    registerOffsetsForVersion(versionId);
    auto& sigs = SignatureManager::instance();
    sigs.applyOverrides();
    registerDefaultHooks();
    st_.sigTotal = sigs.totalCount();

    st_.phase = PreloaderPhase::ScanSignatures;
    st_.message = "scanning signatures";
    LoadedModule target;
    if (findLoadedModule(kTargetModule, &target)) {
        st_.targetFound = true;
        st_.targetBase = target.base;
        st_.targetSize = target.size;
        SLOG_I("target %s base=%p size=%zu", target.name.c_str(),
               reinterpret_cast<void*>(target.base), target.size);
        st_.sigResolved = sigs.scanRanges(target.readable);
    } else {
        // Stock Minecraft is a separate process: its image is not mapped here.
        st_.sigResolved = sigs.scanAll(0, 0);
    }

    st_.phase = PreloaderPhase::ResolveHooks;
    st_.message = "resolving hooks";
    HookRegistry::instance().resolveFromSignatures();
    st_.hooksActive = HookRegistry::instance().activeCount();
    st_.hooksPending = HookRegistry::instance().pendingCount();

    if (!st_.targetFound) {
        st_.phase = PreloaderPhase::Degraded;
        st_.message = "degraded: libminecraftpe.so tidak termuat di proses ini (butuh co-load)";
        st_.canInject = false;
        SLOG_W("%s", st_.message.c_str());
        return true;
    }
    if (st_.sigResolved == 0) {
        st_.phase = PreloaderPhase::Degraded;
        st_.message = "degraded: target ada, 0 signature cocok (isi/cek pattern)";
        st_.canInject = false;
        SLOG_W("%s", st_.message.c_str());
        return true;
    }

    st_.phase = PreloaderPhase::Ready;
    st_.message = "ready";
    st_.canInject = true;
    return true;
}

PreloaderStatus Preloader::status() const {
    return st_;
}

void Preloader::shutdown() {
    HookRegistry::instance().reset();
    SignatureManager::instance().clear();
    ModuleBridge::instance().clear();
    st_ = PreloaderStatus{};
    st_.phase = PreloaderPhase::Idle;
    st_.message = "shutdown";
    SLOG_I("preloader shutdown");
}

} // namespace slime
