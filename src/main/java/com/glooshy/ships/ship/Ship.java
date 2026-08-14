package com.glooshy.ships.ship;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

/**
 * Domain entity: a ship with an immutable {@link ShipIdentity}, a current
 * {@link LifecyclePhase}, hull material, current/max HP, fitted modules
 * (slot → type, RQCA-08), and cargo contents (RQCA-21/22).
 *
 * <p>HP fields are -1 for ships that have not yet had a hull applied
 * (UNFINISHED). On hull application, currentHp and maxHp are set to the
 * computed value derived from material hardness. Damage reduces currentHp;
 * at 0, the ship transitions to DESTROYED.
 *
 * <p>Modules can only be fitted while the ship is HULL_APPLIED (before
 * finalization); the registry enforces this. The map is unmodifiable.
 *
 * <p>Cargo is stored as slot index → serialized ItemStack (a raw map), so the
 * domain stays testable without a server. Bukkit-side glue converts between
 * ItemStack and the map form. Cargo requires at least one fitted CARGO module
 * (enforced at the interaction layer, not here).
 */
public record Ship(
        ShipIdentity identity,
        LifecyclePhase phase,
        @Nullable Material hullMaterial,
        int currentHp,
        int maxHp,
        Map<ModuleSlot, ModuleType> modules,
        Map<Integer, Map<String, Object>> cargo) {

    private static final int CARGO_SIZE = 27;

    public Ship {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(phase, "phase");
        modules = modules == null ? Map.of() : Map.copyOf(modules);
        cargo = cargo == null ? Map.of() : Map.copyOf(cargo);
    }

    public static int cargoSize() {
        return CARGO_SIZE;
    }

    /** Convenience: no hull, no HP, no modules, no cargo (new ships). */
    public Ship(ShipIdentity identity, LifecyclePhase phase, @Nullable Material hullMaterial) {
        this(identity, phase, hullMaterial, -1, -1, Map.of(), Map.of());
    }

    /** Convenience: no modules, no cargo. */
    public Ship(ShipIdentity identity, LifecyclePhase phase, @Nullable Material hullMaterial,
                int currentHp, int maxHp) {
        this(identity, phase, hullMaterial, currentHp, maxHp, Map.of(), Map.of());
    }

    /** Convenience: no cargo. */
    public Ship(ShipIdentity identity, LifecyclePhase phase, @Nullable Material hullMaterial,
                int currentHp, int maxHp, Map<ModuleSlot, ModuleType> modules) {
        this(identity, phase, hullMaterial, currentHp, maxHp, modules, Map.of());
    }

    public boolean hasCargoModule() {
        return modules.containsValue(ModuleType.CARGO);
    }
}
