package com.glooshy.ships.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Session-2 cannon ammunition: a craftable blackstone cannonball. This is the
 * single ammo type — 1 cannonball = 1 shot. Carries a PDC marker (real ammo
 * is identifiable even after restarts) and the resource-pack item model that
 * renders it as a small black ball.
 */
public final class CannonballItem {

    public static final String ITEM_MODEL = "moreships:cannonball";

    private final NamespacedKey marker;

    public CannonballItem(@NotNull NamespacedKey marker) {
        this.marker = marker;
    }

    public @NotNull ItemStack create(int amount) {
        ItemStack stack = new ItemStack(Material.SNOWBALL, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Cannonball", NamedTextColor.DARK_GRAY));
        meta.setItemModel(new NamespacedKey("moreships", "cannonball"));
        meta.lore(java.util.List.of(Component.text(
                "Ammunition for ship cannons", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isCannonball(ItemStack stack) {
        if (stack == null || stack.getType() != Material.SNOWBALL) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }
}
