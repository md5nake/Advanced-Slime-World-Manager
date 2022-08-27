package com.grinderwolf.swm.nms.v1192;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.nms.NmsUtil;
import com.infernalsuite.aswm.nms.level.SlimeWorldLevelWrapper;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.bukkit.generator.BiomeProvider;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SlimeServerLevel extends ServerLevel {

    private static final TicketType<Unit> SWM_TICKET = TicketType.create("swm-chunk", (a, b) -> 0);

    @Getter
    private final v1192SlimeWorld slimeWorld;
    private final Object saveLock = new Object();
    private final BiomeSource defaultBiomeSource;

    @Getter
    @Setter
    private boolean ready = false;

    private final SlimeWorldLevelWrapper<v1192SlimeWorld, ChunkAccess> worldLevelWrapper;

    public SlimeServerLevel(v1192SlimeWorld world, PrimaryLevelData primaryLevelData,
                            ResourceKey<net.minecraft.world.level.Level> worldKey,
                            ResourceKey<LevelStem> dimensionKey, LevelStem worldDimension,
                            org.bukkit.World.Environment environment, org.bukkit.generator.ChunkGenerator gen,
                            BiomeProvider biomeProvider) throws IOException {

        super(MinecraftServer.getServer(), MinecraftServer.getServer().executor,
                v1192SlimeNMS.CUSTOM_LEVEL_STORAGE.createAccess(world.getName() + UUID.randomUUID(),
                dimensionKey), primaryLevelData, worldKey, worldDimension,
                MinecraftServer.getServer().progressListenerFactory.create(11), false, 0,
                Collections.emptyList(), true, environment, gen, biomeProvider);

        this.worldLevelWrapper = new v1192SlimeWorldLevelWrapper(world, this.getWorld());
        this.slimeWorld = world;

        SlimePropertyMap propertyMap = world.getPropertyMap();
        // Slime property map configuration
        this.serverLevelData.setDifficulty(Difficulty.valueOf(propertyMap.getValue(SlimeProperties.DIFFICULTY).toUpperCase()));
        this.serverLevelData.setSpawn(new BlockPos(propertyMap.getValue(SlimeProperties.SPAWN_X), propertyMap.getValue(SlimeProperties.SPAWN_Y), propertyMap.getValue(SlimeProperties.SPAWN_Z)), 0);
        this.setSpawnSettings(propertyMap.getValue(SlimeProperties.ALLOW_MONSTERS), propertyMap.getValue(SlimeProperties.ALLOW_ANIMALS));
        this.pvpMode = propertyMap.getValue(SlimeProperties.PVP);
        {
            String biomeStr = slimeWorld.getPropertyMap().getValue(SlimeProperties.DEFAULT_BIOME);
            ResourceKey<Biome> biomeKey = ResourceKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(biomeStr));
            Holder<Biome> defaultBiome = MinecraftServer.getServer().registryAccess().ownedRegistryOrThrow(Registry.BIOME_REGISTRY).getHolder(biomeKey).orElseThrow();

            this.defaultBiomeSource = new FixedBiomeSource(defaultBiome);
        }
        // --

        this.keepSpawnInMemory = false;
    }

    @Override
    public void save(@Nullable ProgressListener progressUpdate, boolean forceSave, boolean savingDisabled) {
        if (!savingDisabled) {
            return;
        }

        this.worldLevelWrapper.save();
    }

    public CompletableFuture<ChunkEntities<Entity>> handleEntityLoad(EntityStorage storage, ChunkPos pos) {
        List<CompoundTag> entities = slimeWorld.getEntities().get(NmsUtil.asLong(pos.x, pos.z));
        if (entities == null) {
            entities = new ArrayList<>();
        }

        return CompletableFuture.completedFuture(new ChunkEntities<>(pos, new ArrayList<>(
                EntityType.loadEntitiesRecursive(entities
                                .stream()
                                .map((tag) -> (net.minecraft.nbt.CompoundTag) Converter.convertTag(tag))
                                .collect(Collectors.toList()), this)
                        .toList()
        )));


    }

    public void handleEntityUnLoad(EntityStorage storage, ChunkEntities<Entity> entities) {
        ChunkPos pos = entities.getPos();
        List<CompoundTag> entitiesSerialized = new ArrayList<>();

        entities.getEntities().forEach((entity) -> {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            if (entity.save(tag)) {
                entitiesSerialized.add((CompoundTag) Converter.convertTag("", tag));
            }

        });

        slimeWorld.getEntities().put(NmsUtil.asLong(pos.x, pos.z), entitiesSerialized);
    }


    public SlimeWorldLevelWrapper<v1192SlimeWorld, ChunkAccess> getWorldLevelWrapper() {
        return worldLevelWrapper;
    }
}