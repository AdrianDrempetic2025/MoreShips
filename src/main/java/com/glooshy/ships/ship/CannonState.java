package com.glooshy.ships.ship;

import java.util.Map;

/**
 * Per-cannon runtime state (Session 2): charged shots plus the cannon's own
 * inventory of fuel and ammunition (raw-map ItemStacks, keeping the domain
 * server-free like cargo holds).
 *
 * <p>Firing consumes one ammo item and one charged shot per shot. Charged
 * shots are generated from fuel (furnace-style burn values) only at fire
 * time — fuel is never consumed while the cannon idles.
 */
public record CannonState(int shots, Map<Integer, Map<String, Object>> inventory) {

    /** UI: 2 rows — ammo row + fuel row, with locked label/info slots. */
    public static final int INVENTORY_SIZE = 18;

    public CannonState {
        if (shots < 0) {
            throw new IllegalArgumentException("shots must be non-negative, got " + shots);
        }
        inventory = inventory == null ? Map.of() : Map.copyOf(inventory);
    }

    public static CannonState empty() {
        return new CannonState(0, Map.of());
    }

    public CannonState withShots(int newShots) {
        return new CannonState(newShots, inventory);
    }

    public CannonState withInventory(Map<Integer, Map<String, Object>> newInventory) {
        return new CannonState(shots, newInventory);
    }

    /** The item stack stored at a slot, or null. */
    public Map<String, Object> itemAt(int slot) {
        return inventory.get(slot);
    }
}
