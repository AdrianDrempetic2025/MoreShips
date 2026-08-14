package com.glooshy.ships.cargo;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Objects;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marks an opened cargo inventory as belonging to a specific ship, so the
 * {@code InventoryCloseEvent} listener knows where to persist the contents.
 */
public final class CargoHolder implements InventoryHolder {

    private final ShipIdentity shipId;

    public CargoHolder(ShipIdentity shipId) {
        this.shipId = Objects.requireNonNull(shipId, "shipId");
    }

    public ShipIdentity shipId() {
        return shipId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("CargoHolder is a marker, not a real holder");
    }
}
