#include <cassert>
#include <cstdint>
#include "bedrock_engine.hpp"

int main() {
    using slime::bedrock::Engine;
    using slime::bedrock::BlockId;
    Engine engine;
    assert(!engine.generateChunk(0,0));
    engine.createWorld(123456789LL);
    assert(engine.generateChunk(0,0));
    assert(engine.generateChunk(0,0));
    assert(engine.loadedChunkCount()==1);
    const auto first=engine.chunkStats(0,0);
    assert(first.solidBlocks>0 && first.visibleFaces>0);
    assert(first.minHeight>=8 && first.maxHeight<Engine::kWorldHeight);
    int surface=-1;
    for(int y=Engine::kWorldHeight-1;y>=0;--y) {
        auto b=engine.blockAt(0,y,0);
        if(b!=BlockId::Air && b!=BlockId::Water) { surface=y; break; }
    }
    assert(surface>=0 && engine.blockAt(0,surface,0)==BlockId::Grass);
    const auto compactBytes=engine.chunkStorageBytes(0,0);
    assert(compactBytes==16u*128u*16u); // uint8 block IDs: 32 KiB per chunk
    const auto meshBefore=engine.buildChunkMesh(0,0);
    assert(!meshBefore.vertices.empty() && meshBefore.indices.size()%3==0);
    assert(first.visibleFaces==meshBefore.indices.size()/6);
    assert(meshBefore.vertices.size()==meshBefore.indices.size()/6*4);
    assert(meshBefore.memoryBytes()>0);
    engine.generateChunk(1,0);
    const auto meshAfter=engine.buildChunkMesh(0,0);
    assert(meshAfter.vertices.size()<=meshBefore.vertices.size());
    assert(engine.chunkStats(0,0).visibleFaces==meshAfter.indices.size()/6);
    assert(engine.totalStorageBytes()==2u*16u*128u*16u);
    engine.createWorld(123456789LL);
    assert(engine.loadedChunkCount()==0);
    assert(engine.generateChunk(0,0));
    const auto repeat=engine.chunkStats(0,0);
    assert(first.minHeight==repeat.minHeight && first.maxHeight==repeat.maxHeight);
    assert(first.solidBlocks==repeat.solidBlocks);
    engine.reset();
    assert(engine.loadedChunkCount()==0);
    assert(engine.blockAt(0,10,0)==BlockId::Air);
    return 0;
}
