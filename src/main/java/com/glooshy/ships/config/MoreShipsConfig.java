package com.glooshy.ships.config;

import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.ShipSize;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Typed view over the plugin's {@code config.yml}.
 *
 * <p>Single source of truth for everything that is server-owner-configurable
 * in V1. Reading is direct; reload is a future slice. All values fall back to
 * built-in defaults if the configured value cannot be parsed (per CON-01,
 * defaults exist; this is the fallback the spec relies on).
 */
public final class MoreShipsConfig {

    private final FileConfiguration config;

    public MoreShipsConfig(FileConfiguration config) {
        this.config = config;
    }

    private static final Map<ShipSize, Material> DEFAULT_CORE_MATERIALS = Map.of(
            ShipSize.SMALL, Material.HEART_OF_THE_SEA,
            ShipSize.MEDIUM, Material.NAUTILUS_SHELL,
            ShipSize.LARGE, Material.DRAGON_EGG);

    public @NotNull Material shipCoreMaterial(@NotNull ShipSize size) {
        String name = config.getString("shipCore." + size.name().toLowerCase() + ".material");
        if (name != null) {
            Material resolved = Material.matchMaterial(name);
            if (resolved != null) {
                return resolved;
            }
        }
        return DEFAULT_CORE_MATERIALS.get(size);
    }

    public @NotNull Map<ShipSize, Material> shipCoreMaterials() {
        Map<ShipSize, Material> materials = new EnumMap<>(ShipSize.class);
        for (ShipSize size : ShipSize.values()) {
            materials.put(size, shipCoreMaterial(size));
        }
        return materials;
    }

    public @NotNull String shipCoreDisplayName(@NotNull ShipSize size) {
        return config.getString("shipCore." + size.name().toLowerCase() + ".displayName",
                size.name().charAt(0) + size.name().substring(1).toLowerCase() + " Ship Core");
    }

    public @NotNull Map<ShipSize, String> shipCoreDisplayNames() {
        Map<ShipSize, String> names = new EnumMap<>(ShipSize.class);
        for (ShipSize size : ShipSize.values()) {
            names.put(size, shipCoreDisplayName(size));
        }
        return names;
    }

    public double spawnOffsetX() {
        return config.getDouble("placement.spawnOffsetX", 0.5);
    }

    public double spawnOffsetY() {
        return config.getDouble("placement.spawnOffsetY", 1.0);
    }

    public double spawnOffsetZ() {
        return config.getDouble("placement.spawnOffsetZ", 0.5);
    }

    public @NotNull Sound placementSound() {
        String name = config.getString("placement.sound", "BLOCK_CONDUIT_ACTIVATE");
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Sound.BLOCK_CONDUIT_ACTIVATE;
        }
    }

    public float placementSoundVolume() {
        return (float) config.getDouble("placement.soundVolume", 0.7);
    }

    public float placementSoundPitch() {
        return (float) config.getDouble("placement.soundPitch", 1.2);
    }

    public double hullMinHardness() {
        return config.getDouble("hull.minHardness", 1.0);
    }

    public double hpMultiplier() {
        return config.getDouble("hp.multiplier", 10.0);
    }

    public boolean movementEnabled() {
        return config.getBoolean("movement.enabled", true);
    }

    public double movementMaxSpeed() {
        return config.getDouble("movement.maxSpeed", 0.4);
    }

    public double movementAcceleration() {
        return config.getDouble("movement.acceleration", 0.02);
    }

    public double movementFriction() {
        return config.getDouble("movement.friction", 0.05);
    }

    public double movementTurnRate() {
        return config.getDouble("movement.turnRate", 4.0);
    }

    public double shipHitboxWidth() {
        return config.getDouble("ship.hitboxWidth", 3.0);
    }

    public double shipHitboxHeight() {
        return config.getDouble("ship.hitboxHeight", 0.5);
    }

    public double physicsRiseVelocity() {
        return config.getDouble("physics.riseVelocity", 0.35);
    }

    public double physicsSinkVelocity() {
        return -Math.abs(config.getDouble("physics.sinkVelocity", 0.30));
    }

    /**
     * Compiled-in fallback: pre-0.14 servers carry an old config.yml without
     * the resourcepack section, and saveDefaultConfig never overwrites it —
     * an empty default would silently disable the pack push entirely.
     */
    private static final String DEFAULT_PACK_URL =
            "http://192.168.1.12:8765/MoreShips-pack.zip";

    public String resourcePackUrl() {
        return config.getString("resourcepack.url", DEFAULT_PACK_URL);
    }

    public double statsWeightPerModule() {
        return config.getDouble("stats.weightPerModule", 0.05);
    }

    public double statsEngineBoost() {
        return config.getDouble("stats.engineBoost", 0.15);
    }

    public double statsHardnessPenalty() {
        return config.getDouble("stats.hardnessPenalty", 0.002);
    }

    public int statsHealthBonus() {
        return config.getInt("stats.healthBonus", 10);
    }

    public boolean recipesEnabled() {
        return config.getBoolean("recipes.enabled", true);
    }

    public double cannonDamage() {
        return config.getDouble("cannons.damage", 6.0);
    }

    public long cannonCooldownMillis() {
        return (long) (config.getDouble("cannons.cooldownSeconds", 3.0) * 1000.0);
    }

    public double cannonSpeed() {
        return config.getDouble("cannons.speed", 2.0);
    }

    public boolean collisionEnabled() {
        return config.getBoolean("collision.enabled", true);
    }

    public double collisionMargin() {
        return config.getDouble("collision.margin", 0.15);
    }

    private static final Map<ModuleType, Material> DEFAULT_MODULE_MATERIALS = Map.of(
            ModuleType.SEAT, Material.OAK_STAIRS,
            ModuleType.CARGO, Material.CHEST,
            ModuleType.CANNON, Material.DISPENSER,
            ModuleType.ENGINE, Material.FURNACE,
            ModuleType.HEALTH, Material.GOLDEN_APPLE);

    public @NotNull Material moduleMaterial(@NotNull ModuleType type) {
        String name = config.getString("modules." + type.name().toLowerCase() + ".material");
        if (name != null) {
            Material resolved = Material.matchMaterial(name);
            if (resolved != null) {
                return resolved;
            }
        }
        return DEFAULT_MODULE_MATERIALS.get(type);
    }

    public @NotNull Map<ModuleType, Material> moduleMaterials() {
        Map<ModuleType, Material> materials = new EnumMap<>(ModuleType.class);
        for (ModuleType type : ModuleType.values()) {
            materials.put(type, moduleMaterial(type));
        }
        return materials;
    }

    public @NotNull String moduleDisplayName(@NotNull ModuleType type) {
        return config.getString("modules." + type.name().toLowerCase() + ".displayName",
                type.name().charAt(0) + type.name().substring(1).toLowerCase() + " Module");
    }

    public @NotNull Map<ModuleType, String> moduleDisplayNames() {
        Map<ModuleType, String> names = new EnumMap<>(ModuleType.class);
        for (ModuleType type : ModuleType.values()) {
            names.put(type, moduleDisplayName(type));
        }
        return names;
    }
}
