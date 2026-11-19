#include "effects.hpp"
#include "module_bridge.hpp"
#include <algorithm>
#include <cmath>

namespace slime {
namespace effects {

double fov(double baseFov, bool zoomHeld) {
    auto& mb = ModuleBridge::instance();
    if (!zoomHeld || !mb.isEnabled("zoom")) return baseFov;
    const double level = std::clamp(mb.getValue("zoom", "level", 4.0), 1.0, 20.0);
    return std::max(5.0, baseFov / level);
}

double gamma(double baseGamma) {
    auto& mb = ModuleBridge::instance();
    if (!mb.isEnabled("fullbright")) return baseGamma;
    return std::clamp(mb.getValue("fullbright", "gamma", 10.0), 0.0, 25.0);
}

double fogDistance(double baseDistance) {
    auto& mb = ModuleBridge::instance();
    if (!mb.isEnabled("fog")) return baseDistance;
    return baseDistance * std::clamp(mb.getValue("fog", "density", 0.6), 0.0, 4.0);
}

int timeTicks(int baseTicks) {
    auto& mb = ModuleBridge::instance();
    if (!mb.isEnabled("time")) return baseTicks;
    const double hour = std::clamp(mb.getValue("time", "hour", 12.0), 0.0, 24.0);
    const int h = static_cast<int>(std::lround(hour)) % 24;
    return ((h + 18) % 24) * 1000;
}

double viewmodelScale(double baseScale) {
    auto& mb = ModuleBridge::instance();
    if (!mb.isEnabled("viewmodel")) return baseScale;
    return baseScale * std::clamp(mb.getValue("viewmodel", "scale", 1.0), 0.1, 3.0);
}

double evaluate(const std::string& kind, double base, bool* known) {
    if (known) *known = true;
    if (kind == "fov") return fov(base, true);
    if (kind == "gamma") return gamma(base);
    if (kind == "fog") return fogDistance(base);
    if (kind == "time") return static_cast<double>(timeTicks(static_cast<int>(base)));
    if (kind == "viewmodel") return viewmodelScale(base);
    if (known) *known = false;
    return base;
}

} // namespace effects
} // namespace slime
