package com.glooshy.ships.runtime;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Authoritative owner of the live ship↔entity binding.
 *
 * <p>Bidirectional index: every active binding appears once by ship and once
 * by entity. This is the defense for DEFECT-02 (same entity bound to two ships
 * or vice versa) — the registry rejects collisions on both sides.
 *
 * <p>It is also the defense for DEFECT-03 (vanilla entity misclassified as
 * custom ship): {@link #findByEntity(UUID)} returns empty for any UUID that
 * has never been explicitly bound. A vanilla armor stand, even one placed by
 * another plugin, never resolves to a binding.
 *
 * <p>Thread-safe: bind/release may be called from any thread.
 */
public final class RuntimeBindingRegistry {

    private final ConcurrentMap<ShipIdentity, RuntimeBinding> byShip = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, RuntimeBinding> byEntity = new ConcurrentHashMap<>();

    public void bind(RuntimeBinding binding) {
        Objects.requireNonNull(binding, "binding");
        if (binding.state() != BindingState.ACTIVE) {
            throw new IllegalArgumentException("Cannot bind a non-ACTIVE binding");
        }

        RuntimeBinding existingByShip = byShip.putIfAbsent(binding.shipId(), binding);
        if (existingByShip != null) {
            throw new IllegalStateException(
                    "Ship " + binding.shipId() + " already has an active binding");
        }

        RuntimeBinding existingByEntity = byEntity.putIfAbsent(binding.entityUuid(), binding);
        if (existingByEntity != null) {
            // Roll back the ship-side put to keep indices consistent.
            byShip.remove(binding.shipId());
            throw new IllegalStateException(
                    "Entity " + binding.entityUuid() + " already bound to another ship");
        }
    }

    public Optional<RuntimeBinding> findByShip(ShipIdentity shipId) {
        return Optional.ofNullable(byShip.get(shipId));
    }

    public Optional<RuntimeBinding> findByEntity(UUID entityUuid) {
        return Optional.ofNullable(byEntity.get(entityUuid));
    }

    public void release(ShipIdentity shipId) {
        RuntimeBinding existing = byShip.remove(shipId);
        if (existing != null) {
            byEntity.remove(existing.entityUuid());
        }
    }

    public int activeCount() {
        return byShip.size();
    }
}
