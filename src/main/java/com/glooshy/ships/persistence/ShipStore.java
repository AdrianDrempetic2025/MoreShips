package com.glooshy.ships.persistence;

import com.glooshy.ships.ship.Ship;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Persistence layer for the live ship set.
 *
 * <p>Implementations load ships from disk on plugin enable and save them on
 * disable (or autosave, future slice). This is what makes ship identity
 * "durable" per RQCA-26.
 */
public interface ShipStore {

    /**
     * Load all persisted ships. Returns an empty list if the store file does
     * not exist (first run).
     */
    @NotNull List<Ship> load() throws IOException;

    /**
     * Persist the given ships atomically (write-temp-then-rename where possible).
     */
    void save(@NotNull List<Ship> ships) throws IOException;
}
