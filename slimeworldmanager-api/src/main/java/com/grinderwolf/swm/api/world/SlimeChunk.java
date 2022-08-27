package com.grinderwolf.swm.api.world;

import com.flowpowered.nbt.CompoundTag;
import org.bukkit.HeightMap;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;

/**
 * In-memory representation of a SRF chunk.
 */
@ApiStatus.Internal
public interface SlimeChunk {

    /**
     * Returns the X coordinate of the chunk.
     *
     * @return X coordinate of the chunk.
     */
    int getX();

    /**
     * Returns the Z coordinate of the chunk.
     *
     * @return Z coordinate of the chunk.
     */
    int getZ();

    /**
     * Returns all the sections of the chunk.
     *
     * @return A {@link SlimeChunkSection} array.
     */
    SlimeChunkSection[] getSections();

    Map<HeightMap, long[]> getHeightmaps();

    /**
     * Returns all the tile entities of the chunk.
     *
     * @return A {@link CompoundTag} containing all the tile entities of the chunk.
     */
    List<CompoundTag> getTileEntities();

}
