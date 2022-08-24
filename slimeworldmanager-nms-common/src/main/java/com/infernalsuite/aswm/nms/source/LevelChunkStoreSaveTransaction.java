package com.infernalsuite.aswm.nms.source;

import java.io.Closeable;

/**
 * Allows chunk stores to implement their own saving strategies.
 * This can be done to for example collect multiple chunk saves.
 */
public interface LevelChunkStoreSaveTransaction extends Closeable {

    @Override
    void close();
}
