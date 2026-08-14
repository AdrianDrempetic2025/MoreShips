package com.glooshy.ships.movement;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.runtime.ModuleEntityManager;
import com.glooshy.ships.visual.CustomModelVisualManager;
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
    private final CustomModelVisualManager modelVisuals;
    private final org.bukkit.NamespacedKey modelItemKey;
    private final org.bukkit.NamespacedKey shipIdKey;
    private final WaterPhysics waterPhysics;
    private final boolean collisionEnabled;
    private final double collisionMargin;
    /** Degrees per tick the ship turns while the pilot steers left/right. */
    private final double turnRateDeg;
    private final double maxSpeed;
    private final double acceleration;
    private final double friction;
    private final double weightPerModule;
    private final double engineBoost;
    private final double hardnessPenalty;

    private final Map<ShipIdentity, ShipMovement> movements = new ConcurrentHashMap<>();
    private volatile BukkitTask task;

    public ShipMovementService(
            @NotNull JavaPlugin plugin,
            @NotNull ShipRegistry shipRegistry,
            @NotNull RuntimeBindingRegistry bindingRegistry,
            @NotNull ModuleEntityManager moduleEntities,
            @NotNull ShipHitboxManager hitboxes,
            @NotNull CustomModelVisualManager modelVisuals,
            @NotNull org.bukkit.NamespacedKey modelItemKey,
            @NotNull org.bukkit.NamespacedKey shipIdKey,
            double maxSpeed,
            boolean collisionEnabled,
            double collisionMargin,
            double acceleration,
            double friction,
            double riseVelocity,
            double sinkVelocity,
            double turnRateDeg,
            double weightPerModule,
            double engineBoost,
            double hardnessPenalty) {
        this.plugin = plugin;
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
        this.moduleEntities = moduleEntities;
        this.hitboxes = hitboxes;
        this.modelVisuals = modelVisuals;
        this.modelItemKey = modelItemKey;
        this.shipIdKey = shipIdKey;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.friction = friction;
        this.waterPhysics = new WaterPhysics(riseVelocity, sinkVelocity);
        this.collisionEnabled = collisionEnabled;
        this.collisionMargin = collisionMargin;
        this.turnRateDeg = turnRateDeg;
        this.weightPerModule = weightPerModule;
        this.engineBoost = engineBoost;
        this.hardnessPenalty = hardnessPenalty;
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
            if (entity instanceof org.bukkit.entity.LivingEntity living && living.isCollidable()) {
                living.setCollidable(false); // the solid deck must not push the controller
            }
            if (entity instanceof org.bukkit.entity.ArmorStand stand) {
                if (stand.isGlowing()) {
                    stand.setGlowing(false); // heal pre-0.14 stands
                }
                if (stand.isVisible()) {
                    stand.setVisible(false);
                }
                if (!stand.isSmall()) {
                    stand.setSmall(true); // lower passenger seat
                }
                if (!com.glooshy.ships.item.ShipModelHelmet.isShipModel(
                        stand.getEquipment().getHelmet(), modelItemKey)) {
                    stand.getEquipment().setHelmet(
                            com.glooshy.ships.item.ShipModelHelmet.create(modelItemKey));
                }
            }

            if (ship.phase() == LifecyclePhase.FINALIZED) {
                // CON-10/11 + RQCA-07: module weight slows, harder hulls are
                // slower. Engines raise the CEILING directly (1 + boost per
                // engine on top of the base), so a 2-engine ship genuinely
                // tops out faster, not just accelerates faster.
                int engineCount = com.glooshy.ships.ship.ShipStats.countType(
                        ship.modules(), com.glooshy.ships.ship.ModuleType.ENGINE);
                double multiplier = com.glooshy.ships.ship.ShipStats.speedMultiplier(
                        ship.modules().size(), 0,
                        ship.hullMaterial() != null ? ship.hullMaterial().getHardness() : 0.0,
                        weightPerModule, 0.0, hardnessPenalty);
                double effectiveMax = maxSpeed * multiplier * (1.0 + engineCount * engineBoost);
                ShipMovement movement = movements.computeIfAbsent(
                        shipId, k -> new ShipMovement(effectiveMax, acceleration, friction));
                movement.setMaxSpeed(effectiveMax);

                Player pilot = findPilot(entity);
                if (pilot != null) {
                    steerByInput(entity, pilot, movement);
                } else {
                    movement.disengage();
                }

                movement.tick();
                applyVelocity(entity, ship, movement.currentSpeed());
            } else {
                // Unfinished / hull-applied ships: no propulsion, but they
                // still sit in the world — vertical physics applies.
                movements.remove(shipId);
                applyVelocity(entity, ship, 0.0);
            }

            // Module entities hold their slot positions; hitbox + hull visuals ride along.
            moduleEntities.follow(shipId);
            hitboxes.follow(shipId);
            modelVisuals.follow(shipId);
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
     * Vanilla-boat-style control (RQCA-14/15): W accelerates, S brakes,
     * A/D turn the hull. The client reports the pilot's key presses every
     * tick via the Paper Input API.
     */
    private void steerByInput(@NotNull Entity shipEntity, @NotNull Player pilot,
                              @NotNull ShipMovement movement) {
        org.bukkit.Input input = pilot.getCurrentInput();
        boolean sprint = input.isSprint();
        double maxSpeed = sprint ? this.maxSpeed * 1.5 : this.maxSpeed;
        movement.setMaxSpeed(maxSpeed);
        if (input.isForward()) {
            movement.engage();
        } else if (input.isBackward()) {
            if (movement.currentSpeed() <= 0.0 && !movement.isMoving()) {
                movement.reverse(); // S from standstill backs up slowly
            } else {
                movement.brake();
            }
        } else {
            movement.disengage();
        }
        float yaw = shipEntity.getLocation().getYaw();
        if (input.isLeft()) {
            yaw = (float) (yaw - turnRateDeg);
        }
        if (input.isRight()) {
            yaw = (float) (yaw + turnRateDeg);
        }
        shipEntity.setRotation(yaw, 0.0f);
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
            as.setVisible(false);
            as.setSmall(true);
            as.setGravity(true);
            as.setCollidable(false);
            as.setGlowing(false);
            as.getEquipment().setHelmet(
                    com.glooshy.ships.item.ShipModelHelmet.create(modelItemKey));
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
    private void applyVelocity(@NotNull Entity shipEntity, Ship ship, double forwardSpeed) {
        Location loc = shipEntity.getLocation();
        double yawRad = Math.toRadians(loc.getYaw());
        double dx = -Math.sin(yawRad) * forwardSpeed;
        double dz = Math.cos(yawRad) * forwardSpeed;

        CollisionBox collision = collisionEnabled
                ? new CollisionBox(ship.size().hitboxWidth(), 1.8, collisionMargin)
                : null;
        if (collision != null && (dx != 0.0 || dz != 0.0)) {
            CollisionBox box = collision;
            List<double[]> otherShips = otherShipColliders(
                    ship.identity(), ship.size(), loc);
            double[] clamped = box.clampMovement(
                    loc.getX(), loc.getZ(), dx, dz,
                    (x, z) -> collidesAt(box, shipEntity, loc.getY(), x, z)
                            || collidesWithOtherShip(otherShips, x, z));
            dx = clamped[0];
            dz = clamped[1];
        }

        Block feet = loc.getBlock();
        Block above = loc.clone().add(0.0, 1.0, 0.0).getBlock();
        double dy = waterPhysics.verticalVelocity(feet.isLiquid(), above.isLiquid());

        // Ground clamp (beaching): the vertical velocity used to teleport the
        // ship STRAIGHT THROUGH solid ground — falling from an elevated pool
        // onto land sank the ship into the floor forever, because nothing ever
        // stopped dy. If the hull footprint at the TARGET depth is solid, the
        // ship rests on the surface instead (dy = 0).
        if (dy < 0.0 && collision != null) {
            double targetY = loc.getY() + dy;
            if (footprintHasSolidAt(shipEntity, collision, targetY)) {
                dy = 0.0;
            }
        }

        // Teleport-based movement: entity collision (players, the ship's own
        // solid deck, module entities) can never block the hull — terrain and
        // other ships are checked explicitly above. Passengers stay mounted
        // (ignorePassengers=true).
        Location target = loc.clone().add(dx, dy, dz);
        shipEntity.teleport(target, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN,
                io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    /**
     * True if any block under the hull footprint (at the ship's X/Z, rows
     * covering the collision box) is solid at the given Y level. Water and
     * passable blocks (grass, snow) do not count — a ship beaches on stone,
     * not on a seagrass meadow.
     */
    private boolean footprintHasSolidAt(@NotNull Entity shipEntity,
                                        @NotNull CollisionBox collision, double y) {
        Location loc = shipEntity.getLocation();
        int[] xs = collision.blockRangeX(loc.getX(), 0.0);
        int[] zs = collision.blockRangeZ(loc.getZ(), 0.0);
        int[] ys = collision.blockRangeY(y);
        for (int bx = xs[0]; bx <= xs[1]; bx++) {
            for (int by = ys[0]; by <= ys[1]; by++) {
                for (int bz = zs[0]; bz <= zs[1]; bz++) {
                    Block block = shipEntity.getWorld().getBlockAt(bx, by, bz);
                    if (!block.isLiquid() && !block.isPassable()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Hull colliders of every OTHER ship near a position: {centerX, centerZ,
     * combinedRadius}. Radius is the other hull's half-span plus ours.
     */
    private List<double[]> otherShipColliders(ShipIdentity self, com.glooshy.ships.ship.ShipSize size,
                                              Location near) {
        List<double[]> colliders = new java.util.ArrayList<>();
        double myHalf = size.hitboxWidth() / 2.0;
        for (RuntimeBinding other : bindingRegistry.snapshot()) {
            if (other.shipId().equals(self)) {
                continue;
            }
            Ship otherShip = shipRegistry.find(other.shipId()).orElse(null);
            if (otherShip == null) {
                continue;
            }
            Entity otherEntity = Bukkit.getEntity(other.entityUuid());
            if (otherEntity == null || otherEntity.isDead()) {
                continue;
            }
            Location otherLoc = otherEntity.getLocation();
            if (otherLoc.getWorld() == null || otherLoc.getWorld() != near.getWorld()) {
                continue;
            }
            if (otherLoc.distanceSquared(near) > 32 * 32) {
                continue;
            }
            colliders.add(new double[] {
                    otherLoc.getX(), otherLoc.getZ(),
                    myHalf + otherShip.size().hitboxWidth() / 2.0});
        }
        return colliders;
    }

    private static boolean collidesWithOtherShip(List<double[]> colliders, double x, double z) {
        for (double[] c : colliders) {
            double ddx = x - c[0];
            double ddz = z - c[1];
            if (ddx * ddx + ddz * ddz < c[2] * c[2]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Terrain collision test for the ship's hull-sized AABB centered at
     * (x, z), base at y. Water and passable blocks (grass, flowers...) do
     * not collide.
     */
    private boolean collidesAt(CollisionBox collision, @NotNull Entity shipEntity,
                               double y, double x, double z) {
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
