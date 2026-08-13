package com.glooshy.ships.ship;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates transitions between {@link LifecyclePhase}s.
 *
 * <p>Phase transitions are strict — only the explicitly-allowed paths are valid.
 * This is the defense for DEFECT-05 (ILLEGAL_TRANSITION): a transition that
 * would silently corrupt the lifecycle (e.g., FINALIZED → UNFINISHED to
 * "un-finalize" a ship) must throw, not silently succeed.
 *
 * <p>Allowed transitions:
 * <ul>
 *   <li>UNFINISHED → HULL_APPLIED (apply hull, future slice)</li>
 *   <li>UNFINISHED → REMOVED (pre-finalization teardown, BUILD-03b)</li>
 *   <li>HULL_APPLIED → FINALIZED (finalize, future slice)</li>
 *   <li>HULL_APPLIED → REMOVED (pre-finalization teardown, BUILD-03b)</li>
 *   <li>FINALIZED → DESTROYED (HP reaches zero, future slice)</li>
 * </ul>
 *
 * <p>DESTROYED and REMOVED are terminal — no transitions out.
 */
public final class LifecycleTransition {

    private static final Map<LifecyclePhase, Set<LifecyclePhase>> ALLOWED = Map.of(
            LifecyclePhase.UNFINISHED, EnumSet.of(LifecyclePhase.HULL_APPLIED, LifecyclePhase.REMOVED),
            LifecyclePhase.HULL_APPLIED, EnumSet.of(LifecyclePhase.FINALIZED, LifecyclePhase.REMOVED),
            LifecyclePhase.FINALIZED, EnumSet.of(LifecyclePhase.DESTROYED),
            LifecyclePhase.DESTROYED, EnumSet.noneOf(LifecyclePhase.class),
            LifecyclePhase.REMOVED, EnumSet.noneOf(LifecyclePhase.class));

    private LifecycleTransition() {}

    public static boolean isValid(LifecyclePhase from, LifecyclePhase to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(LifecyclePhase.class)).contains(to);
    }

    public static Set<LifecyclePhase> validTargets(LifecyclePhase from) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(LifecyclePhase.class));
    }
}
