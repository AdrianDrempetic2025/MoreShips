package com.glooshy.ships.runtime;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.HullShape;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.ship.ShipSize;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Shulker;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Owns the physical presence of every ship:
 *
 * <ul>
 *   <li><b>Interaction segments</b> — a line of squares along the hull axis
 *       whose union approximates the hull rectangle (2×3, 3×4, 3×8). These
 *       are what players click, punch and aim at.</li>
 *   <li><b>Solidity cells</b> — an invisible, no-AI Shulker grid (1×1 solid
 *       collision each) covering the hull, so the ship is solid like a boat:
 *       players and mobs cannot walk through it, and can stand on the deck.</li>
 * </ul>
 *
 * <p>Every entity carries the ship-id PDC marker, follows the controller each
 * tick (position + rotation), and the whole set self-heals: if the tracked
 * count doesn't match the ship's hull shape, the set is rebuilt from the
 * registry.
 */
public final class ShipHitboxManager {

    public record HitboxBinding(ShipIdentity shipId, UUID entityUuid) {
    }

    private final NamespacedKey shipIdKey;
    private final RuntimeBindingRegistry bindingRegistry;
    private final ShipRegistry shipRegistry;
    private final double height;

    private final Map<ShipIdentity, List<UUID>> byShip = new ConcurrentHashMap<>();
    private final Map<UUID, ShipIdentity> byEntity = new ConcurrentHashMap<>();

    public ShipHitboxManager(NamespacedKey shipIdKey,
                             RuntimeBindingRegistry bindingRegistry,
                             ShipRegistry shipRegistry,
                             double defaultWidth,
                             double height) {
        this.shipIdKey = shipIdKey;
        this.bindingRegistry = bindingRegistry;
        this.shipRegistry = shipRegistry;
        this.height = height;
    }

    /** Resolve any hull entity (segment or solidity cell) to its ship. */
    public Optional<ShipIdentity> resolve(UUID entityUuid) {
        return Optional.ofNullable(byEntity.get(entityUuid));
    }

