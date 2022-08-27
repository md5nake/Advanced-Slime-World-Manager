package com.grinderwolf.swm.nms.v1192;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.LongArrayTag;
import com.grinderwolf.swm.api.utils.NibbleArray;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.nms.CraftSlimeChunk;
import com.infernalsuite.aswm.nms.level.SlimeWorldLevelWrapper;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class v1192SlimeWorldLevelWrapper extends SlimeWorldLevelWrapper<v1192SlimeWorld, ChunkAccess> {

    public v1192SlimeWorldLevelWrapper(v1192SlimeWorld world, World bukkitWorld) {
        super(world, bukkitWorld);
    }

    @Override
    public ChunkAccess getVanillaChunkAccess(int x, int z) {
        SlimeChunk slimeChunk = this.world.getChunk(x, z);
        LevelChunk chunk;

        if (slimeChunk == null) {
            ChunkPos pos = new ChunkPos(x, z);
            LevelChunkTicks<Block> blockLevelChunkTicks = new LevelChunkTicks<>();
            LevelChunkTicks<Fluid> fluidLevelChunkTicks = new LevelChunkTicks<>();

            chunk = new LevelChunk(this.world.getHandle(), pos, UpgradeData.EMPTY, blockLevelChunkTicks, fluidLevelChunkTicks,
                    0L, null, null, null);

            this.world.updateChunk(new NMSSlimeChunk(null, chunk));
        } else if (slimeChunk instanceof NMSSlimeChunk) {
            chunk = ((NMSSlimeChunk) slimeChunk).getChunk(); // This shouldn't happen anymore, unloading should cleanup the chunk
        } else {
            AtomicReference<NMSSlimeChunk> jank = new AtomicReference<>();
            chunk = convertChunk(slimeChunk, () -> {
                jank.get().dirtySlime();
            });

            NMSSlimeChunk nmsSlimeChunk = new NMSSlimeChunk(slimeChunk, chunk);
            jank.set(nmsSlimeChunk);

            this.world.updateChunk(nmsSlimeChunk);
        }

        return new ImposterProtoChunk(chunk, false);
    }

    private SlimeChunk convertChunk(NMSSlimeChunk chunk) {
        return new CraftSlimeChunk(
                chunk.getWorldName(), chunk.getX(), chunk.getZ(),
                chunk.getSections(), chunk.getHeightMaps(),
                new int[0], chunk.getTileEntities(), new ArrayList<>(),
                chunk.getMinSection(), chunk.getMaxSection());
    }

    private LevelChunk convertChunk(SlimeChunk chunk, Runnable onUnload) {
        ServerLevel level = this.world.getHandle();
        int x = chunk.getX();
        int z = chunk.getZ();

        ChunkPos pos = new ChunkPos(x, z);

        // Chunk sections
        LevelChunkSection[] sections = new LevelChunkSection[level.getSectionsCount()];

        Object[] blockNibbles = null;
        Object[] skyNibbles = null;
        if (v1192SlimeNMS.isPaperMC) {
            blockNibbles = ca.spottedleaf.starlight.common.light.StarLightEngine.getFilledEmptyLight(level);
            skyNibbles = ca.spottedleaf.starlight.common.light.StarLightEngine.getFilledEmptyLight(level);
            level.getServer().scheduleOnMain(() -> {
                level.getLightEngine().retainData(pos, true);
            });
        }

        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        // Ignore deprecated method

        Codec<PalettedContainer<Holder<Biome>>> codec = PalettedContainer.codecRW(biomeRegistry.asHolderIdMap(), biomeRegistry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomeRegistry.getHolderOrThrow(Biomes.PLAINS), null);

        for (int sectionId = 0; sectionId < chunk.getSections().length; sectionId++) {
            SlimeChunkSection slimeSection = chunk.getSections()[sectionId];

            if (slimeSection != null) {
                BlockState[] presetBlockStates = null;
                if (v1192SlimeNMS.isPaperMC) {
                    NibbleArray blockLight = slimeSection.getBlockLight();
                    if (blockLight != null) {
                        blockNibbles[sectionId] = new SWMRNibbleArray(blockLight.getBacking());
                    }

                    NibbleArray skyLight = slimeSection.getSkyLight();
                    if (skyLight != null) {
                        skyNibbles[sectionId] = new SWMRNibbleArray(skyLight.getBacking());
                    }

                    presetBlockStates = level.chunkPacketBlockController.getPresetBlockStates(this.world.getHandle(), pos, sectionId << 4); // todo this is for anti xray.. do we need it?
                }

                PalettedContainer<BlockState> blockPalette;
                if (slimeSection.getBlockStatesTag() != null) {
                    Codec<PalettedContainer<BlockState>> blockStateCodec = presetBlockStates == null ? ChunkSerializer.BLOCK_STATE_CODEC : PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState(), presetBlockStates);
                    DataResult<PalettedContainer<BlockState>> dataresult = blockStateCodec.parse(NbtOps.INSTANCE, Converter.convertTag(slimeSection.getBlockStatesTag())).promotePartial((s) -> {
                        System.out.println("Recoverable error when parsing section " + x + "," + z + ": " + s); // todo proper logging
                    });
                    blockPalette = dataresult.getOrThrow(false, System.err::println); // todo proper logging
                } else {
                    blockPalette = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES, presetBlockStates);
                }

                PalettedContainer<Holder<Biome>> biomePalette;

                if (slimeSection.getBiomeTag() != null) {
                    DataResult<PalettedContainer<Holder<Biome>>> dataresult = codec.parse(NbtOps.INSTANCE, Converter.convertTag(slimeSection.getBiomeTag())).promotePartial((s) -> {
                        System.out.println("Recoverable error when parsing section " + x + "," + z + ": " + s); // todo proper logging
                    });
                    biomePalette = dataresult.getOrThrow(false, System.err::println); // todo proper logging
                } else {
                    biomePalette =new PalettedContainer<>(biomeRegistry.asHolderIdMap(), biomeRegistry.getHolderOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES);
                }

                LevelChunkSection section = new LevelChunkSection(sectionId << 4, blockPalette, biomePalette);
                sections[sectionId] = section;
            }
        }

        LevelChunk.PostLoadProcessor loadEntities = (nmsChunk) -> {

            // Load tile entities
            List<CompoundTag> tileEntities = chunk.getTileEntities();

            if (tileEntities != null) {
                for (CompoundTag tag : tileEntities) {
                    Optional<String> type = tag.getStringValue("id");

                    // Sometimes null tile entities are saved
                    if (type.isPresent()) {
                        BlockPos blockPosition = new BlockPos(tag.getIntValue("x").get(), tag.getIntValue("y").get(), tag.getIntValue("z").get());
                        BlockState blockData = nmsChunk.getBlockState(blockPosition);
                        BlockEntity entity = BlockEntity.loadStatic(blockPosition, blockData, (net.minecraft.nbt.CompoundTag) Converter.convertTag(tag));

                        if (entity != null) {
                            nmsChunk.setBlockEntity(entity);
                        }
                    }
                }
            }
        };

        LevelChunk nmsChunk = new LevelChunk(level, pos, UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L,
                sections, loadEntities, null) {
            @Override
            public void unloadCallback() {
                super.unloadCallback();
                onUnload.run();
            }
        };

        // Height Maps
        EnumSet<Heightmap.Types> heightMapTypes = nmsChunk.getStatus().heightmapsAfter();
        CompoundMap heightMaps = chunk.getHeightMaps().getValue();
        EnumSet<Heightmap.Types> unsetHeightMaps = EnumSet.noneOf(Heightmap.Types.class);

        // Light
        if (v1192SlimeNMS.isPaperMC) {
            nmsChunk.setBlockNibbles((SWMRNibbleArray[]) blockNibbles);
            nmsChunk.setSkyNibbles((SWMRNibbleArray[]) skyNibbles);
        }

        for (Heightmap.Types type : heightMapTypes) {
            String name = type.getSerializedName();

            if (heightMaps.containsKey(name)) {
                LongArrayTag heightMap = (LongArrayTag) heightMaps.get(name);
                nmsChunk.setHeightmap(type, heightMap.getValue());
            } else {
                unsetHeightMaps.add(type);
            }
        }

        // Don't try to populate heightmaps if there are none.
        // Does a crazy amount of block lookups
        if (!unsetHeightMaps.isEmpty()) {
            Heightmap.primeHeightmaps(nmsChunk, unsetHeightMaps);
        }

        return nmsChunk;
    }
}
