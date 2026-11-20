#include "bedrock_engine.hpp"

#include <algorithm>
#include <cmath>
#include <limits>
#include <utility>

namespace slime::bedrock {
namespace {
constexpr uint32_t kHashA = 0x9E3779B9u;
constexpr uint32_t kHashB = 0x85EBCA6Bu;

double fade(double v) { return v * v * v * (v * (v * 6.0 - 15.0) + 10.0); }
double lerp(double a, double b, double t) { return a + (b - a) * t; }

// Four corners per face, ordered for two triangles. Coordinates are block-local.
struct Face {
    int dx, dy, dz;
    float normal[3];
    float corners[12];
};
constexpr Face kFaces[6] = {
    { 1,0,0,{ 1,0,0},{1,0,0, 1,1,0, 1,1,1, 1,0,1}},
    {-1,0,0,{-1,0,0},{0,0,1, 0,1,1, 0,1,0, 0,0,0}},
    {0, 1,0,{0, 1,0},{0,1,1, 1,1,1, 1,1,0, 0,1,0}},
    {0,-1,0,{0,-1,0},{0,0,0, 1,0,0, 1,0,1, 0,0,1}},
    {0,0, 1,{0,0, 1},{1,0,1, 1,1,1, 0,1,1, 0,0,1}},
    {0,0,-1,{0,0,-1},{0,0,0, 0,1,0, 1,1,0, 1,0,0}},
};
} // namespace

void Engine::createWorld(int64_t seed) {
    std::lock_guard<std::mutex> lock(mutex_);
    seed_ = seed; worldReady_ = true; chunks_.clear();
}
void Engine::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    chunks_.clear(); worldReady_ = false; seed_ = 0;
}
int64_t Engine::chunkKey(int x, int z) {
    const uint64_t packed = (static_cast<uint64_t>(static_cast<uint32_t>(x)) << 32) |
                            static_cast<uint32_t>(z);
    return static_cast<int64_t>(packed);
}
size_t Engine::blockIndex(int x, int y, int z) {
    return (static_cast<size_t>(y) * kChunkSize + static_cast<size_t>(z)) *
           kChunkSize + static_cast<size_t>(x);
}
uint32_t Engine::hash2(int64_t seed, int x, int z) {
    uint64_t v = static_cast<uint64_t>(seed);
    v ^= static_cast<uint64_t>(static_cast<uint32_t>(x)) * kHashA;
    v ^= static_cast<uint64_t>(static_cast<uint32_t>(z)) * kHashB;
    v ^= v >> 30; v *= 0xBF58476D1CE4E5B9ULL;
    v ^= v >> 27; v *= 0x94D049BB133111EBULL; v ^= v >> 31;
    return static_cast<uint32_t>(v >> 32);
}
double Engine::smoothNoise(int64_t seed, double x, double z) {
    const int x0 = static_cast<int>(std::floor(x));
    const int z0 = static_cast<int>(std::floor(z));
    const double tx = fade(x - x0), tz = fade(z - z0);
    const auto sample = [seed](int sx, int sz) {
        return static_cast<double>(hash2(seed, sx, sz)) /
               static_cast<double>(std::numeric_limits<uint32_t>::max());
    };
    return lerp(lerp(sample(x0,z0),sample(x0+1,z0),tx),
                lerp(sample(x0,z0+1),sample(x0+1,z0+1),tx),tz) * 2.0 - 1.0;
}
int Engine::terrainHeight(int x, int z) const {
    const double broad = smoothNoise(seed_, x / 72.0, z / 72.0);
    const double detail = smoothNoise(seed_ ^ 0x5DEECE66DLL, x / 19.0, z / 19.0);
    const double fine = smoothNoise(seed_ ^ 0xBADC0FFEE0DDF00DULL, x / 6.0, z / 6.0);
    return std::clamp(54 + static_cast<int>(broad*25.0 + detail*9.0 + fine*3.0),
                      8, kWorldHeight - 2);
}
BlockId Engine::blockAtUnlocked(int wx, int y, int wz) const {
    if (!worldReady_ || y < 0 || y >= kWorldHeight) return BlockId::Air;
    const int cx = static_cast<int>(std::floor(wx / 16.0));
    const int cz = static_cast<int>(std::floor(wz / 16.0));
    const auto it = chunks_.find(chunkKey(cx, cz));
    if (it == chunks_.end()) return BlockId::Air;
    const int lx = wx - cx*kChunkSize, lz = wz - cz*kChunkSize;
    return static_cast<BlockId>(it->second.blocks[blockIndex(lx,y,lz)]);
}
bool Engine::generateChunk(int cx, int cz) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!worldReady_) return false;
    const int64_t key = chunkKey(cx,cz);
    if (chunks_.find(key) != chunks_.end()) return true;
    Chunk chunk;
    chunk.blocks.assign(static_cast<size_t>(kChunkSize*kWorldHeight*kChunkSize),
                        static_cast<uint8_t>(BlockId::Air));
    chunk.stats.minHeight = kWorldHeight;
    for (int z=0; z<kChunkSize; ++z) for (int x=0; x<kChunkSize; ++x) {
        const int wx=cx*kChunkSize+x, wz=cz*kChunkSize+z, height=terrainHeight(wx,wz);
        chunk.stats.minHeight=std::min(chunk.stats.minHeight,height);
        chunk.stats.maxHeight=std::max(chunk.stats.maxHeight,height);
        for (int y=0; y<=height; ++y) {
            BlockId b=BlockId::Stone;
            if (y==0) b=BlockId::Bedrock;
            else if (y==height) b=BlockId::Grass;
            else if (y>=height-3) b=BlockId::Dirt;
            chunk.blocks[blockIndex(x,y,z)]=static_cast<uint8_t>(b);
            ++chunk.stats.solidBlocks;
        }
        for (int y=height+1; y<=kSeaLevel; ++y) {
            chunk.blocks[blockIndex(x,y,z)]=static_cast<uint8_t>(BlockId::Water);
            ++chunk.stats.waterBlocks;
        }
    }
    // Keep this statistic as a cheap local estimate; the actual mesh uses loaded neighbors.
    constexpr int dx[6]={1,-1,0,0,0,0}, dy[6]={0,0,1,-1,0,0}, dz[6]={0,0,0,0,1,-1};
    for (int y=0;y<kWorldHeight;++y) for (int z=0;z<kChunkSize;++z)
        for (int x=0;x<kChunkSize;++x) {
            const auto b=static_cast<BlockId>(chunk.blocks[blockIndex(x,y,z)]);
            if (b==BlockId::Air) continue;
            for (int f=0;f<6;++f) {
                const int nx=x+dx[f], ny=y+dy[f], nz=z+dz[f];
                if (nx<0||nx>=kChunkSize||ny<0||ny>=kWorldHeight||nz<0||nz>=kChunkSize||
                    chunk.blocks[blockIndex(nx,ny,nz)]==static_cast<uint8_t>(BlockId::Air))
                    ++chunk.stats.visibleFaces;
            }
        }
    chunks_.emplace(key,std::move(chunk));
    return true;
}
BlockId Engine::blockAt(int x,int y,int z) const {
    std::lock_guard<std::mutex> lock(mutex_); return blockAtUnlocked(x,y,z);
}
ChunkStats Engine::chunkStats(int x,int z) const {
    std::lock_guard<std::mutex> lock(mutex_);
    const auto it=chunks_.find(chunkKey(x,z));
    return it==chunks_.end()?ChunkStats{}:it->second.stats;
}
ChunkMesh Engine::buildChunkMesh(int cx,int cz) const {
    std::lock_guard<std::mutex> lock(mutex_);
    ChunkMesh mesh;
    const auto found=chunks_.find(chunkKey(cx,cz));
    if (found==chunks_.end()) return mesh;
    mesh.vertices.reserve(static_cast<size_t>(found->second.stats.visibleFaces)*4);
    mesh.indices.reserve(static_cast<size_t>(found->second.stats.visibleFaces)*6);
    for (int y=0;y<kWorldHeight;++y) for (int z=0;z<kChunkSize;++z)
        for (int x=0;x<kChunkSize;++x) {
            const auto block=static_cast<BlockId>(found->second.blocks[blockIndex(x,y,z)]);
            if (block==BlockId::Air) continue;
            for (const auto& face:kFaces) {
                if (blockAtUnlocked(cx*kChunkSize+x+face.dx,y+face.dy,
                                    cz*kChunkSize+z+face.dz)!=BlockId::Air) continue;
                const uint32_t base=static_cast<uint32_t>(mesh.vertices.size());
                for (int v=0;v<4;++v) {
                    const int p=v*3;
                    mesh.vertices.push_back({
                        static_cast<float>(x)+face.corners[p],
                        static_cast<float>(y)+face.corners[p+1],
                        static_cast<float>(z)+face.corners[p+2],
                        face.normal[0],face.normal[1],face.normal[2],
                        static_cast<uint32_t>(block)
                    });
                }
                mesh.indices.insert(mesh.indices.end(),
                    {base,base+1,base+2,base,base+2,base+3});
            }
        }
    return mesh;
}
uint64_t Engine::chunkStorageBytes(int cx,int cz) const {
    std::lock_guard<std::mutex> lock(mutex_);
    const auto it=chunks_.find(chunkKey(cx,cz));
    return it==chunks_.end()?0:static_cast<uint64_t>(it->second.blocks.capacity());
}
uint64_t Engine::totalStorageBytes() const {
    std::lock_guard<std::mutex> lock(mutex_);
    uint64_t total=0;
    for (const auto& entry:chunks_) total+=entry.second.blocks.capacity();
    return total;
}
uint32_t Engine::loadedChunkCount() const {
    std::lock_guard<std::mutex> lock(mutex_); return static_cast<uint32_t>(chunks_.size());
}
int64_t Engine::seed() const {
    std::lock_guard<std::mutex> lock(mutex_); return seed_;
}
} // namespace slime::bedrock
