package com.glooshy.ships.cargo;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.ModuleSlot;
import java.util.Objects;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marks an opened cargo inventory as belonging to one specific cargo module
 * (ship + slot), so the {@code InventoryCloseEvent} listener knows where to
 * persist the contents.
 */
public final class CargoHolder implements InventoryHolder {

    private final ShipIdentity shipId;
    private final ModuleSlot slot;

    public CargoHolder(ShipIdentity shipId, ModuleSlot slot) {
        this.shipId = Objects.requireNonNull(shipId, "shipId");
        this.slot = Objects.requireNonNull(slot, "slot");
    }

    public ShipIdentity shipId() {
        return shipId;
    }

    public ModuleSlot slot() {
        return slot;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("CargoHolder is a marker, not a real holder");
    }
}
