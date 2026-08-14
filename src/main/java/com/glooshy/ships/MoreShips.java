package com.glooshy.ships;

import com.glooshy.ships.cargo.CargoService;
import com.glooshy.ships.command.ShipsCommand;
import com.glooshy.ships.config.MoreShipsConfig;
import com.glooshy.ships.hull.HpCalculator;
import com.glooshy.ships.hull.HullValidator;
import com.glooshy.ships.identity.ShipIdentityGenerator;
import com.glooshy.ships.item.ModuleItem;
import com.glooshy.ships.item.ShipCoreItem;
import com.glooshy.ships.listener.CargoInventoryListener;
import com.glooshy.ships.listener.HullApplicationListener;
import com.glooshy.ships.listener.ModuleEntityListener;
import com.glooshy.ships.listener.ShipConfigUiListener;
import com.glooshy.ships.listener.ShipCorePlacementListener;
import com.glooshy.ships.listener.ShipEntityBreakListener;
import com.glooshy.ships.listener.ShipPilotListener;
import com.glooshy.ships.movement.ShipMovementService;
import com.glooshy.ships.persistence.BindingStore;
import com.glooshy.ships.persistence.ModuleEntityStore;
import com.glooshy.ships.persistence.ShipStore;
import com.glooshy.ships.persistence.YamlBindingStore;
import com.glooshy.ships.persistence.YamlModuleEntityStore;
import com.glooshy.ships.persistence.YamlShipHitboxStore;
import com.glooshy.ships.persistence.YamlShipStore;
import com.glooshy.ships.runtime.ModuleEntityManager;
import com.glooshy.ships.runtime.ShipEntityResolver;
import com.glooshy.ships.runtime.ShipHitboxManager;
import com.glooshy.ships.visual.CustomModelVisualManager;
import com.glooshy.ships.runtime.RuntimeBindingRegistry;
import com.glooshy.ships.runtime.ShipEntitySpawner;
import com.glooshy.ships.ship.ModuleType;
import com.glooshy.ships.ship.Ship;
import com.glooshy.ships.ship.ShipRegistry;
import com.glooshy.ships.ship.ShipSize;
import com.glooshy.ships.ship.ShipTeardownService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Project Beacon — Custom Ship System for Paper 26.2.
 *
 * <p>BUILD-05: persistence — ships + bindings survive server restart via YAML.
 */
public final class MoreShips extends JavaPlugin {

    private ShipRegistry shipRegistry;
    private RuntimeBindingRegistry bindingRegistry;
    private ShipStore shipStore;
    private BindingStore bindingStore;
    private ModuleEntityStore moduleEntityStore;
    private ModuleEntityManager moduleEntities;
    private ShipHitboxManager hitboxes;
    private CustomModelVisualManager modelVisuals;
    private YamlShipHitboxStore hitboxStore;
    private ShipMovementService movementService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MoreShipsConfig config = new MoreShipsConfig(getConfig());

        NamespacedKey shipCoreMarker = new NamespacedKey(this, "ship_core_marker");
        NamespacedKey moduleMarker = new NamespacedKey(this, "module_marker");
        NamespacedKey shipIdKey = new NamespacedKey(this, "ship_id");
        NamespacedKey moduleSlotKey = new NamespacedKey(this, "module_slot");

        Path dataFolder = getDataFolder().toPath();
        shipStore = new YamlShipStore(dataFolder.resolve("ships.yml"));
        bindingStore = new YamlBindingStore(dataFolder.resolve("bindings.yml"));
        moduleEntityStore = new YamlModuleEntityStore(dataFolder.resolve("module-entities.yml"));
        hitboxStore = new YamlShipHitboxStore(dataFolder.resolve("ship-hitboxes.yml"));

        ShipCoreItem shipCoreItem = new ShipCoreItem(
                shipCoreMarker,
                config.shipCoreMaterials(),
                config.shipCoreDisplayNames());
        ModuleItem moduleItem = new ModuleItem(
                moduleMarker,
                config.moduleMaterials(),
                config.moduleDisplayNames());
        shipRegistry = new ShipRegistry(ShipIdentityGenerator.uuid(), new HpCalculator(config.hpMultiplier()));
        org.bukkit.NamespacedKey modelItemKey = new org.bukkit.NamespacedKey("moreships", "ship_small_trim");
        ShipEntitySpawner entitySpawner = new ShipEntitySpawner(shipIdKey, modelItemKey);
        bindingRegistry = new RuntimeBindingRegistry();
        ShipTeardownService teardownService = new ShipTeardownService(shipRegistry, bindingRegistry);
        HullValidator hullValidator = new HullValidator(config.hullMinHardness());
        CargoService cargoService = new CargoService(shipRegistry);
        moduleEntities = new ModuleEntityManager(
                shipIdKey, moduleSlotKey, shipRegistry, bindingRegistry, moduleItem);
        hitboxes = new ShipHitboxManager(
                shipIdKey, bindingRegistry, shipRegistry,
                config.shipHitboxWidth(), config.shipHitboxHeight());
        modelVisuals = new CustomModelVisualManager(bindingRegistry, shipRegistry, getLogger());
        ShipEntityResolver resolver = new ShipEntityResolver(bindingRegistry, hitboxes);

