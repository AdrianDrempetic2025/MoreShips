package com.glooshy.ships.runtime;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves "the player clicked/punched/damaged this entity" to the ship it
 * belongs to, accepting EITHER representation:
 *
 * <ul>
 *   <li>the ship's controller ArmorStand (runtime binding)</li>
 *   <li>the ship's Interaction hitbox (follows the controller)</li>
 * </ul>
 */
public final class ShipEntityResolver {

    private final RuntimeBindingRegistry bindingRegistry;
    private final ShipHitboxManager hitboxes;

    public ShipEntityResolver(RuntimeBindingRegistry bindingRegistry, ShipHitboxManager hitboxes) {
        this.bindingRegistry = bindingRegistry;
        this.hitboxes = hitboxes;
    }

    /** Which ship does this entity represent, if any? */
    public Optional<ShipIdentity> shipIdOf(@NotNull Entity entity) {
        Optional<ShipIdentity> viaBinding = bindingRegistry
                .findByEntity(entity.getUniqueId())
                .map(RuntimeBinding::shipId);
        if (viaBinding.isPresent()) {
            return viaBinding;
        }
        return hitboxes.resolve(entity.getUniqueId());
    }

    /** The controller ArmorStand behind this entity (input may be either). */
    public Optional<ArmorStand> shipStandOf(@NotNull Entity entity) {
        Optional<ShipIdentity> shipId = shipIdOf(entity);
        if (shipId.isEmpty()) {
            return Optional.empty();
        }
        if (entity instanceof ArmorStand stand && !stand.isMarker()) {
            return Optional.of(stand);
        }
        return bindingRegistry.findByShip(shipId.get())
                .flatMap(binding -> {
                    var resolved = Bukkit.getEntity(binding.entityUuid());
                    return resolved instanceof ArmorStand stand
                            ? Optional.of(stand)
                            : Optional.<ArmorStand>empty();
                });
    }

    /** Is this entity the controller stand for the given ship? */
    public boolean isControllerStand(@NotNull Entity entity, @NotNull ShipIdentity shipId) {
        return bindingRegistry.findByShip(shipId)
                .map(b -> b.entityUuid().equals(entity.getUniqueId()))
                .orElse(false);
    }

    public Optional<UUID> controllerEntityUuid(@NotNull ShipIdentity shipId) {
        return bindingRegistry.findByShip(shipId).map(RuntimeBinding::entityUuid);
    }
}
