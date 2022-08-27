package com.grinderwolf.swm.nms.v1192;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.nms.SlimeNMS;
import com.grinderwolf.swm.nms.world.SlimeLoadedWorld;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_19_R1.CraftServer;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.scoreboard.CraftScoreboardManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


public class v1192SlimeNMS implements SlimeNMS {

    private static final Logger LOGGER = LogManager.getLogger("SWM");
    public static boolean isPaperMC;

    public static LevelStorageSource CUSTOM_LEVEL_STORAGE;

    static {
        try {
            Path path = Files.createTempDirectory("swm-" + UUID.randomUUID().toString().substring(0, 5)).toAbsolutePath();
            CUSTOM_LEVEL_STORAGE = new LevelStorageSource(path, path, DataFixers.getDataFixer());

            FileUtils.forceDeleteOnExit(path.toFile());

        } catch (IOException ex) {
            throw new IllegalStateException("Couldn't create dummy file directory.", ex);
        }
    }

    private final byte worldVersion = 0x09;

    private boolean injectFakeDimensions = false;

    private SlimeServerLevel defaultWorld;
    private SlimeServerLevel defaultNetherWorld;
    private SlimeServerLevel defaultEndWorld;


    public v1192SlimeNMS(boolean isPaper) {
        try {
            isPaperMC = isPaper;
            SlimeClassModifierBridge.initialize(this);
        } catch (NoClassDefFoundError ex) {
            LOGGER.error("Failed to find ClassModifier classes. Are you sure you installed it correctly?", ex);
            Bukkit.getServer().shutdown();
        }
    }

    @Override
    public Object injectDefaultWorlds() {
        if (!injectFakeDimensions) {
            return null;
        }

        MinecraftServer server = MinecraftServer.getServer();
        server.server.scoreboardManager = new CraftScoreboardManager(server, server.getScoreboard());

        if (defaultWorld != null) {
            registerWorld(defaultWorld);
        }
        if (defaultNetherWorld != null) {
            registerWorld(defaultNetherWorld);
        }
        if (defaultEndWorld != null) {
            registerWorld(defaultEndWorld);
        }

        injectFakeDimensions = false;
        return new MappedRegistry<>(Registry.ACTIVITY_REGISTRY, Lifecycle.stable(), null);
    }

