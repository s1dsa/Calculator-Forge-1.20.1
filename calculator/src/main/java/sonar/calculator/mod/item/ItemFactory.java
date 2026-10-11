package sonar.calculator.mod.item;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import sonar.calculator.mod.CalculatorConfig;
import sonar.calculator.mod.item.entity.CalculatorProjectile;
import sonar.calculator.mod.item.misc.*;
import sonar.calculator.mod.item.modules.ModuleItems;
import sonar.calculator.mod.item.tools.*;

public final class ItemFactory {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "calculator");
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "calculator");
    public static final RegistryObject<MenuType<PortableMenu>> PORTABLE_MENU = MENUS.register("portable", () -> IForgeMenuType.create(PortableMenu::new));
    public static final RegistryObject<EntityType<CalculatorProjectile>> SOIL = projectile("soil");
    public static final RegistryObject<EntityType<CalculatorProjectile>> STONE = projectile("smallstone");
    public static final RegistryObject<EntityType<CalculatorProjectile>> BABY_GRENADE = projectile("babygrenade");
    public static final RegistryObject<EntityType<CalculatorProjectile>> GRENADE = projectile("grenade");

    private static RegistryObject<EntityType<CalculatorProjectile>> projectile(String id) {
        return ENTITIES.register(id, () -> EntityType.Builder.<CalculatorProjectile>of(CalculatorProjectile::new, MobCategory.MISC)
                .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("calculator:" + id));
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
        ENTITIES.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ToolConfig.SPEC, "calculator-tools.toml");
        ModuleItems.register(bus);
    }

    public static Item create(String legacyId) {
        String id = legacyId.toLowerCase(Locale.ROOT);
        Item tool = CalculatorTools.create(id);
        if (tool != null) return tool;
        for (String circuit : new String[]{"circuitboard", "circuitdamaged", "circuitdirty"}) {
            if (id.equals(circuit)) return new CircuitItem(circuit.equals("circuitboard"), 0);
            if (id.startsWith(circuit + "_")) {
                int variant = Integer.parseInt(id.substring(circuit.length() + 1));
                if (variant < 1 || variant > 13) throw new IllegalArgumentException("Circuit metadata outside 0..13: " + id);
                return new CircuitItem(circuit.equals("circuitboard"), variant);
            }
        }
        return switch (id) {
            case "calculator" -> new PortableItem("calculator", () -> CalculatorConfig.CALCULATOR_STORAGE);
            case "scientificcalculator" -> new PortableItem("scientific", () -> CalculatorConfig.SCIENTIFIC_CALCULATOR_STORAGE);
            case "craftingcalculator" -> new PortableItem("crafting", () -> CalculatorConfig.CRAFTING_CALCULATOR_STORAGE);
            case "storagemodule" -> new PortableItem("storage", () -> 0);
            case "flawlesscalculator", "infocalculator", "warpmodule", "jumpmodule", "hungermodule", "healthmodule",
                 "nutritionmodule", "terrainmodule", "advancedterrainmodule", "energymodule", "locatormodule" -> module(id);
            case "soil", "smallstone", "babygrenade", "grenade", "enddiamond" -> new ThrowableCalculatorItem(id);
            case "wrench" -> new WrenchItem();
            case "sickle" -> new SickleItem();
            case "obsidiankey" -> new ObsidianKeyItem();
            case "calculatorscreen" -> new CalculatorScreenItem();
            case "broccoliseeds" -> new CalculatorSeed("cropbroccoli", 0, false);
            case "prunaeseeds" -> new CalculatorSeed("cropprunae", 2, false);
            case "fiddledewfruit" -> new CalculatorSeed("cropfiddledew", 3, true);
            case "broccoli" -> food(1, 0.2F);
            case "pear" -> food(12, 2F);
            case "rottenpear" -> food(1, 0.1F);
            case "cookedbroccoli" -> food(9, 0.6F);
            case "coaldust" -> fuel(1000);
            case "enrichedcoal" -> fuel(5000);
            case "purifiedcoal" -> fuel(10000);
            case "firecoal" -> fuel(25000);
            case "controlledfuel" -> fuel(80000);
            case "firediamond" -> fuel(160000);
            case "speedupgrade", "energyupgrade", "voidupgrade", "transferupgrade", "calculatorassembly",
                 "advancedassembly", "atomicmodule", "atomicassembly", "flawlessassembly", "atomicbinder",
                 "enrichedgold", "enrichedgoldingot", "reinforcedironingot", "redstoneingot", "weakeneddiamond",
                 "flawlessdiamond", "electricdiamond", "largeamethyst", "smallamethyst", "shardamethyst",
                 "largetanzanite", "smalltanzanite", "shardtanzanite", "grenadecasing" -> new Item(new Item.Properties());
            default -> throw new IllegalArgumentException("Unknown Calculator item: " + id);
        };
    }

    private static Item module(String id) {
        Item item = ModuleItems.create(id);
        if (item == null) throw new IllegalArgumentException("Module factory did not implement " + id);
        return item;
    }
    private static Item food(int nutrition, float saturation) {
        return new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(nutrition).saturationMod(saturation).build()));
    }
    private static Item fuel(int ticks) {
        return new Item(new Item.Properties()) {
            @Override public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> type) { return ticks; }
        };
    }
    private ItemFactory() {}
}
