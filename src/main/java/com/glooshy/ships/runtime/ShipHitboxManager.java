package com.glooshy.ships.runtime;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Owns the physical hitbox entity of every ship.
 *
 * <p>The ship's controller is an ArmorStand with a tiny hitbox; this manager
 * attaches a vanilla {@link Interaction} entity with a configurable
 * width/height on top of it, so punching, right-clicking and aiming at the
 * ship feel like interacting with a real 3×~2 block vessel. The hitbox
 * carries the ship-id PDC marker, follows the ship every tick, and
 * self-heals like module entities.
 */
public final class ShipHitboxManager {

    public record HitboxBinding(ShipIdentity shipId, UUID entityUuid) {
    }

    private final NamespacedKey shipIdKey;
    private final RuntimeBindingRegistry bindingRegistry;
    private final double width;
    private final double height;

    private final Map<ShipIdentity, UUID> byShip = new ConcurrentHashMap<>();
    private final Map<UUID, ShipIdentity> byEntity = new ConcurrentHashMap<>();

    public ShipHitboxManager(NamespacedKey shipIdKey,
                             RuntimeBindingRegistry bindingRegistry,
                             double width,
                             double height) {
        this.shipIdKey = shipIdKey;
        this.bindingRegistry = bindingRegistry;
        this.width = width;
        this.height = height;
    }

    /** Resolve a hitbox entity to its ship. */
    public Optional<ShipIdentity> resolve(UUID entityUuid) {
        return Optional.ofNullable(byEntity.get(entityUuid));
    }

    /** The hitbox entity UUID of a ship, if one is tracked. */
    public Optional<UUID> entityUuidOf(ShipIdentity shipId) {
        return Optional.ofNullable(byShip.get(shipId));
    }

    /** Bring the hitbox to the ship's position; (re)spawn if missing. */
    public void follow(ShipIdentity shipId) {
        RuntimeBinding binding = bindingRegistry.findByShip(shipId).orElse(null);
        if (binding == null) {
            return;
        }
        var shipEntity = Bukkit.getEntity(binding.entityUuid());
        if (shipEntity == null || shipEntity.isDead()) {
            return;
        }
        Location target = shipEntity.getLocation();

        UUID uuid = byShip.get(shipId);
        var existing = uuid == null ? null : Bukkit.getEntity(uuid);
        if (existing == null || existing.isDead()) {
            if (uuid != null) {
                byEntity.remove(uuid);
            }
            Interaction hitbox = target.getWorld().spawn(target, Interaction.class, ie -> {
                ie.setInteractionWidth((float) width);
                ie.setInteractionHeight((float) height);
                ie.setPersistent(true);
                ie.getPersistentDataContainer().set(
                        shipIdKey, PersistentDataType.STRING, shipId.encoded());
            });
            byShip.put(shipId, hitbox.getUniqueId());
            byEntity.put(hitbox.getUniqueId(), shipId);
        } else {
            Location current = existing.getLocation();
            if (current.distanceSquared(target) > 0.0001) {
                existing.teleport(target);
            }
        }
    }

    /** Remove a ship's hitbox (teardown / destruction). */
    public void despawn(ShipIdentity shipId) {
        UUID uuid = byShip.remove(shipId);
        if (uuid == null) {
            return;
        }
        byEntity.remove(uuid);
        var entity = Bukkit.getEntity(uuid);
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    /** Restore persisted bindings on enable. */
    public void load(@NotNull List<HitboxBinding> bindings) {
        for (HitboxBinding binding : bindings) {
            byShip.put(binding.shipId(), binding.entityUuid());
            byEntity.put(binding.entityUuid(), binding.shipId());
        }
    }

    public @NotNull List<HitboxBinding> snapshot() {
        return byShip.entrySet().stream()
                .map(e -> new HitboxBinding(e.getKey(), e.getValue()))
                .toList();
    }
}
