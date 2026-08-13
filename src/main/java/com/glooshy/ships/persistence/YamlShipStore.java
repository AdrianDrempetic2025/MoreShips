package com.glooshy.ships.persistence;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.ship.LifecyclePhase;
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
            entry.put("phase", ship.phase().name());
            if (ship.hullMaterial() != null) {
                entry.put("hullMaterial", ship.hullMaterial().name());
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
        return new Ship(identity, phase, hull);
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
