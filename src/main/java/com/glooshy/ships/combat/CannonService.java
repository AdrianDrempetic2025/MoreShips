package com.glooshy.ships.combat;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Cannon firing (RQCA-18/19 pragmatic slice): right-click a fitted CANNON
 * module on a FINALIZED ship to fire. The shot flies outward from the
 * module's hull position (a bow cannon fires forward, a port cannon fires to
 * port, ...), deals fixed damage to ships it hits, and has a per-cannon
 * cooldown.
 *
 * <p>The projectile is a marker-tagged snowball — no gravity arc tuning yet
 * and no ammo economy (future slices per RQCA-19/20).
 */
public final class CannonService {

    private final ShipRegistry shipRegistry;
    private final NamespacedKey cannonMarker;
    private final double damage;
    private final long cooldownMillis;
    private final double speed;

    private final Map<String, Long> lastFired = new ConcurrentHashMap<>();

    public CannonService(ShipRegistry shipRegistry, NamespacedKey cannonMarker,
                         double damage, long cooldownMillis, double speed) {
        this.shipRegistry = shipRegistry;
        this.cannonMarker = cannonMarker;
        this.damage = damage;
        this.cooldownMillis = cooldownMillis;
        this.speed = speed;
    }

    public void fire(@NotNull Player shooter, @NotNull Ship ship,
                     @NotNull Location cannonLocation, double localX, double localZ) {
        String key = ship.identity().encoded() + "|" + localX + "," + localZ;
        long now = System.currentTimeMillis();
        Long last = lastFired.get(key);
        if (last != null && now - last < cooldownMillis) {
            long remaining = (cooldownMillis - (now - last) + 999) / 1000;
            shooter.sendMessage(Component.text(
                    "Cannon reloading — " + remaining + "s.", NamedTextColor.RED));
            return;
        }
        lastFired.put(key, now);

        // Shot direction: from the ship's center THROUGH the module's hull
        // position, rotated by the ship's yaw — each cannon covers its own side
        float yawRad = (float) Math.toRadians(cannonLocation.getYaw());
        double dirX = -Math.cos(yawRad) * localX - Math.sin(yawRad) * localZ;
        double dirZ = -Math.sin(yawRad) * localX + Math.cos(yawRad) * localZ;
        double len = Math.max(1e-6, Math.sqrt(dirX * dirX + dirZ * dirZ));
        // Gravity ON + slight downward aim: the shot arcs into the target's
        // water-level hull instead of flying flat over the half-block hitbox
        Vector velocity = new Vector(dirX / len * speed, -0.02, dirZ / len * speed);

        Location muzzle = cannonLocation.clone().add(
                velocity.clone().normalize().multiply(0.6)).add(0, 0.4, 0);
        Snowball shot = muzzle.getWorld().spawn(muzzle, Snowball.class, sb -> {
            sb.setVelocity(velocity);
            sb.setShooter(shooter);
            sb.setGravity(true);
            sb.setPersistent(false);
            sb.getPersistentDataContainer().set(
                    cannonMarker, PersistentDataType.STRING, ship.identity().encoded());
        });

        shooter.sendMessage(Component.text(
                "Fired cannon! (" + (int) damage + " dmg on hit)", NamedTextColor.GOLD));
    }

    /** The ship this cannon shot belongs to, or null. */
    public ShipIdentity sourceShip(@NotNull Snowball shot) {
        String id = shot.getPersistentDataContainer().get(cannonMarker, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return com.glooshy.ships.identity.ShipIdentity.decode(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public double damage() {
        return damage;
    }

    public ShipRegistry registry() {
        return shipRegistry;
    }
}