        // Load persisted state before listeners attach
        loadPersistedState();

        ShipCorePlacementListener placementListener = new ShipCorePlacementListener(
                shipCoreItem,
                shipRegistry,
                entitySpawner,
                bindingRegistry,
                config.spawnOffsetX(),
                config.spawnOffsetY(),
                config.spawnOffsetZ(),
                config.placementSound(),
                config.placementSoundVolume(),
                config.placementSoundPitch());
        getServer().getPluginManager().registerEvents(placementListener, this);

        ShipEntityBreakListener breakListener = new ShipEntityBreakListener(
                shipCoreItem, moduleItem, cargoService, moduleEntities,
                resolver, hitboxes, modelVisuals, bindingRegistry, shipRegistry, teardownService);
        getServer().getPluginManager().registerEvents(breakListener, this);

        HullApplicationListener hullListener = new HullApplicationListener(
                shipRegistry, resolver, hullValidator);
        getServer().getPluginManager().registerEvents(hullListener, this);

        getServer().getPluginManager().registerEvents(
                new ShipConfigUiListener(shipRegistry, resolver, moduleItem, moduleEntities,
                        cargoService), this);

        org.bukkit.NamespacedKey cannonMarker = new org.bukkit.NamespacedKey(this, "cannon_shot");
        com.glooshy.ships.combat.CannonService cannonService = new com.glooshy.ships.combat.CannonService(
                shipRegistry, cannonMarker,
                config.cannonDamage(), config.cannonCooldownMillis(), config.cannonSpeed());
        getServer().getPluginManager().registerEvents(
                new ModuleEntityListener(moduleEntities, shipRegistry, cargoService, moduleItem,
                        cannonService), this);
        getServer().getPluginManager().registerEvents(
                new com.glooshy.ships.listener.CannonHitListener(cannonService, resolver), this);

        getServer().getPluginManager().registerEvents(
                new CargoInventoryListener(cargoService), this);

