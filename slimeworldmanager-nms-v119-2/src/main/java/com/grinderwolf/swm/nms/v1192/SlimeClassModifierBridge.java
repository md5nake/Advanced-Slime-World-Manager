package com.grinderwolf.swm.nms.v1192;

import com.grinderwolf.swm.clsm.CLSMBridge;
import com.grinderwolf.swm.clsm.ClassModifier;
import com.mojang.datafixers.util.Either;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;

public class SlimeClassModifierBridge implements CLSMBridge {

    private final v1192SlimeNMS nmsInstance;

    SlimeClassModifierBridge(v1192SlimeNMS nmsInstance) {
        this.nmsInstance = nmsInstance;
    }

    @Override
    public Object getChunk(Object worldObject, int x, int z) {
        SlimeServerLevel world = (SlimeServerLevel) worldObject;

        return Either.left(world.getWorldLevelWrapper().getVanillaChunkAccess(x, z));
    }

    @Override
    public boolean saveChunk(Object world, Object chunkAccess) {
        if (!(world instanceof SlimeServerLevel)) {
            return false; // Returning false will just run the original saveChunk method
        }

        // Skip the individual chunk saving logic for chunks
        return true;
    }

    @Override
    public Object loadEntities(Object storage, Object chunkCoords) {
        EntityStorage entityStorage = (EntityStorage) storage;
        if (!isCustomWorld(entityStorage.level)) {
            return null;
        }

        return ((SlimeServerLevel) entityStorage.level).handleEntityLoad(entityStorage, (ChunkPos) chunkCoords);
    }

    @Override
    public boolean storeEntities(Object storage, Object entityList) {
        EntityStorage entityStorage = (EntityStorage) storage;
        if (!isCustomWorld(entityStorage.level)) {
            return false;
        }

        ((SlimeServerLevel) entityStorage.level).handleEntityUnLoad(entityStorage, (ChunkEntities<Entity>) entityList);
        return true;
    }

    @Override
    public boolean flushEntities(Object storage) {
        EntityStorage entityStorage = (EntityStorage) storage;
        return isCustomWorld(entityStorage.level);
    }

    @Override
    public boolean isCustomWorld(Object world) {
        if (world instanceof SlimeServerLevel) {
            return true;
        } else if (world instanceof Level) {
            return false;
        } else {
            throw new IllegalStateException("World is probably not a world, was given %s. Check the classmodifier to ensure the correct level field is passed (check for field name changes)".formatted(world));
        }
    }

    @Override
    public Object injectCustomWorlds() {
        return nmsInstance.injectDefaultWorlds();
    }

    static void initialize(v1192SlimeNMS instance) {
        ClassModifier.setLoader(new SlimeClassModifierBridge(instance));
    }
}
