#include "module_bridge.hpp"
#include <algorithm>

namespace slime {

ModuleBridge& ModuleBridge::instance() {
    static ModuleBridge s;
    return s;
}

void ModuleBridge::setEnabled(const std::string& id, bool enabled) {
    std::lock_guard<std::mutex> g(mu_);
    flags_[id] = enabled;
}

bool ModuleBridge::isEnabled(const std::string& id) const {
    std::lock_guard<std::mutex> g(mu_);
    auto it = flags_.find(id);
    return it != flags_.end() && it->second;
}

void ModuleBridge::setValue(const std::string& id, const std::string& key, double value) {
    std::lock_guard<std::mutex> g(mu_);
    values_[id + "." + key] = value;
}

double ModuleBridge::getValue(const std::string& id, const std::string& key, double def) const {
    std::lock_guard<std::mutex> g(mu_);
    auto it = values_.find(id + "." + key);
    return it == values_.end() ? def : it->second;
}

void ModuleBridge::clear() {
    std::lock_guard<std::mutex> g(mu_);
    flags_.clear();
    values_.clear();
}

int ModuleBridge::enabledCount() const {
    std::lock_guard<std::mutex> g(mu_);
    int n = 0;
    for (const auto& kv : flags_) if (kv.second) ++n;
    return n;
}

std::vector<std::pair<std::string, bool>> ModuleBridge::flags() const {
    std::lock_guard<std::mutex> g(mu_);
    std::vector<std::pair<std::string, bool>> out(flags_.begin(), flags_.end());
    std::sort(out.begin(), out.end());
    return out;
}

std::vector<std::pair<std::string, double>> ModuleBridge::values() const {
    std::lock_guard<std::mutex> g(mu_);
    std::vector<std::pair<std::string, double>> out(values_.begin(), values_.end());
    std::sort(out.begin(), out.end());
    return out;
}

} // namespace slime
