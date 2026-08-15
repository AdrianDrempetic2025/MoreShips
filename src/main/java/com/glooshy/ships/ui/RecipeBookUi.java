package com.glooshy.ships.ui;

import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.item.RecipeCatalog;
import com.glooshy.ships.item.ShipCoreItem;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Visual recipe book: a chest UI showing each recipe exactly as it would sit
 * in a crafting table — 3x3 ingredient grid, result beside it, name beside
 * that. Two recipes per page; arrow buttons at the bottom navigate.
 */
public final class RecipeBookUi {

    public static final int PREV_SLOT = 45;
    public static final int NEXT_SLOT = 53;
    private static final int RECIPES_PER_PAGE = 2;

    private final ShipCoreItem cores;
    private final ModuleItem modules;
    private final com.glooshy.ships.item.CannonballItem cannonballs;

    public RecipeBookUi(ShipCoreItem cores, ModuleItem modules,
                        com.glooshy.ships.item.CannonballItem cannonballs) {
        this.cores = cores;
        this.modules = modules;
        this.cannonballs = cannonballs;
    }

    public static final class Holder implements InventoryHolder {
        public final int page;

        public Holder(int page) {
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("marker holder");
        }
    }

    public void open(Player player, int page) {
        List<RecipeCatalog.Entry> entries = RecipeCatalog.entries(cores, modules, cannonballs);
        int pages = (entries.size() + RECIPES_PER_PAGE - 1) / RECIPES_PER_PAGE;
        page = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = player.getServer().createInventory(
                new Holder(page), 54,
                Component.text("MoreShips Recipes (" + (page + 1) + "/" + pages + ")",
                        NamedTextColor.GOLD));

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        for (int i = 0; i < RECIPES_PER_PAGE; i++) {
            int idx = page * RECIPES_PER_PAGE + i;
            if (idx >= entries.size()) {
                break;
            }
            RecipeCatalog.Entry entry = entries.get(idx);
            int rowOffset = i * 3;

            ItemStack[][] grid = RecipeCatalog.gridOf(entry);
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int slot = (rowOffset + r) * 9 + c;
                    inv.setItem(slot, grid[r][c] != null ? grid[r][c].clone()
                            : named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "-", NamedTextColor.DARK_GRAY));
                }
            }

            // Arrow + result beside the grid
            inv.setItem((rowOffset + 1) * 9 + 4,
                    named(Material.ARROW, "->", NamedTextColor.GRAY));
            inv.setItem((rowOffset + 1) * 9 + 5, entry.result().clone());

            // Label
            inv.setItem((rowOffset + 1) * 9 + 7,
                    named(Material.PAPER, entry.title(), NamedTextColor.AQUA));
        }

        // Navigation
        if (page > 0) {
            inv.setItem(PREV_SLOT, named(Material.ARROW, "Previous page", NamedTextColor.YELLOW));
        }
        if (page < pages - 1) {
            inv.setItem(NEXT_SLOT, named(Material.ARROW, "Next page", NamedTextColor.YELLOW));
        }
        inv.setItem(49, named(Material.BOOK, "Core ingredients must be REAL crafted cores",
                NamedTextColor.GRAY));

        player.openInventory(inv);
    }

    private static ItemStack named(Material material, String name, NamedTextColor color) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        stack.setItemMeta(meta);
        return stack;
    }
}
