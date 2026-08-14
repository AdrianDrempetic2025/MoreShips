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
    private final org.bukkit.NamespacedKey shipIdKey;
    private final WaterPhysics waterPhysics;
    private final CollisionBox collision;
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
            @NotNull org.bukkit.NamespacedKey shipIdKey,
            double maxSpeed,
            boolean collisionEnabled,
            double collisionMargin,
            double hitboxWidth,
            double hitboxHeight,
            double acceleration,
            double friction,
            double riseVelocity,
            double sinkVelocity) {
        this.plugin = plugin;
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
        this.moduleEntities = moduleEntities;
        this.hitboxes = hitboxes;
        this.shipIdKey = shipIdKey;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.friction = friction;
        this.waterPhysics = new WaterPhysics(riseVelocity, sinkVelocity);
        this.collision = collisionEnabled
                ? new CollisionBox(hitboxWidth, hitboxHeight, collisionMargin)
                : null;
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
                respawnControllerAtHitbox(ship, shipId);
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
     * Self-heal: if the controller ArmorStand died (killed, void, world edit)
     * but the ship still exists in the registry, respawn the controller at the
     * last known position — the surviving hitbox entity — and rebind. Without
     * this, a killed stand left an orphaned hitbox and a ghost ship (bug:
     * "on armor stand death, the ship hitbox remains").
     */
    private void respawnControllerAtHitbox(Ship ship, ShipIdentity shipId) {
        org.bukkit.entity.Interaction anchor = hitboxes.entityUuidOf(shipId)
                .map(Bukkit::getEntity)
                .filter(e -> e != null && !e.isDead())
                .filter(org.bukkit.entity.Interaction.class::isInstance)
                .map(org.bukkit.entity.Interaction.class::cast)
                .orElse(null);
        if (anchor == null) {
            return; // no position anchor — nothing we can heal from
        }
        org.bukkit.Location loc = anchor.getLocation();
        String shortId = shipId.encoded();
        int dash = shortId.indexOf('-');
        if (dash > 0) {
            shortId = shortId.substring(0, dash);
        }
        final String label = ship.phase() == LifecyclePhase.FINALIZED
                ? "Ship " + shortId + " [" + ship.currentHp() + "/" + ship.maxHp() + " HP]"
                : "Unfinished Ship " + shortId;
        org.bukkit.entity.ArmorStand stand = loc.getWorld().spawn(loc, org.bukkit.entity.ArmorStand.class, as -> {
            as.setVisible(true);
            as.setGravity(true);
            as.setGlowing(true);
            as.setCustomNameVisible(true);
            as.customName(net.kyori.adventure.text.Component.text(
                    label, net.kyori.adventure.text.format.NamedTextColor.AQUA));
            as.getPersistentDataContainer().set(
                    shipIdKey, org.bukkit.persistence.PersistentDataType.STRING, shipId.encoded());
            if (ship.hullMaterial() != null) {
                as.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(ship.hullMaterial()));
            }
        });
        bindingRegistry.release(shipId);
        bindingRegistry.bind(RuntimeBinding.active(shipId, stand.getUniqueId()));
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

        if (collision != null && (dx != 0.0 || dz != 0.0)) {
            double[] clamped = collision.clampMovement(
                    loc.getX(), loc.getZ(), dx, dz,
                    (x, z) -> collidesAt(shipEntity, loc.getY(), x, z));
            dx = clamped[0];
            dz = clamped[1];
        }

        Block feet = loc.getBlock();
        Block above = loc.clone().add(0.0, 1.0, 0.0).getBlock();
        double dy = waterPhysics.verticalVelocity(feet.isLiquid(), above.isLiquid());

        shipEntity.setVelocity(new Vector(dx, dy, dz));
    }

    /**
     * Terrain collision test for the ship's hull-sized AABB centered at
     * (x, z), base at y. Water and passable blocks (grass, flowers...) do
     * not collide.
     */
    private boolean collidesAt(@NotNull Entity shipEntity, double y, double x, double z) {
        var world = shipEntity.getWorld();
        int[] xs = collision.blockRangeX(x, 0.0);
        int[] ys = collision.blockRangeY(y);
        int[] zs = collision.blockRangeZ(z, 0.0);
        for (int bx = xs[0]; bx <= xs[1]; bx++) {
            for (int by = ys[0]; by <= ys[1]; by++) {
                for (int bz = zs[0]; bz <= zs[1]; bz++) {
                    org.bukkit.block.Block block = world.getBlockAt(bx, by, bz);
                    if (!block.isLiquid() && !block.isPassable()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
