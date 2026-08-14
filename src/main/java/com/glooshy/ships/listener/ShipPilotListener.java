package com.glooshy.ships.listener;

import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.runtime.ShipEntityResolver;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Lets a player board a FINALIZED ship to pilot it.
 *
 * <p>Right-click empty-handed on a FINALIZED ship's ArmorStand → player added
 * as passenger. The player's facing direction steers the ship; sneak to
 * dismount. Right-click with an item in hand is left for other listeners
 * (hull application, future module slot configuration).
 *
 * <p>Only FINALIZED ships can be piloted. UNFINISHED ships accept hull;
 * HULL_APPLIED ships are not yet piloted (finalize first). Vanilla ArmorStand
 * interact is cancelled for FINALIZED ships to prevent the vanilla equip GUI.
 */
public final class ShipPilotListener implements Listener {

    private final ShipRegistry shipRegistry;
    private final ShipEntityResolver resolver;

    public ShipPilotListener(ShipRegistry shipRegistry, ShipEntityResolver resolver) {
        this.shipRegistry = shipRegistry;
        this.resolver = resolver;
    }

    @EventHandler
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        var clicked = event.getRightClicked();
        Optional<ShipIdentity> shipId = resolver.shipIdOf(clicked);
        if (shipId.isEmpty()) {
            return;
        }
        Optional<ArmorStand> standOpt = resolver.shipStandOf(clicked);
        if (standOpt.isEmpty()) {
            return;
        }
        ArmorStand stand = standOpt.get();

        Optional<Ship> shipOpt = shipRegistry.find(shipId.get());
        if (shipOpt.isEmpty()) {
            return;
        }

        Ship ship = shipOpt.get();
        if (ship.phase() != LifecyclePhase.FINALIZED) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }

        event.setCancelled(true);

        if (stand.getPassengers().stream().anyMatch(p -> p instanceof Player)) {
            player.sendMessage(Component.text(
                    "Ship is already being piloted.", NamedTextColor.YELLOW));
            return;
        }

        boolean boarded = stand.addPassenger(player);
        if (!boarded) {
            player.sendMessage(Component.text(
                    "Could not board the ship.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text(
                "Boarded ship. Look to steer, sneak to dismount.", NamedTextColor.GREEN));
    }
}
