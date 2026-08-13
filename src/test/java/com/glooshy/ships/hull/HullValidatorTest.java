package com.glooshy.ships.hull;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * FALSIFICATION_PROOF + REGRESSION_GUARDs for {@link HullValidator}.
 *
 * <p>Tests use {@link HullValidator#validateRules} (pure primitives) instead of
 * {@link HullValidator#validate(Material)} because org.bukkit.Material has a
 * static initializer that requires server context. The Material adapter is
 * verified in BUILD-SMOKE on a real server.
 *
 * <p>Named defect: DEFECT-07 (INVALID_MATERIAL_CONSUMED) — an invalid block
 * passes validation and is consumed as hull.
 */
class HullValidatorTest {

    private final HullValidator validator = new HullValidator(1.0);

    /**
     * FALSIFICATION_PROOF — DEFECT-07 (INVALID_MATERIAL_CONSUMED).
     *
     * <p>Mutation plan: replace validateRules body with {@code return HullValidationResult.valid();}
     * Expected RED: every "rejects" test below fails because the validator accepts
     * the invalid material.
     */
    @Test
    void rejects_air() {
        HullValidationResult result = validator.validateRules(true, true, true, 100.0);
        assertFalse(result.isValid(), "Air must be rejected even if other fields look valid (DEFECT-07)");
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("air"));
    }

    @Test
    void rejects_non_block_item() {
        HullValidationResult result = validator.validateRules(false, false, true, 5.0);
        assertFalse(result.isValid(), "Non-block items must be rejected");
        assertTrue(result.errorMessage().contains("block"));
    }

    @Test
    void rejects_non_solid() {
        HullValidationResult result = validator.validateRules(false, true, false, 5.0);
        assertFalse(result.isValid(), "Non-solid blocks must be rejected");
        assertTrue(result.errorMessage().contains("solid"));
    }

    @Test
    void rejects_too_soft() {
        HullValidationResult result = validator.validateRules(false, true, true, 0.5);
        assertFalse(result.isValid(), "Soft blocks below threshold must be rejected");
        assertTrue(result.errorMessage().contains("too soft"));
        assertTrue(result.errorMessage().contains("0.5"));
    }

    @Test
    void accepts_at_threshold() {
        HullValidationResult result = validator.validateRules(false, true, true, 1.0);
        assertTrue(result.isValid(), "Hardness equal to threshold must pass");
    }

    @Test
    void accepts_above_threshold() {
        HullValidationResult result = validator.validateRules(false, true, true, 50.0);
        assertTrue(result.isValid());
        assertNull(result.errorMessage());
    }

    @Test
    void threshold_lowered_accepts_softer_blocks() {
        HullValidator lenient = new HullValidator(0.0);
        HullValidationResult result = lenient.validateRules(false, true, true, 0.3);
        assertTrue(result.isValid(), "Soft block must pass when threshold is 0");
    }

    @Test
    void threshold_raised_rejects_harder_blocks() {
        HullValidator strict = new HullValidator(10.0);
        HullValidationResult result = strict.validateRules(false, true, true, 5.0);
        assertFalse(result.isValid(), "Hardness 5 must fail when threshold is 10");
    }

    @Test
    void errorMessage_includes_actual_hardness() {
        HullValidationResult result = validator.validateRules(false, true, true, 0.3);
        assertTrue(result.errorMessage().contains("0.3"),
                "Error message must include actual hardness for debugging");
        assertTrue(result.errorMessage().contains("1.0"),
                "Error message must include threshold for context");
    }

    /**
     * Regression — record accessor renamed from {@code valid()} to {@code isValid()}
     * to avoid name collision with the static factory.
     */
    @Test
    void result_record_accessors_work() {
        HullValidationResult ok = HullValidationResult.valid();
        assertTrue(ok.isValid());
        assertNull(ok.errorMessage());

        HullValidationResult bad = HullValidationResult.invalid("test");
        assertFalse(bad.isValid());
        assertEquals("test", bad.errorMessage());
    }
}