        String packUrl = config.resourcePackUrl();
        if (!packUrl.isBlank()) {
            getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                    event.getPlayer().setResourcePack(packUrl);
                }
            }, this);
            getLogger().info("Resource pack push enabled: " + packUrl);
        }

        if (config.movementEnabled()) {
            getServer().getPluginManager().registerEvents(
                    new ShipPilotListener(shipRegistry, resolver), this);
            movementService = new ShipMovementService(
                    this,
                    shipRegistry,
                    bindingRegistry,
                    moduleEntities,
                    hitboxes,
                    modelVisuals,
                    modelItemKey,
                    shipIdKey,
                    config.movementMaxSpeed(),
                    config.collisionEnabled(),
                    config.collisionMargin(),
                    config.movementAcceleration(),
                    config.movementFriction(),
                    config.physicsRiseVelocity(),
                    config.physicsSinkVelocity(),
                    config.movementTurnRate());
            movementService.start();
            getLogger().info("Movement service started: maxSpeed=" + config.movementMaxSpeed()
                    + " accel=" + config.movementAcceleration()
                    + " friction=" + config.movementFriction());
        } else {
            getLogger().info("Movement disabled in config.");
        }

        registerRecipes(shipCoreItem, moduleItem, config.recipesEnabled());

        ShipsCommand shipsCommand = new ShipsCommand(
                shipCoreItem, moduleItem, shipRegistry, bindingRegistry, placementListener,
                cargoService, moduleEntities, resolver, modelVisuals, this);
        PluginCommand command = getCommand("moreships");
        if (command != null) {
            command.setExecutor(shipsCommand);
            command.setTabCompleter(shipsCommand);
        } else {
            getLogger().severe("Could not find /moreships command — plugin.yml misconfiguration?");
        }

        getLogger().info("MoreShips enabled (BUILD-31). Reverse + sprint boost + reload.");
    }

    @Override
    public void onDisable() {
        if (movementService != null) {
            movementService.stop();
        }
        savePersistedState();
        getLogger().info("MoreShips disabled.");
    }

    /**
     * Crafting recipes (spec L1 par2 + par15, rough per spec - not balanced).
     * Cores: iron+sticks / 3 small+iron+wood / 2 medium+stick. Modules:
     * simple material recipes. Disable with recipes.enabled=false.
     */
    private void registerRecipes(ShipCoreItem cores, ModuleItem modules, boolean enabled) {
        if (!enabled) {
            getLogger().info("Crafting recipes disabled in config.");
            return;
        }

        addRecipeWithItems(key("small_core"), cores.create(ShipSize.SMALL),
                new String[]{"ISI"},
                Map.of('I', new ItemStack(Material.IRON_INGOT),
                        'S', new ItemStack(Material.STICK)));
        addRecipeWithItems(key("medium_core"), cores.create(ShipSize.MEDIUM),
                new String[]{"WIW", "SSS", "WIW"},
                Map.of('W', new ItemStack(Material.OAK_PLANKS),
                        'I', new ItemStack(Material.IRON_INGOT),
                        'S', cores.create(ShipSize.SMALL)));
        addRecipeWithItems(key("large_core"), cores.create(ShipSize.LARGE),
                new String[]{"MMS"},
                Map.of('M', cores.create(ShipSize.MEDIUM),
                        'S', new ItemStack(Material.STICK)));

        addRecipe(key("seat_module"), modules.create(ModuleType.SEAT), "PPP",
                Map.of('P', Material.OAK_PLANKS));
        addRecipe(key("cargo_module"), modules.create(ModuleType.CARGO),
                new String[]{"PPP", "PCP", "PPP"},
                Map.of('P', Material.OAK_PLANKS, 'C', Material.CHEST));
        addRecipe(key("cannon_module"), modules.create(ModuleType.CANNON),
                new String[]{"III", "IFI", "III"},
                Map.of('I', Material.IRON_INGOT, 'F', Material.FIRE_CHARGE));

        getLogger().info("Crafting recipes registered (6).");
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    private void addRecipe(NamespacedKey id, ItemStack result, String shape,
                           Map<Character, Material> ingredients) {
        addRecipe(id, result, new String[]{shape}, ingredients);
    }

    private void addRecipe(NamespacedKey id, ItemStack result, String[] shape,
                           Map<Character, Material> ingredients) {
        org.bukkit.inventory.ShapedRecipe recipe =
                new org.bukkit.inventory.ShapedRecipe(id, result);
        recipe.shape(shape);
        ingredients.forEach(recipe::setIngredient);
        getServer().addRecipe(recipe);
    }

    /** Recipe whose ingredients are exact items (cores as ingredients). */
    private void addRecipeWithItems(NamespacedKey id, ItemStack result, String[] shape,
                                    Map<Character, ItemStack> ingredients) {
        org.bukkit.inventory.ShapedRecipe recipe =
                new org.bukkit.inventory.ShapedRecipe(id, result);
        recipe.shape(shape);
        ingredients.forEach(recipe::setIngredient);
        getServer().addRecipe(recipe);
    }

    private void loadPersistedState() {
        try {
            List<Ship> ships = shipStore.load();
            shipRegistry.load(ships);
            getLogger().info("Loaded " + ships.size() + " ships from disk.");
        } catch (IOException e) {
            getLogger().warning("Failed to load ships.yml: " + e.getMessage());
        }
        try {
            var bindings = bindingStore.load();
            bindingRegistry.load(bindings);
            getLogger().info("Loaded " + bindings.size() + " bindings from disk.");
        } catch (IOException e) {
            getLogger().warning("Failed to load bindings.yml: " + e.getMessage());
        }
        try {
            var moduleEntityBindings = moduleEntityStore.load();
            moduleEntities.load(moduleEntityBindings);
            getLogger().info("Loaded " + moduleEntityBindings.size() + " module entities from disk.");
        } catch (IOException e) {
            getLogger().warning("Failed to load module-entities.yml: " + e.getMessage());
        }
        try {
            var hitboxBindings = hitboxStore.load();
            hitboxes.load(hitboxBindings);
            getLogger().info("Loaded " + hitboxBindings.size() + " ship hitboxes from disk.");
        } catch (IOException e) {
            getLogger().warning("Failed to load ship-hitboxes.yml: " + e.getMessage());
        }
    }

    private void savePersistedState() {
        try {
            List<Ship> ships = shipRegistry.snapshot();
            shipStore.save(ships);
            getLogger().info("Saved " + ships.size() + " ships to disk.");
        } catch (IOException e) {
            getLogger().severe("Failed to save ships.yml: " + e.getMessage());
        }
        try {
            var bindings = bindingRegistry.snapshot();
            bindingStore.save(bindings);
            getLogger().info("Saved " + bindings.size() + " bindings to disk.");
        } catch (IOException e) {
            getLogger().severe("Failed to save bindings.yml: " + e.getMessage());
        }
        try {
            var moduleEntityBindings = moduleEntities.snapshot();
            moduleEntityStore.save(moduleEntityBindings);
            getLogger().info("Saved " + moduleEntityBindings.size() + " module entities to disk.");
        } catch (IOException e) {
            getLogger().severe("Failed to save module-entities.yml: " + e.getMessage());
        }
        try {
            var hitboxBindings = hitboxes.snapshot();
            hitboxStore.save(hitboxBindings);
            getLogger().info("Saved " + hitboxBindings.size() + " ship hitboxes to disk.");
        } catch (IOException e) {
            getLogger().severe("Failed to save ship-hitboxes.yml: " + e.getMessage());
        }
    }
}
