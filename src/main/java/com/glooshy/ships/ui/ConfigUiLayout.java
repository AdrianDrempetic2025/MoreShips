package com.glooshy.ships.ui;

import com.glooshy.ships.ship.ModulePos;
import com.glooshy.ships.ship.ShipSize;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure layout of the ship configuration UI (spec L1 §12/14, RQCA-09):
 * a chest inventory representing the ship top-down. BOW/pramac at the top,
 * STERN/krma at the bottom, PORT on the left, STARBOARD on the right — the
 * UI arrangement mirrors the physical module positions on the hull grid.
 *
 * <p>Layouts are defined per {@link ShipSize}; large ships get a 6-row
 * inventory, small and medium a 5-row one.
 */
public final class ConfigUiLayout {

    private static final Map<ShipSize, Map<ModulePos, Integer>> LAYOUT = buildLayout();
    private static final Map<ShipSize, Integer> SIZE = Map.of(
            ShipSize.SMALL, 45, ShipSize.MEDIUM, 45, ShipSize.LARGE, 54);

    private static Map<ShipSize, Map<ModulePos, Integer>> buildLayout() {
        Map<ShipSize, Map<ModulePos, Integer>> layout = new HashMap<>();

        // SMALL: 2 stern positions side by side near the bottom (r2c0/r2c1)
        Map<ModulePos, Integer> small = new HashMap<>();
        small.put(new ModulePos(0, 2), 30);
        small.put(new ModulePos(1, 2), 32);
        layout.put(ShipSize.SMALL, Map.copyOf(small));

        // MEDIUM: bow row on top, stern row on the bottom
        Map<ModulePos, Integer> medium = new HashMap<>();
        medium.put(new ModulePos(0, 0), 3);
        medium.put(new ModulePos(1, 0), 4);
        medium.put(new ModulePos(2, 0), 5);
        medium.put(new ModulePos(0, 3), 39);
        medium.put(new ModulePos(1, 3), 40);
        medium.put(new ModulePos(2, 3), 41);
        layout.put(ShipSize.MEDIUM, Map.copyOf(medium));

        // LARGE: bow row top, stern row bottom, side pairs down the middle
        Map<ModulePos, Integer> large = new HashMap<>();
        large.put(new ModulePos(0, 0), 3);
        large.put(new ModulePos(1, 0), 4);
        large.put(new ModulePos(2, 0), 5);
        large.put(new ModulePos(0, 2), 12);
        large.put(new ModulePos(2, 2), 14);
        large.put(new ModulePos(0, 4), 30);
        large.put(new ModulePos(2, 4), 32);
        large.put(new ModulePos(0, 5), 39);
        large.put(new ModulePos(2, 5), 41);
        large.put(new ModulePos(0, 7), 48);
        large.put(new ModulePos(1, 7), 49);
        large.put(new ModulePos(2, 7), 50);
        layout.put(ShipSize.LARGE, Map.copyOf(large));

        return layout;
    }

    public static int inventorySize(ShipSize size) {
        return SIZE.get(size);
    }

    /** UI index of a module position for a given ship size. */
    public static int indexOf(ShipSize size, ModulePos pos) {
        Integer index = LAYOUT.get(size).get(pos);
        if (index == null) {
            throw new IllegalArgumentException(
                    pos.encoded() + " has no UI slot on a " + size + " ship");
        }
        return index;
    }

    /** The module position an inventory index represents, if any. */
    public static ModulePos posAt(ShipSize size, int index) {
        for (Map.Entry<ModulePos, Integer> e : LAYOUT.get(size).entrySet()) {
            if (e.getValue() == index) {
                return e.getKey();
            }
        }
        return null;
    }

    public static int finalizeIndex(ShipSize size) {
        return size == ShipSize.LARGE ? 53 : 44;
    }

    public static int hullInfoIndex(ShipSize size) {
        return 0;
    }

    public static int statsInfoIndex(ShipSize size) {
        return 8;
    }

    public static int modulesInfoIndex(ShipSize size) {
        return size == ShipSize.LARGE ? 45 : 36;
    }

    public static int helpInfoIndex(ShipSize size) {
        return size == ShipSize.LARGE ? 47 : 38;
    }

    private ConfigUiLayout() {
    }
}
