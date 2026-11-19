#pragma once
#include <string>

namespace slime {
namespace effects {

/**
 * Pure "what should the game see" functions.
 * Hook detours (Camera::getFov, Options::getGamma, FogManager::getFogDistance, ...) call these
 * with the original value; they read module flags/values from ModuleBridge.
 * Disabled module → the base value is returned untouched.
 */
double fov(double baseFov, bool zoomHeld);
double gamma(double baseGamma);
double fogDistance(double baseDistance);
int timeTicks(int baseTicks);          // Bedrock: tick 0 = 06:00
double viewmodelScale(double baseScale);

/** kind: fov | gamma | fog | time | viewmodel. known=false → unknown kind. */
double evaluate(const std::string& kind, double base, bool* known);

} // namespace effects
} // namespace slime
