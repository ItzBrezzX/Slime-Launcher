#include "bedrock_engine.hpp"

#include <algorithm>
#include <cmath>
#include <limits>

namespace slime::bedrock {
namespace {

constexpr uint32_t kHashA = 0x9E3779B9u;
constexpr uint32_t kHashB = 0x85EBCA6Bu;

double fade(double value) {
    return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
}

double lerp(double a, double b, double t) {
    return a + (b - a) * t;
}

} // namespace

void Engine::createWorld(int64_t seed) {
    std::lock_guard<std::mutex> lock(mutex_);
    seed_ = seed;
    worldReady_ = true;
    chunks_.clear();
}

void Engine::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    chunks_.clear();
    worldReady_ = false;
    seed_ = 0;
}

int64_t Engine::chunkKey(int x, int z) {
    return (static_cast<int64_t>(x) << 32) ^ static_cast<uint32_t>(z);
}

size_t Engine::blockIndex(int x, int y, int z) {
    return (static_cast<size_t>(y) * kChunkSize + static_cast<size_t>(z)) *
           kChunkSize + static_cast<size_t>(x);
}

uint32_t Engine::hash2(int64_t seed, int x, int z) {
    uint64_t value = static_cast<uint64_t>(seed);
    value ^= static_cast<uint64_t>(static_cast<uint32_t>(x)) * kHashA;
    value ^= static_cast<uint64_t>(static_cast<uint32_t>(z)) * kHashB;
    value ^= value >> 30;
    value *= 0xBF58476D1CE4E5B9ULL;
    value ^= value >> 27;
    value *= 0x94D049BB133111EBULL;
    value ^= value >> 31;
    return static_cast<uint32_t>(value >> 32);
}

double Engine::smoothNoise(int64_t seed, double x, double z) {
    const int x0 = static_cast<int>(std::floor(x));
    const int z0 = static_cast<int>(std::floor(z));
    const double tx = fade(x - static_cast<double>(x0));
    const double tz = fade(z - static_cast<double>(z0));
    const auto sample = [seed](int sx, int sz) {
        return static_cast<double>(hash2(seed, sx, sz)) /
               static_cast<double>(std::numeric_limits<uint32_t>::max());
    };
    const double a = lerp(sample(x0, z0), sample(x0 + 1, z0), tx);
    const double b = lerp(sample(x0, z0 + 1), sample(x0 + 1, z0 + 1), tx);
    return lerp(a, b, tz) * 2.0 - 1.0;
}

int Engine::terrainHeight(int worldX, int worldZ) const {
    const double broad = smoothNoise(seed_, worldX / 72.0, worldZ / 72.0);
    const double detail = smoothNoise(seed_ ^ 0x5DEECE66DLL,
                                      worldX / 19.0, worldZ / 19.0);
    const double fine = smoothNoise(seed_ ^ 0xBADC0FFEE0DDF00DULL,
                                    worldX / 6.0, worldZ / 6.0);
    const int height = 54 + static_cast<int>(broad * 25.0 +
                                              detail * 9.0 +
                                              fine * 3.0);
    return std::clamp(height, 8, kWorldHeight - 2);
}

BlockId Engine::blockAtUnlocked(int worldX, int y, int worldZ) const {
    if (!worldReady_ || y < 0 || y >= kWorldHeight) return BlockId::Air;
    const int chunkX = static_cast<int>(std::floor(worldX / 16.0));
    const int chunkZ = static_cast<int>(std::floor(worldZ / 16.0));
    const auto it = chunks_.find(chunkKey(chunkX, chunkZ));
    if (it == chunks_.end()) return BlockId::Air;
    const int localX = worldX - chunkX * kChunkSize;
    const int localZ = worldZ - chunkZ * kChunkSize;
    return static_cast<BlockId>(it->second.blocks[blockIndex(localX, y, localZ)]);
}

bool Engine::generateChunk(int chunkX, int chunkZ) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!worldReady_) return false;
    const int64_t key = chunkKey(chunkX, chunkZ);
    if (chunks_.find(key) != chunks_.end()) return true;

    Chunk chunk;
    chunk.blocks.assign(static_cast<size_t>(kChunkSize * kWorldHeight * kChunkSize),
                        static_cast<uint16_t>(BlockId::Air));
    chunk.stats.minHeight = kWorldHeight;
    chunk.stats.maxHeight = 0;

    for (int z = 0; z < kChunkSize; ++z) {
        for (int x = 0; x < kChunkSize; ++x) {
            const int worldX = chunkX * kChunkSize + x;
            const int worldZ = chunkZ * kChunkSize + z;
            const int height = terrainHeight(worldX, worldZ);
            chunk.stats.minHeight = std::min(chunk.stats.minHeight, height);
            chunk.stats.maxHeight = std::max(chunk.stats.maxHeight, height);

            for (int y = 0; y <= height; ++y) {
                BlockId block = BlockId::Stone;
                if (y == 0) block = BlockId::Bedrock;
                else if (y == height) block = BlockId::Grass;
                else if (y >= height - 3) block = BlockId::Dirt;
                chunk.blocks[blockIndex(x, y, z)] = static_cast<uint16_t>(block);
                ++chunk.stats.solidBlocks;
            }
            for (int y = height + 1; y <= kSeaLevel; ++y) {
                chunk.blocks[blockIndex(x, y, z)] = static_cast<uint16_t>(BlockId::Water);
                ++chunk.stats.waterBlocks;
            }
        }
    }

    constexpr int dx[6] = {1, -1, 0, 0, 0, 0};
    constexpr int dy[6] = {0, 0, 1, -1, 0, 0};
    constexpr int dz[6] = {0, 0, 0, 0, 1, -1};
    for (int y = 0; y < kWorldHeight; ++y) {
        for (int z = 0; z < kChunkSize; ++z) {
            for (int x = 0; x < kChunkSize; ++x) {
                const BlockId block = static_cast<BlockId>(
                    chunk.blocks[blockIndex(x, y, z)]);
                if (block == BlockId::Air) continue;
                for (int face = 0; face < 6; ++face) {
                    const int nx = x + dx[face];
                    const int ny = y + dy[face];
                    const int nz = z + dz[face];
                    const bool outside = nx < 0 || nx >= kChunkSize ||
                                         ny < 0 || ny >= kWorldHeight ||
                                         nz < 0 || nz >= kChunkSize;
                    const bool neighborAir = outside ||
                        chunk.blocks[blockIndex(nx, ny, nz)] ==
                            static_cast<uint16_t>(BlockId::Air);
                    if (neighborAir) ++chunk.stats.visibleFaces;
                }
            }
        }
    }

    chunks_.emplace(key, std::move(chunk));
    return true;
}

BlockId Engine::blockAt(int worldX, int y, int worldZ) const {
    std::lock_guard<std::mutex> lock(mutex_);
    return blockAtUnlocked(worldX, y, worldZ);
}

ChunkStats Engine::chunkStats(int chunkX, int chunkZ) const {
    std::lock_guard<std::mutex> lock(mutex_);
    const auto it = chunks_.find(chunkKey(chunkX, chunkZ));
    return it == chunks_.end() ? ChunkStats{} : it->second.stats;
}

uint32_t Engine::loadedChunkCount() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return static_cast<uint32_t>(chunks_.size());
}

int64_t Engine::seed() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return seed_;
}

} // namespace slime::bedrock
