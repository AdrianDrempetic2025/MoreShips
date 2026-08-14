package com.glooshy.ships;

import com.glooshy.ships.cargo.CargoService;
import com.glooshy.ships.command.ShipsCommand;
import com.glooshy.ships.config.MoreShipsConfig;
import com.glooshy.ships.hull.HpCalculator;
import com.glooshy.ships.hull.HullValidator;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.listener.CargoInventoryListener;
import com.glooshy.ships.listener.HullApplicationListener;
import com.glooshy.ships.listener.ModuleInstallListener;
import com.glooshy.ships.listener.ShipCorePlacementListener;
import com.glooshy.ships.listener.ShipEntityBreakListener;
import com.glooshy.ships.listener.ShipPilotListener;
import com.glooshy.ships.movement.ShipMovementService;
import com.glooshy.ships.persistence.BindingStore;
import com.glooshy.ships.persistence.ShipStore;
import com.glooshy.ships.persistence.YamlBindingStore;
import com.glooshy.ships.persistence.YamlShipStore;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.runtime.ShipEntitySpawner;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.ship.ShipTeardownService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Project Beacon — Custom Ship System for Paper 26.2.
 *
 * <p>BUILD-05: persistence — ships + bindings survive server restart via YAML.
 */
public final class MoreShips extends JavaPlugin {

    private ShipRegistry shipRegistry;
    private RuntimeBindingRegistry bindingRegistry;
    private ShipStore shipStore;
    private BindingStore bindingStore;
    private ShipMovementService movementService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MoreShipsConfig config = new MoreShipsConfig(getConfig());

        NamespacedKey shipCoreMarker = new NamespacedKey(this, "ship_core_marker");
        NamespacedKey moduleMarker = new NamespacedKey(this, "module_marker");
        NamespacedKey shipIdKey = new NamespacedKey(this, "ship_id");

        Path dataFolder = getDataFolder().toPath();
        shipStore = new YamlShipStore(dataFolder.resolve("ships.yml"));
        bindingStore = new YamlBindingStore(dataFolder.resolve("bindings.yml"));

        ShipCoreItem shipCoreItem = new ShipCoreItem(
                shipCoreMarker,
                config.shipCoreBaseMaterial(),
                config.shipCoreDisplayName());
        ModuleItem moduleItem = new ModuleItem(
                moduleMarker,
                config.moduleMaterials(),
                config.moduleDisplayNames());
        shipRegistry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(config.hpMultiplier()));
        ShipEntitySpawner entitySpawner = new ShipEntitySpawner(shipIdKey);
        bindingRegistry = new RuntimeBindingRegistry();
        ShipTeardownService teardownService = new ShipTeardownService(shipRegistry, bindingRegistry);
        HullValidator hullValidator = new HullValidator(config.hullMinHardness());
        CargoService cargoService = new CargoService(shipRegistry);

        // Load persisted state before listeners attach
        loadPersistedState();

        ShipCorePlacementListener placementListener = new ShipCorePlacementListener(
                shipCoreItem,
                shipRegistry,
                entitySpawner,
                bindingRegistry,
                config.spawnOffsetX(),
                config.spawnOffsetY(),
                config.spawnOffsetZ(),
                config.placementSound(),
                config.placementSoundVolume(),
                config.placementSoundPitch());
        getServer().getPluginManager().registerEvents(placementListener, this);

        ShipEntityBreakListener breakListener = new ShipEntityBreakListener(
                shipCoreItem, moduleItem, cargoService, bindingRegistry, shipRegistry, teardownService);
        getServer().getPluginManager().registerEvents(breakListener, this);

        HullApplicationListener hullListener = new HullApplicationListener(
                shipRegistry, bindingRegistry, hullValidator);
        getServer().getPluginManager().registerEvents(hullListener, this);

        ModuleInstallListener moduleListener = new ModuleInstallListener(
                shipRegistry, bindingRegistry, moduleItem);
        getServer().getPluginManager().registerEvents(moduleListener, this);

        getServer().getPluginManager().registerEvents(
                new CargoInventoryListener(cargoService), this);

        if (config.movementEnabled()) {
            getServer().getPluginManager().registerEvents(
                    new ShipPilotListener(shipRegistry, bindingRegistry), this);
            movementService = new ShipMovementService(
                    this,
                    shipRegistry,
                    bindingRegistry,
                    config.movementMaxSpeed(),
                    config.movementAcceleration(),
                    config.movementFriction());
            movementService.start();
            getLogger().info("Movement service started: maxSpeed=" + config.movementMaxSpeed()
                    + " accel=" + config.movementAcceleration()
                    + " friction=" + config.movementFriction());
        } else {
            getLogger().info("Movement disabled in config.");
        }

        ShipsCommand shipsCommand = new ShipsCommand(
                shipCoreItem, moduleItem, shipRegistry, bindingRegistry, placementListener,
                cargoService);
        PluginCommand command = getCommand("moreships");
        if (command != null) {
            command.setExecutor(shipsCommand);
            command.setTabCompleter(shipsCommand);
        } else {
            getLogger().severe("Could not find /moreships command — plugin.yml misconfiguration?");
        }

        getLogger().info("MoreShips enabled (BUILD-09). Persistence + movement + modules + cargo loaded.");
    }

    @Override
    public void onDisable() {
        if (movementService != null) {
            movementService.stop();
        }
        savePersistedState();
        getLogger().info("MoreShips disabled.");
    }

    private void loadPersistedState() {
        try {
            List<Ship> ships = shipStore.load();
            shipRegistry.load(ships);
            getLogger().info("Loaded " + ships.size() + " ships from disk.");
        } catch (IOException e) {
            getLogger().warning("Failed to load ships.yml: " + e.getMessage());
        }
        try {
            var bindings = bindingStore.load();
            bindingRegistry.load(bindings);
            getLogger().info("Loaded " + bindings.size() + " bindings from disk.");
        } catch (IOException e) {
            getLogger().warning("Failed to load bindings.yml: " + e.getMessage());
        }
    }

    private void savePersistedState() {
        try {
            List<Ship> ships = shipRegistry.snapshot();
            shipStore.save(ships);
            getLogger().info("Saved " + ships.size() + " ships to disk.");
        } catch (IOException e) {
            getLogger().severe("Failed to save ships.yml: " + e.getMessage());
        }
        try {
            var bindings = bindingRegistry.snapshot();
            bindingStore.save(bindings);
            getLogger().info("Saved " + bindings.size() + " bindings to disk.");
        } catch (IOException e) {
            getLogger().severe("Failed to save bindings.yml: " + e.getMessage());
        }
    }
}
