package com.glooshy.ships.ship;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ship sizes (spec L1 §2/§13): hull dimensions in blocks (width × length),
 * module capacity, and the discrete module positions on the hull grid.
 *
 * <p>Layout principles (spec §13): the helm is fixed (not a module — riding
 * the ship is steering); some space stays open; modules occupy the designated
 * module areas — bow/stern rows for small and medium, plus side columns for
 * large. The center of medium/large hulls stays open as deck space.
 *
 * <p>The physical hitbox is the larger hull dimension (spec: slightly smaller
 * than full visual dimensions — the collision margin shrinks it further).
 */
public enum ShipSize {
    /** 2×3 hull, 2 module positions at the stern. */
    SMALL(2, 3, 3.0, List.of(
            new ModulePos(0, 2),
            new ModulePos(1, 2))),

    /** 3×4 hull, 6 module positions: bow row + stern row. */
    MEDIUM(3, 4, 4.0, List.of(
            new ModulePos(0, 0), new ModulePos(1, 0), new ModulePos(2, 0),
            new ModulePos(0, 3), new ModulePos(1, 3), new ModulePos(2, 3))),

    /** 3×8 hull, 12 module positions: bow row + stern row + side columns. */
    LARGE(3, 8, 8.0, List.of(
            new ModulePos(0, 0), new ModulePos(1, 0), new ModulePos(2, 0),
            new ModulePos(0, 7), new ModulePos(1, 7), new ModulePos(2, 7),
            new ModulePos(0, 2), new ModulePos(0, 4),
            new ModulePos(2, 2), new ModulePos(2, 4),
            new ModulePos(0, 5), new ModulePos(2, 5)));

    private final int width;
    private final int length;
    private final double hitboxWidth;
    private final Set<ModulePos> positions;

    ShipSize(int width, int length, double hitboxWidth, List<ModulePos> positions) {
        this.width = width;
        this.length = length;
        this.hitboxWidth = hitboxWidth;
        this.positions = positions.stream().collect(Collectors.toUnmodifiableSet());
    }

    /** Hull width in blocks (columns, port→starboard). */
    public int width() {
        return width;
    }

    /** Hull length in blocks (rows, bow→stern). */
    public int length() {
        return length;
    }

    /** Physical hitbox span (the larger hull dimension). */
    public double hitboxWidth() {
        return hitboxWidth;
    }

    public Set<ModulePos> positions() {
        return positions;
    }

    public boolean isValid(ModulePos pos) {
        return positions.contains(pos);
    }

    public int capacity() {
        return positions.size();
    }
}
