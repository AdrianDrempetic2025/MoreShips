package com.glooshy.ships.listener;

import com.glooshy.ships.hull.HullValidationResult;
import com.glooshy.ships.hull.HullValidator;
import com.glooshy.ships.runtime.ShipEntityResolver;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for right-click on a ship's ArmorStand entity with a candidate hull
 * block in hand. Validates the material; on accept, consumes one item, stores
 * hull on the ship, transitions to HULL_APPLIED, and updates the entity to wear
 * the hull block as a helmet. On reject, sends a player-facing error and does
 * NOT consume the item.
 *
 * <p>Custom-ship ArmorStands always cancel the underlying interact event so
 * vanilla armor-stand GUI behavior never triggers. Non-bound armor stands are
 * untouched.
 */
public final class HullApplicationListener implements Listener {

    private final ShipRegistry shipRegistry;
    private final ShipEntityResolver resolver;
    private final HullValidator hullValidator;
    private final com.glooshy.ships.item.ModuleItem moduleItem;
    private final com.glooshy.ships.item.ShipCoreItem coreItem;

    public HullApplicationListener(
            ShipRegistry shipRegistry,
            ShipEntityResolver resolver,
            HullValidator hullValidator,
            com.glooshy.ships.item.ModuleItem moduleItem,
            com.glooshy.ships.item.ShipCoreItem coreItem) {
        this.shipRegistry = shipRegistry;
        this.resolver = resolver;
        this.hullValidator = hullValidator;
        this.moduleItem = moduleItem;
        this.coreItem = coreItem;
    }

    @EventHandler
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        var clicked = event.getRightClicked();
        Optional<com.glooshy.ships.identity.ShipIdentity> shipIdOpt = resolver.shipIdOf(clicked);
        if (shipIdOpt.isEmpty()) {
            return; // Not a custom ship — vanilla armor stand behavior
        }
        Optional<ArmorStand> standOpt = resolver.shipStandOf(clicked);
        if (standOpt.isEmpty()) {
            return;
        }
        ArmorStand stand = standOpt.get();

        // Custom ship entity — cancel vanilla armor stand GUI/equip behavior
        event.setCancelled(true);

        Player player = event.getPlayer();
        var shipId = shipIdOpt.get();
        Optional<Ship> shipOpt = shipRegistry.find(shipId);
        if (shipOpt.isEmpty()) {
            return;
        }
        Ship ship = shipOpt.get();

        if (ship.phase() != com.glooshy.ships.ship.LifecyclePhase.UNFINISHED) {
            // Right-click on a HULL_APPLIED or later ship is a no-op for hull application.
            // (Future slice: opens the module configuration UI for HULL_APPLIED ships.)
            return;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        Material candidate = inHand.getType();
        if (candidate.isAir()) {
            player.sendMessage(Component.text(
                    "Right-click with a block to apply hull material.",
                    NamedTextColor.GRAY));
            return;
        }
        // Modules and ship cores are never valid hull material (they are
        // block-shaped items and would otherwise be consumed as one)
        if (moduleItem.parse(inHand).isPresent() || coreItem.parseSize(inHand) != null) {
            player.sendMessage(Component.text(
                    "Modules and ship cores cannot be used as hull material.",
                    NamedTextColor.RED));
            return;
        }

        HullValidationResult result = hullValidator.validate(candidate);
        if (!result.isValid()) {
            player.sendMessage(Component.text(
                    result.errorMessage(), NamedTextColor.RED));
            return;
        }

        // Apply: store hull + transition phase atomically
        shipRegistry.applyHull(shipId, candidate);

        // Consume one item from hand
        inHand.setAmount(inHand.getAmount() - 1);

        // Update entity visual: wear hull block as helmet (vanilla block rendering)
        stand.getEquipment().setHelmet(new ItemStack(candidate));

        player.sendMessage(Component.text(
                "Applied hull material: " + candidate.name()
                        + " (hardness " + candidate.getHardness() + ")",
                NamedTextColor.GREEN));
    }
}
