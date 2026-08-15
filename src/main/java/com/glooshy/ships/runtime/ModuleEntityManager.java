package com.glooshy.ships.runtime;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.ship.ModulePos;
import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.ship.ShipSize;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Owns the module entities of all ships.
 *
 * <p>Every installed module is represented by its own small invisible
 * ArmorStand positioned at the slot's ship-local offset. The stand wears the
 * module item as a helmet (the visible module), has its own hitbox, carries
 * PDC markers (ship id + slot), and follows the ship entity every tick —
 * position and rotation both.
 *
 * <p>Self-healing: if a module entity is missing or dead (killed, chunk
 * glitch, stale store entry), {@link #follow} respawns it from the registry's
 * authoritative module map. Registry data always wins over entity state.
 */
public final class ModuleEntityManager {

    /** Durable ship → module-entity binding, persisted across restarts. */
    public record ModuleEntityBinding(ShipIdentity shipId, ModulePos pos, UUID entityUuid) {
    }

    private final NamespacedKey shipIdKey;
    private final NamespacedKey slotKey;
    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;
    private final ModuleItem moduleItem;
    private final com.glooshy.ships.combat.CannonAimTracker cannonAims;

    private final Map<UUID, ModuleEntityBinding> byEntity = new ConcurrentHashMap<>();
    private final Map<ShipIdentity, Map<ModulePos, UUID>> byShip = new ConcurrentHashMap<>();

    public ModuleEntityManager(NamespacedKey shipIdKey,
                               NamespacedKey slotKey,
                               ShipRegistry shipRegistry,
                               RuntimeBindingRegistry bindingRegistry,
                               ModuleItem moduleItem) {
        this(shipIdKey, slotKey, shipRegistry, bindingRegistry, moduleItem, null);
    }

    public ModuleEntityManager(NamespacedKey shipIdKey,
                               NamespacedKey slotKey,
                               ShipRegistry shipRegistry,
                               RuntimeBindingRegistry bindingRegistry,
                               ModuleItem moduleItem,
                               com.glooshy.ships.combat.CannonAimTracker cannonAims) {
        this.shipIdKey = shipIdKey;
        this.slotKey = slotKey;
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
        this.moduleItem = moduleItem;
        this.cannonAims = cannonAims;
    }

    /** Resolve a clicked/damaged entity to its ship + slot. */
    public Optional<ModuleEntityBinding> resolve(UUID entityUuid) {
        return Optional.ofNullable(byEntity.get(entityUuid));
    }

    /** Spawn the module entity for a newly installed module. */
    public void spawn(Ship ship, ModulePos pos) {
        ModuleType type = ship.modules().get(pos);
        if (type == null) {
            return;
        }
        Location base = shipLocation(ship.identity());
        if (base == null) {
            return;
        }
        despawn(ship.identity(), pos);
        spawnStand(ship.identity(), pos, type, moduleLocation(base, ship.identity(), pos));
    }

    private ArmorStand spawnStand(ShipIdentity shipId, ModulePos pos, ModuleType type, Location loc) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setGravity(false);
            as.setInvisible(true);
            as.setSmall(true);
            as.setMarker(false);
            as.setBasePlate(false);
            as.setRemoveWhenFarAway(false);
            as.customName(Component.text(moduleItem.displayName(type), NamedTextColor.GOLD));
            as.setCustomNameVisible(true);
            as.getEquipment().setHelmet(moduleItem.create(type));
            as.getPersistentDataContainer().set(shipIdKey, PersistentDataType.STRING,
                    shipId.encoded());
            as.getPersistentDataContainer().set(slotKey, PersistentDataType.STRING, pos.encoded());
        });
        byEntity.put(stand.getUniqueId(), new ModuleEntityBinding(shipId, pos, stand.getUniqueId()));
        byShip.computeIfAbsent(shipId, k -> new ConcurrentHashMap<>()).put(pos, stand.getUniqueId());
        return stand;
    }

    /** Remove the module entity (module removed / moved). */
    public void despawn(ShipIdentity shipId, ModulePos pos) {
        Map<ModulePos, UUID> slots = byShip.get(shipId);
        if (slots == null) {
            return;
        }
        UUID uuid = slots.remove(pos);
        if (uuid == null) {
            return;
        }
        byEntity.remove(uuid);
        if (slots.isEmpty()) {
            byShip.remove(shipId);
        }
        var entity = Bukkit.getEntity(uuid);
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    /** Remove all module entities of a ship (teardown / destruction). */
    public void despawnAll(ShipIdentity shipId) {
        for (ModulePos pos : List.copyOf(byShip.getOrDefault(shipId, Map.of()).keySet())) {
            despawn(shipId, pos);
        }
    }

    /**
     * Bring all module entities of a ship to their slot positions. Called
     * every tick for FINALIZED ships — also repairs dead/missing entities.
     */
    public void follow(ShipIdentity shipId) {
        Location base = shipLocation(shipId);
        if (base == null) {
            return;
        }
        Ship ship = shipRegistry.find(shipId).orElse(null);
        if (ship == null) {
            return;
        }

        // Self-heal: registry modules without a live entity get respawned
        for (Map.Entry<ModulePos, ModuleType> entry : ship.modules().entrySet()) {
            ModulePos pos = entry.getKey();
            UUID uuid = byShip.getOrDefault(shipId, Map.of()).get(pos);
            var existing = uuid == null ? null : Bukkit.getEntity(uuid);
            if (existing == null || existing.isDead()) {
                if (uuid != null) {
                    byEntity.remove(uuid);
                }
                spawnStand(shipId, pos, entry.getValue(), moduleLocation(base, shipId, pos));
            }
        }

        // Clean ghosts: entities for positions the ship no longer has
        Map<ModulePos, UUID> positions = byShip.get(shipId);
        if (positions != null) {
            for (ModulePos pos : List.copyOf(positions.keySet())) {
                if (!ship.modules().containsKey(pos)) {
                    despawn(shipId, pos);
                }
            }
        }

        // Position + rotate
        for (Map.Entry<ModulePos, ModuleType> entry : ship.modules().entrySet()) {
            UUID uuid = byShip.getOrDefault(shipId, Map.of()).get(entry.getKey());
            var entity = uuid == null ? null : Bukkit.getEntity(uuid);
            if (entity == null || entity.isDead()) {
                continue;
            }
            Location target = moduleLocation(base, shipId, entry.getKey());
            // A cannon holding a live aim keeps its barrel pointed there;
            // everything else (and expired aims) rests aligned with the ship
            if (entry.getValue() == ModuleType.CANNON && cannonAims != null) {
                // Seated gunner: their CAMERA steers the barrel live (clamped
                // to the 180° arc around the module's outward direction)
                for (org.bukkit.entity.Entity passenger : entity.getPassengers()) {
                    if (passenger instanceof org.bukkit.entity.Player gunner) {
                        float outward = (float) com.glooshy.ships.combat.CannonService
                                .outwardYawDeg(base.getYaw(),
                                        entry.getKey().localX(ship.size()),
                                        entry.getKey().localZ(ship.size()));
                        float aimYaw = com.glooshy.ships.combat.CannonAimTracker
                                .clampToArc(gunner.getEyeLocation().getYaw(), outward, 90.0f);
                        float aimPitch = Math.max(-45.0f, Math.min(15.0f,
                                gunner.getEyeLocation().getPitch()));
                        cannonAims.set(shipId, entry.getKey(), aimYaw, aimPitch,
                                System.currentTimeMillis() + 500L);
                        break;
                    }
                }
                var aim = cannonAims.live(shipId, entry.getKey());
                if (aim != null) {
                    target.setYaw(aim.yaw());
                    target.setPitch(aim.pitch());
                }
            }
            Location current = entity.getLocation();
            if (current.distanceSquared(target) > 0.0001
                    || current.getYaw() != target.getYaw()
                    || current.getPitch() != target.getPitch()) {
                entity.teleport(target);
            }
        }
    }

    /** Restore persisted bindings on enable (after registry + bindings load). */
    public void load(@NotNull List<ModuleEntityBinding> bindings) {
        for (ModuleEntityBinding binding : bindings) {
            Optional<Ship> ship = shipRegistry.find(binding.shipId());
            if (ship.isEmpty() || !ship.get().modules().containsKey(binding.pos())) {
                continue; // Stale entry — registry wins, follow() will respawn if needed
            }
            byEntity.put(binding.entityUuid(), binding);
            byShip.computeIfAbsent(binding.shipId(), k -> new ConcurrentHashMap())
                    .put(binding.pos(), binding.entityUuid());
        }
    }

    public @NotNull List<ModuleEntityBinding> snapshot() {
        return new ArrayList<>(byEntity.values());
    }

    private Location shipLocation(ShipIdentity shipId) {
        RuntimeBinding binding = bindingRegistry.findByShip(shipId).orElse(null);
        if (binding == null) {
            return null;
        }
        var entity = Bukkit.getEntity(binding.entityUuid());
        if (entity == null || entity.isDead()) {
            return null;
        }
        // Deck reference: the controller stand rides 0.5 below the deck
        return entity.getLocation().add(0.0, 0.5, 0.0);
    }

    private Location moduleLocation(Location base, ShipIdentity shipId, ModulePos pos) {
        Optional<Ship> shipOpt = shipRegistry.find(shipId);
        if (shipOpt.isEmpty()) {
            return base.clone();
        }
        ShipSize size = shipOpt.get().size();
        double[] offset = ModulePos.worldOffset(base.getYaw(), pos.localX(size), pos.localZ(size));
        Location loc = base.clone().add(offset[0], 0.35, offset[1]);
        loc.setYaw(base.getYaw());
        loc.setPitch(0f);
        return loc;
    }
}
