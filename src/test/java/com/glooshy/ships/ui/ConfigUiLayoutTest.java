package com.glooshy.ships.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.ship.ModulePos;
import com.glooshy.ships.ship.ShipSize;
import org.junit.jupiter.api.Test;

/**
 * Tests for the per-size configuration UI layout (RQCA-09: UI position =
 * physical position). Every module position of every ship size maps to a
 * unique inventory slot that collides with neither the info items nor the
 * finalize button, and stays inside the inventory.
 */
class ConfigUiLayoutTest {

    @Test
    void every_position_of_every_size_maps_round_trip() {
        for (ShipSize size : ShipSize.values()) {
            for (ModulePos pos : size.positions()) {
                int index = ConfigUiLayout.indexOf(size, pos);
                assertTrue(index >= 0 && index < ConfigUiLayout.inventorySize(size),
                        size + " " + pos.encoded() + " index out of bounds");
                assertEquals(pos, ConfigUiLayout.posAt(size, index),
                        size + " index " + index + " must round-trip to " + pos.encoded());
            }
        }
    }

    @Test
    void module_indexes_never_collide_with_info_or_finalize() {
        for (ShipSize size : ShipSize.values()) {
            int finalize = ConfigUiLayout.finalizeIndex(size);
            int[] info = {ConfigUiLayout.hullInfoIndex(size), ConfigUiLayout.statsInfoIndex(size),
                    ConfigUiLayout.modulesInfoIndex(size), ConfigUiLayout.helpInfoIndex(size)};
            for (ModulePos pos : size.positions()) {
                int index = ConfigUiLayout.indexOf(size, pos);
                assertNotEquals(finalize, index, size + " " + pos.encoded() + " collides with finalize");
                for (int i : info) {
                    assertNotEquals(i, index, size + " " + pos.encoded() + " collides with info " + i);
                }
            }
        }
    }

    @Test
    void bow_is_above_stern_for_every_size() {
        for (ShipSize size : ShipSize.values()) {
            ModulePos bow = size.positions().stream()
                    .min(java.util.Comparator.comparingInt(ModulePos::row)).orElseThrow();
            ModulePos stern = size.positions().stream()
                    .max(java.util.Comparator.comparingInt(ModulePos::row)).orElseThrow();
            if (bow.row() == stern.row()) {
                continue; // SMALL keeps the bow for the fixed helm — no bow slot
            }
            int bowIdx = ConfigUiLayout.indexOf(size, bow);
            int sternIdx = ConfigUiLayout.indexOf(size, stern);
            assertTrue(bowIdx / 9 < sternIdx / 9, size + ": bow row must be above stern row");
        }
    }

    @Test
    void capacities_match_spec() {
        assertEquals(2, ShipSize.SMALL.capacity());
        assertEquals(6, ShipSize.MEDIUM.capacity());
        assertEquals(12, ShipSize.LARGE.capacity());
    }
}
