package com.glooshy.ships.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The controller stand's helmet: an item carrying the custom ship item model.
 * Worn on the (invisible) armor stand, the model's position and rotation come
 * entirely from the stand - no teleports, no interpolation, no shake.
 */
public final class ShipModelHelmet {

    private ShipModelHelmet() {
    }

    public static ItemStack create(NamespacedKey itemModel) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(itemModel);
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isShipModel(ItemStack stack, NamespacedKey itemModel) {
        if (stack == null || stack.getType() != Material.PAPER) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasItemModel()
                && itemModel.equals(meta.getItemModel());
    }
}
