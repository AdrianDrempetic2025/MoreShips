package com.glooshy.ships;

import com.glooshy.ships.command.ShipsCommand;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.listener.ShipCorePlacementListener;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.runtime.ShipEntitySpawner;
import com.glooshy.ships.ship.ShipRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Project Beacon — Custom Ship System for Paper 26.2.
 *
 * <p>V1 / BUILD-01: ship core item + placement in water creates a ship with
 * a unique identity.
 *
 * <p>V1 / BUILD-02 (this revision): the placed ship now has a visible runtime
 * entity (glowing ArmorStand) at the click location, bound bidirectionally
 * to the ship identity. Future slices: persistence, hull application, etc.
 */
public final class MoreShips extends JavaPlugin {

    private ShipRegistry shipRegistry;
    private ShipCoreItem shipCoreItem;
    private RuntimeBindingRegistry bindingRegistry;

    @Override
    public void onEnable() {
        NamespacedKey shipCoreMarker = new NamespacedKey(this, "ship_core_marker");
        NamespacedKey shipIdKey = new NamespacedKey(this, "ship_id");

        shipCoreItem = new ShipCoreItem(shipCoreMarker);
        shipRegistry = new ShipRegistry(ShipIdentityGenerator.uuid());
        ShipEntitySpawner entitySpawner = new ShipEntitySpawner(shipIdKey);
        bindingRegistry = new RuntimeBindingRegistry();

        getServer().getPluginManager().registerEvents(
                new ShipCorePlacementListener(shipCoreItem, shipRegistry, entitySpawner, bindingRegistry),
                this);

        getCommand("moreships").setExecutor(
                new ShipsCommand(shipCoreItem, shipRegistry, bindingRegistry));

        getLogger().info("MoreShips enabled. Ship Core placement is live (with runtime entity).");
    }

    @Override
    public void onDisable() {
        getLogger().info("MoreShips disabled.");
    }
}
