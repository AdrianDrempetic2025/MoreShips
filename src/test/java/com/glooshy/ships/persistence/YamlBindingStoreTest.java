package com.glooshy.ships.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.runtime.BindingState;
import com.glooshy.ships.runtime.RuntimeBinding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FALSIFICATION_PROOF + REGRESSION_GUARDs for {@link YamlBindingStore}.
 */
class YamlBindingStoreTest {

    @TempDir
    Path tempDir;

    /**
     * FALSIFICATION_PROOF — DEFECT-10 (BINDING_PERSISTENCE_LOSS).
     */
    @Test
    void save_then_load_preserves_bindings() throws IOException {
        Path file = tempDir.resolve("bindings.yml");
        YamlBindingStore store = new YamlBindingStore(file);

        RuntimeBinding binding1 = RuntimeBinding.active(
                ShipIdentity.fromUuid(UUID.randomUUID()),
                UUID.randomUUID());
        RuntimeBinding binding2 = RuntimeBinding.active(
                ShipIdentity.fromUuid(UUID.randomUUID()),
                UUID.randomUUID());

        store.save(List.of(binding1, binding2));

        List<RuntimeBinding> loaded = store.load();

        assertEquals(2, loaded.size());
        // Both bindings' ship+entity pairs survive
        assertTrue(loaded.stream().anyMatch(b ->
                b.shipId().equals(binding1.shipId())
                        && b.entityUuid().equals(binding1.entityUuid())
                        && b.state() == BindingState.ACTIVE));
        assertTrue(loaded.stream().anyMatch(b ->
                b.shipId().equals(binding2.shipId())
                        && b.entityUuid().equals(binding2.entityUuid())));
    }

    @Test
    void load_returns_empty_when_file_missing() throws IOException {
        YamlBindingStore store = new YamlBindingStore(tempDir.resolve("nope.yml"));
        assertTrue(store.load().isEmpty());
    }

    @Test
    void save_atomic_no_temp_left_behind() throws IOException {
        Path file = tempDir.resolve("atomic.yml");
        YamlBindingStore store = new YamlBindingStore(file);

        store.save(List.of(RuntimeBinding.active(
                ShipIdentity.fromUuid(UUID.randomUUID()), UUID.randomUUID())));

        assertTrue(Files.exists(file));
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        assertFalse(Files.exists(temp), "Temp file must be renamed away");
    }
}
