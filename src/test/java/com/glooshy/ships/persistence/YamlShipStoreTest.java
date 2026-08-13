package com.glooshy.ships.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.Ship;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FALSIFICATION_PROOF + REGRESSION_GUARDs for {@link YamlShipStore}.
 *
 * <p>Uses {@link TempDir} for isolation. Tests use null hull material because
 * org.bukkit.Material cannot be referenced in unit tests (static init requires
 * server context). The Material-accepting path is verified in BUILD-SMOKE.
 *
 * <p>Named defect: DEFECT-09 (PERSISTENCE_LOSS) — saved ships do not survive
 * the load round-trip.
 */
class YamlShipStoreTest {

    @TempDir
    Path tempDir;

    /**
     * FALSIFICATION_PROOF — DEFECT-09 (PERSISTENCE_LOSS).
     *
     * <p>Mutation plan: make {@code save} a no-op (don't write the file).
     * Expected RED: load returns empty, count assertion fails.
     *
     * <p>Alternative mutation: make {@code load} return an empty list. Expected
     * RED: same assertion fails.
     */
    @Test
    void save_then_load_preserves_ships() throws IOException {
        Path file = tempDir.resolve("ships.yml");
        YamlShipStore store = new YamlShipStore(file);

        Ship ship1 = new Ship(
                ShipIdentity.fromUuid(UUID.randomUUID()),
                LifecyclePhase.UNFINISHED,
                null);
        Ship ship2 = new Ship(
                ShipIdentity.fromUuid(UUID.randomUUID()),
                LifecyclePhase.HULL_APPLIED,
                null);

        store.save(List.of(ship1, ship2));

        List<Ship> loaded = store.load();

        assertEquals(2, loaded.size(),
                "Two saved ships must come back (DEFECT-09)");
        // Identities preserved
        assertTrue(loaded.stream().anyMatch(s -> s.identity().equals(ship1.identity())));
        assertTrue(loaded.stream().anyMatch(s -> s.identity().equals(ship2.identity())));
        // Phases preserved
        Ship loadedShip1 = loaded.stream().filter(s -> s.identity().equals(ship1.identity())).findFirst().orElseThrow();
        Ship loadedShip2 = loaded.stream().filter(s -> s.identity().equals(ship2.identity())).findFirst().orElseThrow();
        assertEquals(LifecyclePhase.UNFINISHED, loadedShip1.phase());
        assertEquals(LifecyclePhase.HULL_APPLIED, loadedShip2.phase());
        assertNull(loadedShip1.hullMaterial(), "Null hull material must round-trip as null");
        assertNull(loadedShip2.hullMaterial());
    }

    @Test
    void load_returns_empty_when_file_does_not_exist() throws IOException {
        Path file = tempDir.resolve("never-existed.yml");
        YamlShipStore store = new YamlShipStore(file);

        List<Ship> loaded = store.load();

        assertTrue(loaded.isEmpty(), "Non-existent file must yield empty list (first run)");
    }

    @Test
    void save_creates_parent_directory() throws IOException {
        Path nested = tempDir.resolve("a/b/c/ships.yml");
        YamlShipStore store = new YamlShipStore(nested);

        store.save(List.of());

        assertTrue(Files.exists(nested), "Save must create parent directories");
    }

    @Test
    void save_empty_list_does_not_throw() throws IOException {
        Path file = tempDir.resolve("empty.yml");
        YamlShipStore store = new YamlShipStore(file);

        assertDoesNotThrow(() -> store.save(List.of()));
        assertTrue(Files.exists(file), "Empty save still writes the file");
    }

    @Test
    void save_is_atomic_via_temp_then_rename() throws IOException {
        Path file = tempDir.resolve("atomic.yml");
        YamlShipStore store = new YamlShipStore(file);

        store.save(List.of(new Ship(
                ShipIdentity.fromUuid(UUID.randomUUID()),
                LifecyclePhase.UNFINISHED, null)));

        assertTrue(Files.exists(file), "Target file must exist after atomic save");
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        assertFalse(Files.exists(temp),
                "Temp file must be renamed away — no .tmp left behind");
    }

    @Test
    void save_overwrites_previous_content() throws IOException {
        Path file = tempDir.resolve("overwrite.yml");
        YamlShipStore store = new YamlShipStore(file);

        Ship first = new Ship(ShipIdentity.fromUuid(UUID.randomUUID()), LifecyclePhase.UNFINISHED, null);
        store.save(List.of(first));
        assertEquals(1, store.load().size());

        // Save a different list — must replace, not append
        store.save(List.of());
        assertTrue(store.load().isEmpty(), "Second save must overwrite the first");
    }

    @Test
    void many_ships_roundtrip() throws IOException {
        Path file = tempDir.resolve("many.yml");
        YamlShipStore store = new YamlShipStore(file);

        List<Ship> many = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            many.add(new Ship(
                    ShipIdentity.fromUuid(UUID.randomUUID()),
                    i % 2 == 0 ? LifecyclePhase.UNFINISHED : LifecyclePhase.HULL_APPLIED,
                    null));
        }
        store.save(many);

        List<Ship> loaded = store.load();

        assertEquals(100, loaded.size(), "All 100 ships must survive round-trip");
    }
}
