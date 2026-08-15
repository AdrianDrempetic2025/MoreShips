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

    public static NamespacedKey itemModelFor(com.glooshy.ships.ship.ShipSize size) {
        return switch (size) {
            case SMALL -> new NamespacedKey("moreships", "ship_small_trim");
            case MEDIUM -> new NamespacedKey("moreships", "ship_medium_trim");
            case LARGE -> new NamespacedKey("moreships", "ship_large_trim");
        };
    }

    /** The correct worn model for a ship of this size. */
    public static ItemStack create(com.glooshy.ships.ship.ShipSize size) {
        return create(itemModelFor(size));
    }

    /** Is this the RIGHT helmet for a ship of this size (healing check)? */
    public static boolean isShipModel(ItemStack stack, com.glooshy.ships.ship.ShipSize size) {
        if (stack == null || stack.getType() != Material.PAPER) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && itemModelFor(size).equals(meta.getItemModel());
    }

    /**
     * Is this stack ANY ship model helmet (any size)?
     */
    public static boolean isShipModel(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasItemModel()) {
            return false;
        }
        NamespacedKey key = meta.getItemModel();
        return key != null
                && key.getNamespace().equals("moreships")
                && key.getKey().startsWith("ship_")
                && key.getKey().endsWith("_trim");
    }

    public static ItemStack create(NamespacedKey itemModel) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(itemModel);
        stack.setItemMeta(meta);
        return stack;
    }


}
