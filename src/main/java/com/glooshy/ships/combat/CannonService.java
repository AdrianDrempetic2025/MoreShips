package com.glooshy.ships.combat;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.CannonState;
import com.glooshy.ships.ship.ModulePos;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Session-2 cannon: seat + own inventory + fuel/ammunition economy +
 * camera-controlled aim in a 180° arc.
 *
 * <ul>
 *   <li><b>Ammo</b>: one snowball item = one shot (consumed on fire).</li>
 *   <li><b>Fuel</b>: furnace-style burn values; one fuel item is consumed at
 *       fire time (never while idling) to generate charged shots.</li>
 *   <li><b>Aim</b>: the barrel rests pointing outward from the module's hull
 *       position; the shooter's camera steers within ±90° of that direction.
 *       ONE aim calculation (here) drives BOTH the barrel visual and the
 *       projectile — they can never disagree.</li>
 *   <li><b>Persistence</b>: shots + inventory live on the {@link Ship} record
 *       and survive restarts via the ship store.</li>
 * </ul>
 */
public final class CannonService {

    /** Marker for opened cannon inventories (InventoryCloseEvent routing). */
    public static final class Holder implements InventoryHolder {
        public final ShipIdentity shipId;
        public final ModulePos pos;

        public Holder(ShipIdentity shipId, ModulePos pos) {
            this.shipId = shipId;
            this.pos = pos;
        }

        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder");
        }
    }

    /** The single ammunition/projectile type of Session 2. */
    public static final Material AMMO_MATERIAL = Material.SNOWBALL;

    /** Barrel holds an aim this long after the last shot, then rests. */
    private static final long AIM_GRACE_MILLIS = 1500L;

    private static final float HALF_ARC_DEGREES = 90.0f;
    private static final float MIN_PITCH = -45.0f;
    private static final float MAX_PITCH = 15.0f;

    private final ShipRegistry shipRegistry;
    private final NamespacedKey cannonMarker;
    private final double damage;
    private final long cooldownMillis;
    private final double speed;
    private final CannonAimTracker aimTracker;

    private final Map<String, Long> lastFired = new ConcurrentHashMap<>();
    private final Map<ShipIdentity, Map<ModulePos, Inventory>> openInventories =
            new ConcurrentHashMap<>();

    public CannonService(ShipRegistry shipRegistry, NamespacedKey cannonMarker,
                         double damage, long cooldownMillis, double speed,
                         CannonAimTracker aimTracker) {
        this.shipRegistry = shipRegistry;
        this.cannonMarker = cannonMarker;
        this.damage = damage;
        this.cooldownMillis = cooldownMillis;
        this.speed = speed;
        this.aimTracker = aimTracker;
    }

    public CannonAimTracker aimTracker() {
        return aimTracker;
    }

    /**
     * Fire the cannon at {@code pos}. Returns true when a shot actually left
     * the barrel.
     */
    public boolean fire(@NotNull Player shooter, @NotNull Ship ship, @NotNull ModulePos pos,
                        @NotNull ArmorStand stand, double localX, double localZ) {
        String key = ship.identity().encoded() + "|" + pos.encoded();
        long now = System.currentTimeMillis();
        Long last = lastFired.get(key);
        if (last != null && now - last < cooldownMillis) {
            long remaining = (cooldownMillis - (now - last) + 999) / 1000;
            shooter.sendMessage(Component.text(
                    "Cannon reloading — " + remaining + "s.", NamedTextColor.RED));
            return false;
        }

        // Resting direction: outward from the ship's center through this
        // module's hull grid position, rotated by the ship's yaw
        double outwardYaw = outwardYawDeg(stand.getLocation().getYaw(), localX, localZ);

        // ONE aim calculation — feeds the barrel visual AND the projectile
        float cameraYaw = shooter.getEyeLocation().getYaw();
        float aimYaw = CannonAimTracker.clampToArc(cameraYaw, (float) outwardYaw, HALF_ARC_DEGREES);
        float aimPitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH,
                shooter.getEyeLocation().getPitch()));

        // Ammo/fuel economy — atomic on the registry's compute
        String[] abort = {null};
        Ship updated = shipRegistry.mutateCannon(ship.identity(), pos, state -> {
            int ammoSlot = firstSlotWith(state, AMMO_MATERIAL);
            if (ammoSlot < 0) {
                abort[0] = "No ammunition (" + AMMO_MATERIAL.name() + ") in the cannon. "
                        + "Right-click it with snowballs to load.";
                return state;
            }
            int shots = state.shots();
            if (shots <= 0) {
                int fuelSlot = firstFuelSlot(state);
                if (fuelSlot < 0) {
                    abort[0] = "No fuel in the cannon — charged shots cannot be generated. "
                            + "Load coal, planks or other furnace fuel.";
                    return state;
                }
                Material fuelMat = materialAt(state, fuelSlot);
                shots += CannonFuels.shotsFor(fuelMat);
                state = consumeAt(state, fuelSlot);
            }
            state = consumeAt(state, ammoSlot);
            return state.withShots(shots - 1);
        });
        if (abort[0] != null) {
            shooter.sendMessage(Component.text(abort[0], NamedTextColor.RED));
            return false;
        }
        CannonState after = updated.cannons().getOrDefault(pos, CannonState.empty());
        lastFired.put(key, now);

        // Projectile along the SAME aim the barrel shows
        double yawRad = Math.toRadians(aimYaw);
        double pitchRad = Math.toRadians(aimPitch);
        double horizontal = Math.cos(pitchRad) * speed;
        Vector velocity = new Vector(
                -Math.sin(yawRad) * horizontal,
                -Math.sin(pitchRad) * speed,
                Math.cos(yawRad) * horizontal);

        Location muzzle = stand.getLocation().clone().add(
                velocity.clone().normalize().multiply(0.6)).add(0, 0.4, 0);
        muzzle.getWorld().spawn(muzzle, Snowball.class, sb -> {
            sb.setVelocity(velocity);
            sb.setShooter(shooter);
            sb.setGravity(true);
            sb.setPersistent(false);
            sb.getPersistentDataContainer().set(
                    cannonMarker, PersistentDataType.STRING, ship.identity().encoded());
        });

        // Barrel visual: rotate the module stand to the aim; it rides back to
        // resting alignment once the aim expires (ModuleEntityManager).
        stand.setRotation(aimYaw, aimPitch);
        aimTracker.set(ship.identity(), pos, aimYaw, aimPitch,
                now + cooldownMillis + AIM_GRACE_MILLIS);

        shooter.sendMessage(Component.text(
                "Fired cannon! (" + (int) damage + " dmg, " + after.shots()
                        + " charged shot(s) left)",
                NamedTextColor.GOLD));
        return true;
    }

    /** Default outward yaw (degrees) for a cannon at ship-local (localX, localZ). */
    public static double outwardYawDeg(float shipYaw, double localX, double localZ) {
        float yawRad = (float) Math.toRadians(shipYaw);
        double dirX = -Math.cos(yawRad) * localX - Math.sin(yawRad) * localZ;
        double dirZ = -Math.sin(yawRad) * localX + Math.cos(yawRad) * localZ;
        return Math.toDegrees(Math.atan2(-dirX, dirZ));
    }

    /** Right-click with fuel/ammo in hand: load the stack into the cannon. */
    public void load(@NotNull Player player, @NotNull Ship ship, @NotNull ModulePos pos,
                     @NotNull ItemStack stack) {
        boolean ammo = stack.getType() == AMMO_MATERIAL;
        if (!ammo && !CannonFuels.isFuel(stack.getType())) {
            return;
        }
        int[] loaded = {0};
        shipRegistry.mutateCannon(ship.identity(), pos, state -> {
            Map<Integer, Map<String, Object>> inv = new LinkedHashMap<>(state.inventory());
            int free = -1;
            for (int slot = 0; slot < CannonState.INVENTORY_SIZE; slot++) {
                if (!inv.containsKey(slot)) {
                    if (free < 0) {
                        free = slot;
                    }
                }
            }
            int amount = stack.getAmount();
            if (free >= 0) {
                ItemStack whole = stack.clone();
                whole.setAmount(amount);
                inv.put(free, whole.serialize());
                loaded[0] = amount;
            } else {
                // No free slot — top up the first partial same-type stack
                for (int slot = 0; slot < CannonState.INVENTORY_SIZE && amount > loaded[0]; slot++) {
                    Map<String, Object> item = inv.get(slot);
                    if (item == null || !stack.getType().name().equals(item.get("type"))) {
                        continue;
                    }
                    int have = ((Number) item.getOrDefault("amount", 1)).intValue();
                    int take = Math.min(amount, 64 - have);
                    if (take <= 0) {
                        continue;
                    }
                    Map<String, Object> copy = new LinkedHashMap<>(item);
                    copy.put("amount", have + take);
                    inv.put(slot, copy);
                    loaded[0] += take;
                }
            }
            return state.withInventory(inv);
        });
        if (loaded[0] <= 0) {
            player.sendMessage(Component.text(
                    "Cannon inventory is full.", NamedTextColor.RED));
            return;
        }
        stack.setAmount(stack.getAmount() - loaded[0]);
        player.getInventory().setItemInMainHand(stack.getAmount() > 0 ? stack : null);
        player.sendMessage(Component.text(
                "Loaded " + loaded[0] + " " + (ammo ? "snowball(s)" : "fuel") + ".",
                NamedTextColor.GREEN));
    }

    /** Open the cannon's own inventory UI. */
    public void openInventory(@NotNull Player player, @NotNull Ship ship, @NotNull ModulePos pos) {
        Inventory inventory = player.getServer().createInventory(
                new Holder(ship.identity(), pos),
                CannonState.INVENTORY_SIZE,
                Component.text("Cannon " + pos.encoded()
                        + " — snowballs + furnace fuel", NamedTextColor.GOLD));
        CannonState state = ship.cannons().getOrDefault(pos, CannonState.empty());
        state.inventory().forEach((slot, itemMap) -> {
            if (slot < 0 || slot >= CannonState.INVENTORY_SIZE) {
                return;
            }
            ItemStack item = deserializeItem(itemMap);
            if (item != null) {
                inventory.setItem(slot, item);
            }
        });
        openInventories.computeIfAbsent(ship.identity(), k -> new ConcurrentHashMap<>())
                .put(pos, inventory);
        player.openInventory(inventory);
    }

    /** InventoryCloseEvent path: persist the cannon inventory back to the ship. */
    public boolean save(@NotNull Holder holder, @NotNull Inventory inventory, @NotNull Player player) {
        Map<Integer, Map<String, Object>> contents = new HashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (item.getType() != AMMO_MATERIAL && !CannonFuels.isFuel(item.getType())) {
                // Not ammo/fuel — hand it back instead of storing
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                continue;
            }
            contents.put(slot, item.serialize());
        }
        markClosed(holder.shipId, holder.pos);
        try {
            shipRegistry.setCannon(holder.shipId, holder.pos, new CannonState(
                    shipRegistry.find(holder.shipId)
                            .map(s -> s.cannons().getOrDefault(holder.pos, CannonState.empty()).shots())
                            .orElse(0),
                    contents));
            return true;
        } catch (IllegalStateException e) {
            return false; // Ship destroyed between open and close
        }
    }

    public void markClosed(ShipIdentity shipId, ModulePos pos) {
        Map<ModulePos, Inventory> open = openInventories.get(shipId);
        if (open != null) {
            open.remove(pos);
        }
    }

    /**
     * Destruction path: drop the LIVE (freshest) contents of any open cannon
     * inventory and close it. Returns positions whose contents were handled —
     * the caller must NOT also drop the serialized state for those.
     */
    public Set<ModulePos> dropOpenAndClose(ShipIdentity shipId, Location dropAt) {
        Map<ModulePos, Inventory> open = openInventories.remove(shipId);
        if (open == null) {
            return Set.of();
        }
        Set<ModulePos> handled = new java.util.HashSet<>();
        for (Map.Entry<ModulePos, Inventory> entry : open.entrySet()) {
            Inventory inv = entry.getValue();
            for (ItemStack item : inv.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    dropAt.getWorld().dropItemNaturally(dropAt, item);
                }
            }
            inv.clear();
            handled.add(entry.getKey());
        }
        return handled;
    }

    /** The ship this cannon shot belongs to, or null. */
    public ShipIdentity sourceShip(@NotNull Snowball shot) {
        String id = shot.getPersistentDataContainer().get(cannonMarker, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return ShipIdentity.decode(id);
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

    public void clearShip(ShipIdentity shipId) {
        openInventories.remove(shipId);
        aimTracker.clear(shipId);
        lastFired.keySet().removeIf(k -> k.startsWith(shipId.encoded() + "|"));
    }

    public ItemStack deserializeItem(Map<String, Object> itemMap) {
        try {
            return ItemStack.deserialize(itemMap);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

    private static int firstSlotWith(CannonState state, Material material) {
        for (int slot = 0; slot < CannonState.INVENTORY_SIZE; slot++) {
            if (materialAt(state, slot) == material) {
                return slot;
            }
        }
        return -1;
    }

    private static int firstFuelSlot(CannonState state) {
        for (int slot = 0; slot < CannonState.INVENTORY_SIZE; slot++) {
            Material mat = materialAt(state, slot);
            if (mat != null && mat != AMMO_MATERIAL && CannonFuels.isFuel(mat)) {
                return slot;
            }
        }
        return -1;
    }

    private static Material materialAt(CannonState state, int slot) {
        Map<String, Object> item = state.itemAt(slot);
        if (item == null || !(item.get("type") instanceof String typeName)) {
            return null;
        }
        return Material.matchMaterial(typeName);
    }

    /** Remove ONE item from a slot (decrement or delete). */
    private static CannonState consumeAt(CannonState state, int slot) {
        Map<String, Object> item = state.itemAt(slot);
        if (item == null) {
            return state;
        }
        Map<Integer, Map<String, Object>> inv = new LinkedHashMap<>(state.inventory());
        int amount = ((Number) item.getOrDefault("amount", 1)).intValue();
        if (amount > 1) {
            Map<String, Object> copy = new LinkedHashMap<>(item);
            copy.put("amount", amount - 1);
            inv.put(slot, copy);
        } else {
            inv.remove(slot);
        }
        return state.withInventory(inv);
    }
}
