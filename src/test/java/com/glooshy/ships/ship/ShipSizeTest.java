package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the hull grid model: position encoding, size validation, and the
 * ship-local offset math (rotation).
 */
class ShipSizeTest {

    @Test
    void encoded_round_trips() {
        for (ModulePos pos : ShipSize.LARGE.positions()) {
            assertEquals(pos, ModulePos.decode(pos.encoded()));
        }
    }

    @Test
    void decode_rejects_malformed() {
        assertNull(ModulePos.decode(null));
        assertNull(ModulePos.decode(""));
        assertNull(ModulePos.decode("bow"));
        assertNull(ModulePos.decode("r"));
        assertNull(ModulePos.decode("rXcY"));
    }

    @Test
    void small_accepts_only_its_two_positions() {
        assertTrue(ShipSize.SMALL.isValid(new ModulePos(0, 2)));
        assertTrue(ShipSize.SMALL.isValid(new ModulePos(1, 2)));
        assertFalse(ShipSize.SMALL.isValid(new ModulePos(0, 0)), "bow row is helm space on small");
        assertFalse(ShipSize.SMALL.isValid(new ModulePos(1, 1)), "center stays open");
        assertFalse(ShipSize.SMALL.isValid(new ModulePos(5, 5)));
    }

    @Test
    void medium_keeps_center_open() {
        assertFalse(ShipSize.MEDIUM.isValid(new ModulePos(1, 1)));
        assertFalse(ShipSize.MEDIUM.isValid(new ModulePos(1, 2)));
        assertTrue(ShipSize.MEDIUM.isValid(new ModulePos(1, 0)));
        assertTrue(ShipSize.MEDIUM.isValid(new ModulePos(2, 3)));
    }

    @Test
    void large_has_bow_stern_and_sides() {
        assertTrue(ShipSize.LARGE.isValid(new ModulePos(1, 0)), "bow center");
        assertTrue(ShipSize.LARGE.isValid(new ModulePos(1, 7)), "stern center");
        assertTrue(ShipSize.LARGE.isValid(new ModulePos(0, 2)), "port side");
        assertTrue(ShipSize.LARGE.isValid(new ModulePos(2, 5)), "starboard side");
        assertFalse(ShipSize.LARGE.isValid(new ModulePos(1, 4)), "center deck stays open");
    }

    @Test
    void local_offsets_are_forward_and_starboard() {
        ModulePos bow = new ModulePos(1, 0);   // center bow on medium
        ModulePos stern = new ModulePos(1, 3); // center stern
        assertEquals(1.5, bow.localZ(ShipSize.MEDIUM), 1e-9, "bow is forward of center");
        assertEquals(-1.5, stern.localZ(ShipSize.MEDIUM), 1e-9, "stern is behind center");

        ModulePos port = new ModulePos(0, 0);
        ModulePos starboard = new ModulePos(2, 0);
        assertEquals(-1.0, port.localX(ShipSize.MEDIUM), 1e-9, "port is left");
        assertEquals(1.0, starboard.localX(ShipSize.MEDIUM), 1e-9, "starboard is right");
    }

    @Test
    void world_offset_rotates_without_stretching() {
        for (double yaw : new double[] {0, 45, 90, 180, 270}) {
            double[] o = ModulePos.worldOffset(yaw, 1.0, 2.0);
            assertEquals(Math.sqrt(5), Math.sqrt(o[0] * o[0] + o[1] * o[1]), 1e-9,
                    "yaw " + yaw);
        }
    }

    @Test
    void world_offset_yaw0_forward_is_south() {
        double[] o = ModulePos.worldOffset(0, 0, 1);
        assertEquals(0, o[0], 1e-9);
        assertEquals(1, o[1], 1e-9);
    }
}