    /** Any tracked hull entity of the ship (anchor for controller respawn). */
    public Optional<UUID> entityUuidOf(ShipIdentity shipId) {
        List<UUID> list = byShip.get(shipId);
        return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** Bring all hull entities to the ship's position; rebuild if incomplete. */
    public void follow(ShipIdentity shipId) {
        RuntimeBinding binding = bindingRegistry.findByShip(shipId).orElse(null);
        if (binding == null) {
            return;
        }
        Entity shipEntity = Bukkit.getEntity(binding.entityUuid());
        if (shipEntity == null || shipEntity.isDead()) {
            return;
        }
        Ship ship = shipRegistry.find(shipId).orElse(null);
        if (ship == null) {
            return;
        }
        ShipSize size = ship.size();
        // Deck reference: the controller stand rides 0.5 below the deck
        Location base = shipEntity.getLocation().add(0.0, 0.5, 0.0);

        List<UUID> tracked = new ArrayList<>(byShip.getOrDefault(shipId, List.of()));
        tracked.removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null && !e.isDead()) {
                return false;
            }
            byEntity.remove(uuid);
            return true;
        });

        int expected = expectedCount(size);
        if (tracked.size() != expected) {
            for (UUID uuid : tracked) {
                Entity e = Bukkit.getEntity(uuid);
                if (e != null && !e.isDead()) {
                    e.remove();
                }
                byEntity.remove(uuid);
            }
            tracked = spawnHullEntities(shipId, size, base);
            byShip.put(shipId, tracked);
        } else {
            byShip.put(shipId, tracked);
            positionHullEntities(shipId, size, base, tracked);
        }
    }

    private int expectedCount(ShipSize size) {
        return HullShape.segmentCentersZ(size).size() + HullShape.solidCells(size).size() + 1;
    }

    private List<UUID> spawnHullEntities(ShipIdentity shipId, ShipSize size, Location base) {
        List<UUID> entities = new ArrayList<>();
        double segSize = HullShape.segmentSize(size);

        for (double centerZ : HullShape.segmentCentersZ(size)) {
            Location loc = localToWorld(base, 0.0, centerZ);
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue;
            }
            Interaction hitbox = base.getWorld().spawn(loc, Interaction.class, ie -> {
                ie.setInteractionWidth((float) segSize);
                ie.setInteractionHeight((float) height);
                ie.setPersistent(true);
                ie.getPersistentDataContainer().set(
                        shipIdKey, PersistentDataType.STRING, shipId.encoded());
            });
            entities.add(hitbox.getUniqueId());
            byEntity.put(hitbox.getUniqueId(), shipId);
        }

        java.util.List<HullShape.Cell> cells =
                new java.util.ArrayList<>(HullShape.solidCells(size));
        cells.add(HullShape.centerCell()); // deck cell under the controller stand
        for (HullShape.Cell cell : cells) {
            Location loc = localToWorld(base, cell.localX(), cell.localZ())
                    .add(0.0, height / 2.0 - 1.0, 0.0); // Session 2: shulker floor half a block lower
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue;
            }
            Shulker solid = base.getWorld().spawn(loc, Shulker.class, sh -> {
                sh.setAI(false);
                sh.setGravity(false);
                sh.setInvisible(true);
                sh.setInvulnerable(false); // damage handled + cancelled by the ship listener
                sh.setSilent(true);
                sh.setRemoveWhenFarAway(false);
                sh.setPeek(0.0f);
                sh.getPersistentDataContainer().set(
                        shipIdKey, PersistentDataType.STRING, shipId.encoded());
            });
            entities.add(solid.getUniqueId());
            byEntity.put(solid.getUniqueId(), shipId);
        }
        return entities;
    }

    private void positionHullEntities(ShipIdentity shipId, ShipSize size, Location base,
                                      List<UUID> tracked) {
        int segments = HullShape.segmentCentersZ(size).size();
        List<HullShape.Cell> cells =
                new java.util.ArrayList<>(HullShape.solidCells(size));
        cells.add(HullShape.centerCell());
        int total = segments + cells.size();
        for (int i = 0; i < Math.min(total, tracked.size()); i++) {
            Entity entity = Bukkit.getEntity(tracked.get(i));
            if (entity == null || entity.isDead()) {
                continue;
            }
            Location target;
            if (i < segments) {
                target = localToWorld(base, 0.0, HullShape.segmentCentersZ(size).get(i));
            } else {
                HullShape.Cell cell = cells.get(i - segments);
                target = localToWorld(base, cell.localX(), cell.localZ())
                        .add(0.0, height / 2.0 - 1.0, 0.0); // Session 2: shulker floor half a block lower
            }
            Location current = entity.getLocation();
            if (current.distanceSquared(target) > 0.01) {
                entity.teleport(target);
            }
        }
    }

    private static Location localToWorld(Location base, double localX, double localZ) {
        double yawRad = Math.toRadians(base.getYaw());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        // Forward F = (-sin, cos); starboard R = (-cos, -sin)
        double dx = -cos * localX - sin * localZ;
        double dz = -sin * localX + cos * localZ;
        return base.clone().add(new Vector(dx, 0.0, dz));
    }

    /** Remove all hull entities of a ship (teardown / destruction). */
    public void despawn(ShipIdentity shipId) {
        List<UUID> tracked = byShip.remove(shipId);
        if (tracked == null) {
            return;
        }
        for (UUID uuid : tracked) {
            byEntity.remove(uuid);
            Entity e = Bukkit.getEntity(uuid);
            if (e != null && !e.isDead()) {
                e.remove();
            }
        }
    }

    /** Restore persisted bindings on enable. */
    public void load(@NotNull List<HitboxBinding> bindings) {
        for (HitboxBinding binding : bindings) {
            byShip.computeIfAbsent(binding.shipId(), k -> new ArrayList<>())
                    .add(binding.entityUuid());
            byEntity.put(binding.entityUuid(), binding.shipId());
        }
    }

    public @NotNull List<HitboxBinding> snapshot() {
        List<HitboxBinding> all = new ArrayList<>();
        byShip.forEach((shipId, uuids) -> uuids.forEach(
                uuid -> all.add(new HitboxBinding(shipId, uuid))));
        return all;
    }
}
