package com.glooshy.ships.ship;

import java.util.Map;

/**
 * Module-driven statistics (spec CON-10/CON-11, RQCA-07) as pure math.
 *
 * <ul>
 *   <li><b>Speed</b>: every fitted module adds weight (slower); each ENGINE
 *       offsets weight and adds boost; harder hulls are tougher but slower.
 *       Multiplier is clamped to [0.4, 1.6].</li>
 *   <li><b>HP</b>: each HEALTH module adds a flat bonus to max HP.</li>
 * </ul>
 */
public final class ShipStats {

    public static double speedMultiplier(int moduleCount, int engineCount,
                                        double hullHardness,
                                        double weightPerModule,
                                        double engineBoost,
                                        double hardnessPenalty) {
        double weight = moduleCount * weightPerModule;
        double boost = engineCount * engineBoost;
        double hardness = hullHardness * hardnessPenalty;
        double multiplier = 1.0 - weight + boost - hardness;
        return Math.max(0.4, Math.min(1.6, multiplier));
    }

    public static int bonusMaxHp(int healthModules, int healthBonusPerModule) {
        return healthModules * healthBonusPerModule;
    }

    public static int countType(Map<ModulePos, ModuleType> modules, ModuleType type) {
        return (int) modules.values().stream().filter(type::equals).count();
    }

    private ShipStats() {
    }
}
