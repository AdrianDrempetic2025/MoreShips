package com.glooshy.ships.persistence;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.runtime.BindingState;
import com.glooshy.ships.runtime.RuntimeBinding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * YAML-file-backed {@link BindingStore}.
 *
 * <p>File layout:
 * <pre>
 * bindings:
 *   - shipId: "uuid-string"
 *     entityUuid: "entity-uuid-string"
 *     state: ACTIVE
 * </pre>
 *
 * <p>Atomic save via temp-file + atomic move.
 */
public final class YamlBindingStore implements BindingStore {

    private final Path file;

    public YamlBindingStore(@NotNull Path file) {
        this.file = Objects.requireNonNull(file);
    }

    @Override
    public @NotNull List<RuntimeBinding> load() throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        List<?> rawList = yaml.getList("bindings");
        if (rawList == null) {
            return List.of();
        }

        List<RuntimeBinding> bindings = new ArrayList<>(rawList.size());
        for (Object entry : rawList) {
            Map<String, Object> map = asMap(entry);
            if (map == null) {
                continue;
            }
            RuntimeBinding binding = deserializeBinding(map);
            if (binding != null) {
                bindings.add(binding);
            }
        }
        return bindings;
    }

    @Override
    public void save(@NotNull List<RuntimeBinding> bindings) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> data = new ArrayList<>(bindings.size());
        for (RuntimeBinding binding : bindings) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("shipId", binding.shipId().encoded());
            entry.put("entityUuid", binding.entityUuid().toString());
            entry.put("state", binding.state().name());
            data.add(entry);
        }
        yaml.set("bindings", data);

        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        yaml.save(temp.toFile());
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static @Nullable RuntimeBinding deserializeBinding(Map<String, Object> map) {
        Object shipIdRaw = map.get("shipId");
        Object entityUuidRaw = map.get("entityUuid");
        Object stateRaw = map.get("state");
        if (!(shipIdRaw instanceof String shipIdStr)
                || !(entityUuidRaw instanceof String entityUuidStr)
                || !(stateRaw instanceof String stateStr)) {
            return null;
        }
        try {
            ShipIdentity shipId = ShipIdentity.decode(shipIdStr);
            UUID entityUuid = UUID.fromString(entityUuidStr);
            BindingState state = BindingState.valueOf(stateStr);
            return new RuntimeBinding(shipId, entityUuid, state);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
