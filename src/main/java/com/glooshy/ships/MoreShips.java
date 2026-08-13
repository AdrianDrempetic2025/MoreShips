package com.glooshy.ships;

import com.glooshy.ships.command.ShipsCommand;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.listener.ShipCorePlacementListener;
import com.glooshy.ships.ship.ShipRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Project Beacon — Custom Ship System for Paper 26.2.
 *
 * <p>Multi-occupant, modular, combat-capable water vehicles. Greenfield plugin
 * developed against the Development Ontology v2 workflow (RC3 risk class).
 *
 * <p>V1 / BUILD-01: ship core item + placement in water creates a ship with
 * a unique identity. No runtime entity yet (deferred to a later slice).
 */
public final class MoreShips extends JavaPlugin {

    private ShipRegistry shipRegistry;
    private ShipCoreItem shipCoreItem;

    @Override
    public void onEnable() {
        NamespacedKey shipCoreMarker = new NamespacedKey(this, "ship_core_marker");

        shipCoreItem = new ShipCoreItem(shipCoreMarker);
        shipRegistry = new ShipRegistry(ShipIdentityGenerator.uuid());

        getServer().getPluginManager().registerEvents(
                new ShipCorePlacementListener(shipCoreItem, shipRegistry), this);

        getCommand("moreships").setExecutor(new ShipsCommand(shipCoreItem, shipRegistry));

        getLogger().info("MoreShips enabled. Ship Core placement is live.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MoreShips disabled.");
    }
}
