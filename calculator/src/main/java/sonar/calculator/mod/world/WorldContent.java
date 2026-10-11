package sonar.calculator.mod.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import sonar.calculator.mod.CalculatorContent;

/** Natural blocks and the shared, server-side harvesting operations. */
public final class WorldContent {
    private static final String[] TREES = {"amethyst", "tanzanite", "pear", "diamond"};
    private static final MapColor[] FENCE_COLORS = {
            MapColor.COLOR_PURPLE, MapColor.COLOR_BLUE, MapColor.COLOR_BROWN, MapColor.COLOR_CYAN
    };
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, "calculator");
    private static final List<RegistryObject<CalculatorTreeFeature>> TREE_FEATURES = new ArrayList<>();

    static {
        for (int i = 0; i < TREES.length; i++) {
            final int type = i;
            TREE_FEATURES.add(FEATURES.register(TREES[i], () -> new CalculatorTreeFeature(type)));
        }
    }

    private WorldContent() {}

    public static void register(IEventBus bus) {
        // These features are called by saplings only. Calculator never registered biome generation.
        FEATURES.register(bus);
    }

    public static Block createBlock(String id) {
        for (int type = 0; type < TREES.length; type++) {
            String tree = TREES[type];
            if (id.equals(tree + "log")) return new CalculatorLogBlock();
            if (id.equals(tree + "planks")) return new Block(woodProperties());
            if (id.equals(tree + "stairs")) {
                return new StairBlock(() -> CalculatorContent.block(tree + "planks").defaultBlockState(), woodProperties());
            }
            if (id.equals(tree + "fence")) {
                // The legacy fence constructor never assigned a hardness or resistance.
                return new FenceBlock(BlockBehaviour.Properties.of().mapColor(FENCE_COLORS[type])
                        .sound(SoundType.STONE).strength(0.0F).ignitedByLava());
            }
            if (id.equals(tree + "leaves")) return new CalculatorLeavesBlock(type);
            if (id.equals(tree + "sapling")) return new CalculatorSaplingBlock(type);
        }
        return switch (id) {
            case "material" -> new CalculatorMaterialBlock(0);
            case "material_1" -> new CalculatorMaterialBlock(1);
            case "material_2" -> new CalculatorMaterialBlock(2);
            case "material_3" -> new CalculatorMaterialBlock(3);
            case "material_4" -> new CalculatorMaterialBlock(4);
            case "material_5" -> new CalculatorMaterialBlock(5);
            case "material_6" -> new CalculatorMaterialBlock(6);
            case "material_7" -> new CalculatorMaterialBlock(7);
            case "material_8" -> new CalculatorMaterialBlock(8);
            case "material_9" -> new CalculatorMaterialBlock(9);
            case "cropbroccoli" -> new CalculatorCropBlock(0, 0, "broccoliseeds", "broccoli");
            case "cropprunae" -> new CalculatorCropBlock(1, 2, "prunaeseeds", "coaldust");
            case "cropfiddledew" -> new CalculatorCropBlock(2, 3, "fiddledewfruit", "fiddledewfruit");
            case "flawlessglass" -> new CalculatorConnectedBlock(3, true,
                    BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.6F, 0.12F)
                            // Feedback #1: the flawless glass must sound like glass, not stone.
                            .sound(SoundType.GLASS).noOcclusion().lightLevel(state -> 9)
                            .isRedstoneConductor((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false));
            case "purifiedobsidian" -> new PurifiedObsidianBlock();
            case "amethystpiping" -> new CalculatorPipingBlock(true);
            case "tanzanitepiping" -> new CalculatorPipingBlock(false);
            default -> null;
        };
    }

    private static BlockBehaviour.Properties woodProperties() {
        // Legacy setHardness also raised internal resistance; the effective explosion value was hardness / 5.
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.7F, 0.14F)
                .sound(SoundType.STONE).ignitedByLava();
    }

    static BlockBehaviour.Properties plantProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().noOcclusion()
                .instabreak().randomTicks().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY);
    }

    static String treeName(int type) {
        return TREES[type];
    }

    static Holder<ConfiguredFeature<?, ?>> treeFeature(int type) {
        return Holder.direct(new ConfiguredFeature<>(TREE_FEATURES.get(type).get(), NoneFeatureConfiguration.INSTANCE));
    }

    public static boolean isLeafReady(BlockState state) {
        return state.getBlock() instanceof CalculatorLeavesBlock
                && state.getValue(CalculatorLeavesBlock.GROWTH).isReady();
    }

    /**
     * Upstream TreeHarvestRecipes semantics: consumes the current pear/diamond leaf growth once.
     * READY gives the first result; MATURED gives both. Override forces both, even on an unripe leaf.
     * Does not spawn items: the caller inserts or drops the returned, independent stacks.
     */
    public static List<ItemStack> harvestLeaves(Level level, BlockPos pos, boolean override) {
        if (level.isClientSide) return List.of();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CalculatorLeavesBlock leaves) || leaves.leafType < 2) return List.of();
        CalculatorLeavesBlock.LeafGrowth growth = state.getValue(CalculatorLeavesBlock.GROWTH);
        level.setBlock(pos, state.setValue(CalculatorLeavesBlock.GROWTH, CalculatorLeavesBlock.LeafGrowth.FRESH), Block.UPDATE_ALL);
        List<ItemStack> drops = new ArrayList<>(2);
        if (override || growth.isReady()) {
            drops.add(CalculatorContent.stack(leaves.leafType == 2 ? "pear" : "weakeneddiamond", 1));
        }
        if (override || growth == CalculatorLeavesBlock.LeafGrowth.MATURED) {
            drops.add(CalculatorContent.stack(leaves.leafType == 2 ? "rottenpear" : "flawlessdiamond", 1));
        }
        return drops;
    }

    /** Returns one nutrition point and resets a ripe amethyst (hunger) or tanzanite (health) leaf. */
    public static int harvestNutrition(Level level, BlockPos pos, boolean hunger) {
        if (level.isClientSide) return 0;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CalculatorLeavesBlock leaves
                && leaves.leafType == (hunger ? 0 : 1) && isLeafReady(state)) {
            level.setBlock(pos, state.setValue(CalculatorLeavesBlock.GROWTH, CalculatorLeavesBlock.LeafGrowth.FRESH), Block.UPDATE_ALL);
            return 1;
        }
        return 0;
    }

    public static boolean canKeyDrop(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.getBlock() instanceof PurifiedObsidianBlock;
    }

    /** All legacy ConnectedBlocks implement IStableBlock, including the two decorative pipes. */
    public static boolean isStableBlock(BlockState state) {
        return state.getBlock() instanceof CalculatorConnectedBlock;
    }
}
