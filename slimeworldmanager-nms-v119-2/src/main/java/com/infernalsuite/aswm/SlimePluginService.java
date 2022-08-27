package com.infernalsuite.aswm;

import com.grinderwolf.swm.api.SlimePlugin;
import com.grinderwolf.swm.api.exceptions.*;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class SlimePluginService implements SlimePlugin {


    @Override
    public SlimeWorld loadWorld(SlimeLoader loader, String worldName, boolean readOnly, SlimePropertyMap propertyMap) throws UnknownWorldException, IOException, CorruptedWorldException, NewerFormatException, WorldInUseException {
        return null;
    }

    @Override
    public SlimeWorld getWorld(String worldName) {
        return null;
    }

    @Override
    public List<SlimeWorld> getLoadedWorlds() {
        return null;
    }

    @Override
    public SlimeWorld createEmptyWorld(SlimeLoader loader, String worldName, boolean readOnly, SlimePropertyMap propertyMap) throws WorldAlreadyExistsException, IOException {
        return null;
    }

    @Override
    public void generateWorld(SlimeWorld world) {

    }

    @Override
    public void migrateWorld(String worldName, SlimeLoader currentLoader, SlimeLoader newLoader) throws IOException, WorldInUseException, WorldAlreadyExistsException, UnknownWorldException {

    }

    @Override
    public SlimeLoader getLoader(String dataSource) {
        return null;
    }

    @Override
    public void registerLoader(String dataSource, SlimeLoader loader) {

    }

    @Override
    public void importWorld(File worldDir, String worldName, SlimeLoader loader) throws WorldAlreadyExistsException, InvalidWorldException, WorldLoadedException, WorldTooBigException, IOException {

    }

    @Override
    public CompletableFuture<Optional<SlimeWorld>> asyncLoadWorld(SlimeLoader loader, String worldName, boolean readOnly, SlimePropertyMap propertyMap) {
        return null;
    }

    @Override
    public CompletableFuture<Optional<SlimeWorld>> asyncGetWorld(String worldName) {
        return null;
    }

    @Override
    public CompletableFuture<Optional<SlimeWorld>> asyncCreateEmptyWorld(SlimeLoader loader, String worldName, boolean readOnly, SlimePropertyMap propertyMap) {
        return null;
    }

    @Override
    public CompletableFuture<Void> asyncMigrateWorld(String worldName, SlimeLoader currentLoader, SlimeLoader newLoader) {
        return null;
    }

    @Override
    public CompletableFuture<Void> asyncImportWorld(File worldDir, String worldName, SlimeLoader loader) {
        return null;
    }
}
