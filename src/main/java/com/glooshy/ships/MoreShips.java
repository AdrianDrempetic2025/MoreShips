package com.glooshy.ships;

import com.glooshy.ships.command.ShipsCommand;
import com.glooshy.ships.config.MoreShipsConfig;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.listener.ShipCorePlacementListener;
import com.glooshy.ships.listener.ShipEntityBreakListener;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.runtime.ShipEntitySpawner;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.ship.ShipTeardownService;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Project Beacon — Custom Ship System for Paper 26.2.
 *
 * <p>BUILD-03a: config system + lifecycle phase.
 * <p>BUILD-03b: teardown conservation (RQCA-21).
 */
public final class MoreShips extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MoreShipsConfig config = new MoreShipsConfig(getConfig());

        NamespacedKey shipCoreMarker = new NamespacedKey(this, "ship_core_marker");
        NamespacedKey shipIdKey = new NamespacedKey(this, "ship_id");

        ShipCoreItem shipCoreItem = new ShipCoreItem(
                shipCoreMarker,
                config.shipCoreBaseMaterial(),
                config.shipCoreDisplayName());
        ShipRegistry shipRegistry = new ShipRegistry(ShipIdentityGenerator.uuid());
        ShipEntitySpawner entitySpawner = new ShipEntitySpawner(shipIdKey);
        RuntimeBindingRegistry bindingRegistry = new RuntimeBindingRegistry();
        ShipTeardownService teardownService = new ShipTeardownService(shipRegistry, bindingRegistry);

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

        ShipsCommand shipsCommand = new ShipsCommand(
                shipCoreItem, shipRegistry, bindingRegistry, placementListener);
        PluginCommand command = getCommand("moreships");
        if (command != null) {
            command.setExecutor(shipsCommand);
            command.setTabCompleter(shipsCommand);
        } else {
            getLogger().severe("Could not find /moreships command — plugin.yml misconfiguration?");
        }

        getLogger().info("MoreShips enabled (BUILD-03b). Teardown conservation + break listener in place.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MoreShips disabled.");
    }
}
