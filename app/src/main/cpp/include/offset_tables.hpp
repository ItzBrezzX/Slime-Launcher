#pragma once
#include <string>
#include <vector>
#include <utility>

namespace slime {

struct VersionInfo {
    const char* id;
    const char* label;
    const char* arch; // "arm64" | "arm" | "x86_64"
};

/**
 * Per-version offset + signature registration.
 * Catalog names aligned with Flarial OffsetInit / Hook targets for portability of *names*.
 *
 * Values for Android are NOT copied from Windows Flarial binaries — different ABI, different layout.
 * Unset offsets stay 0; empty signature patterns stay unresolved until RE fills them.
 */
void registerOffsetsForVersion(const std::string& versionId);

const VersionInfo* supportedVersions(int* outCount);

/** Debug dump: name → offset (0 = unset). */
std::vector<std::pair<std::string, int>> listOffsets();
std::vector<std::pair<std::string, bool>> listSignatureStatus();

} // namespace slime
