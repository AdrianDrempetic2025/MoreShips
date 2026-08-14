package com.glooshy.ships.item;

import com.glooshy.ships.ship.ShipSize;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Factory and recognizer for Ship Core items, one per {@link ShipSize}
 * (spec L1 §2: Small/Medium/Large cores).
 *
 * <p>The PersistentDataContainer marker (value = size name) is what the
 * placement listener detects — not the material alone, so a vanilla item of
 * the same material never triggers ship placement.
 */
public final class ShipCoreItem {

    private final NamespacedKey markerKey;
    private final Map<ShipSize, Material> materials;
    private final Map<ShipSize, String> displayNames;

    public ShipCoreItem(NamespacedKey markerKey,
                        Map<ShipSize, Material> materials,
                        Map<ShipSize, String> displayNames) {
        this.markerKey = Objects.requireNonNull(markerKey, "markerKey");
        this.materials = Map.copyOf(materials);
        this.displayNames = Map.copyOf(displayNames);
        for (ShipSize size : ShipSize.values()) {
            if (!this.materials.containsKey(size) || !this.displayNames.containsKey(size)) {
                throw new IllegalArgumentException("Missing core material or display name for " + size);
            }
        }
    }

    public ItemStack create(ShipSize size) {
        ItemStack stack = new ItemStack(materials.get(size));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(displayNames.get(size), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(size.width() + "x" + size.length() + " hull, "
                        + size.capacity() + " module slots", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, size.name());
        stack.setItemMeta(meta);
        return stack;
    }

    /** The ship size of a core item, or null if the stack is not a core. */
    public @Nullable ShipSize parseSize(@NotNull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String sizeName = meta.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
        if (sizeName == null) {
            return null;
        }
        try {
            ShipSize size = ShipSize.valueOf(sizeName);
            return stack.getType() == materials.get(size) ? size : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String displayName(ShipSize size) {
        return displayNames.get(size);
    }

    public Map<ShipSize, Material> materials() {
        return new EnumMap<>(materials);
    }
}
