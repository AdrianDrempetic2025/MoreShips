package com.glooshy.ships.movement;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.runtime.ModuleEntityManager;
import com.glooshy.ships.runtime.ShipHitboxManager;
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
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Per-tick ship physics + movement engine.
 *
 * <p>Schedules a single repeating task on the main server thread. For every
 * ship with a live binding, in EVERY lifecycle phase:
 * <ul>
 *   <li><b>Vertical physics</b> ({@link WaterPhysics}): ships are IN the world
 *       now — they rise while fully submerged, hold at the surface, and fall
 *       when out of the water.</li>
 *   <li><b>Hitbox + module entities</b> follow the controller.</li>
 * </ul>
 *
 * <p>For FINALIZED ships additionally: if a {@link Player} rides the ship,
 * mark the {@link ShipMovement} engaged and steer the ship's yaw toward the
 * pilot's yaw (center-turn, RQCA-15); tick the movement and apply the
 * resulting forward velocity.
 *
 * <p>Forward direction is derived from the ship's current yaw:
 * {@code (dx, dz) = (-sin(yaw_rad), cos(yaw_rad)) * currentSpeed}. This is the
 * standard Minecraft convention (yaw=0 → +Z / south).
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>Movement is velocity-based ({@code setVelocity} each tick): the
 *       vertical component from {@link WaterPhysics} fully owns gravity, so
 *       vanilla gravity accumulation is overridden deterministically.</li>
 *   <li>Ships spawned before physics existed (gravity=false NBT) are healed
 *       to gravity=true on first contact.</li>
 * </ul>
 */
public final class ShipMovementService implements Runnable {

    private final JavaPlugin plugin;
    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;
    private final ModuleEntityManager moduleEntities;
    private final ShipHitboxManager hitboxes;
    private final WaterPhysics waterPhysics;
    private final double maxSpeed;
    private final double acceleration;
    private final double friction;

    private final Map<ShipIdentity, ShipMovement> movements = new ConcurrentHashMap<>();
    private volatile BukkitTask task;

    public ShipMovementService(
            @NotNull JavaPlugin plugin,
            @NotNull ShipRegistry shipRegistry,
            @NotNull RuntimeBindingRegistry bindingRegistry,
            @NotNull ModuleEntityManager moduleEntities,
            @NotNull ShipHitboxManager hitboxes,
            double maxSpeed,
            double acceleration,
            double friction,
            double riseVelocity,
            double sinkVelocity) {
        this.plugin = plugin;
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
        this.moduleEntities = moduleEntities;
        this.hitboxes = hitboxes;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.friction = friction;
        this.waterPhysics = new WaterPhysics(riseVelocity, sinkVelocity);
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

            Entity entity = Bukkit.getEntity(entityUuid);
            if (entity == null || entity.isDead()) {
                movements.remove(shipId);
                continue;
            }

            if (!entity.hasGravity()) {
                entity.setGravity(true); // heal pre-physics ships
            }

            if (ship.phase() == LifecyclePhase.FINALIZED) {
                ShipMovement movement = movements.computeIfAbsent(
                        shipId, k -> new ShipMovement(maxSpeed, acceleration, friction));

                Player pilot = findPilot(entity);
                if (pilot != null) {
                    movement.engage();
                    steerTowardPilot(entity, pilot);
                } else {
                    movement.disengage();
                }

                movement.tick();
                applyVelocity(entity, movement.currentSpeed());
            } else {
                // Unfinished / hull-applied ships: no propulsion, but they
                // still sit in the world — vertical physics applies.
                movements.remove(shipId);
                applyVelocity(entity, 0.0);
            }

            // Module entities hold their slot positions; hitbox rides along.
            moduleEntities.follow(shipId);
            hitboxes.follow(shipId);
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
     * Compose horizontal forward speed (along yaw) and vertical water physics
     * into one velocity applied this tick. We own the full velocity vector —
     * vanilla gravity accumulation is overridden every tick.
     */
    private void applyVelocity(@NotNull Entity shipEntity, double forwardSpeed) {
        Location loc = shipEntity.getLocation();
        double yawRad = Math.toRadians(loc.getYaw());
        double dx = -Math.sin(yawRad) * forwardSpeed;
        double dz = Math.cos(yawRad) * forwardSpeed;

        Block feet = loc.getBlock();
        Block above = loc.clone().add(0.0, 1.0, 0.0).getBlock();
        double dy = waterPhysics.verticalVelocity(feet.isLiquid(), above.isLiquid());

        shipEntity.setVelocity(new Vector(dx, dy, dz));
    }
}
