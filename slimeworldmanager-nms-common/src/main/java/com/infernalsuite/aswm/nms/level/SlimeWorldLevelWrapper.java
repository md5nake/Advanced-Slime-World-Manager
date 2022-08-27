package com.infernalsuite.aswm.nms.level;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.nms.world.SlimeLoadedWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.world.WorldSaveEvent;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public abstract class SlimeWorldLevelWrapper<S extends SlimeLoadedWorld, CHUNK> {

    private static final ExecutorService WORLD_SAVER_SERVICE = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                    .setNameFormat("SWM Pool Thread #%1$d")
                    .build());

    private final Object saveLock = new Object();

    protected final S world;
    private final World bukkitWorld;

    public SlimeWorldLevelWrapper(S world, World bukkitWorld) {
        this.world = world;
        this.bukkitWorld = bukkitWorld;
    }

    public void save() {
        if (this.world.isReadOnly()) {
            return;
        }

        Bukkit.getPluginManager().callEvent(new WorldSaveEvent(this.bukkitWorld));

        // Update level data
        if (Bukkit.isStopping()) { // Make sure the world gets saved before stopping the server by running it from the main thread
            this.saveWorld();

            // Have to manually unlock the world as well
            try {
                this.world.getLoader().unlockWorld(this.world.getName());
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (UnknownWorldException ignored) {
            }
        } else {
            WORLD_SAVER_SERVICE.execute(this::saveWorld);
        }
    }

    private void saveWorld() {
        synchronized (saveLock) { // Don't want to save the SlimeWorld from multiple threads simultaneously
            try {
                Bukkit.getLogger().log(Level.INFO, "Saving world " + this.world.getName() + "...");
                long start = System.currentTimeMillis();
                byte[] serializedWorld = this.world.serialize().join();
                long saveStart = System.currentTimeMillis();
                this.world.getLoader().saveWorld(this.world.getName(), serializedWorld, false);
                Bukkit.getLogger().log(Level.INFO, "World " + this.world.getName() + " serialized in " + (saveStart - start) + "ms and saved in " + (System.currentTimeMillis() - saveStart) + "ms.");
            } catch (IOException | IllegalStateException ex) {
                ex.printStackTrace();
            }
        }
    }

    public abstract CHUNK getVanillaChunkAccess(int x, int z);

}
