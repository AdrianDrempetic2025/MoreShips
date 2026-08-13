package com.glooshy.ships.listener;

import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.ship.ShipTeardownService;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Listens for damage to the ArmorStand that represents a ship.
 *
 * <p>Always cancels damage — the plugin manages HP on the {@link Ship} record
 * directly, not via vanilla ArmorStand HP. Behavior depends on phase:
 *
 * <p><b>Teardownable phases (UNFINISHED, HULL_APPLIED):</b>
 * <ul>
 *   <li>Player damage → full teardown (drop core + hull, transition REMOVED,
 *       release binding, remove entity)</li>
 *   <li>Non-player damage → just cancelled (RQCA-21: inputs must be conserved
 *       via explicit teardown, not lost to creeper/fall/etc.)</li>
 * </ul>
 *
 * <p><b>FINALIZED phase:</b>
 * <ul>
 *   <li>Any damage → apply to ship HP via {@link ShipRegistry#applyDamage}</li>
 *   <li>Update entity custom name to show current/max HP</li>
 *   <li>When HP reaches 0 → transition to DESTROYED (deletes from registry),
 *       release binding, remove entity, notify attacker</li>
 * </ul>
 *
 * <p>V1 simplification: ALL damage sources apply to FINALIZED ships (arrows,
 * melee, explosions). Spec-faithful combat selectivity (normal-melee rejection,
 * ASSUMP-02/03) is a future slice.
 */
public final class ShipEntityBreakListener implements Listener {

    private final ShipCoreItem shipCoreItem;
    private final RuntimeBindingRegistry bindingRegistry;
    private final ShipRegistry shipRegistry;
    private final ShipTeardownService teardownService;

    public ShipEntityBreakListener(
            ShipCoreItem shipCoreItem,
            RuntimeBindingRegistry bindingRegistry,
            ShipRegistry shipRegistry,
            ShipTeardownService teardownService) {
        this.shipCoreItem = shipCoreItem;
        this.bindingRegistry = bindingRegistry;
        this.shipRegistry = shipRegistry;
        this.teardownService = teardownService;
    }

    @EventHandler
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) {
            return;
        }

        Optional<RuntimeBinding> binding = bindingRegistry.findByEntity(stand.getUniqueId());
        if (binding.isEmpty()) {
            return; // Not a custom ship
        }

        // Always cancel — plugin manages HP via the Ship record
        event.setCancelled(true);

        var shipId = binding.get().shipId();
        Optional<Ship> shipOpt = shipRegistry.find(shipId);
        if (shipOpt.isEmpty()) {
            bindingRegistry.release(shipId);
            return;
        }
        Ship ship = shipOpt.get();

        if (ShipTeardownService.isTeardownable(ship.phase())) {
            handleTeardownableDamage(event, stand, ship);
            return;
        }

        if (ship.phase() == LifecyclePhase.FINALIZED) {
            handleFinalizedDamage(event, stand, ship);
        }
        // DESTROYED ships: shouldn't reach here (entity removed on transition)
    }

    private void handleTeardownableDamage(EntityDamageEvent event, ArmorStand stand, Ship ship) {
        Player player = damagerAsPlayer(event);
        if (player == null) {
            return; // Non-player damage: just cancelled
        }

        // Drop inputs BEFORE mutating state
        stand.getWorld().dropItemNaturally(stand.getLocation(), shipCoreItem.create());

        Material hull = ship.hullMaterial();
        if (hull != null) {
            stand.getWorld().dropItemNaturally(stand.getLocation(), new ItemStack(hull));
        }

        teardownService.teardown(ship.identity());
        stand.remove();

        String recovery = hull != null
                ? "Recovered Ship Core + " + hull.name() + " from ship."
                : "Recovered Ship Core from unfinished ship.";
        player.sendMessage(Component.text(recovery, NamedTextColor.GREEN));
    }

    private void handleFinalizedDamage(EntityDamageEvent event, ArmorStand stand, Ship ship) {
        double damage = event.getFinalDamage();
        Ship after = shipRegistry.applyDamage(ship.identity(), damage);

        if (after.currentHp() <= 0) {
            // Ship destroyed
            Player attacker = damagerAsPlayer(event);
            String id = ship.identity().encoded();
            try {
                shipRegistry.transition(ship.identity(), LifecyclePhase.DESTROYED);
            } catch (IllegalStateException ignored) {
                // Race — best effort
            }
            bindingRegistry.release(ship.identity());
            stand.remove();
            if (attacker != null) {
                attacker.sendMessage(Component.text(
                        "Ship " + id + " destroyed.", NamedTextColor.RED));
            }
            return;
        }

        // Update entity name to show HP
        stand.customName(Component.text(
                "Ship " + shortId(ship.identity()) + " [" + after.currentHp()
                        + "/" + after.maxHp() + " HP]",
                NamedTextColor.AQUA));
    }

    private static @NotNull String shortId(com.glooshy.ships.identity.ShipIdentity id) {
        String encoded = id.encoded();
        int dash = encoded.indexOf('-');
        return dash > 0 ? encoded.substring(0, dash) : encoded;
    }

    @Nullable
    private static Player damagerAsPlayer(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        return byEntity.getDamager() instanceof Player p ? p : null;
    }
}
