package com.glooshy.ships.hull;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Validates whether a {@link Material} is acceptable as a ship hull.
 *
 * <p>Rules (V1):
 * <ul>
 *   <li>must be a placeable block (not air, not an item-only material)</li>
 *   <li>must be a full opaque cube ({@link Material#isOccluding()}) — rejects
 *       slabs, stairs, fences, glass, signs, plants, fluids, etc.</li>
 *   <li>must have hardness ≥ {@code minHardness}</li>
 * </ul>
 *
 * <p>{@code minHardness} is configurable (CON-01). Per-material overrides are a
 * future slice.
 *
 * <p>Note on {@code isOccluding} vs {@code isSolid}: {@code isSolid} only
 * reports whether a block has collision. Slabs, stairs, fences, and signs all
 * have collision and would pass an {@code isSolid} check — that was the BUILD-04
 * bug. {@code isOccluding} requires a full opaque cube, which is what we want
 * for a hull.
 *
 * <p>Rule logic is split into {@link #validateRules} (pure primitives, unit-tested)
 * and {@link #validate(Material)} (adapter that reads Material metadata at
 * runtime — verified in BUILD-SMOKE).
 */
public final class HullValidator {

    private final double minHardness;

    public HullValidator(double minHardness) {
        this.minHardness = minHardness;
    }

    public @NotNull HullValidationResult validate(@NotNull Material material) {
        return validateRules(
                material.isAir(),
                material.isBlock(),
                material.isOccluding(),
                material.getHardness());
    }

    /**
     * Pure-logic validation rules — testable without server context.
     */
    public @NotNull HullValidationResult validateRules(
            boolean isAir, boolean isBlock, boolean isOccluding, double hardness) {
        if (isAir) {
            return HullValidationResult.invalid("Hull material cannot be air.");
        }
        if (!isBlock) {
            return HullValidationResult.invalid("Hull material must be a placeable block.");
        }
        if (!isOccluding) {
            return HullValidationResult.invalid(
                    "Hull material must be a full opaque cube (rejects slabs, stairs, fences, glass, signs, plants, fluids).");
        }
        if (hardness < minHardness) {
            return HullValidationResult.invalid(
                    "Hull material too soft (hardness " + hardness + " < minimum " + minHardness + ").");
        }
        return HullValidationResult.valid();
    }
}
