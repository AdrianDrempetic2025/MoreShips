package com.glooshy.ships.ship;

/**
 * Lifecycle phases a ship may occupy (per L1 SCIN-01).
 *
 * <p>V1 supports only the linear path needed for early slices:
 * <pre>
 *   (start) → UNFINISHED → HULL_APPLIED → FINALIZED → DESTROYED
 *                  ↓             ↓
 *               REMOVED       REMOVED         (teardown — pre-finalization break)
 * </pre>
 *
 * <p>Future phases (operational sub-states, wreck recovery states) are added
 * as the lifecycle grows. DESTROYED is terminal for the ship entity; the wreck
 * is a separate entity with its own lifecycle (RQCA-03).
 */
public enum LifecyclePhase {
    UNFINISHED,
    HULL_APPLIED,
    FINALIZED,
    DESTROYED,
    REMOVED
}
