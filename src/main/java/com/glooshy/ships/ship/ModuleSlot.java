package com.glooshy.ships.ship;

import org.bukkit.inventory.EquipmentSlot;

/**
 * Discrete module positions on a ship (RQCA-08: "occupying discrete
 * positions").
 *
 * <p>V1 physical representation: each slot maps to one ArmorStand equipment
 * position, so a fitted module is directly visible on the ship entity and
 * rides along with it for free (no extra entities, no per-tick follow logic).
 * The hull block keeps the helmet slot; modules take the four remaining
 * equipment positions.
 */
public enum ModuleSlot {
    BOW(EquipmentSlot.CHEST),
    STERN(EquipmentSlot.LEGS),
    PORT(EquipmentSlot.FEET),
    STARBOARD(EquipmentSlot.HAND);

    private final EquipmentSlot equipmentSlot;

    ModuleSlot(EquipmentSlot equipmentSlot) {
        this.equipmentSlot = equipmentSlot;
    }

    public EquipmentSlot equipmentSlot() {
        return equipmentSlot;
    }
}
