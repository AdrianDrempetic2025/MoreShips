package com.glooshy.ships.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Factory and recognizer for Ship Core items.
 *
 * <p>V1 uses a re-skinned vanilla material (HEART_OF_THE_SEA) with a custom
 * display name and a PersistentDataContainer marker. The marker is what the
 * placement listener detects — not the material alone, so a vanilla
 * heart-of-the-sea does not trigger ship placement.
 */
public final class ShipCoreItem {

    public static final Material BASE_MATERIAL = Material.HEART_OF_THE_SEA;

    public static final String DISPLAY_NAME = "Ship Core";

    private final NamespacedKey markerKey;

    public ShipCoreItem(NamespacedKey markerKey) {
        this.markerKey = markerKey;
    }

    public ItemStack create() {
        ItemStack stack = new ItemStack(BASE_MATERIAL);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(DISPLAY_NAME, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isShipCore(@NotNull ItemStack stack) {
        if (stack.getType() != BASE_MATERIAL) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(markerKey, PersistentDataType.BYTE);
    }

    /**
     * Diagnostic — describe the item's relevant state.
     * Used by {@code /moreships debug} when placement is not behaving.
     */
    public @NotNull String diagnose(@NotNull ItemStack stack) {
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(stack.getType());
        sb.append(" amount=").append(stack.getAmount());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            sb.append(" meta=none");
        } else {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            sb.append(" marker=").append(pdc.has(markerKey, PersistentDataType.BYTE));
            sb.append(" hasDisplayName=").append(meta.hasDisplayName());
        }
        sb.append(" isShipCore=").append(isShipCore(stack));
        return sb.toString();
    }
}
