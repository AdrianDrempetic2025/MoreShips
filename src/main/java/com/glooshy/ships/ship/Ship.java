package com.glooshy.ships.ship;

import com.glooshy.ships.identity.ShipIdentity;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

/**
 * Domain entity: a ship with an immutable {@link ShipIdentity}, a fixed
 * {@link ShipSize}, a current {@link LifecyclePhase}, hull material,
 * current/max HP, fitted modules (position → type, RQCA-08), and cargo holds
 * (RQCA-21/22).
 *
 * <p>HP fields are -1 for ships that have not yet had a hull applied
 * (UNFINISHED). On hull application, currentHp and maxHp are set to the
 * computed value derived from material hardness. Damage reduces currentHp;
 * at 0, the ship transitions to DESTROYED.
 *
 * <p>Modules can only be fitted while the ship is HULL_APPLIED (before
 * finalization) and only at positions valid for the ship's size; the registry
 * enforces both. Maps are unmodifiable.
 *
 * <p>Each fitted CARGO module has its OWN hold: cargo is keyed by the module's
 * position, mapping inventory index → serialized ItemStack (a raw map, keeping
 * the domain testable without a server).
 */
public record Ship(
        ShipIdentity identity,
        ShipSize size,
        LifecyclePhase phase,
        @Nullable Material hullMaterial,
        int currentHp,
        int maxHp,
        Map<ModulePos, ModuleType> modules,
        Map<ModulePos, Map<Integer, Map<String, Object>>> cargo) {

    private static final int CARGO_HOLD_SIZE = 27;

    public Ship {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(phase, "phase");
        modules = modules == null ? Map.of() : Map.copyOf(modules);
        cargo = cargo == null ? Map.of() : Map.copyOf(cargo);
    }

    public static int cargoHoldSize() {
        return CARGO_HOLD_SIZE;
    }

    /** Convenience: new UNFINISHED ship of the given size, no hull/HP/modules. */
    public Ship(ShipIdentity identity, ShipSize size, LifecyclePhase phase,
                @Nullable Material hullMaterial) {
        this(identity, size, phase, hullMaterial, -1, -1, Map.of(), Map.of());
    }

    /** Convenience: no modules, no cargo. */
    public Ship(ShipIdentity identity, ShipSize size, LifecyclePhase phase,
                @Nullable Material hullMaterial, int currentHp, int maxHp) {
        this(identity, size, phase, hullMaterial, currentHp, maxHp, Map.of(), Map.of());
    }

    public boolean hasCargoModule() {
        return modules.containsValue(ModuleType.CARGO);
    }
}
