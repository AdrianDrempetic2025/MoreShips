package com.glooshy.ships.item;

import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.ShipSize;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Single source of truth for all MoreShips crafting recipes (spec L1 rough).
 * Both the server-side recipe registration and the visual recipe book UI read
 * from here, so they can never disagree.
 */
public final class RecipeCatalog {

    public record Entry(String title, String[] shape, Map<Character, ItemStack> ingredients,
                        ItemStack result) {
    }

    public static List<Entry> entries(ShipCoreItem cores, ModuleItem modules) {
        List<Entry> list = new ArrayList<>();

        list.add(new Entry("Small Ship Core",
                new String[]{"ISI"},
                Map.of('I', new ItemStack(Material.IRON_INGOT),
                        'S', new ItemStack(Material.STICK)),
                cores.create(ShipSize.SMALL)));

        list.add(new Entry("Medium Ship Core",
                new String[]{"WIW", "SSS", "WIW"},
                Map.of('W', new ItemStack(Material.OAK_PLANKS),
                        'I', new ItemStack(Material.IRON_INGOT),
                        'S', cores.create(ShipSize.SMALL)),
                cores.create(ShipSize.MEDIUM)));

        list.add(new Entry("Large Ship Core",
                new String[]{"MMS"},
                Map.of('M', cores.create(ShipSize.MEDIUM),
                        'S', new ItemStack(Material.STICK)),
                cores.create(ShipSize.LARGE)));

        list.add(new Entry("Seat Module",
                new String[]{"PPP"},
                Map.of('P', new ItemStack(Material.OAK_PLANKS)),
                modules.create(ModuleType.SEAT)));

        list.add(new Entry("Cargo Module",
                new String[]{"PPP", "PCP", "PPP"},
                Map.of('P', new ItemStack(Material.OAK_PLANKS),
                        'C', new ItemStack(Material.CHEST)),
                modules.create(ModuleType.CARGO)));

        list.add(new Entry("Cannon Module",
                new String[]{"III", "IFI", "III"},
                Map.of('I', new ItemStack(Material.IRON_INGOT),
                        'F', new ItemStack(Material.FIRE_CHARGE)),
                modules.create(ModuleType.CANNON)));

        list.add(new Entry("Engine Module",
                new String[]{"IRI"},
                Map.of('I', new ItemStack(Material.IRON_INGOT),
                        'R', new ItemStack(Material.REDSTONE)),
                modules.create(ModuleType.ENGINE)));

        list.add(new Entry("Health Module",
                new String[]{"III", "IGI", "III"},
                Map.of('I', new ItemStack(Material.IRON_INGOT),
                        'G', new ItemStack(Material.GOLDEN_APPLE)),
                modules.create(ModuleType.HEALTH)));

        return list;
    }

    /**
     * Render a shape into a fixed 3x3 grid of ingredient stacks (null = empty
     * slot), exactly as the crafting table would show it.
     */
    public static ItemStack[][] gridOf(Entry entry) {
        ItemStack[][] grid = new ItemStack[3][3];
        for (int r = 0; r < entry.shape().length && r < 3; r++) {
            String row = entry.shape()[r];
            for (int c = 0; c < row.length() && c < 3; c++) {
                char ch = row.charAt(c);
                if (ch != ' ') {
                    grid[r][c] = entry.ingredients().get(ch);
                }
            }
        }
        return grid;
    }

    /** Unique recipe key (namespaced key suffix) for server registration. */
    public static String keyOf(Entry entry) {
        return entry.title().toLowerCase().replace(' ', '_');
    }

    private RecipeCatalog() {
    }
}
