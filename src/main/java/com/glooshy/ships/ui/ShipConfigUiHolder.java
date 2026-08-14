package com.glooshy.ships.ui;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Objects;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marks an opened inventory as the ship configuration UI for one ship.
 * Carries the finalize-confirmation window state (two clicks required).
 */
public final class ShipConfigUiHolder implements InventoryHolder {

    private final ShipIdentity shipId;
    private long finalizeConfirmDeadline = 0L;

    public ShipConfigUiHolder(ShipIdentity shipId) {
        this.shipId = Objects.requireNonNull(shipId, "shipId");
    }

    public ShipIdentity shipId() {
        return shipId;
    }

    /** First finalize click: returns true if a confirmation window is now open. */
    public boolean armFinalize(long nowMillis) {
        finalizeConfirmDeadline = nowMillis + 5000L;
        return true;
    }

    /** Second finalize click: true if within the confirmation window. */
    public boolean confirmFinalize(long nowMillis) {
        return nowMillis <= finalizeConfirmDeadline;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("ShipConfigUiHolder is a marker, not a real holder");
    }
}
