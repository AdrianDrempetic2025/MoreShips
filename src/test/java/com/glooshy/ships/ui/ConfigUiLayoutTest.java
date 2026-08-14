package com.glooshy.ships.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.ship.ModuleSlot;
import org.junit.jupiter.api.Test;

/**
 * Tests for the configuration UI layout (RQCA-09: UI position = physical
 * position). BOW at the top, STERN at the bottom, PORT left, STARBOARD
 * right — mirroring the physical module slots around the ship.
 */
class ConfigUiLayoutTest {

    @Test
    void bow_is_top_center_stern_is_bottom_center() {
        int bow = ConfigUiLayout.SLOT_INDEX.get(ModuleSlot.BOW);
        int stern = ConfigUiLayout.SLOT_INDEX.get(ModuleSlot.STERN);
        assertEquals(0, bow / 9, "BOW must be in the top row");
        assertEquals(4, bow % 9, "BOW must be centered");
        assertEquals(4, ConfigUiLayout.SIZE / 9 - 1, stern / 9,
                "STERN must be in the bottom row");
        assertEquals(4, stern % 9, "STERN must be centered");
    }

    @Test
    void port_is_left_of_starboard() {
        int port = ConfigUiLayout.SLOT_INDEX.get(ModuleSlot.PORT);
        int starboard = ConfigUiLayout.SLOT_INDEX.get(ModuleSlot.STARBOARD);
        assertEquals(port / 9, starboard / 9, "PORT and STARBOARD share a row");
        assertTrue(port % 9 < starboard % 9, "PORT (left) < STARBOARD (right)");
    }

    @Test
    void slot_at_round_trips_every_module_slot() {
        for (ModuleSlot slot : ModuleSlot.values()) {
            int index = ConfigUiLayout.SLOT_INDEX.get(slot);
            assertTrue(ConfigUiLayout.isModuleSlot(index), slot + " index must be a module slot");
            assertEquals(slot, ConfigUiLayout.slotAt(index));
        }
    }

    @Test
    void no_index_is_both_module_slot_and_info() {
        for (ModuleSlot slot : ModuleSlot.values()) {
            int index = ConfigUiLayout.SLOT_INDEX.get(slot);
            assertFalse(ConfigUiLayout.isInfoIndex(index),
                    slot + " slot collides with an info index");
        }
    }

    @Test
    void all_indexes_within_inventory_bounds() {
        assertTrue(ConfigUiLayout.FINALIZE_INDEX < ConfigUiLayout.SIZE);
        assertTrue(ConfigUiLayout.HULL_INFO_INDEX < ConfigUiLayout.SIZE);
        assertTrue(ConfigUiLayout.STATS_INFO_INDEX < ConfigUiLayout.SIZE);
        assertTrue(ConfigUiLayout.MODULES_INFO_INDEX < ConfigUiLayout.SIZE);
        assertTrue(ConfigUiLayout.HELP_INFO_INDEX < ConfigUiLayout.SIZE);
    }
}
