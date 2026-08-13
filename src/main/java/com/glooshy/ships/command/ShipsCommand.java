package com.glooshy.ships.command;

import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.ship.ShipRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * /moreships command — admin surface.
 *
 * <p>V1 supports {@code give} (hand a ship core to the executing player) and
 * {@code info} (registry + binding statistics).
 */
public final class ShipsCommand implements CommandExecutor {

    private final ShipCoreItem shipCoreItem;
    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;

    public ShipsCommand(ShipCoreItem shipCoreItem,
                        ShipRegistry shipRegistry,
                        RuntimeBindingRegistry bindingRegistry) {
        this.shipCoreItem = shipCoreItem;
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
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
            case "help" -> sendHelp(sender);
            default -> sender.sendMessage(Component.text(
                    "Unknown subcommand: " + args[0], NamedTextColor.RED));
        }
        return true;
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("/moreships give", NamedTextColor.AQUA)
                .append(Component.text(" — receive a Ship Core", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/moreships info", NamedTextColor.AQUA)
                .append(Component.text(" — show live ship + binding counts", NamedTextColor.GRAY)));
    }
}
