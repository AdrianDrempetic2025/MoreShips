package com.glooshy.ships.cargo;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.ModulePos;
import java.util.Objects;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marks an opened cargo inventory as belonging to one specific cargo module
 * (ship + position), so the {@code InventoryCloseEvent} listener knows where
 * to persist the contents.
 */
public final class CargoHolder implements InventoryHolder {

    private final ShipIdentity shipId;
    private final ModulePos pos;

    public CargoHolder(ShipIdentity shipId, ModulePos pos) {
        this.shipId = Objects.requireNonNull(shipId, "shipId");
        this.pos = Objects.requireNonNull(pos, "pos");
    }

    public ShipIdentity shipId() {
        return shipId;
    }

    public ModulePos pos() {
        return pos;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("CargoHolder is a marker, not a real holder");
    }
}
