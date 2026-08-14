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

        ModelSpec(List<HullCube> hullCubes, NamespacedKey trimItemModel) {
            this.hullCubes = List.copyOf(hullCubes);
            this.trimItemModel = trimItemModel;
        }
    }

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
                    new NamespacedKey("moreships", trimItemModel)));
            logger.info("Custom model for " + size + ": " + cubes.size() + " hull cubes");
        } catch (Exception e) {
            logger.warning("Failed to load custom model " + resource + ": " + e.getMessage());
        }
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

        int expected = spec.hullCubes.size() + 1; // + trim ItemDisplay
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
        Material hull = ship.hullMaterial();

        for (HullCube cube : spec.hullCubes) {
            BlockDisplay display = base.getWorld().spawn(base, BlockDisplay.class, bd -> {
                bd.setBlock(hull.createBlockData());
                bd.setPersistent(false);
                bd.setTeleportDuration(1);
            });
            entities.add(display.getUniqueId());
        }

        ItemDisplay trim = base.getWorld().spawn(base, ItemDisplay.class, id -> {
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(spec.trimItemModel);
            stack.setItemMeta(meta);
            id.setItemStack(stack);
            id.setPersistent(false);
            id.setTeleportDuration(1);
            id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            id.setViewRange(1.0f);
        });
        entities.add(trim.getUniqueId());

        positionVisuals(ship, spec, base, entities);
        return entities;
    }

    private void positionVisuals(Ship ship, ModelSpec spec, Location base, List<UUID> tracked) {
        float yawRad = (float) Math.toRadians(base.getYaw());
        Quaternionf shipRot = new Quaternionf().rotationY(-yawRad);

        for (int i = 0; i < spec.hullCubes.size() && i < tracked.size(); i++) {
            Entity entity = Bukkit.getEntity(tracked.get(i));
            if (!(entity instanceof BlockDisplay display) || display.isDead()) {
                continue;
            }
            HullCube cube = spec.hullCubes.get(i);
            double[] size = cube.size();
            double[] center = cube.center();

            // Rigid-body placement: the cube's own rotation (any axis) about its
            // origin, then the ship's yaw — every cube shares the ship frame,
            // so the assembly moves and turns as ONE object.
            Quaternionf cubeRot = axisRotation(cube.axis(), cube.angleDeg());
            Vector3f offset = new Vector3f(
                    (float) (center[0] - cube.rotOrigin()[0]),
                    (float) (center[1] - cube.rotOrigin()[1]),
                    (float) (center[2] - cube.rotOrigin()[2]));
            offset.rotate(cubeRot);
            offset.add((float) (cube.rotOrigin()[0] - 8.0),
                    (float) (cube.rotOrigin()[1] - 8.0),
                    (float) (cube.rotOrigin()[2] - 8.0));

            Vector3f world = new Vector3f(offset).rotate(shipRot).div(16.0f);
            Location target = base.clone().add(world.x, world.y, world.z);
            if (display.getLocation().distanceSquared(target) > 0.01) {
                display.teleport(target);
            }

            Quaternionf fullRot = new Quaternionf(shipRot).mul(cubeRot);
            Transformation t = new Transformation(
                    new Vector3f((float) (-size[0] / 2.0), (float) (-size[1] / 2.0),
                            (float) (-size[2] / 2.0)),
                    fullRot,
                    new Vector3f((float) size[0], (float) size[1], (float) size[2]),
                    new Quaternionf());
            display.setTransformation(t);
        }

        // Trim ItemDisplay: teleports WITH the ship (bug: it never moved after
        // spawn) and rotates with it. Model px [8,8,8] maps to the entity, the
        // same frame the hull cubes use, so the two layers stay glued.
        int trimIndex = spec.hullCubes.size();
        if (trimIndex < tracked.size()) {
            Entity entity = Bukkit.getEntity(tracked.get(trimIndex));
            if (entity instanceof ItemDisplay display && !display.isDead()) {
                if (display.getLocation().distanceSquared(base) > 0.01) {
                    display.teleport(base);
                }
                Vector3f transl = new Vector3f(-0.5f, -0.5f, -0.5f).rotate(shipRot);
                Transformation t = new Transformation(
                        transl, new Quaternionf(shipRot),
                        new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf());
                display.setTransformation(t);
            }
        }
    }

    private static Quaternionf axisRotation(String axis, double angleDeg) {
        float rad = (float) -Math.toRadians(angleDeg);
        return switch (axis == null ? "y" : axis) {
            case "x" -> new Quaternionf().rotationX(rad);
            case "z" -> new Quaternionf().rotationZ(rad);
            default -> new Quaternionf().rotationY(rad);
        };
    }

    /** Remove the custom visuals of a ship. */
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
