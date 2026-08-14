package com.glooshy.ships.listener;

import com.glooshy.ships.cargo.CargoService;
import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.runtime.ModuleEntityManager;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.ModuleSlot;
import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
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

    public ModuleEntityListener(ModuleEntityManager moduleEntities,
                                ShipRegistry shipRegistry,
                                CargoService cargoService,
                                ModuleItem moduleItem) {
        this.moduleEntities = moduleEntities;
        this.shipRegistry = shipRegistry;
        this.cargoService = cargoService;
        this.moduleItem = moduleItem;
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
        ModuleSlot slot = binding.get().slot();
        Optional<Ship> shipOpt = shipRegistry.find(binding.get().shipId());
        if (shipOpt.isEmpty()) {
            return;
        }
        Ship ship = shipOpt.get();
        ModuleType type = ship.modules().get(slot);
        if (type == null) {
            return;
        }

        switch (type) {
            case CARGO -> cargoService.open(player, ship);
            case SEAT -> {
                if (!stand.getPassengers().isEmpty()) {
                    player.sendMessage(Component.text(
                            "This seat is occupied.", NamedTextColor.RED));
                    return;
                }
                stand.addPassenger(player);
            }
            case CANNON -> player.sendMessage(Component.text(
                    "Cannons fire in the next slice — this cannon is decorative for now.",
                    NamedTextColor.GRAY));
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

        ModuleSlot slot = binding.get().slot();
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

        ModuleType removed = ship.modules().get(slot);
        if (removed == null) {
            return;
        }
        shipRegistry.removeModule(binding.get().shipId(), slot);
        stand.getWorld().dropItemNaturally(
                stand.getLocation(), moduleItem.create(removed));
        moduleEntities.despawn(binding.get().shipId(), slot);
        player.sendMessage(Component.text(
                "Removed " + moduleItem.displayName(removed)
                        + " from slot " + slot.name().toLowerCase() + ".",
                NamedTextColor.GREEN));
    }
}
