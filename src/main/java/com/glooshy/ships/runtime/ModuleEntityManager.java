package com.glooshy.ships.runtime;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.ship.ModuleSlot;
import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
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
    public record ModuleEntityBinding(ShipIdentity shipId, ModuleSlot slot, UUID entityUuid) {
    }

    private final NamespacedKey shipIdKey;
    private final NamespacedKey slotKey;
    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;
    private final ModuleItem moduleItem;

    private final Map<UUID, ModuleEntityBinding> byEntity = new ConcurrentHashMap<>();
    private final Map<ShipIdentity, Map<ModuleSlot, UUID>> byShip = new ConcurrentHashMap<>();

    public ModuleEntityManager(NamespacedKey shipIdKey,
                               NamespacedKey slotKey,
                               ShipRegistry shipRegistry,
                               RuntimeBindingRegistry bindingRegistry,
                               ModuleItem moduleItem) {
        this.shipIdKey = shipIdKey;
        this.slotKey = slotKey;
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
        this.moduleItem = moduleItem;
    }

    /** Resolve a clicked/damaged entity to its ship + slot. */
    public Optional<ModuleEntityBinding> resolve(UUID entityUuid) {
        return Optional.ofNullable(byEntity.get(entityUuid));
    }

    /** Spawn the module entity for a newly installed module. */
    public void spawn(Ship ship, ModuleSlot slot) {
        ModuleType type = ship.modules().get(slot);
        if (type == null) {
            return;
        }
        Location base = shipLocation(ship.identity());
        if (base == null) {
            return;
        }
        despawn(ship.identity(), slot);
        spawnStand(ship.identity(), slot, type, moduleLocation(base, ship.identity(), slot));
    }

    private ArmorStand spawnStand(ShipIdentity shipId, ModuleSlot slot, ModuleType type, Location loc) {
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
            as.getPersistentDataContainer().set(slotKey, PersistentDataType.STRING, slot.name());
        });
        byEntity.put(stand.getUniqueId(), new ModuleEntityBinding(shipId, slot, stand.getUniqueId()));
        byShip.computeIfAbsent(shipId, k -> new ConcurrentHashMap<>()).put(slot, stand.getUniqueId());
        return stand;
    }

    /** Remove the module entity (module removed / moved). */
    public void despawn(ShipIdentity shipId, ModuleSlot slot) {
        Map<ModuleSlot, UUID> slots = byShip.get(shipId);
        if (slots == null) {
            return;
        }
        UUID uuid = slots.remove(slot);
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
        for (ModuleSlot slot : ModuleSlot.values()) {
            despawn(shipId, slot);
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
        for (Map.Entry<ModuleSlot, ModuleType> entry : ship.modules().entrySet()) {
            ModuleSlot slot = entry.getKey();
            UUID uuid = byShip.getOrDefault(shipId, Map.of()).get(slot);
            var existing = uuid == null ? null : Bukkit.getEntity(uuid);
            if (existing == null || existing.isDead()) {
                if (uuid != null) {
                    byEntity.remove(uuid);
                }
                spawnStand(shipId, slot, entry.getValue(), moduleLocation(base, shipId, slot));
            }
        }

        // Clean ghosts: entities for slots the ship no longer has
        Map<ModuleSlot, UUID> slots = byShip.get(shipId);
        if (slots != null) {
            for (ModuleSlot slot : List.copyOf(slots.keySet())) {
                if (!ship.modules().containsKey(slot)) {
                    despawn(shipId, slot);
                }
            }
        }

        // Position + rotate
        for (Map.Entry<ModuleSlot, ModuleType> entry : ship.modules().entrySet()) {
            UUID uuid = byShip.getOrDefault(shipId, Map.of()).get(entry.getKey());
            var entity = uuid == null ? null : Bukkit.getEntity(uuid);
            if (entity == null || entity.isDead()) {
                continue;
            }
            Location target = moduleLocation(base, shipId, entry.getKey());
            Location current = entity.getLocation();
            if (current.distanceSquared(target) > 0.0001
                    || current.getYaw() != base.getYaw()) {
                entity.teleport(target);
            }
        }
    }

    /** Restore persisted bindings on enable (after registry + bindings load). */
    public void load(@NotNull List<ModuleEntityBinding> bindings) {
        for (ModuleEntityBinding binding : bindings) {
            Optional<Ship> ship = shipRegistry.find(binding.shipId());
            if (ship.isEmpty() || !ship.get().modules().containsKey(binding.slot())) {
                continue; // Stale entry — registry wins, follow() will respawn if needed
            }
            byEntity.put(binding.entityUuid(), binding);
            byShip.computeIfAbsent(binding.shipId(), k -> new ConcurrentHashMap())
                    .put(binding.slot(), binding.entityUuid());
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
        return entity.getLocation();
    }

    private Location moduleLocation(Location base, ShipIdentity shipId, ModuleSlot slot) {
        double[] offset = ModuleSlot.worldOffset(base.getYaw(), slot.localX(), slot.localZ());
        Location loc = base.clone().add(offset[0], ModuleSlot.Y_OFFSET, offset[1]);
        loc.setYaw(base.getYaw());
        loc.setPitch(0f);
        return loc;
    }
}
