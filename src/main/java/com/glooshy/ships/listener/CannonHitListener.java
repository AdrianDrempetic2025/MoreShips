package com.glooshy.ships.listener;

import com.glooshy.ships.combat.CannonService;
import com.glooshy.ships.identity.ShipIdentity;
import com.glooshy.ships.runtime.ShipEntityResolver;
import com.glooshy.ships.ship.LifecyclePhase;
import com.glooshy.ships.ship.Ship;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Cannon shot impact: a hit on a ship's hull (controller stand, hitbox
 * segments, solidity cells — anything the resolver knows) applies cannon
 * damage to that ship's HP. Self-hits are ignored (you cannot shoot your
 * own ship).
 */
public final class CannonHitListener implements Listener {

    private final CannonService cannons;
    private final ShipEntityResolver resolver;

    public CannonHitListener(CannonService cannons, ShipEntityResolver resolver) {
        this.cannons = cannons;
        this.resolver = resolver;
    }

    @EventHandler
    public void onProjectileHit(@NotNull ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball shot)) {
            return;
        }
        ShipIdentity source = cannons.sourceShip(shot);
        if (source == null) {
            return; // not a cannon shot
        }

        Entity hit = event.getHitEntity();
        if (hit == null) {
            return; // terrain hit — the shot just despawns
        }

        // Direct player/mob hit: modest direct damage + knockback feel
        if (hit instanceof Player victim) {
            victim.damage(2.0, shot);
            return;
        }

        var hitShipId = resolver.shipIdOf(hit);
        if (hitShipId.isEmpty()) {
            return; // hit some unrelated entity
        }
        if (hitShipId.get().equals(source)) {
            return; // own ship — ignore
        }

        Ship target = cannons.registry().find(hitShipId.get()).orElse(null);
        if (target == null || target.phase() != LifecyclePhase.FINALIZED) {
            return;
        }

        Ship after = cannons.registry().applyDamage(hitShipId.get(), cannons.damage());

        if (after.currentHp() <= 0) {
            // Destruction path runs in ShipEntityBreakListener via the next
            // hull damage; here we only report. (Snowball impact itself is
            // not a hull damage event.)
            if (shot.getShooter() instanceof Player shooter) {
                shooter.sendMessage(Component.text(
                        "Direct hit — enemy ship destroyed!", NamedTextColor.RED));
            }
            return;
        }

        if (shot.getShooter() instanceof Player shooter) {
            shooter.sendMessage(Component.text(
                    "Hit! Enemy ship: " + after.currentHp() + "/" + after.maxHp() + " HP",
                    NamedTextColor.YELLOW));
        }
        if (hit instanceof ArmorStand stand) {
            stand.customName(Component.text(
                    "Ship " + shortId(hitShipId.get()) + " [" + after.currentHp()
                            + "/" + after.maxHp() + " HP]", NamedTextColor.AQUA));
        }
    }

    private static String shortId(ShipIdentity id) {
        String encoded = id.encoded();
        int dash = encoded.indexOf('-');
        return dash > 0 ? encoded.substring(0, dash) : encoded;
    }
}
