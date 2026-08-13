package com.glooshy.ships.persistence;

import com.glooshy.ships.runtime.RuntimeBinding;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Persistence layer for the live runtime binding set.
 */
public interface BindingStore {

    @NotNull List<RuntimeBinding> load() throws IOException;

    void save(@NotNull List<RuntimeBinding> bindings) throws IOException;
}
