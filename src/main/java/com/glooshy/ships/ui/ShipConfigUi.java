package com.glooshy.ships.ui;

import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.ship.ModuleSlot;
import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.Ship;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds and refreshes the ship configuration UI (spec L1 §14): top-down
 * ship representation, module slots, statistics, finalize button.
 */
public final class ShipConfigUi {

    private final ModuleItem moduleItem;

    public ShipConfigUi(ModuleItem moduleItem) {
        this.moduleItem = moduleItem;
    }

    public Inventory open(Player player, Ship ship, ShipConfigUiHolder holder) {
        Inventory inventory = player.getServer().createInventory(
                holder, ConfigUiLayout.SIZE,
                Component.text("Ship Configuration", NamedTextColor.GOLD));

        // Fillers first, then real content on top
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
        for (int i = 0; i < ConfigUiLayout.SIZE; i++) {
            inventory.setItem(i, filler);
        }

        for (ModuleSlot slot : ModuleSlot.values()) {
            inventory.setItem(ConfigUiLayout.SLOT_INDEX.get(slot), slotIcon(ship, slot));
        }

        inventory.setItem(ConfigUiLayout.HULL_INFO_INDEX, hullInfo(ship));
        inventory.setItem(ConfigUiLayout.STATS_INFO_INDEX, statsInfo(ship));
        inventory.setItem(ConfigUiLayout.MODULES_INFO_INDEX, modulesInfo(ship));
        inventory.setItem(ConfigUiLayout.HELP_INFO_INDEX, helpInfo());
        inventory.setItem(ConfigUiLayout.FINALIZE_INDEX, finalizeButton(false));

        player.openInventory(inventory);
        return inventory;
    }

    /** Icon for a module slot: the fitted module, or an "empty" placeholder. */
    public ItemStack slotIcon(Ship ship, ModuleSlot slot) {
        ModuleType type = ship.modules().get(slot);
        if (type != null) {
            return moduleItem.create(type);
        }
        return named(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                slot.name() + " — empty (place a module here)", NamedTextColor.GRAY);
    }

    public ItemStack finalizeButton(boolean armed) {
        return named(armed ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                armed ? "Confirm finalize?" : "Finalize ship",
                armed ? NamedTextColor.RED : NamedTextColor.GREEN);
    }

    private ItemStack hullInfo(Ship ship) {
        Material hull = ship.hullMaterial();
        ItemStack stack = hull != null
                ? new ItemStack(hull)
                : named(Material.BARRIER, "No hull", NamedTextColor.RED);
        if (hull != null) {
            edit(stack, Component.text("Hull: " + hull.name(), NamedTextColor.AQUA));
        }
        return stack;
    }

    private ItemStack statsInfo(Ship ship) {
        ItemStack stack = named(Material.NETHER_STAR, "Statistics", NamedTextColor.AQUA);
        edit(stack, Component.text("HP: " + Math.max(0, ship.currentHp())
                        + " / " + Math.max(0, ship.maxHp()), NamedTextColor.WHITE),
                Component.text("Phase: " + ship.phase().name(), NamedTextColor.GRAY));
        return stack;
    }

    private ItemStack modulesInfo(Ship ship) {
        ItemStack stack = named(Material.HOPPER, "Modules", NamedTextColor.AQUA);
        edit(stack, Component.text(ship.modules().size() + " / "
                        + ModuleSlot.values().length + " slots used", NamedTextColor.WHITE),
                Component.text("Punch a module on the ship to remove it",
                        NamedTextColor.GRAY));
        return stack;
    }

    private ItemStack helpInfo() {
        ItemStack stack = named(Material.BOOK, "How to use", NamedTextColor.AQUA);
        edit(stack,
                Component.text("Place: hold a module, click a slot", NamedTextColor.WHITE),
                Component.text("Remove: click a fitted module", NamedTextColor.WHITE),
                Component.text("Move: pick up, then place elsewhere", NamedTextColor.WHITE),
                Component.text("Finalize: click the green button twice", NamedTextColor.WHITE));
        return stack;
    }

    private static ItemStack named(Material material, String name, NamedTextColor color) {
        ItemStack stack = new ItemStack(material);
        edit(stack, Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        return stack;
    }

    private static void edit(ItemStack stack, Component... lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.lore(List.of(lore));
        stack.setItemMeta(meta);
    }
}
