package com.glooshy.ships.listener;

import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Installs modules on a HULL_APPLIED ship (RQCA-08).
 *
 * <p>Right-click the ship's ArmorStand with a module item in hand → the module
 * is consumed and fitted into the first free slot (BOW, STERN, PORT,
 * STARBOARD), rendered at that slot's equipment position on the entity.
 * Wrong-phase clicks get a player-facing refusal; a full ship refuses without
 * consuming the item.
 *
 * <p>Slot selection order is fixed so install behavior is predictable; use
 * {@code /moreships module move} to rearrange afterwards.
 */
public final class ModuleInstallListener implements Listener {

    private static final ModuleSlot[] SLOT_ORDER = {
            ModuleSlot.BOW, ModuleSlot.STERN, ModuleSlot.PORT, ModuleSlot.STARBOARD};

    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;
    private final ModuleItem moduleItem;

    public ModuleInstallListener(
            ShipRegistry shipRegistry,
            RuntimeBindingRegistry bindingRegistry,
            ModuleItem moduleItem) {
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
        this.moduleItem = moduleItem;
    }

    @EventHandler
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) {
            return;
        }

        Optional<RuntimeBinding> binding = bindingRegistry.findByEntity(stand.getUniqueId());
        if (binding.isEmpty()) {
            return; // Not a custom ship — vanilla armor stand behavior
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        Optional<ModuleType> heldModule = moduleItem.parse(inHand);
        if (heldModule.isEmpty()) {
            return; // Not a module item — hull/pilot listeners handle their cases
        }

        var shipId = binding.get().shipId();
        Optional<Ship> shipOpt = shipRegistry.find(shipId);
        if (shipOpt.isEmpty()) {
            return;
        }
        Ship ship = shipOpt.get();

        if (ship.phase() != LifecyclePhase.HULL_APPLIED) {
            player.sendMessage(Component.text(
                    phaseRefusal(ship.phase()), NamedTextColor.RED));
            return;
        }

        Optional<ModuleSlot> freeSlot = firstFreeSlot(ship);
        if (freeSlot.isEmpty()) {
            player.sendMessage(Component.text(
                    "All module slots are full. Remove one with "
                            + "/moreships module remove <slot> first.",
                    NamedTextColor.RED));
            return;
        }
        ModuleSlot slot = freeSlot.get();
        ModuleType type = heldModule.get();

        Ship updated = shipRegistry.installModule(shipId, type, slot);

        inHand.setAmount(inHand.getAmount() - 1);

        stand.getEquipment().setItem(slot.equipmentSlot(), moduleItem.create(type));

        player.sendMessage(Component.text(
                "Installed " + moduleItem.displayName(type) + " in slot " + slot.name()
                        + " (" + updated.modules().size() + "/"
                        + SLOT_ORDER.length + " slots used).",
                NamedTextColor.GREEN));
    }

    private static Optional<ModuleSlot> firstFreeSlot(Ship ship) {
        for (ModuleSlot slot : SLOT_ORDER) {
            if (!ship.modules().containsKey(slot)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    private static String phaseRefusal(LifecyclePhase phase) {
        return switch (phase) {
            case UNFINISHED -> "Apply a hull material before installing modules.";
            case FINALIZED -> "Ship is finalized — modules can only change before finalization.";
            default -> "Cannot install modules on a ship in phase " + phase + ".";
        };
    }
}
