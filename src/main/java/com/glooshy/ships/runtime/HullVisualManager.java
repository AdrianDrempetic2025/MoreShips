package com.glooshy.ships.runtime;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.HullShape;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.ship.ShipSize;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders the ship hull as real blocks (spec L1: the hull material IS the
 * ship's body): one {@link BlockDisplay} per hull cell, showing the applied
 * hull material as a walkable-looking deck that rotates with the ship.
 *
 * <p>Displays are non-persistent: they vanish on server stop and are rebuilt
 * by {@link #follow} on the next tick — no store file, no orphaned ghosts.
 * Hull visuals exist from HULL_APPLIED on (UNFINISHED ships have no hull yet).
 */
public final class HullVisualManager {

    private final RuntimeBindingRegistry bindingRegistry;
    private final ShipRegistry shipRegistry;
    private final java.util.function.Predicate<ShipSize> customModel;

    private final Map<ShipIdentity, List<UUID>> byShip = new ConcurrentHashMap<>();

    public HullVisualManager(RuntimeBindingRegistry bindingRegistry,
                             ShipRegistry shipRegistry) {
        this(bindingRegistry, shipRegistry, size -> false);
    }

    public HullVisualManager(RuntimeBindingRegistry bindingRegistry,
                             ShipRegistry shipRegistry,
                             java.util.function.Predicate<ShipSize> customModel) {
        this.bindingRegistry = bindingRegistry;
        this.shipRegistry = shipRegistry;
        this.customModel = customModel;
    }

    /** Bring the hull visuals to the ship; rebuild if missing or wrong block. */
    public void follow(ShipIdentity shipId) {
        Ship ship = shipRegistry.find(shipId).orElse(null);
        if (ship == null || ship.hullMaterial() == null) {
            despawn(shipId);
            return;
        }
        if (customModel.test(ship.size())) {
            despawn(shipId); // custom Blockbench model renders this size
            return;
        }
        RuntimeBinding binding = bindingRegistry.findByShip(shipId).orElse(null);
        if (binding == null) {
            return;
        }
        Entity shipEntity = Bukkit.getEntity(binding.entityUuid());
        if (shipEntity == null || shipEntity.isDead()) {
            return;
        }
        Location base = shipEntity.getLocation();

        List<UUID> tracked = new ArrayList<>(byShip.getOrDefault(shipId, List.of()));
        tracked.removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null && !e.isDead()) {
                return false;
            }
            return true;
        });

        int expected = ship.size().width() * ship.size().length();
        boolean blockChanged = !tracked.isEmpty() && tracked.stream()
                .map(Bukkit::getEntity)
                .filter(e -> e instanceof BlockDisplay)
                .map(e -> (BlockDisplay) e)
                .findFirst()
                .map(bd -> bd.getBlock().getMaterial() != ship.hullMaterial())
                .orElse(false);

        if (tracked.size() != expected || blockChanged) {
            for (UUID uuid : tracked) {
                Entity e = Bukkit.getEntity(uuid);
                if (e != null && !e.isDead()) {
                    e.remove();
                }
            }
            tracked = spawnDeck(shipId, ship.size(), ship.hullMaterial(), base);
            byShip.put(shipId, tracked);
        } else {
            positionDeck(ship.size(), base, tracked);
        }
    }

    private List<UUID> spawnDeck(ShipIdentity shipId, ShipSize size,
                                 org.bukkit.Material material, Location base) {
        List<UUID> entities = new ArrayList<>();
        List<HullShape.Cell> cells = allCells(size);
        for (HullShape.Cell cell : cells) {
            Location loc = cellWorldLocation(base, cell);
            BlockDisplay display = base.getWorld().spawn(loc, BlockDisplay.class, bd -> {
                bd.setBlock(material.createBlockData());
                bd.setPersistent(false);
                bd.setTeleportDuration(1); // smooth 1-tick interpolation
            });
            entities.add(display.getUniqueId());
        }
        positionDeck(size, base, entities);
        return entities;
    }

    private void positionDeck(ShipSize size, Location base, List<UUID> tracked) {
        List<HullShape.Cell> cells = allCells(size);
        float yawRad = (float) Math.toRadians(base.getYaw());
        Quaternionf rotation = new Quaternionf().rotationY(-yawRad);
        // Center the 1x1x1 block on the cell (block renders from its origin
        // corner; translation = -R * center), lifted so the deck top sits at
        // hull-deck height.
        Vector3f center = new Vector3f(0.5f, 0.0f, 0.5f);
        Vector3f translation = new Vector3f(center).mul(-1.0f).add(0.0f, 0.0f, 0.0f);
        translation = new Vector3f(0.5f, 0.0f, 0.5f).mul(-1.0f);
        translation.rotate(rotation).add(0.0f, 0.4f, 0.0f);
        Transformation transformation = new Transformation(
                translation, rotation, new Vector3f(1f, 1f, 1f), new Quaternionf());

        for (int i = 0; i < Math.min(cells.size(), tracked.size()); i++) {
            Entity entity = Bukkit.getEntity(tracked.get(i));
            if (!(entity instanceof BlockDisplay display) || display.isDead()) {
                continue;
            }
            Location target = cellWorldLocation(base, cells.get(i));
            if (display.getLocation().distanceSquared(target) > 0.01) {
                display.teleport(target);
            }
            display.setTransformation(transformation);
        }
    }

    /** All hull cells INCLUDING the center clearance — the visual deck is complete. */
    private static List<HullShape.Cell> allCells(ShipSize size) {
        List<HullShape.Cell> cells = new ArrayList<>(size.width() * size.length());
        for (int col = 0; col < size.width(); col++) {
            for (int row = 0; row < size.length(); row++) {
                cells.add(new HullShape.Cell(
                        col - (size.width() - 1) / 2.0,
                        (size.length() - 1) / 2.0 - row));
            }
        }
        return cells;
    }

    private static Location cellWorldLocation(Location base, HullShape.Cell cell) {
        double yawRad = Math.toRadians(base.getYaw());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double dx = -cos * cell.localX() - sin * cell.localZ();
        double dz = -sin * cell.localX() + cos * cell.localZ();
        return base.clone().add(new Vector(dx, 0.0, dz));
    }

    /** Remove the hull visuals (teardown / destruction / no hull). */
    public void despawn(ShipIdentity shipId) {
        List<UUID> tracked = byShip.remove(shipId);
        if (tracked == null) {
            return;
        }
        for (UUID uuid : tracked) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null && !e.isDead()) {
                e.remove();
            }
        }
    }

    /** Visuals exist only with a live binding — no persistence needed. */
    public void clear() {
        byShip.keySet().forEach(this::despawn);
    }

    public Optional<Integer> visualCount(ShipIdentity shipId) {
        List<UUID> tracked = byShip.get(shipId);
        return tracked == null ? Optional.empty() : Optional.of(tracked.size());
    }
}
