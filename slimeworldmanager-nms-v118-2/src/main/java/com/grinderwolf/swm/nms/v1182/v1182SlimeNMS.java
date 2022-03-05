package com.grinderwolf.swm.nms.v1182;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.*;
import com.grinderwolf.swm.api.world.properties.*;
import com.grinderwolf.swm.nms.*;
import com.mojang.serialization.*;
import lombok.*;
import net.minecraft.*;
import net.minecraft.core.Registry;
import net.minecraft.core.*;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.*;
import net.minecraft.server.dedicated.*;
import net.minecraft.tags.*;
import net.minecraft.util.datafix.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.dimension.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.storage.*;
import org.apache.commons.io.*;
import org.apache.logging.log4j.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_18_R2.*;
import org.bukkit.craftbukkit.v1_18_R2.scoreboard.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Getter
public class v1182SlimeNMS implements SlimeNMS {

    private static final Logger LOGGER = LogManager.getLogger("SWM");
    private static final File UNIVERSE_DIR;
    public static LevelStorageSource CONVERTABLE;
    public static boolean isPaperMC;

    static {
        Path path;

        try {
            path = Files.createTempDirectory("swm-" + UUID.randomUUID().toString().substring(0, 5) + "-");
        } catch (IOException ex) {
//            LOGGER.log(Level.FATAL, "Failed to create temp directory", ex);
            path = null;
            System.exit(1);
        }

        UNIVERSE_DIR = path.toFile();
        CONVERTABLE = LevelStorageSource.createDefault(path);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            try {
                FileUtils.deleteDirectory(UNIVERSE_DIR);
            } catch (IOException ex) {
//                LOGGER.log(Level.FATAL, "Failed to delete temp directory", ex);
            }

        }));
    }

    private final byte worldVersion = 0x08;

    private boolean injectFakeDimensions = false;

    private CustomWorldServer defaultWorld;
    private CustomWorldServer defaultNetherWorld;
    private CustomWorldServer defaultEndWorld;

    public v1182SlimeNMS(boolean isPaper) {
        try {
            isPaperMC = isPaper;
            CraftCLSMBridge.initialize(this);
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

        System.out.println("INJECTING: " + defaultWorld + " " + defaultNetherWorld + " " + defaultEndWorld);


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

            var field = MinecraftServer.class.getDeclaredField("p");

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

        CustomWorldServer server = createCustomWorld(world, null);
        registerWorld(server);
    }

    @Override
    public SlimeWorld getSlimeWorld(World world) {
        CraftWorld craftWorld = (CraftWorld) world;

        if (!(craftWorld.getHandle() instanceof CustomWorldServer)) {
            return null;
        }

        CustomWorldServer worldServer = (CustomWorldServer) craftWorld.getHandle();
        return worldServer.getSlimeWorld();
    }

    public void registerWorld(CustomWorldServer server) {
        MinecraftServer mcServer = MinecraftServer.getServer();
        mcServer.initWorld(server, server.serverLevelData, mcServer.getWorldData(), server.serverLevelData.worldGenSettings());

        mcServer.levels.put(server.dimension(), server);
    }

    private CustomWorldServer createCustomWorld(SlimeWorld world, @Nullable ResourceKey<Level> dimensionOverride) {
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


        Holder<DimensionType> type = null;
        {
            DimensionType predefinedType = worldDimension.typeHolder().value();

            OptionalLong fixedTime = switch (environment) {
                case NORMAL -> OptionalLong.empty();
                case NETHER -> OptionalLong.of(18000L);
                case THE_END -> OptionalLong.of(6000L);
                case CUSTOM -> throw new UnsupportedOperationException();
            };
            double light = switch (environment) {
                case NORMAL, THE_END -> 0;
                case NETHER -> 0.1;
                case CUSTOM -> throw new UnsupportedOperationException();
            };

            TagKey<Block> infiniburn = switch (environment) {
                case NORMAL -> BlockTags.INFINIBURN_OVERWORLD;
                case NETHER -> BlockTags.INFINIBURN_NETHER;
                case THE_END -> BlockTags.INFINIBURN_END;
                case CUSTOM -> throw new UnsupportedOperationException();
            };


            type = Holder.direct(DimensionType.create(fixedTime, predefinedType.hasSkyLight(), predefinedType.hasCeiling(),
                    predefinedType.ultraWarm(), predefinedType.natural(), predefinedType.coordinateScale(),
                    world.getPropertyMap().getValue(SlimeProperties.DRAGON_BATTLE), predefinedType.piglinSafe(), predefinedType.bedWorks(),
                    predefinedType.respawnAnchorWorks(), predefinedType.hasRaids(),
                    predefinedType.minY(), predefinedType.height(), predefinedType.logicalHeight(),
                    infiniburn,
                    predefinedType.effectsLocation(),
                    (float) light));
        }

        ChunkGenerator chunkGenerator = worldDimension.generator();

        ResourceKey<Level> worldKey = dimensionOverride == null ? ResourceKey.create(Registry.DIMENSION_REGISTRY,
                new ResourceLocation(worldName.toLowerCase(java.util.Locale.ENGLISH))) : dimensionOverride;

        CustomWorldServer level;

        try {
            level = new CustomWorldServer((CraftSlimeWorld) world, worldDataServer,
                    worldKey, dimension, type, chunkGenerator, environment);
        } catch (IOException ex) {
            throw new RuntimeException(ex); // TODO do something better with this?
        }

        level.setReady(true);
        level.setSpawnSettings(world.getPropertyMap().getValue(SlimeProperties.ALLOW_MONSTERS), world.getPropertyMap().getValue(SlimeProperties.ALLOW_ANIMALS));

        return level;
    }

    private World.Environment getEnvironment(SlimeWorld world) {
        return World.Environment.valueOf(world.getPropertyMap().getValue(SlimeProperties.ENVIRONMENT).toUpperCase());
    }

    private PrimaryLevelData createWorldData(SlimeWorld world) {
        String worldName = world.getName();
        CompoundTag extraData = world.getExtraData();
        PrimaryLevelData worldDataServer;
        net.minecraft.nbt.CompoundTag extraTag = (net.minecraft.nbt.CompoundTag) Converter.convertTag(extraData);
        MinecraftServer mcServer = MinecraftServer.getServer();
        DedicatedServerProperties serverProps = ((DedicatedServer) mcServer).getProperties();

        if (extraTag.getTagType("LevelData") == Tag.TAG_COMPOUND) {
            net.minecraft.nbt.CompoundTag levelData = extraTag.getCompound("LevelData");
            int dataVersion = levelData.getTagType("DataVersion") == Tag.TAG_INT ? levelData.getInt("DataVersion") : -1;
            Dynamic<Tag> dynamic = mcServer.getFixerUpper().update(DataFixTypes.LEVEL.getType(),
                    new Dynamic<>(NbtOps.INSTANCE, levelData), dataVersion, SharedConstants.getCurrentVersion()
                            .getWorldVersion());

            LevelVersion levelVersion = LevelVersion.parse(dynamic);
            LevelSettings worldSettings = LevelSettings.parse(dynamic, mcServer.datapackconfiguration);

            worldDataServer = PrimaryLevelData.parse(dynamic, mcServer.getFixerUpper(), dataVersion, null,
                    worldSettings, levelVersion, serverProps.getWorldGenSettings(mcServer.registryHolder), Lifecycle.stable());
        } else {

            // Game rules
            Optional<CompoundTag> gameRules = extraData.getAsCompoundTag("gamerules");
            GameRules rules = new GameRules();

            gameRules.ifPresent(compoundTag -> {
                net.minecraft.nbt.CompoundTag compound = ((net.minecraft.nbt.CompoundTag) Converter.convertTag(compoundTag));
                Map<String, GameRules.Key<?>> gameRuleKeys = CraftWorld.getGameRulesNMS();

                compound.getAllKeys().forEach(gameRule -> {
                    if (gameRuleKeys.containsKey(gameRule)) {
                        GameRules.Value<?> gameRuleValue = rules.getRule(gameRuleKeys.get(gameRule));
                        String theValue = compound.getString(gameRule);
                        gameRuleValue.deserialize(theValue);
                        gameRuleValue.onChanged(mcServer);
                    }
                });
            });

            LevelSettings worldSettings = new LevelSettings(worldName, serverProps.gamemode, false,
                    serverProps.difficulty, false, rules, mcServer.datapackconfiguration);

            worldDataServer = new PrimaryLevelData(worldSettings, serverProps.getWorldGenSettings(mcServer.registryHolder), Lifecycle.stable());
        }

        worldDataServer.checkName(worldName);
        worldDataServer.setModdedInfo(mcServer.getServerModName(), mcServer.getModdedStatus().shouldReportAsModified());
        worldDataServer.setInitialized(true);

        return worldDataServer;
    }
}