    @Override
    public void setDefaultWorlds(SlimeWorld normalWorld, SlimeWorld netherWorld, SlimeWorld endWorld) {
        try {
            MinecraftServer server = MinecraftServer.getServer();

            LevelSettings worldsettings;
            WorldGenSettings generatorsettings;

            DedicatedServerProperties dedicatedserverproperties = ((DedicatedServer) server).getProperties();

            worldsettings = new LevelSettings(dedicatedserverproperties.levelName,
                    dedicatedserverproperties.gamemode, dedicatedserverproperties.hardcore, dedicatedserverproperties.difficulty,
                    false, new GameRules(),
                    server.datapackconfiguration);
            generatorsettings = dedicatedserverproperties.getWorldGenSettings(server.registryAccess());

            WorldData data = new PrimaryLevelData(worldsettings, generatorsettings, Lifecycle.stable());

            var field = MinecraftServer.class.getDeclaredField("m");

            field.setAccessible(true);
            field.set(server, data); // Set default world settings ( prevent mean nullpointers)
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        if (normalWorld != null) {
            normalWorld.getPropertyMap().setValue(SlimeProperties.ENVIRONMENT, World.Environment.NORMAL.toString().toLowerCase());
            defaultWorld = createCustomWorld(normalWorld, Level.OVERWORLD);
            injectFakeDimensions = true;
        }

        if (netherWorld != null) {
            netherWorld.getPropertyMap().setValue(SlimeProperties.ENVIRONMENT, World.Environment.NETHER.toString().toLowerCase());
            defaultNetherWorld = createCustomWorld(netherWorld, Level.NETHER);
            injectFakeDimensions = true;
        }

        if (endWorld != null) {
            endWorld.getPropertyMap().setValue(SlimeProperties.ENVIRONMENT, World.Environment.THE_END.toString().toLowerCase());
            defaultEndWorld = createCustomWorld(endWorld, Level.END);
            injectFakeDimensions = true;
        }

    }

    @Override
    public void generateWorld(SlimeWorld world) {
        String worldName = world.getName();

        if (Bukkit.getWorld(worldName) != null) {
            throw new IllegalArgumentException("World " + worldName + " already exists! Maybe it's an outdated SlimeWorld object?");
        }

        SlimeServerLevel server = createCustomWorld(world, null);
        registerWorld(server);
    }

    @Override
    public SlimeWorld getSlimeWorld(World world) {
        CraftWorld craftWorld = (CraftWorld) world;

        if (!(craftWorld.getHandle() instanceof SlimeServerLevel worldServer)) {
            return null;
        }

        return worldServer.getSlimeWorld();
    }

    @Override
    public byte getWorldVersion() {
        return this.worldVersion;
    }

    public void registerWorld(SlimeServerLevel server) {
        MinecraftServer mcServer = MinecraftServer.getServer();
        mcServer.initWorld(server, server.serverLevelData, mcServer.getWorldData(), server.serverLevelData.worldGenSettings());

        mcServer.addLevel(server);
    }

    private SlimeServerLevel createCustomWorld(SlimeWorld world, @Nullable ResourceKey<Level> dimensionOverride) {
        v1192SlimeWorld nmsWorld = (v1192SlimeWorld) world;
        String worldName = world.getName();

        PrimaryLevelData worldDataServer = createWorldData(world);
        World.Environment environment = getEnvironment(world);
        ResourceKey<LevelStem> dimension = switch (environment) {
            case NORMAL -> LevelStem.OVERWORLD;
            case NETHER -> LevelStem.NETHER;
            case THE_END -> LevelStem.END;
            default -> throw new IllegalArgumentException("Unknown dimension supplied");
        };

        Registry<LevelStem> registryMaterials = worldDataServer.worldGenSettings().dimensions();
        LevelStem worldDimension = registryMaterials.get(dimension);

        ResourceKey<Level> worldKey = dimensionOverride == null ? ResourceKey.create(Registry.DIMENSION_REGISTRY,
                new ResourceLocation(worldName.toLowerCase(Locale.ENGLISH))) : dimensionOverride;

        SlimeServerLevel level;
        CraftServer server = MinecraftServer.getServer().server;

        try {
            level = new SlimeServerLevel(nmsWorld, worldDataServer, worldKey, dimension, worldDimension,
                    environment, server.getGenerator(worldName), server.getBiomeProvider(worldName));
            nmsWorld.setHandle(level);
        } catch (IOException ex) {
            throw new RuntimeException(ex); // TODO do something better with this?
        }

        level.setReady(true);

        return level;
    }

    private World.Environment getEnvironment(SlimeWorld world) {
        return World.Environment.valueOf(world.getPropertyMap().getValue(SlimeProperties.ENVIRONMENT).toUpperCase());
    }

    private PrimaryLevelData createWorldData(SlimeWorld world) {
        String worldName = world.getName();

        PrimaryLevelData worldDataServer;
        MinecraftServer mcServer = MinecraftServer.getServer();
        DedicatedServerProperties serverProps = ((DedicatedServer) mcServer).getProperties();

        LevelSettings worldSettings = new LevelSettings(worldName, serverProps.gamemode, false,
                serverProps.difficulty, false, new GameRules(), mcServer.datapackconfiguration);

        worldDataServer = new PrimaryLevelData(worldSettings, serverProps.getWorldGenSettings(mcServer.registryHolder), Lifecycle.stable());

        worldDataServer.checkName(worldName);
        worldDataServer.setModdedInfo(mcServer.getServerModName(), mcServer.getModdedStatus().shouldReportAsModified());
        worldDataServer.setInitialized(true);

        return worldDataServer;
    }

    @Override
    public SlimeLoadedWorld createSlimeWorld(SlimeLoader loader, String worldName, Long2ObjectOpenHashMap<SlimeChunk> chunks, CompoundTag extraCompound, List<CompoundTag> mapList, byte worldVersion, SlimePropertyMap worldPropertyMap, boolean readOnly, boolean lock, Long2ObjectOpenHashMap<List<CompoundTag>> entities) {
        return new v1192SlimeWorld(this, worldVersion, loader, worldName, chunks, extraCompound, worldPropertyMap, readOnly, lock, entities);
    }
}