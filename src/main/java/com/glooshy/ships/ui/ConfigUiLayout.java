package com.glooshy.ships.ui;

import com.glooshy.ships.ship.ModuleSlot;
import java.util.Map;

/**
 * Pure layout of the ship configuration UI (spec L1 §12/14, RQCA-09):
 * a 5×9 chest inventory representing the ship top-down — BOW at the top,
 * STERN at the bottom, PORT on the left, STARBOARD on the right, exactly
 * matching the physical module positions around the ship.
 */
public final class ConfigUiLayout {

    public static final int SIZE = 45;

    /** Top-down: pramac gore, krma dolje, lijevo/desno po bokovima. */
    public static final Map<ModuleSlot, Integer> SLOT_INDEX = Map.of(
            ModuleSlot.BOW, 4,
            ModuleSlot.PORT, 12,
            ModuleSlot.STARBOARD, 14,
            ModuleSlot.STERN, 40);

    /** Finalize button (bottom-right). */
    public static final int FINALIZE_INDEX = 44;

    /** Info items. */
    public static final int HULL_INFO_INDEX = 0;
    public static final int STATS_INFO_INDEX = 8;
    public static final int MODULES_INFO_INDEX = 36;
    public static final int HELP_INFO_INDEX = 38;

    private static final Map<Integer, ModuleSlot> INDEX_SLOT = Map.of(
            4, ModuleSlot.BOW,
            12, ModuleSlot.PORT,
            14, ModuleSlot.STARBOARD,
            40, ModuleSlot.STERN);

    /** The module slot an inventory index represents, if any. */
    public static ModuleSlot slotAt(int index) {
        return INDEX_SLOT.get(index);
    }

    public static boolean isModuleSlot(int index) {
        return INDEX_SLOT.containsKey(index);
    }

    public static boolean isInfoIndex(int index) {
        return index == HULL_INFO_INDEX || index == STATS_INFO_INDEX
                || index == MODULES_INFO_INDEX || index == HELP_INFO_INDEX
                || index == FINALIZE_INDEX;
    }

    private ConfigUiLayout() {
    }
}
