package com.infernalsuite.aswm.nms.source;

public interface LevelChunkStore<CHUNK> {

    CHUNK loadChunk(int x, int z);

    LevelChunkStoreSaveTransaction save();
}
