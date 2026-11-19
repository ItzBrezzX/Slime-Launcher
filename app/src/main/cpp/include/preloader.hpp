#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace slime {

enum class PreloaderPhase {
    Idle,
    InitNative,
    LoadOffsets,
    ScanSignatures,
    ResolveHooks,
    Ready,
    Degraded,   // running without resolved hooks
    Error
};

struct PreloaderStatus {
    PreloaderPhase phase = PreloaderPhase::Idle;
    std::string versionId;
    std::string message;
    int sigResolved = 0;
    int sigTotal = 0;
    int hooksActive = 0;
    int hooksPending = 0;
    bool canInject = false;
    bool targetFound = false;     // libminecraftpe.so mapped in THIS process
    uintptr_t targetBase = 0;
    size_t targetSize = 0;
};

class Preloader {
public:
    static constexpr const char* kTargetModule = "libminecraftpe.so";

    static Preloader& instance();

    /** Full init pipeline for a catalog version id. */
    bool initialize(const std::string& versionId);

    PreloaderStatus status() const;
    void shutdown();

private:
    Preloader() = default;
    PreloaderStatus st_{};
};

/** Native self-test (parser, scanner, bridge, effects, process modules). Lines: "PASS x" / "FAIL x: why". */
std::vector<std::string> runSelfTest();

} // namespace slime
