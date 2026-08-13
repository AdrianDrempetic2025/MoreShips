package com.glooshy.ships.movement;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * Per-tick ship movement engine.
 *
 * <p>Schedules a single repeating task on the main server thread. For every
 * FINALIZED ship with a live binding:
 * <ul>
 *   <li>If a {@link Player} is a passenger, mark the {@link ShipMovement}
 *       engaged and steer the ship's yaw toward the pilot's yaw (center-turn,
 *       RQCA-15).</li>
 *   <li>Otherwise mark it disengaged (drift via friction).</li>
 *   <li>Tick the movement and apply the resulting forward velocity to the
 *       ship entity.</li>
 * </ul>
 *
 * <p>Forward direction is derived from the ship's current yaw:
 * {@code (dx, dz) = (-sin(yaw_rad), cos(yaw_rad)) * currentSpeed}. This is
 * the standard Minecraft convention (yaw=0 → +Z / south).
 *
 * <p>Movement entries are cached per ship and cleared when the ship leaves the
 * FINALIZED lifecycle phase (DESTROYED / REMOVED). The cache grows with active
 * ships; it does not grow unboundedly.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>V1 uses teleport() rather than setVelocity() because the ArmorStand
 *       has gravity disabled — setVelocity may be ignored by the physics
 *       engine. Teleport is deterministic.</li>
 *   <li>Vertical motion is left untouched — the ship stays at its spawn Y.
 *       Water-surface physics (floating, sinking on land) is a future slice.</li>
 *   <li>Collisions are bypassed. A future slice can re-enable via setVelocity
 *       once gravity/water physics are in place.</li>
 * </ul>
 */
public final class ShipMovementService implements Runnable {

    private final JavaPlugin plugin;
    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;
    private final double maxSpeed;
    private final double acceleration;
    private final double friction;

    private final Map<ShipIdentity, ShipMovement> movements = new ConcurrentHashMap<>();
    private volatile BukkitTask task;

    public ShipMovementService(
            @NotNull JavaPlugin plugin,
            @NotNull ShipRegistry shipRegistry,
            @NotNull RuntimeBindingRegistry bindingRegistry,
            double maxSpeed,
            double acceleration,
            double friction) {
        this.plugin = plugin;
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.friction = friction;
    }

    public synchronized void start() {
        if (task != null) {
            throw new IllegalStateException("Movement service already started");
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 1L);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        movements.clear();
    }

    @Override
    public void run() {
        List<RuntimeBinding> snapshot;
        try {
            snapshot = bindingRegistry.snapshot();
        } catch (ConcurrentModificationException e) {
            return;
        }

        for (RuntimeBinding binding : snapshot) {
            ShipIdentity shipId = binding.shipId();
            UUID entityUuid = binding.entityUuid();

            Optional<Ship> shipOpt = shipRegistry.find(shipId);
            if (shipOpt.isEmpty()) {
                movements.remove(shipId);
                continue;
            }
            Ship ship = shipOpt.get();

            if (ship.phase() != LifecyclePhase.FINALIZED) {
                // Non-finalized ships don't move. Drop cache entry to bound memory.
                movements.remove(shipId);
                continue;
            }

            ShipMovement movement = movements.computeIfAbsent(
                    shipId, k -> new ShipMovement(maxSpeed, acceleration, friction));

            Entity entity = Bukkit.getEntity(entityUuid);
            if (entity == null || entity.isDead()) {
                movement.disengage();
                continue;
            }

            Player pilot = findPilot(entity);
            if (pilot != null) {
                movement.engage();
                steerTowardPilot(entity, pilot);
            } else {
                movement.disengage();
            }

            movement.tick();
            applyMovement(entity, movement);
        }
    }

    private static @org.jetbrains.annotations.Nullable Player findPilot(@NotNull Entity shipEntity) {
        for (Entity passenger : shipEntity.getPassengers()) {
            if (passenger instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    /**
     * Center-turn (RQCA-15): the ship's yaw follows the pilot's yaw. We set
     * the ship's rotation directly. Passengers ride along automatically.
     */
    private static void steerTowardPilot(@NotNull Entity shipEntity, @NotNull Player pilot) {
        Location pilotLoc = pilot.getLocation();
        shipEntity.setRotation(pilotLoc.getYaw(), pilotLoc.getPitch());
    }

    /**
     * Translate currentSpeed into a horizontal teleport offset along the
     * ship's current yaw. Vertical motion is left to gravity (which is
     * currently disabled on the spawner — ships hold their spawn Y).
     */
    private static void applyMovement(@NotNull Entity shipEntity, @NotNull ShipMovement movement) {
        if (!movement.isMoving()) {
            return;
        }
        Location loc = shipEntity.getLocation();
        double yawRad = Math.toRadians(loc.getYaw());
        double dx = -Math.sin(yawRad) * movement.currentSpeed();
        double dz = Math.cos(yawRad) * movement.currentSpeed();
        loc.add(dx, 0.0, dz);
        shipEntity.teleport(loc);
    }
}
