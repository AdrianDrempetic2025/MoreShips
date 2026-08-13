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
import org.jetbrains.annotations.Nullable;

/**
 * Factory and recognizer for Ship Core items.
 *
 * <p>Base material and display name are configurable (CON-01). The
 * PersistentDataContainer marker is what the placement listener detects — not
 * the material alone, so a vanilla heart-of-the-sea does not trigger ship
 * placement even if the configured base material matches the vanilla item.
 */
public final class ShipCoreItem {

    private final NamespacedKey markerKey;
    private final Material baseMaterial;
    private final String displayName;

    public ShipCoreItem(NamespacedKey markerKey, Material baseMaterial, String displayName) {
        this.markerKey = markerKey;
        this.baseMaterial = baseMaterial;
        this.displayName = displayName;
    }

    public ItemStack create() {
        ItemStack stack = new ItemStack(baseMaterial);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(displayName, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isShipCore(@NotNull ItemStack stack) {
        if (stack.getType() != baseMaterial) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(markerKey, PersistentDataType.BYTE);
    }

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
