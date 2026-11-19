#pragma once
#include <mutex>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace slime {

/**
 * Module flags + numeric settings shared with Java (Flarial ModuleManager pattern).
 * Thread-safe: Java UI writes, hook detours (game thread) read.
 */
class ModuleBridge {
public:
    static ModuleBridge& instance();

    void setEnabled(const std::string& id, bool enabled);
    bool isEnabled(const std::string& id) const;

    void setValue(const std::string& id, const std::string& key, double value);
    double getValue(const std::string& id, const std::string& key, double def) const;

    void clear();
    int enabledCount() const;
    std::vector<std::pair<std::string, bool>> flags() const;     // sorted by id
    std::vector<std::pair<std::string, double>> values() const;  // "id.key" sorted

private:
    ModuleBridge() = default;
    mutable std::mutex mu_;
    std::unordered_map<std::string, bool> flags_;
    std::unordered_map<std::string, double> values_;
};

} // namespace slime
