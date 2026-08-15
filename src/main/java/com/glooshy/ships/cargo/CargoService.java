package com.glooshy.ships.cargo;

import com.glooshy.ships.ship.ModulePos;
import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.identity.ShipIdentity;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
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
    /** Live open holds — the freshest cargo state (Session-2 anti-dup). */
    private final Map<ShipIdentity, Map<ModulePos, Inventory>> openInventories =
            new ConcurrentHashMap<>();

    public CargoService(ShipRegistry shipRegistry) {
        this.shipRegistry = shipRegistry;
    }

    /** Open the cargo hold of one specific cargo module for the player. */
    public void open(Player player, Ship ship, ModulePos pos) {
        ModuleType type = ship.modules().get(pos);
        if (type != ModuleType.CARGO) {
            player.sendMessage(Component.text(
                    "No cargo module at " + pos.encoded() + ".",
                    NamedTextColor.RED));
            return;
        }

        Inventory inventory = player.getServer().createInventory(
                new CargoHolder(ship.identity(), pos),
                Ship.cargoHoldSize(),
                Component.text("Cargo " + pos.encoded(), NamedTextColor.GOLD));

        ship.cargo().getOrDefault(pos, Map.of()).forEach((index, itemMap) -> {
            if (index < 0 || index >= Ship.cargoHoldSize()) {
                return;
            }
            ItemStack item = deserializeItem(itemMap);
            if (item != null) {
                inventory.setItem(index, item);
            }
        });

        openInventories.computeIfAbsent(ship.identity(), k -> new ConcurrentHashMap<>())
                .put(pos, inventory);
        player.openInventory(inventory);
    }

    public void markClosed(ShipIdentity shipId, ModulePos pos) {
        Map<ModulePos, Inventory> open = openInventories.get(shipId);
        if (open != null) {
            open.remove(pos);
        }
    }

    /**
     * Destruction path: drop the LIVE contents of any open cargo inventory
     * (the freshest copy) and clear it. Returns the positions whose contents
     * were handled — the caller must NOT also drop the serialized holds for
     * those, or items would duplicate.
     */
    public java.util.Set<ModulePos> dropOpenAndClose(ShipIdentity shipId, Location dropAt) {
        Map<ModulePos, Inventory> open = openInventories.remove(shipId);
        if (open == null) {
            return java.util.Set.of();
        }
        java.util.Set<ModulePos> handled = new java.util.HashSet<>();
        for (Map.Entry<ModulePos, Inventory> entry : open.entrySet()) {
            for (ItemStack item : entry.getValue().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    dropAt.getWorld().dropItemNaturally(dropAt, item);
                }
            }
            entry.getValue().clear();
            handled.add(entry.getKey());
        }
        return handled;
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
        markClosed(holder.shipId(), holder.pos());
        try {
            shipRegistry.setCargo(holder.shipId(), holder.pos(), contents);
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
