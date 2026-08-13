package com.glooshy.ships.hull;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Validates whether a {@link Material} is acceptable as a ship hull.
 *
 * <p>Rules (V1):
 * <ul>
 *   <li>must be a placeable block (not air, not an item-only material)</li>
 *   <li>must be solid (rejects fluids, plants, signs, torches, etc.)</li>
 *   <li>must have hardness ≥ {@code minHardness}</li>
 * </ul>
 *
 * <p>{@code minHardness} is configurable (CON-01). Per-material overrides are a
 * future slice.
 *
 * <p>Rule logic is split into {@link #validateRules} (pure primitives, unit-tested)
 * and {@link #validate(Material)} (adapter that reads Material metadata at
 * runtime — verified in BUILD-SMOKE because Material's static initializer
 * requires server context).
 */
public final class HullValidator {

    private final double minHardness;

    public HullValidator(double minHardness) {
        this.minHardness = minHardness;
    }

    public @NotNull HullValidationResult validate(@NotNull Material material) {
        return validateRules(material.isAir(), material.isBlock(), material.isSolid(), material.getHardness());
    }

    /**
     * Pure-logic validation rules — testable without server context.
     */
    public @NotNull HullValidationResult validateRules(
            boolean isAir, boolean isBlock, boolean isSolid, double hardness) {
        if (isAir) {
            return HullValidationResult.invalid("Hull material cannot be air.");
        }
        if (!isBlock) {
            return HullValidationResult.invalid("Hull material must be a placeable block.");
        }
        if (!isSolid) {
            return HullValidationResult.invalid("Hull material must be solid (rejects fluids, plants, signs).");
        }
        if (hardness < minHardness) {
            return HullValidationResult.invalid(
                    "Hull material too soft (hardness " + hardness + " < minimum " + minHardness + ").");
        }
        return HullValidationResult.valid();
    }
}
