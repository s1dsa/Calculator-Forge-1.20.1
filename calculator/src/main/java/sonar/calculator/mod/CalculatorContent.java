package sonar.calculator.mod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import sonar.calculator.mod.item.ItemFactory;
import sonar.calculator.mod.machine.MachineBlock;
import sonar.calculator.mod.world.WorldContent;

public final class CalculatorContent {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Calculator.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Calculator.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Calculator.MODID);
    private static final Map<String, RegistryObject<Block>> BLOCK_MAP = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Item>> ITEM_MAP = new LinkedHashMap<>();
    private static final Set<String> HIDDEN = Set.of("conductormastblock", "weatherstationblock", "transmitterblock", "scarecrowblock", "calculatorscreenblock", "gaslanternon");
    public static final List<String> BLOCK_IDS = List.of(
            "flawlessglass",
            "purifiedobsidian",
            "powercube",
            "advancedpowercube",
            "creativepowercube",
            "atomiccalculator",
            "dynamiccalculator",
            "moduleworkstation",
            "reinforcedfurnace",
            "reinforcedchest",
            "stoneseparator",
            "algorithmseparator",
            "hungerprocessor",
            "healthprocessor",
            "amethystpiping",
            "tanzanitepiping",
            "dockingstation",
            "atomicmultiplier",
            "extractionchamber",
            "restorationchamber",
            "reassemblychamber",
            "precisionchamber",
            "processingchamber",
            "analysingchamber",
            "storagechamber",
            "fabricationchamber",
            "basicgreenhouse",
            "advancedgreenhouse",
            "flawlessgreenhouse",
            "co2generator",
            "calculatorlocator",
            "calculatorplug",
            "conductormast",
            "conductormastblock",
            "weatherstation",
            "weatherstationblock",
            "transmitter",
            "transmitterblock",
            "starchextractor",
            "redstoneextractor",
            "glowstoneextractor",
            "handcrankedgenerator",
            "crankhandle",
            "magneticflux",
            "weathercontroller",
            "rainsensor",
            "stoneassimilator",
            "algorithmassimilator",
            "gaslanternoff",
            "gaslanternon",
            "lantern",
            "scarecrow",
            "scarecrowblock",
            "amethystlog",
            "amethystplanks",
            "amethyststairs",
            "amethystfence",
            "amethystleaves",
            "amethystsapling",
            "tanzanitelog",
            "tanzaniteplanks",
            "tanzanitestairs",
            "tanzanitefence",
            "tanzaniteleaves",
            "tanzanitesapling",
            "pearlog",
            "pearplanks",
            "pearstairs",
            "pearfence",
            "pearleaves",
            "pearsapling",
            "diamondlog",
            "diamondplanks",
            "diamondstairs",
            "diamondfence",
            "diamondleaves",
            "diamondsapling",
            "calculatorscreenblock",
            "material",
            "cropbroccoli",
            "cropprunae",
            "cropfiddledew",
            "material_1",
            "material_2",
            "material_3",
            "material_4",
            "material_5",
            "material_6",
            "material_7",
            "material_8",
            "material_9");
    public static final List<String> ITEM_IDS = List.of(
            "infocalculator",
            "calculator",
            "craftingcalculator",
            "scientificcalculator",
            "flawlesscalculator",
            "storagemodule",
            "warpmodule",
            "jumpmodule",
            "hungermodule",
            "healthmodule",
            "nutritionmodule",
            "terrainmodule",
            "advancedterrainmodule",
            "energymodule",
            "locatormodule",
            "soil",
            "smallstone",
            "speedupgrade",
            "energyupgrade",
            "voidupgrade",
            "transferupgrade",
            "calculatorscreen",
            "calculatorassembly",
            "advancedassembly",
            "atomicmodule",
            "atomicassembly",
            "flawlessassembly",
            "atomicbinder",
            "wrench",
            "sickle",
            "obsidiankey",
            "reinforcedsword",
            "enrichedgoldsword",
            "reinforcedironsword",
            "redstonesword",
            "weakeneddiamondsword",
            "flawlessdiamondsword",
            "firediamondsword",
            "electricsword",
            "endforgedsword",
            "reinforcedpickaxe",
            "enrichedgoldpickaxe",
            "reinforcedironpickaxe",
            "redstonepickaxe",
            "weakeneddiamondpickaxe",
            "flawlessdiamondpickaxe",
            "firediamondpickaxe",
            "electricpickaxe",
            "endforgedpickaxe",
            "reinforcedaxe",
            "enrichedgoldaxe",
            "reinforcedironaxe",
            "redstoneaxe",
            "weakeneddiamondaxe",
            "flawlessdiamondaxe",
            "firediamondaxe",
            "electricaxe",
            "endforgedaxe",
            "reinforcedshovel",
            "enrichedgoldshovel",
            "reinforcedironshovel",
            "redstoneshovel",
            "weakeneddiamondshovel",
            "flawlessdiamondshovel",
            "firediamondshovel",
            "electricshovel",
            "endforgedshovel",
            "reinforcedhoe",
            "enrichedgoldhoe",
            "reinforcedironhoe",
            "redstonehoe",
            "weakeneddiamondhoe",
            "flawlessdiamondhoe",
            "firediamondhoe",
            "electrichoe",
            "endforgedhoe",
            "enrichedgold",
            "enrichedgoldingot",
            "reinforcedironingot",
            "redstoneingot",
            "weakeneddiamond",
            "flawlessdiamond",
            "firediamond",
            "electricdiamond",
            "enddiamond",
            "largeamethyst",
            "smallamethyst",
            "shardamethyst",
            "largetanzanite",
            "smalltanzanite",
            "shardtanzanite",
            "broccoliseeds",
            "prunaeseeds",
            "fiddledewfruit",
            "broccoli",
            "pear",
            "rottenpear",
            "cookedbroccoli",
            "coaldust",
            "enrichedcoal",
            "purifiedcoal",
            "firecoal",
            "controlledfuel",
            "grenadecasing",
            "babygrenade",
            "grenade",
            "circuitboard",
            "circuitdamaged",
            "circuitdirty",
            "circuitboard_1",
            "circuitboard_2",
            "circuitboard_3",
            "circuitboard_4",
            "circuitboard_5",
            "circuitboard_6",
            "circuitboard_7",
            "circuitboard_8",
            "circuitboard_9",
            "circuitboard_10",
            "circuitboard_11",
            "circuitboard_12",
            "circuitboard_13",
            "circuitdamaged_1",
            "circuitdamaged_2",
            "circuitdamaged_3",
            "circuitdamaged_4",
            "circuitdamaged_5",
            "circuitdamaged_6",
            "circuitdamaged_7",
            "circuitdamaged_8",
            "circuitdamaged_9",
            "circuitdamaged_10",
            "circuitdamaged_11",
            "circuitdamaged_12",
            "circuitdamaged_13",
            "circuitdirty_1",
            "circuitdirty_2",
            "circuitdirty_3",
            "circuitdirty_4",
            "circuitdirty_5",
            "circuitdirty_6",
            "circuitdirty_7",
            "circuitdirty_8",
            "circuitdirty_9",
            "circuitdirty_10",
            "circuitdirty_11",
            "circuitdirty_12",
            "circuitdirty_13");

    static {
        for (String id : BLOCK_IDS) {
            RegistryObject<Block> block = BLOCKS.register(id, () -> createBlock(id));
            BLOCK_MAP.put(id, block);
            ITEM_MAP.put(id, ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
        }
        for (String id : ITEM_IDS) {
            ITEM_MAP.put(id, ITEMS.register(id, () -> ItemFactory.create(id)));
        }
        TABS.register("calculator", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.Calculator"))
                .icon(() -> stack("calculator", 1))
                .displayItems((parameters, output) -> ITEM_MAP.forEach((id, item) -> {
                    if (!HIDDEN.contains(id)) output.accept(item.get());
                })).build());
    }

    private static Block createBlock(String id) {
        Block natural = WorldContent.createBlock(id);
        if (natural != null) return natural;
        var properties = BlockBehaviour.Properties.of().strength(1.0F, 12.0F);
        if (MachineBlock.hasOpenGeometry(id)) properties.noOcclusion();
        if (id.equals("lantern")) properties.lightLevel(state -> 15);
        if (id.equals("gaslanternon") || id.equals("gaslanternoff"))
            properties.lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 15 : 0);
        return new MachineBlock(id, properties);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }

    public static Item item(String id) {
        RegistryObject<Item> entry = ITEM_MAP.get(id);
        if (entry == null) throw new IllegalArgumentException("Unknown Calculator item: " + id);
        return entry.get();
    }

    public static Block block(String id) {
        RegistryObject<Block> entry = BLOCK_MAP.get(id);
        if (entry == null) throw new IllegalArgumentException("Unknown Calculator block: " + id);
        return entry.get();
    }

    public static ItemStack stack(String id, int count) {
        return new ItemStack(item(id), count);
    }

    private CalculatorContent() {}
}
