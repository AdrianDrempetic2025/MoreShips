package com.glooshy.ships.ship;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * FALSIFICATION_PROOF for DEFECT-05 (ILLEGAL_TRANSITION).
 *
 * <p>Named defect: a transition that violates the lifecycle state machine
 * (e.g., FINALIZED → UNFINISHED to "un-finalize") succeeds silently instead
 * of throwing.
 *
 * <p>Mutation plan: replace {@link LifecycleTransition#isValid} body with
 * {@code return true;} — every transition would then be accepted. Expected RED
 * across the "rejects" tests below.
 */
class LifecycleTransitionTest {

    @Test
    void unstarted_ship_cannot_reach_finalized_directly() {
        assertFalse(LifecycleTransition.isValid(LifecyclePhase.UNFINISHED, LifecyclePhase.FINALIZED),
                "Cannot skip HULL_APPLIED — UNFINISHED → FINALIZED must be rejected (DEFECT-05)");
    }

    @Test
    void finalized_ship_cannot_return_to_unfinished() {
        assertFalse(LifecycleTransition.isValid(LifecyclePhase.FINALIZED, LifecyclePhase.UNFINISHED),
                "FINALIZED → UNFINISHED must be rejected (finalization is irreversible, CON-07)");
    }

    @Test
    void finalized_ship_cannot_return_to_hull_applied() {
        assertFalse(LifecycleTransition.isValid(LifecyclePhase.FINALIZED, LifecyclePhase.HULL_APPLIED),
                "FINALIZED → HULL_APPLIED must be rejected (CON-07)");
    }

    @Test
    void destroyed_is_terminal() {
        for (LifecyclePhase target : LifecyclePhase.values()) {
            assertFalse(LifecycleTransition.isValid(LifecyclePhase.DESTROYED, target),
                    "DESTROYED is terminal — no transition out, including to " + target);
        }
    }

    @Test
    void removed_is_terminal() {
        for (LifecyclePhase target : LifecyclePhase.values()) {
            assertFalse(LifecycleTransition.isValid(LifecyclePhase.REMOVED, target),
                    "REMOVED is terminal — no transition out, including to " + target);
        }
    }

    @Test
    void valid_transitions_accepted() {
        assertTrue(LifecycleTransition.isValid(LifecyclePhase.UNFINISHED, LifecyclePhase.HULL_APPLIED));
        assertTrue(LifecycleTransition.isValid(LifecyclePhase.UNFINISHED, LifecyclePhase.REMOVED));
        assertTrue(LifecycleTransition.isValid(LifecyclePhase.HULL_APPLIED, LifecyclePhase.FINALIZED));
        assertTrue(LifecycleTransition.isValid(LifecyclePhase.HULL_APPLIED, LifecyclePhase.REMOVED));
        assertTrue(LifecycleTransition.isValid(LifecyclePhase.FINALIZED, LifecyclePhase.DESTROYED));
    }

    @Test
    void self_transition_rejected() {
        for (LifecyclePhase phase : LifecyclePhase.values()) {
            assertFalse(LifecycleTransition.isValid(phase, phase),
                    "Self-transition " + phase + " → " + phase + " is not a real transition");
        }
    }
}
