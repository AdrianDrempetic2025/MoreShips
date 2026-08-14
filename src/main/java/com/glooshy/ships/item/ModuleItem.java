package com.glooshy.ships.item;

import com.glooshy.ships.ship.ModuleType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * Factory and recognizer for ship module items (RQCA-08).
 *
 * <p>Same pattern as {@link ShipCoreItem}: the PersistentDataContainer marker
 * (value = module type name) is what the install listener detects — not the
 * material alone, so a vanilla chest never installs a cargo module even though
 * the configured cargo material is CHEST.
 */
public final class ModuleItem {

    private final NamespacedKey markerKey;
    private final Map<ModuleType, Material> materials;
    private final Map<ModuleType, String> displayNames;

    public ModuleItem(NamespacedKey markerKey,
                      Map<ModuleType, Material> materials,
                      Map<ModuleType, String> displayNames) {
        this.markerKey = Objects.requireNonNull(markerKey, "markerKey");
        this.materials = Map.copyOf(materials);
        this.displayNames = Map.copyOf(displayNames);
        for (ModuleType type : ModuleType.values()) {
            if (!this.materials.containsKey(type) || !this.displayNames.containsKey(type)) {
                throw new IllegalArgumentException("Missing material or display name for " + type);
            }
        }
    }

    public ItemStack create(ModuleType type) {
        ItemStack stack = new ItemStack(materials.get(type));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(displayNames.get(type), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, type.name());
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Recognize a module item. Returns the module type recorded in the PDC
     * marker, or empty if the stack is not a module item (no marker, unknown
     * type, or material no longer matching the configuration).
     */
    public Optional<ModuleType> parse(@NotNull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String typeName = pdc.get(markerKey, PersistentDataType.STRING);
        if (typeName == null) {
            return Optional.empty();
        }
        ModuleType type;
        try {
            type = ModuleType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (stack.getType() != materials.get(type)) {
            return Optional.empty();
        }
        return Optional.of(type);
    }

    public String displayName(ModuleType type) {
        return displayNames.get(type);
    }
}
