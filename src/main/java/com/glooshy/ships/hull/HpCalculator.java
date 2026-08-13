package com.glooshy.ships.hull;

/**
 * Computes ship HP from hull material hardness via a configurable formula.
 *
 * <p>V1 formula: {@code maxHp = round(hardness × multiplier)}, clamped to a
 * minimum of 1. Configurable via {@code hp.multiplier} in config.yml (CON-01).
 *
 * <p>Per L1 spec raw §43: "Final Max HP = (hull hardness × configurable HP
 * formula × core-size modifier) + module HP changes". V1 omits core-size
 * modifier (no core size differentiation yet) and module HP changes (no
 * modules yet) — both are future slices.
 */
public final class HpCalculator {

    private final double multiplier;

    public HpCalculator(double multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("HP multiplier must be positive, got " + multiplier);
        }
        this.multiplier = multiplier;
    }

    public int computeMaxHp(double hardness) {
        return Math.max(1, (int) Math.round(hardness * multiplier));
    }

    public double multiplier() {
        return multiplier;
    }
}
