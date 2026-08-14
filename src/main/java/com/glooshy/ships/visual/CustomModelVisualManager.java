package com.glooshy.ships.visual;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.ship.ShipSize;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders ships with custom Blockbench models (Jan's assets).
 *
 * <p>Each model splits into two layers, exactly as the artist structured it:
 * <ul>
 *   <li><b>Untextured cubes</b> → one {@link BlockDisplay} each, showing the
 *       ship's <b>live hull material</b> (oak deck, stone hull, ...) — the
 *       vanilla texture is applied by us, in game, per ship.</li>
 *   <li><b>Textured cubes</b> (e.g. the metal coat trim) → one
 *       {@link ItemDisplay} rendering the trim model from the MoreShips
 *       resource pack ({@code moreships:ship_small_trim}).</li>
 * </ul>
 *
 * <p>Model space: Blockbench px, 16px = 1 block, origin [8,8,8]px = ship
 * center. Cube rotations are applied about their own origins; everything
 * rotates with the ship each tick.
 */
public final class CustomModelVisualManager {

    /** One untextured hull cube from the model (rotation axis included). */
    record HullCube(double[] from, double[] to, String axis, double angleDeg, double[] rotOrigin) {
        double[] size() {
            return new double[] {(to[0] - from[0]) / 16.0, (to[1] - from[1]) / 16.0,
                    (to[2] - from[2]) / 16.0};
        }

        double[] center() {
            return new double[] {(from[0] + to[0]) / 2.0, (from[1] + to[1]) / 2.0,
                    (from[2] + to[2]) / 2.0};
        }
    }

    static final class ModelSpec {
        final List<HullCube> hullCubes;
        final NamespacedKey trimItemModel;
        /** True center of the WHOLE model (bbox of all elements, px) — the
         *  artist's pivot-based ship center. Maps onto the controller. */
        final double[] center;
        /** Y (px) of the seat element (SeatMAIN) — the controller stand's head
         *  is aligned to this height, per the artist's layout. */
        final double seatY;

        ModelSpec(List<HullCube> hullCubes, NamespacedKey trimItemModel,
                  double[] center, double seatY) {
            this.hullCubes = List.copyOf(hullCubes);
            this.trimItemModel = trimItemModel;
            this.center = center;
            this.seatY = seatY;
        }
    }

    /** Armor stand passenger head/eye height — the seat aligns here. */
    private static final double PILOT_HEAD_HEIGHT = 1.45;

    /** Ship pose at the last model update (anti-shake gate). */
    private final Map<ShipIdentity, double[]> lastPose = new ConcurrentHashMap<>();

    /** Small floating hull-material cubes orbiting the ship (defense symbol). */
    private static final int DEFENSE_BLOCK_COUNT = 3;
    private static final float DEFENSE_BLOCK_SIZE = 0.3f;
    private static final double DEFENSE_ORBIT_RADIUS = 1.6;
    private static final double DEFENSE_BLOCK_HEIGHT = 0.9;

    private final RuntimeBindingRegistry bindingRegistry;
    private final ShipRegistry shipRegistry;
    private final Logger logger;
    private final Map<ShipSize, ModelSpec> models = new EnumMap<>(ShipSize.class);
    private final Map<ShipIdentity, List<UUID>> byShip = new ConcurrentHashMap<>();

    public CustomModelVisualManager(RuntimeBindingRegistry bindingRegistry,
                                    ShipRegistry shipRegistry,
                                    Logger logger) {
        this.bindingRegistry = bindingRegistry;
        this.shipRegistry = shipRegistry;
        this.logger = logger;
        loadSpec(ShipSize.SMALL, "models/ship_small_hull.json", "ship_small_trim");
    }

    private void loadSpec(ShipSize size, String resource, String trimItemModel) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                logger.info("No custom model for " + size + " (" + resource + " missing)");
                return;
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<HullCube> cubes = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("elements")) {
                JsonObject cube = el.getAsJsonObject();
                // Blockbench omits "angle" when it is 0 — treat missing as 0,
                // a hard .get("angle") NPEd here and silently killed the whole
                // model load (specs stayed empty, nothing ever spawned)
                double angle = 0;
                String axis = "y";
                double[] rotOrigin = {8, 8, 8};
                if (cube.has("rotation")) {
                    JsonObject rot = cube.getAsJsonObject("rotation");
                    if (rot.has("angle")) {
                        angle = rot.get("angle").getAsDouble();
                    }
                    if (rot.has("axis")) {
                        axis = rot.get("axis").getAsString();
                    }
                    if (rot.has("origin")) {
                        rotOrigin = px(rot.getAsJsonArray("origin"));
                    }
                }
                cubes.add(new HullCube(
                        px(cube.getAsJsonArray("from")), px(cube.getAsJsonArray("to")),
                        axis, angle, rotOrigin));
            }
            models.put(size, new ModelSpec(cubes,
                    new NamespacedKey("moreships", trimItemModel), bboxCenter(root),
                    seatY(root)));
            logger.info("Custom model for " + size + ": " + cubes.size() + " hull cubes");
        } catch (Exception e) {
            logger.warning("Failed to load custom model " + resource + ": " + e.getMessage());
        }
    }

    /** Center Y (px) of the element named SeatMAIN; fallback 4 (mid-deck). */
    private static double seatY(JsonObject root) {
        for (JsonElement el : root.getAsJsonArray("elements")) {
            JsonObject cube = el.getAsJsonObject();
            if ("SeatMAIN".equals(cube.get("name") == null ? "" : cube.get("name").getAsString())) {
                double[] from = px(cube.getAsJsonArray("from"));
                double[] to = px(cube.getAsJsonArray("to"));
                return (from[1] + to[1]) / 2.0;
            }
        }
        return 4.0;
    }

    /** Bounding-box center of ALL elements in the model (px). */
    private static double[] bboxCenter(JsonObject root) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (JsonElement el : root.getAsJsonArray("elements")) {
            JsonObject cube = el.getAsJsonObject();
            double[] from = px(cube.getAsJsonArray("from"));
            double[] to = px(cube.getAsJsonArray("to"));
            minX = Math.min(minX, Math.min(from[0], to[0]));
            minY = Math.min(minY, Math.min(from[1], to[1]));
            minZ = Math.min(minZ, Math.min(from[2], to[2]));
            maxX = Math.max(maxX, Math.max(from[0], to[0]));
            maxY = Math.max(maxY, Math.max(from[1], to[1]));
            maxZ = Math.max(maxZ, Math.max(from[2], to[2]));
        }
        return new double[] {(minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0};
    }

    private static double[] px(JsonArray arr) {
        return new double[] {arr.get(0).getAsDouble(), arr.get(1).getAsDouble(),
                arr.get(2).getAsDouble()};
    }

    /** Does this size have a custom Blockbench model? */
    public boolean hasModel(ShipSize size) {
        return models.containsKey(size);
    }

    /** Bring the custom visuals to the ship; rebuild when missing. */
    public void follow(ShipIdentity shipId) {
        Ship ship = shipRegistry.find(shipId).orElse(null);
        if (ship == null || ship.hullMaterial() == null || !hasModel(ship.size())) {
            return;
        }
        ModelSpec spec = models.get(ship.size());
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
            return e == null || e.isDead();
        });

        int expected = 1 + DEFENSE_BLOCK_COUNT; // whole-model display + defense blocks
        if (tracked.size() != expected) {
            for (UUID uuid : tracked) {
                Entity e = Bukkit.getEntity(uuid);
                if (e != null && !e.isDead()) {
                    e.remove();
                }
            }
            tracked = spawnVisuals(shipId, ship, spec, base);
            byShip.put(shipId, tracked);
        } else {
            positionVisuals(ship, spec, base, tracked);
        }
    }

    private List<UUID> spawnVisuals(ShipIdentity shipId, Ship ship, ModelSpec spec, Location base) {
        List<UUID> entities = new ArrayList<>();

        // ONE ItemDisplay renders the whole baked model — the client composes
        // every cube into a single rigid object. Hull material no longer
        // retextures the hull; it is shown by the orbiting defense blocks.
        ItemDisplay model = base.getWorld().spawn(base, ItemDisplay.class, id -> {
            id.setTeleportDuration(1);
            id.setInterpolationDuration(1);
            id.setInterpolationDelay(0);
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(spec.trimItemModel);
            stack.setItemMeta(meta);
            id.setItemStack(stack);
            id.setPersistent(false);
            id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            id.setViewRange(1.0f);
        });
        entities.add(model.getUniqueId());

        // Defense blocks: small floating cubes of the LIVE hull material,
        // orbiting the ship — the material the player applied is always visible
        for (int i = 0; i < DEFENSE_BLOCK_COUNT; i++) {
            BlockDisplay block = base.getWorld().spawn(base, BlockDisplay.class, bd -> {
                bd.setBlock(ship.hullMaterial().createBlockData());
                bd.setPersistent(false);
                bd.setTeleportDuration(1);
                bd.setInterpolationDuration(1);
                bd.setInterpolationDelay(0);
            });
            entities.add(block.getUniqueId());
        }

        positionVisuals(ship, spec, base, entities);
        return entities;
    }

    private void positionVisuals(Ship ship, ModelSpec spec, Location base, List<UUID> tracked) {
        // Anti-shake: teleporting + restarting the interpolation window every
        // tick (even when the ship stands still) reads as constant micro-jitter.
        // Only touch the model display when the ship's pose actually changed.
        double[] pose = {base.getX(), base.getY(), base.getZ(), base.getYaw()};
        double[] last = lastPose.get(ship.identity());
        boolean poseChanged = last == null
                || Math.abs(pose[0] - last[0]) > 1e-4
                || Math.abs(pose[1] - last[1]) > 1e-4
                || Math.abs(pose[2] - last[2]) > 1e-4
                || Math.abs(pose[3] - last[3]) > 0.05;

        // Model north (= its -Z bow) points where the armor stand faces:
        // an extra 180 deg aligns the baked model's facing with the stand
        float yawRad = (float) Math.toRadians(base.getYaw());
        Quaternionf shipRot = new Quaternionf().rotationY(-yawRad + (float) Math.PI);

        if (poseChanged) {
            lastPose.put(ship.identity(), pose);
            double[] c = spec.center;
            if (!tracked.isEmpty()) {
                Entity entity = Bukkit.getEntity(tracked.get(0));
                if (entity instanceof ItemDisplay display && !display.isDead()) {
                    display.teleport(base);
                    display.setInterpolationDelay(0);
                    // XZ: model's TRUE bbox center sits on the controller.
                    // Y: the model's SEAT is lifted to the stand's head height,
                    // so the rider sits in the seat, per the artist's layout.
                    float seatLift = (float) (PILOT_HEAD_HEIGHT - (spec.seatY - c[1]) / 16.0);
                    Vector3f transl = new Vector3f(
                            (float) ((8.0 - c[0]) / 16.0),
                            seatLift,
                            (float) ((8.0 - c[2]) / 16.0)).rotate(shipRot);
                    Transformation t = new Transformation(
                            transl, new Quaternionf(shipRot),
                            new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf());
                    display.setTransformation(t);
                }
            }
        }

        // Defense blocks orbit the ship center, evenly spaced, slow rotation
        long now = System.currentTimeMillis();
        double orbitAngle = (now % 6000L) / 6000.0 * 2.0 * Math.PI;
        for (int i = 0; i < DEFENSE_BLOCK_COUNT && 1 + i < tracked.size(); i++) {
            Entity entity = Bukkit.getEntity(tracked.get(1 + i));
            if (!(entity instanceof BlockDisplay display) || display.isDead()) {
                continue;
            }
            display.setInterpolationDelay(0);
            double angle = orbitAngle + (2.0 * Math.PI * i / DEFENSE_BLOCK_COUNT);
            double localX = Math.cos(angle) * DEFENSE_ORBIT_RADIUS;
            double localZ = Math.sin(angle) * DEFENSE_ORBIT_RADIUS;
            double[] world = worldOffset(base.getYaw(), localX, localZ);
            Location target = base.clone().add(world[0], DEFENSE_BLOCK_HEIGHT, world[1]);
            display.teleport(target);
            Quaternionf spin = new Quaternionf()
                    .rotationY((float) (-angle - yawRad));
            Vector3f centering = new Vector3f(
                    -DEFENSE_BLOCK_SIZE / 2.0f, -DEFENSE_BLOCK_SIZE / 2.0f,
                    -DEFENSE_BLOCK_SIZE / 2.0f).rotate(spin);
            display.setTransformation(new Transformation(
                    centering, spin,
                    new Vector3f(DEFENSE_BLOCK_SIZE, DEFENSE_BLOCK_SIZE, DEFENSE_BLOCK_SIZE),
                    new Quaternionf()));
        }
    }

    private static double[] worldOffset(double yawDegrees, double localX, double localZ) {
        double yawRad = Math.toRadians(yawDegrees);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double dx = -cos * localX - sin * localZ;
        double dz = -sin * localX + cos * localZ;
        return new double[] {dx, dz};
    }

    /** Remove the custom visuals of a ship. */
    public void despawn(ShipIdentity shipId) {
        lastPose.remove(shipId);
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

    public void clear() {
        byShip.keySet().forEach(this::despawn);
    }

    /** Player-facing debug line for /moreships debug. */
    public String debugLine(ShipIdentity shipId) {
        List<UUID> tracked = byShip.get(shipId);
        int alive = tracked == null ? 0 : (int) tracked.stream()
                .map(Bukkit::getEntity)
                .filter(e -> e != null && !e.isDead())
                .count();
        return "customModel specs=" + models.keySet()
                + " visuals=" + (tracked == null ? "none" : alive + "/" + tracked.size());
    }
}
