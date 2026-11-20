#pragma once

#include <cstdint>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>

namespace slime::bedrock {

enum class BlockId : uint16_t {
    Air = 0,
    Grass = 1,
    Dirt = 2,
    Stone = 3,
    Water = 4,
    Sand = 5,
    Bedrock = 6,
};

struct ChunkStats {
    uint32_t solidBlocks = 0;
    uint32_t waterBlocks = 0;
    uint32_t visibleFaces = 0;
    int minHeight = 0;
    int maxHeight = 0;
};

class Engine final {
public:
    static constexpr int kChunkSize = 16;
    static constexpr int kWorldHeight = 128;
    static constexpr int kSeaLevel = 40;

    Engine() = default;
    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;

    void createWorld(int64_t seed);
    void reset();
    bool generateChunk(int chunkX, int chunkZ);
    BlockId blockAt(int worldX, int y, int worldZ) const;
    ChunkStats chunkStats(int chunkX, int chunkZ) const;
    uint32_t loadedChunkCount() const;
    int64_t seed() const;

private:
    struct Chunk {
        std::vector<uint16_t> blocks;
        ChunkStats stats;
    };

    static int64_t chunkKey(int x, int z);
    static size_t blockIndex(int x, int y, int z);
    static uint32_t hash2(int64_t seed, int x, int z);
    static double smoothNoise(int64_t seed, double x, double z);
    int terrainHeight(int worldX, int worldZ) const;
    BlockId blockAtUnlocked(int worldX, int y, int worldZ) const;

    mutable std::mutex mutex_;
    int64_t seed_ = 0;
    bool worldReady_ = false;
    std::unordered_map<int64_t, Chunk> chunks_;
};

} // namespace slime::bedrock
