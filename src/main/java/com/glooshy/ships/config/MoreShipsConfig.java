package com.glooshy.ships.config;

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

    public @NotNull Material shipCoreBaseMaterial() {
        String name = config.getString("shipCore.baseMaterial", "HEART_OF_THE_SEA");
        Material resolved = Material.matchMaterial(name);
        return resolved != null ? resolved : Material.HEART_OF_THE_SEA;
    }

    public @NotNull String shipCoreDisplayName() {
        return config.getString("shipCore.displayName", "Ship Core");
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
}
