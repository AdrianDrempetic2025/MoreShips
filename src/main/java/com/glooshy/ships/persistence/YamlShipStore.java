package com.glooshy.ships.persistence;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.CannonState;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.ModulePos;
import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.ShipSize;
import com.glooshy.ships.ship.Ship;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * YAML-file-backed {@link ShipStore}.
 *
 * <p>File layout:
 * <pre>
 * ships:
 *   - identity: "uuid-string"
 *     phase: UNFINISHED
 *     hullMaterial: STONE     # omitted when null
 * </pre>
 *
 * <p>Atomic save: writes to {@code <file>.tmp} then renames over the target.
 * A crash mid-write leaves the previous file intact.
 */
public final class YamlShipStore implements ShipStore {

    private final Path file;

    public YamlShipStore(@NotNull Path file) {
        this.file = Objects.requireNonNull(file);
    }

    @Override
    public @NotNull List<Ship> load() throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        List<?> rawList = yaml.getList("ships");
        if (rawList == null) {
            return List.of();
        }

        List<Ship> ships = new ArrayList<>(rawList.size());
        for (Object entry : rawList) {
            Map<String, Object> map = asMap(entry);
            if (map == null) {
                continue;
            }
            Ship ship = deserializeShip(map);
            if (ship != null) {
                ships.add(ship);
            }
        }
        return ships;
    }

    @Override
    public void save(@NotNull List<Ship> ships) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> data = new ArrayList<>(ships.size());
        for (Ship ship : ships) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("identity", ship.identity().encoded());
            entry.put("size", ship.size().name());
            entry.put("phase", ship.phase().name());
            if (ship.hullMaterial() != null) {
                entry.put("hullMaterial", ship.hullMaterial().name());
            }
            if (ship.currentHp() != -1 || ship.maxHp() != -1) {
                entry.put("currentHp", ship.currentHp());
                entry.put("maxHp", ship.maxHp());
            }
            if (!ship.modules().isEmpty()) {
                Map<String, String> modules = new LinkedHashMap<>();
                ship.modules().forEach((pos, type) -> modules.put(pos.encoded(), type.name()));
                entry.put("modules", modules);
            }
            if (!ship.cargo().isEmpty()) {
                Map<String, List<Map<String, Object>>> cargo = new LinkedHashMap<>();
                ship.cargo().forEach((pos, hold) -> {
                    List<Map<String, Object>> rows = new ArrayList<>(hold.size());
                    hold.forEach((index, item) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("slot", index);
                        row.put("item", item);
                        rows.add(row);
                    });
                    cargo.put(pos.encoded(), rows);
                });
                entry.put("cargo", cargo);
            }
            if (!ship.cannons().isEmpty()) {
                Map<String, Object> cannons = new LinkedHashMap<>();
                ship.cannons().forEach((pos, state) -> {
                    Map<String, Object> cs = new LinkedHashMap<>();
                    cs.put("shots", state.shots());
                    List<Map<String, Object>> rows = new ArrayList<>(state.inventory().size());
                    state.inventory().forEach((index, item) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("slot", index);
                        row.put("item", item);
                        rows.add(row);
                    });
                    cs.put("inventory", rows);
                    cannons.put(pos.encoded(), cs);
                });
                entry.put("cannons", cannons);
            }
            data.add(entry);
        }
        yaml.set("ships", data);

        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        yaml.save(temp.toFile());
        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    private static @Nullable Ship deserializeShip(Map<String, Object> map) {
        Object identityRaw = map.get("identity");
        if (!(identityRaw instanceof String identityStr)) {
            return null;
        }
        ShipIdentity identity;
        try {
            identity = ShipIdentity.decode(identityStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Object sizeRaw = map.get("size");
        ShipSize size;
        try {
            size = sizeRaw instanceof String sizeName ? ShipSize.valueOf(sizeName) : ShipSize.SMALL;
        } catch (IllegalArgumentException e) {
            size = ShipSize.SMALL;
        }
        Object phaseRaw = map.get("phase");
        if (!(phaseRaw instanceof String phaseStr)) {
            return null;
        }
        LifecyclePhase phase;
        try {
            phase = LifecyclePhase.valueOf(phaseStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Object hullRaw = map.get("hullMaterial");
        Material hull = (hullRaw instanceof String hullName) ? Material.matchMaterial(hullName) : null;

        int currentHp = map.get("currentHp") instanceof Number n ? n.intValue() : -1;
        int maxHp = map.get("maxHp") instanceof Number n2 ? n2.intValue() : -1;

        Map<ModulePos, ModuleType> modules = new LinkedHashMap<>();
        Object modulesRaw = map.get("modules");
        Map<String, Object> modulesMap = modulesRaw instanceof MemorySection section
                ? section.getValues(false)
                : (modulesRaw instanceof Map<?, ?> raw ? asStringMap(raw) : null);
        if (modulesMap != null) {
            for (Map.Entry<String, Object> e : modulesMap.entrySet()) {
                ModulePos pos = ModulePos.decode(e.getKey());
                if (pos == null || !size.isValid(pos)) {
                    continue; // Old-format named slot or invalid position — dropped
                }
                try {
                    modules.put(pos, ModuleType.valueOf(String.valueOf(e.getValue())));
                } catch (IllegalArgumentException ignored) {
                    // Unknown type from a newer version — skip this entry
                }
            }
        }
        Map<ModulePos, Map<Integer, Map<String, Object>>> cargo = new LinkedHashMap<>();
        Object cargoRaw = map.get("cargo");
        Map<String, Object> holdsBySlot = cargoRaw instanceof MemorySection section
                ? section.getValues(false)
                : (cargoRaw instanceof Map<?, ?> raw ? asStringMap(raw) : null);
        if (holdsBySlot != null) {
            for (Map.Entry<String, Object> holdEntry : holdsBySlot.entrySet()) {
                ModulePos modulePos = ModulePos.decode(holdEntry.getKey());
                if (modulePos == null || !size.isValid(modulePos)) {
                    continue;
                }
                if (!(holdEntry.getValue() instanceof List<?> rows)) {
                    continue;
                }
                Map<Integer, Map<String, Object>> hold = new LinkedHashMap<>();
                for (Object row : rows) {
                    Map<String, Object> rowMap = row instanceof MemorySection rowSection
                            ? rowSection.getValues(false)
                            : (row instanceof Map<?, ?> raw ? asStringMap(raw) : null);
                    if (rowMap == null) {
                        continue;
                    }
                    if (!(rowMap.get("slot") instanceof Number index)
                            || !(rowMap.get("item") instanceof Map<?, ?>)) {
                        continue;
                    }
                    hold.put(index.intValue(), asStringMap((Map<?, ?>) rowMap.get("item")));
                }
                cargo.put(modulePos, hold);
            }
        }

        Map<ModulePos, CannonState> cannons = new LinkedHashMap<>();
        Object cannonsRaw = map.get("cannons");
        Map<String, Object> cannonsBySlot = cannonsRaw instanceof MemorySection section2
                ? section2.getValues(false)
                : (cannonsRaw instanceof Map<?, ?> raw2 ? asStringMap(raw2) : null);
        if (cannonsBySlot != null) {
            for (Map.Entry<String, Object> cannonEntry : cannonsBySlot.entrySet()) {
                ModulePos cannonPos = ModulePos.decode(cannonEntry.getKey());
                if (cannonPos == null || !size.isValid(cannonPos)) {
                    continue;
                }
                if (!(cannonEntry.getValue() instanceof Map<?, ?> cannonRawMap)) {
                    continue;
                }
                Map<String, Object> cannonMap = asStringMap(cannonRawMap);
                int shots = cannonMap.get("shots") instanceof Number n3 ? n3.intValue() : 0;
                Map<Integer, Map<String, Object>> inv = new LinkedHashMap<>();
                if (cannonMap.get("inventory") instanceof List<?> invRows) {
                    for (Object row : invRows) {
                        Map<String, Object> rowMap = row instanceof MemorySection rowSection
                                ? rowSection.getValues(false)
                                : (row instanceof Map<?, ?> raw3 ? asStringMap(raw3) : null);
                        if (rowMap == null) {
                            continue;
                        }
                        if (!(rowMap.get("slot") instanceof Number index2)
                                || !(rowMap.get("item") instanceof Map<?, ?>)) {
                            continue;
                        }
                        inv.put(index2.intValue(), asStringMap((Map<?, ?>) rowMap.get("item")));
                    }
                }
                cannons.put(cannonPos, new CannonState(shots, inv));
            }
        }

        return new Ship(identity, size, phase, hull, currentHp, maxHp,
                Map.copyOf(modules), Map.copyOf(cargo), Map.copyOf(cannons));
    }

    private static Map<String, Object> asStringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            result.put(String.valueOf(e.getKey()), e.getValue());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Map<String, Object> asMap(Object entry) {
        if (entry instanceof MemorySection section) {
            return section.getValues(false);
        }
        if (entry instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return null;
    }
}
