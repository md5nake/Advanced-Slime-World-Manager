package com.grinderwolf.swm.nms;

import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.ListTag;
import com.grinderwolf.swm.api.utils.NibbleArray;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

public class CraftSlimeChunkSection implements SlimeChunkSection {

    // Post 1.17 block data
    private CompoundTag blockStatesTag;
    private CompoundTag biomeTag;

    @Nullable
    private final NibbleArray blockLight;
    @Nullable
    private final NibbleArray skyLight;

    public CraftSlimeChunkSection(CompoundTag blockStatesTag, CompoundTag biomeTag, @Nullable NibbleArray blockLight, @Nullable NibbleArray skyLight) {
        this.blockStatesTag = blockStatesTag;
        this.biomeTag = biomeTag;
        this.blockLight = blockLight;
        this.skyLight = skyLight;
    }

    @Override
    public CompoundTag getBlockStatesTag() {
        return blockStatesTag;
    }

    @Override
    public CompoundTag getBiomeTag() {
        return biomeTag;
    }

    @Nullable
    @Override
    public NibbleArray getBlockLight() {
        return blockLight;
    }

    @Nullable
    @Override
    public NibbleArray getSkyLight() {
        return skyLight;
    }
}
