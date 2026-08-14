package com.glooshy.ships.runtime;

import com.glooshy.ships.identity.ShipIdentity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataType;
import java.util.UUID;

/**
 * Spawns the platform entity that represents an unfinished ship in the world.
 *
 * <p>V1 uses a glowing ArmorStand with no gravity, a custom name above it, and
 * a PersistentDataContainer marker recording the ship identity. The PDC marker
 * is the durable truth on the entity; the binding registry is the live in-memory
 * truth. They must agree.
 *
 * <p>Entity-strategy choice (ArmorStand vs. custom entity vs. display entity)
 * is OCBIND-03 from the L5-04 plan. This slice picks ArmorStand as the simplest
 * visible, glow-capable, PDC-capable option. Reopen condition: if motion or
 * multi-block hull rendering proves impractical on ArmorStand, switch to a
 * different substrate.
 */
public final class ShipEntitySpawner {

    public static final String ENTITY_LABEL = "Unfinished Ship";

    private final NamespacedKey shipIdKey;

    public ShipEntitySpawner(NamespacedKey shipIdKey) {
        this.shipIdKey = shipIdKey;
    }

    public UUID spawnUnfinishedShip(Location location, ShipIdentity shipId) {
        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class, as -> {
            as.setVisible(true);
            as.setGravity(true);
            as.setCollidable(false); // the solid deck must not push the controller
            as.setGlowing(true);
            as.customName(Component.text(
                    ENTITY_LABEL + " " + shortId(shipId), NamedTextColor.AQUA));
            as.setCustomNameVisible(true);
            as.getPersistentDataContainer().set(
                    shipIdKey, PersistentDataType.STRING, shipId.encoded());
        });
        return stand.getUniqueId();
    }

    /**
     * Short, human-readable prefix of the ship identity (first UUID group).
     * Used only for display; the full UUID is in the PDC marker.
     */
    private static String shortId(ShipIdentity shipId) {
        String encoded = shipId.encoded();
        int dash = encoded.indexOf('-');
        return dash > 0 ? encoded.substring(0, dash) : encoded;
    }
}
