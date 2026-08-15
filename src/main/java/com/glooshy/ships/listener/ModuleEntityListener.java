package com.glooshy.ships.listener;

import com.glooshy.ships.cargo.CargoService;
import com.glooshy.ships.combat.CannonService;
import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.runtime.ModuleEntityManager;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.ModulePos;
import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Direct interaction with module entities — each module is its own entity
 * with its own hitbox, so no commands are needed for everyday use.
 *
 * <p><b>Right-click</b> (module-specific):
 * <ul>
 *   <li>SEAT — sit on the module; you travel with the ship (shift to stand)</li>
 *   <li>CARGO — open the ship's cargo hold</li>
 *   <li>CANNON — placeholder until the armament slice</li>
 * </ul>
 *
 * <p><b>Punch</b> (left-click): on a HULL_APPLIED ship the module is removed
 * and drops as an item; on a FINALIZED ship modules are locked. Damage is
 * always cancelled — module entities never die to mobs/explosions.
 */
public final class ModuleEntityListener implements Listener {

    private final ModuleEntityManager moduleEntities;
    private final ShipRegistry shipRegistry;
    private final CargoService cargoService;
    private final ModuleItem moduleItem;
    private final com.glooshy.ships.combat.CannonService cannons;

    public ModuleEntityListener(ModuleEntityManager moduleEntities,
                                ShipRegistry shipRegistry,
                                CargoService cargoService,
                                ModuleItem moduleItem,
                                com.glooshy.ships.combat.CannonService cannons) {
        this.moduleEntities = moduleEntities;
        this.shipRegistry = shipRegistry;
        this.cargoService = cargoService;
        this.moduleItem = moduleItem;
        this.cannons = cannons;
    }

    /**
     * Session-2 cannon interactions:
     * <ul>
     *   <li>right-click → SIT on the cannon (its seat)</li>
     *   <li>right-click holding snowballs/fuel while standing outside → quick-load</li>
     * </ul>
     * While seated: camera steers the barrel (180° arc), right-click fires,
     * E opens the cannon management UI (cannon + player inventory).
     */
    private void useCannon(Player player, Ship ship, ModulePos pos, ArmorStand stand) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!inHand.getType().isAir()) {
            cannons.load(player, ship, pos, inHand); // quick-load while outside
            return;
        }
        if (!stand.getPassengers().isEmpty()) {
            player.sendMessage(Component.text(
                    "This cannon seat is occupied.", NamedTextColor.RED));
            return;
        }
        stand.addPassenger(player);
        player.sendMessage(Component.text(
                "Seated at the cannon. Right-click to fire (aim with camera), "
                        + "E to manage ammo/fuel, shift to dismount.",
                NamedTextColor.GRAY));
    }

    /** The cannon module entity a player is seated at, or null. */
    private SeatedCannon seatedCannonOf(Player player) {
        if (!(player.getVehicle() instanceof ArmorStand stand)) {
            return null;
        }
        var binding = moduleEntities.resolve(stand.getUniqueId());
        if (binding.isEmpty()) {
            return null;
        }
        Optional<Ship> shipOpt = shipRegistry.find(binding.get().shipId());
        if (shipOpt.isEmpty()
                || shipOpt.get().modules().get(binding.get().pos()) != ModuleType.CANNON) {
            return null;
        }
        return new SeatedCannon(shipOpt.get(), binding.get().pos(), stand);
    }

    private record SeatedCannon(Ship ship, ModulePos pos, ArmorStand stand) {
    }

    /** Seated at the cannon: right-click anywhere fires along the camera aim. */
    @EventHandler
    public void onPlayerInteract(@NotNull org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        SeatedCannon cannon = seatedCannonOf(event.getPlayer());
        if (cannon == null) {
            return;
        }
        event.setCancelled(true);
        if (cannon.ship().phase() != LifecyclePhase.FINALIZED) {
            event.getPlayer().sendMessage(Component.text(
                    "Cannons only fire on finalized ships.", NamedTextColor.RED));
            return;
        }
        cannons.fire(event.getPlayer(), cannon.ship(), cannon.pos(), cannon.stand(),
                cannon.pos().localX(cannon.ship().size()),
                cannon.pos().localZ(cannon.ship().size()));
    }

    /** Seated at the cannon: E opens the management UI instead of the backpack. */
    @EventHandler
    public void onInventoryOpen(@NotNull org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof CannonService.Holder) {
            return; // our own UI opening — allow
        }
        SeatedCannon cannon = seatedCannonOf(player);
        if (cannon == null) {
            return;
        }
        event.setCancelled(true);
        cannons.openInventory(player, cannon.ship(), cannon.pos());
    }

    @EventHandler
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) {
            return;
        }
        var binding = moduleEntities.resolve(stand.getUniqueId());
        if (binding.isEmpty()) {
            return; // Not a module entity
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        ModulePos pos = binding.get().pos();
        Optional<Ship> shipOpt = shipRegistry.find(binding.get().shipId());
        if (shipOpt.isEmpty()) {
            return;
        }
        Ship ship = shipOpt.get();
        ModuleType type = ship.modules().get(pos);
        if (type == null) {
            return;
        }

        switch (type) {
            case CARGO -> cargoService.open(player, ship, pos);
            case SEAT -> {
                if (!stand.getPassengers().isEmpty()) {
                    player.sendMessage(Component.text(
                            "This seat is occupied.", NamedTextColor.RED));
                    return;
                }
                stand.addPassenger(player);
            }
            case CANNON -> useCannon(player, ship, pos, stand);
            default -> { }
        }
    }

    @EventHandler
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) {
            return;
        }
        var binding = moduleEntities.resolve(stand.getUniqueId());
        if (binding.isEmpty()) {
            return;
        }
        event.setCancelled(true);

        if (!(event instanceof EntityDamageByEntityEvent byEntity)
                || !(byEntity.getDamager() instanceof Player player)) {
            return; // Only a player punch removes a module
        }

        ModulePos pos = binding.get().pos();
        Optional<Ship> shipOpt = shipRegistry.find(binding.get().shipId());
        if (shipOpt.isEmpty()) {
            return;
        }
        Ship ship = shipOpt.get();

        if (ship.phase() == LifecyclePhase.FINALIZED) {
            player.sendMessage(Component.text(
                    "Modules are locked after finalization.", NamedTextColor.RED));
            return;
        }
        if (ship.phase() != LifecyclePhase.HULL_APPLIED) {
            return;
        }

        ModuleType removed = ship.modules().get(pos);
        if (removed == null) {
            return;
        }
        // RQCA-22: a removed cargo module drops its hold contents (conservation)
        Map<Integer, Map<String, Object>> hold = ship.cargo().get(pos);
        if (hold != null) {
            hold.values().forEach(itemMap -> {
                org.bukkit.inventory.ItemStack item = cargoService.deserializeItem(itemMap);
                if (item != null) {
                    stand.getWorld().dropItemNaturally(stand.getLocation(), item);
                }
            });
        }
        shipRegistry.removeModule(binding.get().shipId(), pos);
        stand.getWorld().dropItemNaturally(
                stand.getLocation(), moduleItem.create(removed));
        moduleEntities.despawn(binding.get().shipId(), pos);
        player.sendMessage(Component.text(
                "Removed " + moduleItem.displayName(removed)
                        + " from " + pos.encoded() + ".",
                NamedTextColor.GREEN));
    }
}
