package com.glooshy.ships.runtime;

import com.glooshy.ships.cargo.CargoService;
import com.glooshy.ships.combat.CannonService;
import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.ship.ModulePos;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.visual.CustomModelVisualManager;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Session-2 shared destruction path: the SAME clean teardown runs whether the
 * killing blow came from melee, an arrow or a cannon projectile.
 *
 * <p>Dismounts every passenger, conserves items exactly once (open cargo /
 * cannon inventories are the freshest copy and are dropped live; serialized
 * holds drop only for positions nobody had open — no duplication), despawns
 * all ship entities, marks the ship DESTROYED and releases the binding.
 */
public final class ShipDestructionService {

    private final ShipRegistry shipRegistry;
    private final RuntimeBindingRegistry bindingRegistry;
    private final ModuleEntityManager moduleEntities;
    private final ShipHitboxManager hitboxes;
    private final CustomModelVisualManager modelVisuals;
    private final CargoService cargoService;
    private final CannonService cannons;
    private final ModuleItem moduleItem;

    public ShipDestructionService(ShipRegistry shipRegistry,
                                  RuntimeBindingRegistry bindingRegistry,
                                  ModuleEntityManager moduleEntities,
                                  ShipHitboxManager hitboxes,
                                  CustomModelVisualManager modelVisuals,
                                  CargoService cargoService,
                                  CannonService cannons,
                                  ModuleItem moduleItem) {
        this.shipRegistry = shipRegistry;
        this.bindingRegistry = bindingRegistry;
        this.moduleEntities = moduleEntities;
        this.hitboxes = hitboxes;
        this.modelVisuals = modelVisuals;
        this.cargoService = cargoService;
        this.cannons = cannons;
        this.moduleItem = moduleItem;
    }

    /** Destroy a ship at 0 HP: dismount, conserve, despawn, remove. */
    public void destroy(@NotNull org.bukkit.entity.ArmorStand controllerStand,
                        @NotNull Ship ship, @Nullable Player attacker) {
        Location dropAt = controllerStand.getLocation();

        // Dismount everyone first — destroyed ships leave no mounted passengers
        for (Entity passenger : controllerStand.getPassengers()) {
            controllerStand.removePassenger(passenger);
        }

        // Live inventories are the freshest state — drop those and remember
        // which positions they covered so the serialized copy is skipped
        Set<ModulePos> liveCargo = cargoService.dropOpenAndClose(ship.identity(), dropAt);
        Set<ModulePos> liveCannons = cannons.dropOpenAndClose(ship.identity(), dropAt);

        dropModules(dropAt, ship);
        for (Map.Entry<ModulePos, Map<Integer, Map<String, Object>>> hold
                : ship.cargo().entrySet()) {
            if (liveCargo.contains(hold.getKey())) {
                continue;
            }
            hold.getValue().values().forEach(itemMap -> {
                org.bukkit.inventory.ItemStack item = cargoService.deserializeItem(itemMap);
                if (item != null) {
                    dropAt.getWorld().dropItemNaturally(dropAt, item);
                }
            });
        }
        ship.cannons().forEach((pos, state) -> {
            if (liveCannons.contains(pos)) {
                return;
            }
            state.inventory().values().forEach(itemMap -> {
                org.bukkit.inventory.ItemStack item = cannons.deserializeItem(itemMap);
                if (item != null) {
                    dropAt.getWorld().dropItemNaturally(dropAt, item);
                }
            });
        });

        moduleEntities.despawnAll(ship.identity());
        hitboxes.despawn(ship.identity());
        modelVisuals.despawn(ship.identity());
        cannons.clearShip(ship.identity());

        try {
            shipRegistry.transition(ship.identity(),
                    com.glooshy.ships.ship.LifecyclePhase.DESTROYED);
        } catch (IllegalStateException ignored) {
            // Race — best effort
        }
        bindingRegistry.release(ship.identity());
        controllerStand.remove();

        if (attacker != null) {
            attacker.sendMessage(Component.text(
                    "Ship " + shortId(ship.identity()) + " destroyed.", NamedTextColor.RED));
        }
    }

    private void dropModules(Location dropAt, Ship ship) {
        for (var entry : ship.modules().entrySet()) {
            dropAt.getWorld().dropItemNaturally(
                    dropAt, moduleItem.create(entry.getValue()));
        }
    }

    private static String shortId(ShipIdentity id) {
        String encoded = id.encoded();
        int dash = encoded.indexOf('-');
        return dash > 0 ? encoded.substring(0, dash) : encoded;
    }

    /** Vanilla-style melee crit: attacking while falling. */
    public static boolean isCriticalMelee(@Nullable Player player) {
        return player != null
                && player.getFallDistance() > 0.0f
                && !player.isOnGround()
                && !player.isInWater()
                && !player.isClimbing();
    }
}
