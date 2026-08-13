package com.glooshy.ships.command;

import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.listener.ShipCorePlacementListener;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * /moreships command — admin surface with tab completion.
 *
 * <p>Subcommands: {@code give} (hand a ship core), {@code info} (counts),
 * {@code debug} (last-interact + main-hand diagnostics), {@code help}.
 */
public final class ShipsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("give", "info", "debug", "help");

    private final ShipCoreItem shipCoreItem;
    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;
    private final ShipCorePlacementListener placementListener;

    public ShipsCommand(ShipCoreItem shipCoreItem,
                        ShipRegistry shipRegistry,
                        RuntimeBindingRegistry bindingRegistry,
                        ShipCorePlacementListener placementListener) {
        this.shipCoreItem = shipCoreItem;
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
            case "give" -> handleGive(sender);
            case "info" -> handleInfo(sender);
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
        return List.of();
    }

    private void handleGive(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can receive items.", NamedTextColor.RED));
            return;
        }
        ItemStack core = shipCoreItem.create();
        player.getInventory().addItem(core);
        player.sendMessage(Component.text("Given a Ship Core.", NamedTextColor.GREEN));
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(Component.text(
                "Live ships: " + shipRegistry.size(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text(
                "Active bindings: " + bindingRegistry.activeCount(), NamedTextColor.AQUA));
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("/moreships give", NamedTextColor.AQUA)
                .append(Component.text(" — receive a Ship Core", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships info", NamedTextColor.AQUA)
                .append(Component.text(" — show live ship + binding counts", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships debug", NamedTextColor.AQUA)
                .append(Component.text(" — show last interact + main hand state", NamedTextColor.GRAY)));
    }
}
