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
import com.glooshy.ships.listener.ModuleEntityListener;
import com.glooshy.ships.listener.ShipConfigUiListener;
import com.glooshy.ships.listener.ShipCorePlacementListener;
import com.glooshy.ships.listener.ShipEntityBreakListener;
import com.glooshy.ships.listener.ShipPilotListener;
import com.glooshy.ships.movement.ShipMovementService;
import com.glooshy.ships.persistence.BindingStore;
import com.glooshy.ships.persistence.ModuleEntityStore;
import com.glooshy.ships.persistence.ShipStore;
import com.glooshy.ships.persistence.YamlBindingStore;
import com.glooshy.ships.persistence.YamlModuleEntityStore;
import com.glooshy.ships.persistence.YamlShipHitboxStore;
import com.glooshy.ships.persistence.YamlShipStore;
import com.glooshy.ships.runtime.ModuleEntityManager;
import com.glooshy.ships.runtime.ShipEntityResolver;
import com.glooshy.ships.runtime.HullVisualManager;
import com.glooshy.ships.runtime.ShipHitboxManager;
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
    private ModuleEntityStore moduleEntityStore;
    private ModuleEntityManager moduleEntities;
    private ShipHitboxManager hitboxes;
    private HullVisualManager hullVisuals;
    private YamlShipHitboxStore hitboxStore;
    private ShipMovementService movementService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MoreShipsConfig config = new MoreShipsConfig(getConfig());

        NamespacedKey shipCoreMarker = new NamespacedKey(this, "ship_core_marker");
        NamespacedKey moduleMarker = new NamespacedKey(this, "module_marker");
        NamespacedKey shipIdKey = new NamespacedKey(this, "ship_id");
        NamespacedKey moduleSlotKey = new NamespacedKey(this, "module_slot");

        Path dataFolder = getDataFolder().toPath();
        shipStore = new YamlShipStore(dataFolder.resolve("ships.yml"));
        bindingStore = new YamlBindingStore(dataFolder.resolve("bindings.yml"));
        moduleEntityStore = new YamlModuleEntityStore(dataFolder.resolve("module-entities.yml"));
        hitboxStore = new YamlShipHitboxStore(dataFolder.resolve("ship-hitboxes.yml"));

        ShipCoreItem shipCoreItem = new ShipCoreItem(
                shipCoreMarker,
                config.shipCoreMaterials(),
                config.shipCoreDisplayNames());
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
        moduleEntities = new ModuleEntityManager(
                shipIdKey, moduleSlotKey, shipRegistry, bindingRegistry, moduleItem);
        hitboxes = new ShipHitboxManager(
                shipIdKey, bindingRegistry, shipRegistry,
                config.shipHitboxWidth(), config.shipHitboxHeight());
        hullVisuals = new HullVisualManager(bindingRegistry, shipRegistry);
        ShipEntityResolver resolver = new ShipEntityResolver(bindingRegistry, hitboxes);

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
                shipCoreItem, moduleItem, cargoService, moduleEntities,
                resolver, hitboxes, hullVisuals, bindingRegistry, shipRegistry, teardownService);
        getServer().getPluginManager().registerEvents(breakListener, this);

        HullApplicationListener hullListener = new HullApplicationListener(
                shipRegistry, resolver, hullValidator);
        getServer().getPluginManager().registerEvents(hullListener, this);

        getServer().getPluginManager().registerEvents(
                new ShipConfigUiListener(shipRegistry, resolver, moduleItem, moduleEntities,
                        cargoService), this);

        getServer().getPluginManager().registerEvents(
                new ModuleEntityListener(moduleEntities, shipRegistry, cargoService, moduleItem), this);

        getServer().getPluginManager().registerEvents(
                new CargoInventoryListener(cargoService), this);

        if (config.movementEnabled()) {
            getServer().getPluginManager().registerEvents(
                    new ShipPilotListener(shipRegistry, resolver), this);
            movementService = new ShipMovementService(
                    this,
                    shipRegistry,
                    bindingRegistry,
                    moduleEntities,
                    hitboxes,
                    hullVisuals,
                    shipIdKey,
                    config.movementMaxSpeed(),
                    config.collisionEnabled(),
                    config.collisionMargin(),
                    config.movementAcceleration(),
                    config.movementFriction(),
                    config.physicsRiseVelocity(),
                    config.physicsSinkVelocity(),
                    config.movementTurnRate());
            movementService.start();
            getLogger().info("Movement service started: maxSpeed=" + config.movementMaxSpeed()
                    + " accel=" + config.movementAcceleration()
                    + " friction=" + config.movementFriction());
        } else {
            getLogger().info("Movement disabled in config.");
        }

        ShipsCommand shipsCommand = new ShipsCommand(
                shipCoreItem, moduleItem, shipRegistry, bindingRegistry, placementListener,
                cargoService, moduleEntities, resolver);
        PluginCommand command = getCommand("moreships");
        if (command != null) {
            command.setExecutor(shipsCommand);
            command.setTabCompleter(shipsCommand);
        } else {
            getLogger().severe("Could not find /moreships command — plugin.yml misconfiguration?");
        }

        getLogger().info("MoreShips enabled (BUILD-18). Hull visuals (block deck) loaded.");
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
        try {
            var moduleEntityBindings = moduleEntityStore.load();
            moduleEntities.load(moduleEntityBindings);
            getLogger().info("Loaded " + moduleEntityBindings.size() + " module entities from disk.");
        } catch (IOException e) {
            getLogger().warning("Failed to load module-entities.yml: " + e.getMessage());
        }
        try {
            var hitboxBindings = hitboxStore.load();
            hitboxes.load(hitboxBindings);
            getLogger().info("Loaded " + hitboxBindings.size() + " ship hitboxes from disk.");
        } catch (IOException e) {
            getLogger().warning("Failed to load ship-hitboxes.yml: " + e.getMessage());
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
        try {
            var moduleEntityBindings = moduleEntities.snapshot();
            moduleEntityStore.save(moduleEntityBindings);
            getLogger().info("Saved " + moduleEntityBindings.size() + " module entities to disk.");
        } catch (IOException e) {
            getLogger().severe("Failed to save module-entities.yml: " + e.getMessage());
        }
        try {
            var hitboxBindings = hitboxes.snapshot();
            hitboxStore.save(hitboxBindings);
            getLogger().info("Saved " + hitboxBindings.size() + " ship hitboxes to disk.");
        } catch (IOException e) {
            getLogger().severe("Failed to save ship-hitboxes.yml: " + e.getMessage());
        }
    }
}
