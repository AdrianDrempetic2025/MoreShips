package com.glooshy.ships.persistence;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.runtime.ShipHitboxManager.HitboxBinding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * YAML-file-backed store for ship → hitbox-entity bindings. Atomic save via
 * temp + rename, same pattern as the other stores.
 */
public final class YamlShipHitboxStore {

    private final Path file;

    public YamlShipHitboxStore(@NotNull Path file) {
        this.file = Objects.requireNonNull(file);
    }

    public @NotNull List<HitboxBinding> load() throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        List<?> rawList = yaml.getList("entries");
        if (rawList == null) {
            return List.of();
        }
        List<HitboxBinding> bindings = new ArrayList<>(rawList.size());
        for (Object entry : rawList) {
            Map<String, Object> map = asMap(entry);
            if (map == null) {
                continue;
            }
            try {
                bindings.add(new HitboxBinding(
                        ShipIdentity.decode(String.valueOf(map.get("ship"))),
                        UUID.fromString(String.valueOf(map.get("entity")))));
            } catch (IllegalArgumentException e) {
                // skip unreadable
            }
        }
        return bindings;
    }

    public void save(@NotNull List<HitboxBinding> bindings) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> data = new ArrayList<>(bindings.size());
        for (HitboxBinding binding : bindings) {
            data.add(Map.of(
                    "ship", binding.shipId().encoded(),
                    "entity", binding.entityUuid().toString()));
        }
        yaml.set("entries", data);

        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        yaml.save(temp.toFile());
        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
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
