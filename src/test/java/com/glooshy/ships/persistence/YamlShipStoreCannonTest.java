package com.glooshy.ships.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.CannonState;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.ModulePos;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipSize;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Session 2: cannon shots + inventory survive the YAML round-trip. */
class YamlShipStoreCannonTest {

    @TempDir
    Path tempDir;

    @Test
    void cannon_state_round_trips() throws IOException {
        Path file = tempDir.resolve("ships.yml");
        YamlShipStore store = new YamlShipStore(file);

        ShipIdentity id = ShipIdentity.fromUuid(UUID.randomUUID());
        ModulePos pos = new ModulePos(0, 2);
        Map<Integer, Map<String, Object>> inv = new LinkedHashMap<>();
        Map<String, Object> snowball = new LinkedHashMap<>();
        snowball.put("type", "SNOWBALL");
        snowball.put("amount", 12);
        inv.put(2, snowball);

        Map<ModulePos, CannonState> cannons = Map.of(pos, new CannonState(3, inv));
        Ship ship = new Ship(id, ShipSize.SMALL, LifecyclePhase.FINALIZED, null,
                10, 10, Map.of(pos, com.glooshy.ships.ship.ModuleType.CANNON),
                Map.of(), cannons);

        store.save(List.of(ship));
        List<Ship> loaded = store.load();

        assertEquals(1, loaded.size());
        CannonState state = loaded.get(0).cannons().get(pos);
        assertEquals(3, state.shots());
        assertEquals("SNOWBALL", state.itemAt(2).get("type"));
        assertEquals(12, ((Number) state.itemAt(2).get("amount")).intValue());
    }

    @Test
    void ships_without_cannons_load_as_empty() throws IOException {
        Path file = tempDir.resolve("ships.yml");
        YamlShipStore store = new YamlShipStore(file);
        Ship ship = new Ship(ShipIdentity.fromUuid(UUID.randomUUID()),
                ShipSize.SMALL, LifecyclePhase.UNFINISHED, null);
        store.save(List.of(ship));
        assertEquals(0, store.load().get(0).cannons().size());
    }
}
