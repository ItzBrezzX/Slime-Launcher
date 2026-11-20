#include <cassert>
#include <cstdint>
#include "bedrock_engine.hpp"

int main() {
    slime::bedrock::Engine engine;
    assert(!engine.generateChunk(0, 0));
    assert(engine.loadedChunkCount() == 0);

    engine.createWorld(123456789LL);
    assert(engine.generateChunk(0, 0));
    assert(engine.generateChunk(0, 0));
    assert(engine.loadedChunkCount() == 1);

    const auto first = engine.chunkStats(0, 0);
    assert(first.solidBlocks > 0);
    assert(first.visibleFaces > 0);
    assert(first.minHeight >= 8);
    assert(first.maxHeight < slime::bedrock::Engine::kWorldHeight);

    const auto top = engine.blockAt(0, first.maxHeight, 0);
    assert(top == slime::bedrock::BlockId::Grass ||
           top == slime::bedrock::BlockId::Dirt);

    engine.createWorld(123456789LL);
    assert(engine.loadedChunkCount() == 0);
    assert(engine.generateChunk(0, 0));
    const auto repeat = engine.chunkStats(0, 0);
    assert(first.minHeight == repeat.minHeight);
    assert(first.maxHeight == repeat.maxHeight);
    assert(first.solidBlocks == repeat.solidBlocks);

    engine.reset();
    assert(engine.loadedChunkCount() == 0);
    assert(engine.blockAt(0, 10, 0) == slime::bedrock::BlockId::Air);
    return 0;
}
