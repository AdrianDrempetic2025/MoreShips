package com.glooshy.ships.cargo;

import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Opens and saves a ship's cargo inventory (RQCA-21/22).
 *
 * <p>One shared 27-slot cargo per ship, available when the ship has at least
 * one fitted CARGO module and is in a pre-destruction phase. Bukkit-side
 * conversion between ItemStack and the raw-map domain form lives here.
 */
public final class CargoService {

    private final ShipRegistry shipRegistry;

    public CargoService(ShipRegistry shipRegistry) {
        this.shipRegistry = shipRegistry;
    }

    /** Open the cargo inventory of the given ship for the player. */
    public void open(Player player, Ship ship) {
        if (!ship.hasCargoModule()) {
            player.sendMessage(Component.text(
                    "This ship has no cargo module fitted.", NamedTextColor.RED));
            return;
        }

        Inventory inventory = player.getServer().createInventory(
                new CargoHolder(ship.identity()),
                Ship.cargoSize(),
                Component.text("Ship Cargo", NamedTextColor.GOLD));

        ship.cargo().forEach((slot, itemMap) -> {
            if (slot < 0 || slot >= Ship.cargoSize()) {
                return;
            }
            try {
                inventory.setItem(slot, ItemStack.deserialize(itemMap));
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // Unreadable item from an older/manual edit — skip, never block opening
            }
        });

        player.openInventory(inventory);
    }

    /**
     * Persist the inventory contents back onto the ship. Called on inventory
     * close. Returns true if the ship was still live.
     */
    public boolean save(CargoHolder holder, Inventory inventory) {
        Map<Integer, Map<String, Object>> cargo = new HashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            cargo.put(slot, item.serialize());
        }
        try {
            shipRegistry.setCargo(holder.shipId(), cargo);
            return true;
        } catch (IllegalStateException e) {
            return false; // Ship destroyed between open and close
        }
    }

    /** Deserialize one cargo entry; null if unreadable. */
    public ItemStack deserializeItem(Map<String, Object> itemMap) {
        try {
            return ItemStack.deserialize(itemMap);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }
}
