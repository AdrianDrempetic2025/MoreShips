package com.glooshy.ships.hull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of validating a candidate hull material.
 *
 * <p>On {@link #isValid()} == false, {@link #errorMessage()} carries the
 * player-facing reason. On valid, errorMessage is null.
 */
public record HullValidationResult(boolean isValid, @Nullable String errorMessage) {

    public static @NotNull HullValidationResult valid() {
        return new HullValidationResult(true, null);
    }

    public static @NotNull HullValidationResult invalid(@NotNull String reason) {
        return new HullValidationResult(false, reason);
    }
}
