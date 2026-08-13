package com.glooshy.ships.listener;

import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.runtime.RuntimeBinding;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.runtime.ShipEntitySpawner;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Listens for right-click water with a Ship Core.
 *
 * <p>Handles both {@link Action#RIGHT_CLICK_BLOCK} (player clicked a water block
 * directly) and {@link Action#RIGHT_CLICK_AIR} (player's crosshair is on water
 * but the event fires as air). For the air case, the listener raytraces the
 * player's view to find the targeted fluid block.
 *
 * <p>Spawn offset and sound are configurable (CON-01).
 */
public final class ShipCorePlacementListener implements Listener {

    private final ShipCoreItem shipCoreItem;
    private final ShipRegistry shipRegistry;
    private final ShipEntitySpawner entitySpawner;
    private final RuntimeBindingRegistry bindingRegistry;

    private final double spawnOffsetX;
    private final double spawnOffsetY;
    private final double spawnOffsetZ;
    private final Sound placementSound;
    private final float soundVolume;
    private final float soundPitch;

    private volatile String lastInteractDescription = "(no interact events seen yet)";

    public ShipCorePlacementListener(
            ShipCoreItem shipCoreItem,
            ShipRegistry shipRegistry,
            ShipEntitySpawner entitySpawner,
            RuntimeBindingRegistry bindingRegistry,
            double spawnOffsetX,
            double spawnOffsetY,
            double spawnOffsetZ,
            Sound placementSound,
            float soundVolume,
            float soundPitch) {
        this.shipCoreItem = shipCoreItem;
        this.shipRegistry = shipRegistry;
        this.entitySpawner = entitySpawner;
        this.bindingRegistry = bindingRegistry;
        this.spawnOffsetX = spawnOffsetX;
        this.spawnOffsetY = spawnOffsetY;
        this.spawnOffsetZ = spawnOffsetZ;
        this.placementSound = placementSound;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        Block target = resolveTargetBlock(event, action, player);

        String description = describe(action, event.getClickedBlock(), target, inHand);
        lastInteractDescription = description;

        if (target == null || !isWaterBlock(target)) {
            return;
        }
        if (!shipCoreItem.isShipCore(inHand)) {
            return;
        }

        Ship ship = shipRegistry.createShip();

        Location spawnLoc = target.getLocation().add(spawnOffsetX, spawnOffsetY, spawnOffsetZ);
        UUID entityUuid = entitySpawner.spawnUnfinishedShip(spawnLoc, ship.identity());

        bindingRegistry.bind(RuntimeBinding.active(ship.identity(), entityUuid));

        inHand.setAmount(inHand.getAmount() - 1);

        player.playSound(spawnLoc, placementSound, soundVolume, soundPitch);
        player.sendMessage(Component.text(
                "Placed ship core (id " + ship.identity().encoded() + ", phase " + ship.phase() + ")",
                NamedTextColor.GREEN));
    }

    public @NotNull String lastInteractDescription() {
        return lastInteractDescription;
    }

    @Nullable
    private Block resolveTargetBlock(@NotNull PlayerInteractEvent event, Action action, Player player) {
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            return event.getClickedBlock();
        }
        if (action == Action.RIGHT_CLICK_AIR) {
            return player.getTargetBlockExact(5, FluidCollisionMode.ALWAYS);
        }
        return null;
    }

    static boolean isWaterBlock(@NotNull Block block) {
        Material type = block.getType();
        return type == Material.WATER || type == Material.BUBBLE_COLUMN;
    }

    private @NotNull String describe(Action action, Block clicked, Block target, ItemStack inHand) {
        StringBuilder sb = new StringBuilder();
        sb.append("action=").append(action);
        sb.append(" clicked=").append(clicked == null ? "null" : clicked.getType());
        sb.append(" target=").append(target == null ? "null" : target.getType());
        sb.append(" item=[").append(shipCoreItem.diagnose(inHand)).append("]");
        return sb.toString();
    }
}
