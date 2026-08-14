package com.glooshy.ships.persistence;

import com.glooshy.ships.runtime.ModuleEntityManager.ModuleEntityBinding;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Durable store for ship → module-entity bindings. */
public interface ModuleEntityStore {

    @NotNull List<ModuleEntityBinding> load() throws IOException;

    void save(@NotNull List<ModuleEntityBinding> bindings) throws IOException;
}
