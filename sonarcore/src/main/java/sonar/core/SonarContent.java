package sonar.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import sonar.core.block.ConnectedBlock;
import sonar.core.block.SonarFenceBlock;

/** The 66 blocks/BlockItems actually registered by upstream SonarBlocks; registry IDs are unchanged. */
public final class SonarContent {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SonarCore.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SonarCore.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SonarCore.MODID);
    public static final TagKey<Block> STABLE_STONE = TagKey.create(Registries.BLOCK, new ResourceLocation(SonarCore.MODID, "stable_stone"));
    public static final TagKey<Item> STABLE_STONE_ITEMS = TagKey.create(Registries.ITEM, STABLE_STONE.location());
    public static final String[] COLORS = {"normal", "black", "blue", "brown", "cyan", "green", "lightblue", "lightgrey", "lime", "magenta", "orange", "pink", "plain", "purple", "red", "yellow"};
    private static final Map<String, RegistryObject<Block>> BLOCK_BY_ID = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Item>> ITEM_BY_ID = new LinkedHashMap<>();

    static {
        buildingSet("reinforcedstone", "reinforcedstoneblock", false);
        buildingSet("reinforcedstonebrick", "reinforcedstonebrick", false);
        buildingSet("reinforceddirt", "reinforceddirtblock", true);
        buildingSet("reinforceddirtbrick", "reinforceddirtbrick", true);
        String[] styles = {"stablestone", "stablestonerimmed", "stablestoneblackrimmed"};
        for (int style = 0; style < styles.length; style++) {
            for (int color = 0; color < COLORS.length; color++) {
                final int connection = (style + 1) * 100 + color;
                add(styles[style] + "_" + COLORS[color], () -> new ConnectedBlock(stone(), connection));
            }
        }
        add("stableglass", () -> new ConnectedBlock.Glass(glass(), 1));
        add("clearstableglass", () -> new ConnectedBlock.Glass(glass(), 2));
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("sonarcore", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.sonarcore"))
            .icon(() -> item("reinforcedstoneblock").getDefaultInstance())
            .displayItems((parameters, output) -> ITEM_BY_ID.values().forEach(item -> output.accept(item.get())))
            .build());

    private SonarContent() { }

    private static BlockBehaviour.Properties stone() {
        // 1.12 setResistance multiplies by 3, getExplosionResistance divides by 5: 50 -> 30.
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F, 30.0F).sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties base(boolean dirt) {
        return dirt ? BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(1.0F, 12.0F).sound(SoundType.STONE) : stone();
    }

    private static BlockBehaviour.Properties glass() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.6F, 30.0F).noOcclusion()
                .isValidSpawn((state, level, pos, entity) -> false).isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)
                .lightLevel(state -> 9).sound(SoundType.STONE);
    }

    private static void buildingSet(String prefix, String fullBlock, boolean dirt) {
        RegistryObject<Block> base = add(fullBlock, () -> new Block(base(dirt)));
        add(prefix + "stairs", () -> new StairBlock(() -> base.get().defaultBlockState(), base(dirt).noOcclusion()));
        add(prefix + "fence", () -> new SonarFenceBlock(base(dirt).strength(2.0F, 6.0F), dirt));
        // The upstream gate uses vanilla oak mechanics, with default zero strength.
        add(prefix + "gate", () -> new FenceGateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                .strength(0.0F).sound(SoundType.STONE), WoodType.OAK));
    }

    private static RegistryObject<Block> add(String id, Supplier<? extends Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(id, factory);
        BLOCK_BY_ID.put(id, block);
        ITEM_BY_ID.put(id, ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
        return block;
    }

    public static Block block(String id) {
        RegistryObject<Block> value = BLOCK_BY_ID.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown SonarCore block: " + id);
        return value.get();
    }

    public static Item item(String id) {
        RegistryObject<Item> value = ITEM_BY_ID.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown SonarCore item: " + id);
        return value.get();
    }

    public static Collection<String> ids() { return Collections.unmodifiableSet(BLOCK_BY_ID.keySet()); }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
