package com.grinderwolf.swm.api.world;

import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.ListTag;
import com.grinderwolf.swm.api.utils.NibbleArray;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory representation of a SRF chunk section.
 */
@ApiStatus.Internal
public interface SlimeChunkSection {

    CompoundTag getBlockStatesTag();

    CompoundTag getBiomeTag();

    /**
     * Returns the block light data.
     *
     * @return A {@link NibbleArray} with the block light data.
     */
    @Nullable
    NibbleArray getBlockLight();

    /**
     * Returns the sky light data.
     *
     * @return A {@link NibbleArray} containing the sky light data.
     */
    @Nullable
    NibbleArray getSkyLight();
}
