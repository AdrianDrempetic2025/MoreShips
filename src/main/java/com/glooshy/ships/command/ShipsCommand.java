package com.glooshy.ships.command;

import com.glooshy.ships.cargo.CargoService;
import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.listener.ShipCorePlacementListener;
import com.glooshy.ships.runtime.ModuleEntityManager;
import com.glooshy.ships.visual.CustomModelVisualManager;
import com.glooshy.ships.runtime.ShipEntityResolver;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.ModulePos;
import com.glooshy.ships.ship.ShipSize;
import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.ArrayList;
import java.util.Map;
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
            List.of("give", "info", "finalize", "module", "cargo", "debug", "reload", "help");

    private static final List<String> MODULE_SUBCOMMANDS = List.of("list", "remove", "move");

    private static final int TARGET_RAYTRACE_DISTANCE = 5;

    private final ShipCoreItem shipCoreItem;
    private final ModuleItem moduleItem;
    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;
    private final ShipCorePlacementListener placementListener;
    private final CargoService cargoService;
    private final ModuleEntityManager moduleEntities;
    private final ShipEntityResolver resolver;
    private final CustomModelVisualManager modelVisuals;
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public ShipsCommand(ShipCoreItem shipCoreItem,
                        ModuleItem moduleItem,
                        ShipRegistry shipRegistry,
                        RuntimeBindingRegistry bindingRegistry,
                        ShipCorePlacementListener placementListener,
                        CargoService cargoService,
                        ModuleEntityManager moduleEntities,
                        ShipEntityResolver resolver,
                        CustomModelVisualManager modelVisuals,
                        org.bukkit.plugin.java.JavaPlugin plugin) {
        this.shipCoreItem = shipCoreItem;
        this.moduleItem = moduleItem;
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
        this.placementListener = placementListener;
        this.cargoService = cargoService;
        this.moduleEntities = moduleEntities;
        this.resolver = resolver;
        this.modelVisuals = modelVisuals;
        this.plugin = plugin;
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
            case "cargo" -> handleCargo(sender);
            case "debug" -> handleDebug(sender);
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(Component.text(
                        "MoreShips config reloaded. Some values (recipes, item "
                                + "materials) need a full restart.", NamedTextColor.GREEN));
            }
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
            List<String> matches = new ArrayList<>(List.of("small", "medium", "large"));
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
                // Admin codes: r<row>c<col> for the looked-at ship's size
                Entity target = sender instanceof Player p ? p.getTargetEntity(TARGET_RAYTRACE_DISTANCE) : null;
                return resolver.shipIdOf(target == null ? null : target)
                        .flatMap(shipRegistry::find)
                        .map(sh -> sh.size().positions().stream()
                                .map(ModulePos::encoded)
                                .filter(code -> code.startsWith(args[args.length - 1].toLowerCase()))
                                .toList())
                        .orElse(List.of());
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
            // Module?
            ModuleType type;
            try {
                type = ModuleType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException moduleEx) {
                // Core size?
                ShipSize size;
                try {
                    size = ShipSize.valueOf(args[1].toUpperCase());
                } catch (IllegalArgumentException sizeEx) {
                    sender.sendMessage(Component.text(
                            "Unknown type: " + args[1]
                                    + " (small, medium, large, seat, cargo, cannon)",
                            NamedTextColor.RED));
                    return;
                }
                player.getInventory().addItem(shipCoreItem.create(size));
                player.sendMessage(Component.text(
                        "Given a " + shipCoreItem.displayName(size) + ".", NamedTextColor.GREEN));
                return;
            }
            player.getInventory().addItem(moduleItem.create(type));
            player.sendMessage(Component.text(
                    "Given a " + moduleItem.displayName(type) + ".", NamedTextColor.GREEN));
            return;
        }
        ItemStack core = shipCoreItem.create(ShipSize.SMALL);
        player.getInventory().addItem(core);
        player.sendMessage(Component.text("Given a Small Ship Core.", NamedTextColor.GREEN));
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
        Optional<com.glooshy.ships.identity.ShipIdentity> shipIdOpt = target == null
                ? Optional.empty()
                : resolver.shipIdOf(target);
        if (shipIdOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    "Look at a ship within " + TARGET_RAYTRACE_DISTANCE
                            + " blocks first.", NamedTextColor.RED));
            return;
        }
        var shipId = shipIdOpt.get();
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
                player.sendMessage(Component.text(
                        "  Size: " + ship.size() + " (" + ship.size().capacity() + " slots)",
                        NamedTextColor.GRAY));
                if (ship.modules().isEmpty()) {
                    player.sendMessage(Component.text(
                            "  (none — right-click the ship to open the configuration UI)",
                            NamedTextColor.GRAY));
                }
                for (ModulePos pos : ship.size().positions()) {
                    ModuleType type = ship.modules().get(pos);
                    String text = type == null
                            ? "  " + pos.encoded() + ": —"
                            : "  " + pos.encoded() + ": " + moduleItem.displayName(type);
                    player.sendMessage(Component.text(text,
                            type == null ? NamedTextColor.GRAY : NamedTextColor.GREEN));
                }
            }
            case "remove" -> {
                ModulePos pos = parsePos(sender, args, 2);
                if (pos == null) {
                    return;
                }
                ModuleType removed = ship.modules().get(pos);
                if (removed == null) {
                    player.sendMessage(Component.text(
                            "Position " + pos.encoded() + " is empty.", NamedTextColor.RED));
                    return;
                }
                // RQCA-22: a removed cargo module drops its hold contents
                Map<Integer, Map<String, Object>> hold = ship.cargo().get(pos);
                Optional<ArmorStand> standOpt = resolver.shipStandOf(target);
                if (hold != null) {
                    standOpt.ifPresent(stand -> hold.values().forEach(itemMap -> {
                        org.bukkit.inventory.ItemStack item = cargoService.deserializeItem(itemMap);
                        if (item != null) {
                            stand.getWorld().dropItemNaturally(stand.getLocation(), item);
                        }
                    }));
                }
                Optional<ArmorStand> dropAt = resolver.shipStandOf(target);
                shipRegistry.removeModule(shipId, pos);
                moduleEntities.despawn(shipId, pos);
                dropAt.ifPresent(stand -> stand.getWorld().dropItemNaturally(
                        stand.getLocation(), moduleItem.create(removed)));
                player.sendMessage(Component.text(
                        "Removed " + moduleItem.displayName(removed) + " from "
                                + pos.encoded() + " and dropped it.",
                        NamedTextColor.GREEN));
            }
            case "move" -> {
                ModulePos from = parsePos(sender, args, 2);
                if (from == null) {
                    return;
                }
                ModulePos to = parsePos(sender, args, 3);
                if (to == null) {
                    return;
                }
                ModuleType moved = ship.modules().get(from);
                if (moved == null) {
                    player.sendMessage(Component.text(
                            "Source position " + from.encoded() + " is empty.",
                            NamedTextColor.RED));
                    return;
                }
                Ship updatedShip;
                try {
                    updatedShip = shipRegistry.moveModule(shipId, from, to);
                } catch (IllegalStateException e) {
                    player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                    return;
                }
                moduleEntities.despawn(shipId, from);
                moduleEntities.spawn(updatedShip, to);
                player.sendMessage(Component.text(
                        "Moved " + moduleItem.displayName(moved) + ": "
                                + from.encoded() + " → " + to.encoded(),
                        NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text(
                    "Unknown module subcommand: " + args[1], NamedTextColor.RED));
        }
    }

    private static @Nullable ModulePos parsePos(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(Component.text(
                    "Usage: /moreships module remove|move <r<row>c<col>>",
                    NamedTextColor.RED));
            return null;
        }
        ModulePos pos = ModulePos.decode(args[index].toLowerCase());
        if (pos == null) {
            sender.sendMessage(Component.text(
                    "Unknown position: " + args[index] + " (format: r2c0)",
                    NamedTextColor.RED));
        }
        return pos;
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

        Optional<com.glooshy.ships.identity.ShipIdentity> shipIdOpt =
                resolver.shipIdOf(target);
        if (shipIdOpt.isEmpty()) {
            player.sendMessage(Component.text("That entity is not a ship.", NamedTextColor.RED));
            return;
        }

        var shipId = shipIdOpt.get();
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
            var coreSize = shipCoreItem.parseSize(inMainHand);
            sender.sendMessage(Component.text("Main hand: ", NamedTextColor.AQUA)
                    .append(Component.text(coreSize == null ? "not a ship core"
                            : coreSize.name() + " core", NamedTextColor.YELLOW)));
        }

        sender.sendMessage(Component.text(
                "If last interact shows action=RIGHT_CLICK_AIR with target=water but no ship spawned, listener fired but isShipCore was false.",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "If last interact still shows nothing after a right-click, the event did not reach the plugin.",
                NamedTextColor.GRAY));

        if (sender instanceof Player dbgPlayer) {
            Entity dbgTarget = dbgPlayer.getTargetEntity(TARGET_RAYTRACE_DISTANCE);
            if (dbgTarget != null) {
                resolver.shipIdOf(dbgTarget).ifPresent(sid ->
                        sender.sendMessage(Component.text("Visuals: ", NamedTextColor.AQUA)
                                .append(Component.text(modelVisuals.debugLine(sid),
                                        NamedTextColor.YELLOW))));
            }
        }
    }

    private void handleCargo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open cargo.", NamedTextColor.RED));
            return;
        }

        Entity target = player.getTargetEntity(TARGET_RAYTRACE_DISTANCE);
        Optional<com.glooshy.ships.identity.ShipIdentity> shipIdOpt = target == null
                ? Optional.empty()
                : resolver.shipIdOf(target);
        if (shipIdOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    "Look at a ship within " + TARGET_RAYTRACE_DISTANCE
                            + " blocks first.", NamedTextColor.RED));
            return;
        }
        Optional<Ship> shipOpt = shipRegistry.find(shipIdOpt.get());
        if (shipOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    "Ship no longer exists in registry.", NamedTextColor.RED));
            return;
        }
        Ship ship = shipOpt.get();
        if (ship.phase() != LifecyclePhase.HULL_APPLIED && ship.phase() != LifecyclePhase.FINALIZED) {
            player.sendMessage(Component.text(
                    "Cargo is available on hull-applied and finalized ships only.",
                    NamedTextColor.RED));
            return;
        }
        // Admin convenience: opens the first cargo module's hold;
        // right-clicking a specific cargo module opens that one.
        ModulePos firstCargo = ship.modules().entrySet().stream()
                .filter(e -> e.getValue() == ModuleType.CARGO)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (firstCargo == null) {
            player.sendMessage(Component.text(
                    "This ship has no cargo module fitted.", NamedTextColor.RED));
            return;
        }
        cargoService.open(player, ship, firstCargo);
    }

    private static String shortId(com.glooshy.ships.identity.ShipIdentity id) {
        String encoded = id.encoded();
        int dash = encoded.indexOf('-');
        return dash > 0 ? encoded.substring(0, dash) : encoded;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("/moreships give [type]", NamedTextColor.AQUA)
                .append(Component.text(" — receive a Ship Core or module (seat/cargo/cannon)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships info", NamedTextColor.AQUA)
                .append(Component.text(" — show live ship + binding counts", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships finalize", NamedTextColor.AQUA)
                .append(Component.text(" — finalize the ship you are looking at (HULL_APPLIED only)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships module list", NamedTextColor.AQUA)
                .append(Component.text(" — list modules on the ship you are looking at", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships module remove <slot>", NamedTextColor.AQUA)
                .append(Component.text(" — admin: remove a module, drops the item (punching the module works too)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships module move <from> <to>", NamedTextColor.AQUA)
                .append(Component.text(" — move a module to another slot", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships cargo", NamedTextColor.AQUA)
                .append(Component.text(" — open the cargo hold of the ship you are looking at (needs a Cargo Module)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships debug", NamedTextColor.AQUA)
                .append(Component.text(" — show last interact + main hand state", NamedTextColor.GRAY)));
    }
}
