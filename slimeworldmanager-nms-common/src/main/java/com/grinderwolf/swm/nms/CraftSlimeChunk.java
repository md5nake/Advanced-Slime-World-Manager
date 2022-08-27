package com.grinderwolf.swm.nms;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import org.bukkit.HeightMap;

import java.util.List;
import java.util.Map;

public class CraftSlimeChunk implements SlimeChunk {

    private final int x;
    private final int z;

    private SlimeChunkSection[] sections;
    private final Map<HeightMap, long[]> heightMaps;
    private final List<CompoundTag> tileEntities;

    public CraftSlimeChunk(int x, int z, SlimeChunkSection[] sections, Map<HeightMap, long[]> heightMaps, List<CompoundTag> tileEntities) {
        this.x = x;
        this.z = z;
        this.sections = sections;
        this.heightMaps = heightMaps;
        this.tileEntities = tileEntities;
    }

    @Override
    public int getX() {
        return this.x;
    }

    @Override
    public int getZ() {
        return this.z;
    }

    @Override
    public SlimeChunkSection[] getSections() {
        return this.sections;
    }

    @Override
    public Map<HeightMap, long[]> getHeightmaps() {
        return this.heightMaps;
    }

    @Override
    public List<CompoundTag> getTileEntities() {
        return this.tileEntities;
    }
}
