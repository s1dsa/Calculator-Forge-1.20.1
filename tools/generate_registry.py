from pathlib import Path
import json
root = Path(__file__).resolve().parents[1]
data = json.loads((root / 'notes/registry-map.json').read_text(encoding='utf-8'))
target = root / 'calculator/src/main/java/sonar/calculator/mod/CalculatorContent.java'
if target.exists():
    raise SystemExit('CalculatorContent already exists; review it before regeneration')
blocks = ',\n            '.join('"'+s+'"' for s in data['block_ids'])
items = ',\n            '.join('"'+s+'"' for s in data['item_ids'])
code = '''package sonar.calculator.mod;

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
            BLOCK_LIST);
    public static final List<String> ITEM_IDS = List.of(
            ITEM_LIST);

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
                .title(Component.literal("Calculator"))
                .icon(() -> stack("calculator", 1))
                .displayItems((parameters, output) -> ITEM_MAP.forEach((id, item) -> {
                    if (!HIDDEN.contains(id)) output.accept(item.get());
                })).build());
    }

    private static Block createBlock(String id) {
        Block natural = WorldContent.createBlock(id);
        if (natural != null) return natural;
        return new MachineBlock(id, BlockBehaviour.Properties.of().strength(1.0F, 12.0F));
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
'''.replace('BLOCK_LIST', blocks).replace('ITEM_LIST', items)
target.write_text(code, encoding='utf-8')
print('Created registry facade:', len(data['block_ids']), 'blocks,', len(data['item_ids']), 'independent items')
