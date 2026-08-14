package com.glooshy.ships.listener;

import com.glooshy.ships.cargo.CargoService;
import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.runtime.ModuleEntityManager;
import com.glooshy.ships.runtime.ShipEntityResolver;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.ModulePos;
import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.ui.ConfigUiLayout;
import com.glooshy.ships.ui.ShipConfigUi;
import com.glooshy.ships.ui.ShipConfigUiHolder;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The ship configuration surface (spec L1 §14, RQCA-08/09).
 *
 * <p><b>Opening:</b> right-click a HULL_APPLIED ship with anything in hand →
 * configuration UI. (UNFINISHED ships take hull material; FINALIZED ships are
 * boarded — the configuration surface is closed after finalization, RQCA-02.)
 *
 * <p><b>Slots:</b> modules behave like items — click a slot with a module on
 * the cursor to install, click a fitted module with an empty cursor to remove,
 * pick up + place to move. Punching the module entity on the ship also works.
 *
 * <p><b>Finalize:</b> two clicks within 5 s (confirmation step).
 */
public final class ShipConfigUiListener implements Listener {

    private final ShipRegistry shipRegistry;
    private final ShipEntityResolver resolver;
    private final ModuleItem moduleItem;
    private final ModuleEntityManager moduleEntities;
    private final CargoService cargoService;
    private final ShipConfigUi ui;

    public ShipConfigUiListener(ShipRegistry shipRegistry,
                                ShipEntityResolver resolver,
                                ModuleItem moduleItem,
                                ModuleEntityManager moduleEntities,
                                CargoService cargoService) {
        this.shipRegistry = shipRegistry;
        this.resolver = resolver;
        this.moduleItem = moduleItem;
        this.moduleEntities = moduleEntities;
        this.cargoService = cargoService;
        this.ui = new ShipConfigUi(moduleItem);
    }

    @EventHandler
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        Optional<com.glooshy.ships.identity.ShipIdentity> shipIdOpt =
                resolver.shipIdOf(event.getRightClicked());
        if (shipIdOpt.isEmpty()) {
            return;
        }
        Optional<Ship> shipOpt = shipRegistry.find(shipIdOpt.get());
        if (shipOpt.isEmpty()) {
            return;
        }
        Ship ship = shipOpt.get();
        if (ship.phase() != LifecyclePhase.HULL_APPLIED) {
            return; // UNFINISHED: hull listener; FINALIZED: pilot listener
        }
        event.setCancelled(true);
        ui.open(event.getPlayer(), ship, new ShipConfigUiHolder(ship.identity()));
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShipConfigUiHolder holder)) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            // Click in the player's own inventory: allow normal pickup, but no
            // shift-clicking items into the config UI.
            if (event.getClick().isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Optional<Ship> shipOpt = shipRegistry.find(holder.shipId());
        if (shipOpt.isEmpty()) {
            player.closeInventory();
            return;
        }
        Ship ship = shipOpt.get();
        if (ship.phase() != LifecyclePhase.HULL_APPLIED) {
            player.closeInventory();
            return;
        }

        int index = event.getSlot();
        ModulePos pos = ConfigUiLayout.posAt(ship.size(), index);
        if (pos != null) {
            handleModuleSlotClick(event, player, ship, holder, pos);
            return;
        }
        if (index == ConfigUiLayout.finalizeIndex(ship.size())) {
            handleFinalizeClick(event, player, ship, holder);
        }
        // Info + filler slots: cancelled, nothing to do
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShipConfigUiHolder) {
            event.setCancelled(true);
        }
    }

    private void handleModuleSlotClick(InventoryClickEvent event, Player player,
                                       Ship ship, ShipConfigUiHolder holder, ModulePos pos) {
        ItemStack cursor = event.getCursor();
        ModuleType held = cursor == null ? null : moduleItem.parse(cursor).orElse(null);
        ModuleType fitted = ship.modules().get(pos);

        if (held != null && fitted == null) {
            // Place: install the module from the cursor
            try {
                Ship updated = shipRegistry.installModule(ship.identity(), held, pos);
                moduleEntities.spawn(updated, pos);
                // Consume exactly ONE module; the rest of the stack stays on
                // the cursor (the old setCursor(null) ate the whole stack)
                if (cursor.getAmount() > 1) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    event.getView().setCursor(cursor);
                } else {
                    event.getView().setCursor(null);
                }
                event.getInventory().setItem(
                        ConfigUiLayout.indexOf(ship.size(), pos), moduleItem.create(held));
                player.sendMessage(Component.text(
                        "Installed " + moduleItem.displayName(held) + " at "
                                + pos.encoded() + ".",
                        NamedTextColor.GREEN));
            } catch (IllegalStateException e) {
                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            }
            return;
        }

        if (held == null && fitted != null) {
            // Pick up: remove the module onto the cursor
            dropCargoHold(player, ship, pos);
            try {
                shipRegistry.removeModule(ship.identity(), pos);
                moduleEntities.despawn(ship.identity(), pos);
                event.getView().setCursor(moduleItem.create(fitted));
                event.getInventory().setItem(
                        ConfigUiLayout.indexOf(ship.size(), pos), ui.slotIcon(
                                shipRegistry.find(ship.identity()).orElse(ship), pos));
                player.sendMessage(Component.text(
                        "Removed " + moduleItem.displayName(fitted) + " from "
                                + pos.encoded() + ".",
                        NamedTextColor.GREEN));
            } catch (IllegalStateException e) {
                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            }
            return;
        }

        if (held != null && fitted != null) {
            player.sendMessage(Component.text(
                    "Position " + pos.encoded() + " is occupied — remove the "
                            + moduleItem.displayName(fitted) + " first.",
                    NamedTextColor.RED));
        }
        // Empty position, empty cursor: nothing to do
    }

    private void handleFinalizeClick(InventoryClickEvent event, Player player,
                                     Ship ship, ShipConfigUiHolder holder) {
        long now = System.currentTimeMillis();
        if (!holder.confirmFinalize(now)) {
            holder.armFinalize(now);
            event.getInventory().setItem(
                    ConfigUiLayout.finalizeIndex(ship.size()), ui.finalizeButton(true));
            player.sendMessage(Component.text(
                    "Click again within 5s to finalize. This is IRREVERSIBLE.",
                    NamedTextColor.YELLOW));
            return;
        }
        try {
            shipRegistry.transition(ship.identity(), LifecyclePhase.FINALIZED);
            player.closeInventory();
            player.sendMessage(Component.text(
                    "Ship finalized. Right-click to board and pilot it.",
                    NamedTextColor.GREEN));
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    /** RQCA-22: removing a cargo module returns its hold contents. */
    private void dropCargoHold(Player player, Ship ship, ModulePos pos) {
        Map<Integer, Map<String, Object>> hold = ship.cargo().get(pos);
        if (hold == null) {
            return;
        }
        hold.values().forEach(itemMap -> {
            ItemStack item = cargoService.deserializeItem(itemMap);
            if (item != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        });
    }
}
