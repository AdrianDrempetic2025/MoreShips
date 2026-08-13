package com.glooshy.ships;

import com.glooshy.ships.command.ShipsCommand;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.listener.ShipCorePlacementListener;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.runtime.ShipEntitySpawner;
import com.glooshy.ships.ship.ShipRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Project Beacon — Custom Ship System for Paper 26.2.
 */
public final class MoreShips extends JavaPlugin {

    @Override
    public void onEnable() {
        NamespacedKey shipCoreMarker = new NamespacedKey(this, "ship_core_marker");
        NamespacedKey shipIdKey = new NamespacedKey(this, "ship_id");

        ShipCoreItem shipCoreItem = new ShipCoreItem(shipCoreMarker);
        ShipRegistry shipRegistry = new ShipRegistry(ShipIdentityGenerator.uuid());
        ShipEntitySpawner entitySpawner = new ShipEntitySpawner(shipIdKey);
        RuntimeBindingRegistry bindingRegistry = new RuntimeBindingRegistry();

        ShipCorePlacementListener placementListener = new ShipCorePlacementListener(
                shipCoreItem, shipRegistry, entitySpawner, bindingRegistry);
        getServer().getPluginManager().registerEvents(placementListener, this);

        ShipsCommand shipsCommand = new ShipsCommand(
                shipCoreItem, shipRegistry, bindingRegistry, placementListener);
        PluginCommand command = getCommand("moreships");
        if (command != null) {
            command.setExecutor(shipsCommand);
            command.setTabCompleter(shipsCommand);
        } else {
            getLogger().severe("Could not find /moreships command — plugin.yml misconfiguration?");
        }

        getLogger().info("MoreShips enabled (BUILD-02b). Ship Core placement is live.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MoreShips disabled.");
    }
}
