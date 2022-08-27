package com.grinderwolf.swm.plugin.loaders.slime.impl;

import com.flowpowered.nbt.*;
import com.github.luben.zstd.Zstd;
import com.grinderwolf.swm.api.exceptions.CorruptedWorldException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.utils.NibbleArray;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.nms.CraftSlimeChunk;
import com.grinderwolf.swm.nms.CraftSlimeChunkSection;
import com.grinderwolf.swm.nms.NmsUtil;
import com.grinderwolf.swm.nms.world.SlimeLoadedWorld;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.loaders.slime.SlimeWorldReader;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Data;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class v10SlimeWorldFormat implements SlimeWorldReader {

    @Override
    public SlimeLoadedWorld deserializeWorld(byte version, SlimeLoader loader, String worldName, DataInputStream dataStream, SlimePropertyMap propertyMap, boolean readOnly)
            throws IOException, CorruptedWorldException {

        // World version
        byte worldVersion = dataStream.readByte();
        // Chunk Data
        {
            Long2ObjectOpenHashMap<SlimeChunk> chunks = readChunks(worldVersion);

        }

        byte[] tileEntities = readCompressed(dataStream);
        byte[] entities = readCompressed(dataStream);
        byte[] extra = readCompressed(dataStream);

        // Chunk deserialization

        // Entity deserialization
        CompoundTag entitiesCompound = readCompoundTag(entities);

        Long2ObjectOpenHashMap<List<CompoundTag>> entityStorage = new Long2ObjectOpenHashMap<>();
        if (entitiesCompound != null) {
            List<CompoundTag> serializedEntities = ((ListTag<CompoundTag>) entitiesCompound.getValue().get("entities")).getValue();

            for (CompoundTag entityCompound : serializedEntities) {
                ListTag<DoubleTag> listTag = (ListTag<DoubleTag>) entityCompound.getAsListTag("Pos").get();

                int chunkX = floor(listTag.getValue().get(0).getValue()) >> 4;
                int chunkZ = floor(listTag.getValue().get(2).getValue()) >> 4;
                long chunkKey = NmsUtil.asLong(chunkX, chunkZ);
                SlimeChunk chunk = chunks.get(chunkKey);
                if (chunk != null) {
                    chunk.getEntities().add(entityCompound);
                }
                if (entityStorage.containsKey(chunkKey)) {
                    entityStorage.get(chunkKey).add(entityCompound);
                } else {
                    List<CompoundTag> entityStorageList = new ArrayList<>();
                    entityStorageList.add(entityCompound);
                    entityStorage.put(chunkKey, entityStorageList);
                }
            }
        }

        // Tile Entity deserialization
        CompoundTag tileEntitiesCompound = readCompoundTag(tileEntities);

        if (tileEntitiesCompound != null) {
            ListTag<CompoundTag> tileEntitiesList = (ListTag<CompoundTag>) tileEntitiesCompound.getValue().get("tiles");
            for (CompoundTag tileEntityCompound : tileEntitiesList.getValue()) {
                int chunkX = ((IntTag) tileEntityCompound.getValue().get("x")).getValue() >> 4;
                int chunkZ = ((IntTag) tileEntityCompound.getValue().get("z")).getValue() >> 4;
                long chunkKey = NmsUtil.asLong(chunkX, chunkZ);
                SlimeChunk chunk = chunks.get(chunkKey);

                if (chunk == null) {
                    throw new CorruptedWorldException(worldName);
                }

                chunk.getTileEntities().add(tileEntityCompound);
            }
        }

        // Extra Data
        CompoundTag extraCompound = readCompoundTag(extraTag);

        if (extraCompound == null) {
            extraCompound = new CompoundTag("", new CompoundMap());
        }


        // World properties
        SlimePropertyMap worldPropertyMap = propertyMap;
        Optional<CompoundMap> propertiesMap = extraCompound
                .getAsCompoundTag("properties")
                .map(CompoundTag::getValue);

        if (propertiesMap.isPresent()) {
            worldPropertyMap = new SlimePropertyMap(propertiesMap.get());
            worldPropertyMap.merge(propertyMap); // Override world properties
        } else if (propertyMap == null) { // Make sure the property map is never null
            worldPropertyMap = new SlimePropertyMap();
        }

        return SWMPlugin.getInstance().getNms().createSlimeWorld(loader, worldName, chunks, extraCompound, mapList, worldVersion, worldPropertyMap, readOnly, !readOnly, entityStorage);
    }

    private static int floor(double num) {
        final int floor = (int) num;
        return floor == num ? floor : floor - (int) (Double.doubleToRawLongBits(num) >>> 63);
    }

    private static Long2ObjectOpenHashMap<SlimeChunk> readChunks(byte worldVersion, DataInputStream stream) throws IOException {
        DataInputStream chunkData = new DataInputStream(new ByteArrayInputStream(readCompressed(stream)));
        Long2ObjectOpenHashMap<SlimeChunk> chunkMap = new Long2ObjectOpenHashMap<>();


        int minSection = stream.readInt();
        int maxSection = stream.readInt() + 1;

        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int bitsetIndex = z * width + x;

                if (chunkBitset.get(bitsetIndex)) {
                    // Height Maps
                    CompoundTag heightMaps;

                    int heightMapsLength = chunkData.readInt();
                    byte[] heightMapsArray = new byte[heightMapsLength];
                    chunkData.read(heightMapsArray);
                    heightMaps = readCompoundTag(heightMapsArray);

                    // Height Maps might be null if empty
                    if (heightMaps == null) {
                        heightMaps = new CompoundTag("", new CompoundMap());
                    }


                    if (version == 8 && worldVersion < 0x04) {
                        // Patch the v8 bug: biome array size is wrong for old worlds
                        chunkData.readInt();
                    }


                    // Chunk Sections
                    v1_9SlimeWorldFormat.ChunkSectionData data = worldVersion < 0x08 ? readChunkSections(chunkData, worldVersion, version) : readChunkSectionsNew(chunkData, worldVersion, version);

                    int chunkX = minX + x;
                    int chunkZ = minZ + z;

                    chunkMap.put(NmsUtil.asLong(chunkX, chunkZ), new CraftSlimeChunk(worldName, chunkX, chunkZ,
                            data.sections, heightMaps, biomes, new ArrayList<>(), new ArrayList<>(), data.minSectionY, data.maxSectionY));
                }
            }
        }

        return chunkMap;
    }

    private static int[] toIntArray(byte[] buf) {
        ByteBuffer buffer = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
        int[] ret = new int[buf.length / 4];

        buffer.asIntBuffer().get(ret);

        return ret;
    }


    private static v1_9SlimeWorldFormat.ChunkSectionData readChunkSectionsNew(DataInputStream dataStream, int worldVersion, int version) throws IOException {
        int minSectionY = dataStream.readInt();
        int maxSectionY = dataStream.readInt();
        int sectionCount = dataStream.readInt();
        SlimeChunkSection[] chunkSectionArray = new SlimeChunkSection[maxSectionY - minSectionY];

        for (int i = 0; i < sectionCount; i++) {
            int y = dataStream.readInt();

            // Block Light Nibble Array
            NibbleArray blockLightArray;

            if (version < 5 || dataStream.readBoolean()) {
                byte[] blockLightByteArray = new byte[2048];
                dataStream.read(blockLightByteArray);
                blockLightArray = new NibbleArray((blockLightByteArray));
            } else {
                blockLightArray = null;
            }

            // Block data
            byte[] blockStateData = new byte[dataStream.readInt()];
            dataStream.read(blockStateData);
            CompoundTag blockStateTag = readCompoundTag(blockStateData);

            byte[] biomeData = new byte[dataStream.readInt()];
            dataStream.read(biomeData);
            CompoundTag biomeTag = readCompoundTag(biomeData);

            // Sky Light Nibble Array
            NibbleArray skyLightArray;

            if (version < 5 || dataStream.readBoolean()) {
                byte[] skyLightByteArray = new byte[2048];
                dataStream.read(skyLightByteArray);
                skyLightArray = new NibbleArray((skyLightByteArray));
            } else {
                skyLightArray = null;
            }

            // HypixelBlocks 3
            if (version < 4) {
                short hypixelBlocksLength = dataStream.readShort();
                dataStream.skip(hypixelBlocksLength);
            }

            chunkSectionArray[y] = new CraftSlimeChunkSection(null, null, blockStateTag, biomeTag, blockLightArray, skyLightArray);
        }

        return new v1_9SlimeWorldFormat.ChunkSectionData(chunkSectionArray, minSectionY, maxSectionY);
    }



    private static byte[] readCompressed(DataInputStream stream) throws IOException {
        int compressedLength = stream.readInt();
        int normalLength = stream.readInt();
        byte[] compressed = new byte[compressedLength];
        byte[] normal = new byte[normalLength];

        Zstd.decompress(normal, compressed);
        return normal;
    }
}
