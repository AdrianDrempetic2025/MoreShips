package com.glooshy.ships.cargo;

import com.glooshy.ships.ship.ModuleSlot;
import com.glooshy.ships.ship.ModuleType;
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
 * Opens and saves a ship's per-module cargo holds (RQCA-21/22).
 *
 * <p>Every fitted CARGO module has its own 27-slot hold, opened by
 * right-clicking that module's entity. Bukkit-side conversion between
 * ItemStack and the raw-map domain form lives here.
 */
public final class CargoService {

    private final ShipRegistry shipRegistry;

    public CargoService(ShipRegistry shipRegistry) {
        this.shipRegistry = shipRegistry;
    }

    /** Open the cargo hold of one specific cargo module for the player. */
    public void open(Player player, Ship ship, ModuleSlot slot) {
        ModuleType type = ship.modules().get(slot);
        if (type != ModuleType.CARGO) {
            player.sendMessage(Component.text(
                    "No cargo module in slot " + slot.name().toLowerCase() + ".",
                    NamedTextColor.RED));
            return;
        }

        Inventory inventory = player.getServer().createInventory(
                new CargoHolder(ship.identity(), slot),
                Ship.cargoHoldSize(),
                Component.text("Cargo " + slot.name(), NamedTextColor.GOLD));

        ship.cargo().getOrDefault(slot, Map.of()).forEach((index, itemMap) -> {
            if (index < 0 || index >= Ship.cargoHoldSize()) {
                return;
            }
            ItemStack item = deserializeItem(itemMap);
            if (item != null) {
                inventory.setItem(index, item);
            }
        });

        player.openInventory(inventory);
    }

    /**
     * Persist the inventory contents back onto the module slot. Called on
     * inventory close. Returns true if the ship was still live.
     */
    public boolean save(CargoHolder holder, Inventory inventory) {
        Map<Integer, Map<String, Object>> contents = new HashMap<>();
        for (int index = 0; index < inventory.getSize(); index++) {
            ItemStack item = inventory.getItem(index);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            contents.put(index, item.serialize());
        }
        try {
            shipRegistry.setCargo(holder.shipId(), holder.slot(), contents);
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
