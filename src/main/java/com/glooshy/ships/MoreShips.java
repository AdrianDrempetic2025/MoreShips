package com.glooshy.ships;

import com.glooshy.ships.command.ShipsCommand;
import com.glooshy.ships.config.MoreShipsConfig;
import com.glooshy.ships.hull.HullValidator;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.listener.HullApplicationListener;
import com.glooshy.ships.listener.ShipCorePlacementListener;
import com.glooshy.ships.listener.ShipEntityBreakListener;
import com.glooshy.ships.listener.ShipEntityDeathListener;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MoreShipsConfig config = new MoreShipsConfig(getConfig());

        NamespacedKey shipCoreMarker = new NamespacedKey(this, "ship_core_marker");
        NamespacedKey shipIdKey = new NamespacedKey(this, "ship_id");

        Path dataFolder = getDataFolder().toPath();
        shipStore = new YamlShipStore(dataFolder.resolve("ships.yml"));
        bindingStore = new YamlBindingStore(dataFolder.resolve("bindings.yml"));

        ShipCoreItem shipCoreItem = new ShipCoreItem(
                shipCoreMarker,
                config.shipCoreBaseMaterial(),
                config.shipCoreDisplayName());
        shipRegistry = new ShipRegistry(ShipIdentityGenerator.uuid());
        ShipEntitySpawner entitySpawner = new ShipEntitySpawner(shipIdKey);
        bindingRegistry = new RuntimeBindingRegistry();
        ShipTeardownService teardownService = new ShipTeardownService(shipRegistry, bindingRegistry);
        HullValidator hullValidator = new HullValidator(config.hullMinHardness());

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
                shipCoreItem, bindingRegistry, shipRegistry, teardownService);
        getServer().getPluginManager().registerEvents(breakListener, this);

        ShipEntityDeathListener deathListener = new ShipEntityDeathListener(shipRegistry, bindingRegistry);
        getServer().getPluginManager().registerEvents(deathListener, this);

        HullApplicationListener hullListener = new HullApplicationListener(
                shipRegistry, bindingRegistry, hullValidator);
        getServer().getPluginManager().registerEvents(hullListener, this);

        ShipsCommand shipsCommand = new ShipsCommand(
                shipCoreItem, shipRegistry, bindingRegistry, placementListener);
        PluginCommand command = getCommand("moreships");
        if (command != null) {
            command.setExecutor(shipsCommand);
            command.setTabCompleter(shipsCommand);
        } else {
            getLogger().severe("Could not find /moreships command — plugin.yml misconfiguration?");
        }

        getLogger().info("MoreShips enabled (BUILD-05). Persistence loaded.");
    }

    @Override
    public void onDisable() {
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
