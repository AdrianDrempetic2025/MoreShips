package com.glooshy.ships;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Project Beacon — Custom Ship System for Paper 26.2.
 *
 * <p>Multi-occupant, modular, combat-capable water vehicles. Greenfield plugin
 * developed against the Development Ontology v2 workflow (RC3 risk class).
 *
 * <p>This is a scaffold stub. Implementation slices are sequenced per
 * {@code L5-04-Implementation-Plan.md} (BUILD-00 → BUILD-FINAL), critical
 * invariants first.
 */
public final class MoreShips extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("MoreShips (Project Beacon) scaffold loaded — no behavior yet.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MoreShips disabled.");
    }
}
