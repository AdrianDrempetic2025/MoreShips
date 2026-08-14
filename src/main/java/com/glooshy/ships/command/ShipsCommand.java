package com.glooshy.ships.command;

import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.listener.ShipCorePlacementListener;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.ModuleSlot;
import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * /moreships command — admin surface with tab completion.
 *
 * <p>Subcommands: {@code give [module]}, {@code info}, {@code finalize},
 * {@code module list|remove|move}, {@code debug}, {@code help}.
 */
public final class ShipsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("give", "info", "finalize", "module", "debug", "help");

    private static final List<String> MODULE_SUBCOMMANDS = List.of("list", "remove", "move");

    private static final int TARGET_RAYTRACE_DISTANCE = 5;

    private final ShipCoreItem shipCoreItem;
    private final ModuleItem moduleItem;
    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;
    private final ShipCorePlacementListener placementListener;

    public ShipsCommand(ShipCoreItem shipCoreItem,
                        ModuleItem moduleItem,
                        ShipRegistry shipRegistry,
                        RuntimeBindingRegistry bindingRegistry,
                        ShipCorePlacementListener placementListener) {
        this.shipCoreItem = shipCoreItem;
        this.moduleItem = moduleItem;
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
        this.placementListener = placementListener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> handleGive(sender, args);
            case "info" -> handleInfo(sender);
            case "finalize" -> handleFinalize(sender);
            case "module" -> handleModule(sender, args);
            case "debug" -> handleDebug(sender);
            case "help" -> sendHelp(sender);
            default -> sender.sendMessage(Component.text(
                    "Unknown subcommand: " + args[0], NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    matches.add(sub);
                }
            }
            return matches;
        }
        if (args[0].equalsIgnoreCase("give") && args.length == 2) {
            String prefix = args[1].toLowerCase();
            List<String> matches = new ArrayList<>(List.of("core"));
            for (ModuleType type : ModuleType.values()) {
                matches.add(type.name().toLowerCase());
            }
            matches.removeIf(m -> !m.startsWith(prefix));
            return matches;
        }
        if (args[0].equalsIgnoreCase("module")) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase();
                List<String> matches = new ArrayList<>();
                for (String sub : MODULE_SUBCOMMANDS) {
                    if (sub.startsWith(prefix)) {
                        matches.add(sub);
                    }
                }
                return matches;
            }
            if ((args[1].equalsIgnoreCase("remove") && args.length == 3)
                    || (args[1].equalsIgnoreCase("move") && (args.length == 3 || args.length == 4))) {
                String prefix = args[args.length - 1].toLowerCase();
                List<String> matches = new ArrayList<>();
                for (ModuleSlot slot : ModuleSlot.values()) {
                    if (slot.name().toLowerCase().startsWith(prefix)) {
                        matches.add(slot.name().toLowerCase());
                    }
                }
                return matches;
            }
        }
        return List.of();
    }

    private void handleGive(CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can receive items.", NamedTextColor.RED));
            return;
        }
        if (args.length >= 2 && !args[1].equalsIgnoreCase("core")) {
            ModuleType type;
            try {
                type = ModuleType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Component.text(
                        "Unknown module type: " + args[1]
                                + " (helmet, seat, cargo, cannon, or core)", NamedTextColor.RED));
                return;
            }
            player.getInventory().addItem(moduleItem.create(type));
            player.sendMessage(Component.text(
                    "Given a " + moduleItem.displayName(type) + ".", NamedTextColor.GREEN));
            return;
        }
        ItemStack core = shipCoreItem.create();
        player.getInventory().addItem(core);
        player.sendMessage(Component.text("Given a Ship Core.", NamedTextColor.GREEN));
    }

    private void handleModule(CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can manage modules.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /moreships module <list|remove|move>", NamedTextColor.RED));
            return;
        }

        Entity target = player.getTargetEntity(TARGET_RAYTRACE_DISTANCE);
        Optional<RuntimeBinding> binding = target == null
                ? Optional.empty()
                : bindingRegistry.findByEntity(target.getUniqueId());
        if (binding.isEmpty()) {
            player.sendMessage(Component.text(
                    "Look at a ship within " + TARGET_RAYTRACE_DISTANCE
                            + " blocks first.", NamedTextColor.RED));
            return;
        }
        var shipId = binding.get().shipId();
        Optional<Ship> shipOpt = shipRegistry.find(shipId);
        if (shipOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    "Ship no longer exists in registry.", NamedTextColor.RED));
            return;
        }
        Ship ship = shipOpt.get();

        switch (args[1].toLowerCase()) {
            case "list" -> {
                player.sendMessage(Component.text(
                        "Modules on ship " + shortId(ship.identity()) + ":",
                        NamedTextColor.AQUA));
                if (ship.modules().isEmpty()) {
                    player.sendMessage(Component.text(
                            "  (none — right-click the ship with a module item to install)",
                            NamedTextColor.GRAY));
                }
                for (ModuleSlot slot : ModuleSlot.values()) {
                    ModuleType type = ship.modules().get(slot);
                    String text = type == null
                            ? "  " + slot.name().toLowerCase() + ": —"
                            : "  " + slot.name().toLowerCase() + ": " + moduleItem.displayName(type);
                    player.sendMessage(Component.text(text,
                            type == null ? NamedTextColor.GRAY : NamedTextColor.GREEN));
                }
            }
            case "remove" -> {
                ModuleSlot slot = parseSlot(sender, args, 2);
                if (slot == null) {
                    return;
                }
                ModuleType removed = ship.modules().get(slot);
                if (removed == null) {
                    player.sendMessage(Component.text(
                            "Slot " + slot.name().toLowerCase() + " is empty.", NamedTextColor.RED));
                    return;
                }
                shipRegistry.removeModule(shipId, slot);
                if (target instanceof ArmorStand stand) {
                    stand.getEquipment().setItem(slot.equipmentSlot(), null);
                    stand.getWorld().dropItemNaturally(
                            stand.getLocation(), moduleItem.create(removed));
                }
                player.sendMessage(Component.text(
                        "Removed " + moduleItem.displayName(removed) + " from slot "
                                + slot.name().toLowerCase() + " and dropped it.",
                        NamedTextColor.GREEN));
            }
            case "move" -> {
                ModuleSlot from = parseSlot(sender, args, 2);
                if (from == null) {
                    return;
                }
                ModuleSlot to = parseSlot(sender, args, 3);
                if (to == null) {
                    return;
                }
                ModuleType moved = ship.modules().get(from);
                if (moved == null) {
                    player.sendMessage(Component.text(
                            "Source slot " + from.name().toLowerCase() + " is empty.",
                            NamedTextColor.RED));
                    return;
                }
                try {
                    shipRegistry.moveModule(shipId, from, to);
                } catch (IllegalStateException e) {
                    player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                    return;
                }
                if (target instanceof ArmorStand stand) {
                    stand.getEquipment().setItem(from.equipmentSlot(), null);
                    stand.getEquipment().setItem(to.equipmentSlot(), moduleItem.create(moved));
                }
                player.sendMessage(Component.text(
                        "Moved " + moduleItem.displayName(moved) + ": "
                                + from.name().toLowerCase() + " → " + to.name().toLowerCase(),
                        NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text(
                    "Unknown module subcommand: " + args[1], NamedTextColor.RED));
        }
    }

    private static @Nullable ModuleSlot parseSlot(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(Component.text(
                    "Usage: /moreships module remove|move <BOW|STERN|PORT|STARBOARD>",
                    NamedTextColor.RED));
            return null;
        }
        try {
            return ModuleSlot.valueOf(args[index].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(
                    "Unknown slot: " + args[index]
                            + " (BOW, STERN, PORT, STARBOARD)", NamedTextColor.RED));
            return null;
        }
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(Component.text(
                "Live ships: " + shipRegistry.size(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text(
                "Active bindings: " + bindingRegistry.activeCount(), NamedTextColor.AQUA));
    }

    private void handleFinalize(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can finalize ships.", NamedTextColor.RED));
            return;
        }

        Entity target = player.getTargetEntity(TARGET_RAYTRACE_DISTANCE);
        if (target == null) {
            player.sendMessage(Component.text(
                    "Look at a ship within " + TARGET_RAYTRACE_DISTANCE
                            + " blocks and run /moreships finalize again.",
                    NamedTextColor.RED));
            return;
        }

        Optional<RuntimeBinding> binding = bindingRegistry.findByEntity(target.getUniqueId());
        if (binding.isEmpty()) {
            player.sendMessage(Component.text("That entity is not a ship.", NamedTextColor.RED));
            return;
        }

        var shipId = binding.get().shipId();
        Optional<Ship> shipOpt = shipRegistry.find(shipId);
        if (shipOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    "Ship no longer exists in registry.", NamedTextColor.RED));
            return;
        }
        Ship ship = shipOpt.get();

        LifecyclePhase phase = ship.phase();
        if (phase != LifecyclePhase.HULL_APPLIED) {
            player.sendMessage(Component.text(
                    refusalMessage(phase), NamedTextColor.RED));
            return;
        }

        try {
            Ship finalized = shipRegistry.transition(shipId, LifecyclePhase.FINALIZED);
            player.sendMessage(Component.text(
                    "Finalized ship " + finalized.identity().encoded()
                            + ". This is irreversible.",
                    NamedTextColor.GREEN));
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(
                    "Cannot finalize: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private static @NotNull String refusalMessage(@Nullable LifecyclePhase phase) {
        if (phase == null) {
            return "Ship has no phase.";
        }
        return switch (phase) {
            case UNFINISHED -> "Ship is unfinished. Apply a hull material first.";
            case FINALIZED -> "Ship is already finalized.";
            case DESTROYED -> "Ship is destroyed.";
            case REMOVED -> "Ship is removed.";
            default -> "Ship cannot be finalized in phase " + phase + ".";
        };
    }

    private void handleDebug(CommandSender sender) {
        sender.sendMessage(Component.text("Last interact: ", NamedTextColor.AQUA)
                .append(Component.text(placementListener.lastInteractDescription(), NamedTextColor.YELLOW)));

        if (sender instanceof Player player) {
            ItemStack inMainHand = player.getInventory().getItemInMainHand();
            sender.sendMessage(Component.text("Main hand: ", NamedTextColor.AQUA)
                    .append(Component.text(shipCoreItem.diagnose(inMainHand), NamedTextColor.YELLOW)));
        }

        sender.sendMessage(Component.text(
                "If last interact shows action=RIGHT_CLICK_AIR with target=water but no ship spawned, listener fired but isShipCore was false.",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "If last interact still shows nothing after a right-click, the event did not reach the plugin.",
                NamedTextColor.GRAY));
    }

    private static String shortId(com.glooshy.ships.identity.ShipIdentity id) {
        String encoded = id.encoded();
        int dash = encoded.indexOf('-');
        return dash > 0 ? encoded.substring(0, dash) : encoded;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("/moreships give [type]", NamedTextColor.AQUA)
                .append(Component.text(" — receive a Ship Core or module (helm/seat/cargo/cannon)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships info", NamedTextColor.AQUA)
                .append(Component.text(" — show live ship + binding counts", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships finalize", NamedTextColor.AQUA)
                .append(Component.text(" — finalize the ship you are looking at (HULL_APPLIED only)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships module list", NamedTextColor.AQUA)
                .append(Component.text(" — list modules on the ship you are looking at", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships module remove <slot>", NamedTextColor.AQUA)
                .append(Component.text(" — remove a module (bow/stern/port/starboard), drops the item", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships module move <from> <to>", NamedTextColor.AQUA)
                .append(Component.text(" — move a module to another slot", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships debug", NamedTextColor.AQUA)
                .append(Component.text(" — show last interact + main hand state", NamedTextColor.GRAY)));
    }
}
