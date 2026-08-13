package com.glooshy.ships.runtime;

/**
 * Lifecycle state of a runtime binding between a ship identity and a platform entity.
 *
 * <p>V1 has only {@link #ACTIVE} and {@link #RELEASED}. Future states
 * ({@code SUCCEEDED_BY_WRECK}, etc.) are added as the lifecycle grows.
 */
public enum BindingState {
    ACTIVE,
    RELEASED
}
