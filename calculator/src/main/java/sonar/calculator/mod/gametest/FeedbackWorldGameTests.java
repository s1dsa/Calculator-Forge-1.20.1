package sonar.calculator.mod.gametest;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.world.CalculatorLeavesBlock;
import sonar.calculator.mod.world.CalculatorLogBlock;

/**
 * World feedback tests (items 1, 15, 16, 17). These exercise the real registered blocks, the real
 * sapling -> tree feature growth path (bonemeal/advanceTree, not a stub), and the real neighbour
 * shape state machine that feeds the flawless glass blockstate variants. Client RenderType
 * registration is a client-side concern and cannot be asserted from a server GameTest; the required
 * layers are listed in notes/feedback-world.md.
 */
@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
public final class FeedbackWorldGameTests {

    // ---------------------------------------------------------------- #1 flawless glass

    @GameTest(template = "test_empty")
    public static void flawlessGlassUsesTheGlassSoundGroup(GameTestHelper helper) {
        Block glass = CalculatorContent.block("flawlessglass");
        BlockState state = glass.defaultBlockState();
        SoundType sound = state.getSoundType();
        helper.assertTrue(sound == SoundType.GLASS, "flawlessglass sound group is " + sound + ", expected GLASS (feedback #1)");
        helper.assertTrue(sound != SoundType.STONE, "flawlessglass still carries the old stone sound group");
        helper.assertTrue(sound.getBreakSound() == SoundType.GLASS.getBreakSound()
                        && sound.getPlaceSound() == SoundType.GLASS.getPlaceSound()
                        && sound.getStepSound() == SoundType.GLASS.getStepSound(),
                "flawlessglass sound events differ from vanilla glass");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void flawlessGlassConnectsOnlyToItsOwnKind(GameTestHelper helper) {
        Block glass = CalculatorContent.block("flawlessglass");
        BlockState lone = glass.defaultBlockState();
        helper.assertTrue(lone.getProperties().containsAll(PipeBlock.PROPERTY_BY_DIRECTION.values()),
                "flawlessglass does not expose the six connection properties its 64 blockstate variants require");
        for (BooleanProperty property : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
            helper.assertFalse(lone.getValue(property), "flawlessglass default state already connected on " + property.getName());
        }

        BlockPos first = new BlockPos(1, 1, 1);
        BlockPos east = new BlockPos(2, 1, 1); // directly east of first
        BlockPos south = new BlockPos(1, 1, 2); // directly south of first
        helper.setBlock(first, glass);
        helper.setBlock(east, glass);
        recomputeShape(helper, first);
        recomputeShape(helper, east);

        helper.assertTrue(helper.getBlockState(first).getValue(PipeBlock.EAST), "adjacent flawless glass did not connect east");
        helper.assertTrue(helper.getBlockState(east).getValue(PipeBlock.WEST), "adjacent flawless glass did not connect west (reciprocal)");
        for (BooleanProperty property : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
            if (property != PipeBlock.EAST) {
                helper.assertFalse(helper.getBlockState(first).getValue(property),
                        "unexpected flawless glass connection on " + property.getName());
            }
        }

        // A different connected family (purified obsidian, target 4) shares the marker but not the target.
        helper.setBlock(south, CalculatorContent.block("purifiedobsidian"));
        recomputeShape(helper, first);
        helper.assertFalse(helper.getBlockState(first).getValue(PipeBlock.SOUTH),
                "flawless glass connected to a foreign connected block (wrong target)");

        // Removing the neighbour releases the connection again.
        helper.setBlock(east, Blocks.AIR);
        recomputeShape(helper, first);
        helper.assertFalse(helper.getBlockState(first).getValue(PipeBlock.EAST),
                "stale flawless glass connection survived the neighbour removal");
        helper.succeed();
    }

    /** Runs the real vanilla neighbour-shape recomputation and writes the result back. */
    private static void recomputeShape(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relative);
        BlockState updated = Block.updateFromNeighbourShapes(level.getBlockState(absolute), level, absolute);
        level.setBlock(absolute, updated, Block.UPDATE_ALL);
    }

    // ---------------------------------------------------------------- #15 crops / saplings / leaves

    @GameTest(template = "test_empty")
    public static void plantBlocksAreCollisionlessCutoutCrosses(GameTestHelper helper) {
        BlockPos soilPos = new BlockPos(1, 0, 1);
        BlockPos plantPos = new BlockPos(1, 1, 1);
        String[] plants = {"amethystsapling", "tanzanitesapling", "pearsapling", "diamondsapling",
                "cropbroccoli", "cropprunae", "cropfiddledew"};
        for (String id : plants) {
            Block soil = id.equals("diamondsapling") ? CalculatorContent.block("material_8")
                    : id.startsWith("crop") ? Blocks.FARMLAND : Blocks.GRASS_BLOCK;
            Block plant = CalculatorContent.block(id);
            helper.setBlock(soilPos, soil);
            helper.setBlock(plantPos, plant);
            BlockState placed = helper.getBlockState(plantPos);
            helper.assertTrue(placed.getBlock() == plant, id + ": plant block was not placed / wrong registered family");
            helper.assertTrue(placed.getCollisionShape(helper.getLevel(), helper.absolutePos(plantPos)).isEmpty(),
                    id + ": plant blocks movement, but the original crops and saplings are collisionless");
            helper.assertFalse(placed.canOcclude(), id + ": plant occludes, so it cannot render as a cutout cross");
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void treeLeavesAreNonOccluding(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        for (String id : new String[]{"amethystleaves", "tanzaniteleaves", "pearleaves", "diamondleaves"}) {
            Block leaves = CalculatorContent.block(id);
            helper.setBlock(pos, leaves);
            BlockState placed = helper.getBlockState(pos);
            helper.assertTrue(placed.getBlock() == leaves && placed.getBlock() instanceof CalculatorLeavesBlock,
                    id + ": unexpected registered leaves block");
            helper.assertFalse(placed.canOcclude(), id + ": leaves occlude, so they cannot render as cutout_mipped");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- #16 sapling consumption

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void successfulGrowthConsumesTheSapling(GameTestHelper helper) {
        BlockPos soilPos = new BlockPos(2, 1, 2);
        BlockPos saplingPos = new BlockPos(2, 2, 2);
        clearGrowthSpace(helper, saplingPos);
        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);

        Block sapling = CalculatorContent.block("pearsapling");
        BlockPos absolute = helper.absolutePos(saplingPos);
        ServerLevel level = helper.getLevel();
        level.setBlock(absolute, stage1(sapling), Block.UPDATE_ALL);
        growNow(level, sapling, absolute, 11L);

        BlockState base = helper.getBlockState(saplingPos);
        helper.assertTrue(base.getBlock() instanceof CalculatorLogBlock,
                "successful growth left no trunk at the sapling position (feedback #16): " + base);
        helper.assertFalse(base.getBlock() instanceof SaplingBlock, "the sapling survived its own successful growth");
        helper.assertTrue(anyIn(helper, saplingPos, 2, 8, state -> state.getBlock() instanceof CalculatorLeavesBlock),
                "successful growth produced no leaves");
        helper.assertTrue(helper.getBlockState(soilPos).is(Blocks.DIRT),
                "successful growth did not perform the soil conversion: " + helper.getBlockState(soilPos));
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void failedGrowthKeepsTheExactSapling(GameTestHelper helper) {
        BlockPos soilPos = new BlockPos(2, 1, 2);
        BlockPos saplingPos = new BlockPos(2, 2, 2);
        clearGrowthSpace(helper, saplingPos);
        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        // A non-replaceable obstruction inside the trunk volume makes generation fail before it writes.
        helper.setBlock(saplingPos.offset(0, 3, 0), Blocks.STONE);

        Block sapling = CalculatorContent.block("pearsapling");
        BlockPos absolute = helper.absolutePos(saplingPos);
        ServerLevel level = helper.getLevel();
        level.setBlock(absolute, stage1(sapling), Block.UPDATE_ALL);
        growNow(level, sapling, absolute, 3L);

        BlockState kept = helper.getBlockState(saplingPos);
        helper.assertTrue(kept.getBlock() == sapling && kept.getValue(SaplingBlock.STAGE) == 1,
                "failed growth did not preserve the exact sapling state: " + kept);
        helper.assertFalse(anyIn(helper, saplingPos, 2, 8, state -> state.getBlock() instanceof CalculatorLogBlock),
                "failed growth still wrote a trunk");
        helper.assertFalse(anyIn(helper, saplingPos, 2, 8, state -> state.getBlock() instanceof CalculatorLeavesBlock),
                "failed growth still wrote leaves");
        helper.assertTrue(helper.getBlockState(soilPos).is(Blocks.GRASS_BLOCK),
                "failed growth converted the soil even though it produced no tree");
        helper.succeed();
    }

    // ---------------------------------------------------------------- #17 diamond tree

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void diamondSaplingGrowsOnTheEndDiamondBlock(GameTestHelper helper) {
        BlockPos soilPos = new BlockPos(2, 1, 2);
        BlockPos saplingPos = new BlockPos(2, 2, 2);
        clearGrowthSpace(helper, saplingPos);
        helper.setBlock(soilPos, CalculatorContent.block("material_8"));

        Block sapling = CalculatorContent.block("diamondsapling");
        BlockPos absolute = helper.absolutePos(saplingPos);
        ServerLevel level = helper.getLevel();
        BlockState stage1 = stage1(sapling);
        level.setBlock(absolute, stage1, Block.UPDATE_ALL);
        helper.assertTrue(stage1.canSurvive(level, absolute),
                "diamond sapling cannot survive on material_8 (the end diamond block)");

        growNow(level, sapling, absolute, 7L);
        BlockState base = helper.getBlockState(saplingPos);
        helper.assertTrue(base.getBlock() instanceof CalculatorLogBlock,
                "diamond sapling did not grow into a trunk on the end diamond block (feedback #17): " + base);
        helper.assertFalse(base.getBlock() instanceof SaplingBlock, "the diamond sapling survived its own growth");
        helper.assertTrue(anyIn(helper, saplingPos, 2, 8, state -> state.getBlock() instanceof CalculatorLeavesBlock),
                "the diamond tree grew no leaves");
        helper.assertTrue(helper.getBlockState(soilPos).is(Blocks.GRASS_BLOCK),
                "the diamond tree did not apply its original soil conversion: " + helper.getBlockState(soilPos));
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void diamondSaplingRejectsForeignSoil(GameTestHelper helper) {
        BlockPos soilPos = new BlockPos(2, 1, 2);
        BlockPos saplingPos = new BlockPos(2, 2, 2);
        clearGrowthSpace(helper, saplingPos);
        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);

        Block diamond = CalculatorContent.block("diamondsapling");
        BlockPos absolute = helper.absolutePos(saplingPos);
        ServerLevel level = helper.getLevel();
        BlockState stage1 = stage1(diamond);
        level.setBlock(absolute, stage1, Block.UPDATE_ALL);
        helper.assertFalse(stage1.canSurvive(level, absolute), "the diamond sapling accepted a grass block as soil");

        growNow(level, diamond, absolute, 7L);
        BlockState kept = helper.getBlockState(saplingPos);
        helper.assertTrue(kept.getBlock() == diamond && kept.getValue(SaplingBlock.STAGE) == 1,
                "failed diamond growth did not keep the exact sapling: " + kept);
        helper.assertFalse(anyIn(helper, saplingPos, 2, 8, state -> state.getBlock() instanceof CalculatorLogBlock),
                "the diamond tree grew on the wrong soil");

        // The ordinary saplings must not accept the end diamond block either.
        Block pear = CalculatorContent.block("pearsapling");
        helper.setBlock(soilPos, CalculatorContent.block("material_8"));
        helper.assertFalse(pear.defaultBlockState().canSurvive(level, absolute),
                "a normal sapling accepted the end diamond block as soil");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static BlockState stage1(Block sapling) {
        return sapling.defaultBlockState().setValue(SaplingBlock.STAGE, 1);
    }

    /** Runs the real sapling growth path: Forge sapling event -> tree feature, exactly like bonemeal. */
    private static void growNow(ServerLevel level, Block sapling, BlockPos absolute, long seed) {
        ((SaplingBlock) sapling).performBonemeal(level, RandomSource.create(seed), absolute, level.getBlockState(absolute));
    }

    /** Guarantees the whole trunk/canopy volume is air so growth is decided by the feature, not terrain. */
    private static void clearGrowthSpace(GameTestHelper helper, BlockPos sapling) {
        for (int dy = 0; dy <= 9; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    helper.setBlock(sapling.offset(dx, dy, dz), Blocks.AIR);
                }
            }
        }
    }

    private static boolean anyIn(GameTestHelper helper, BlockPos origin, int radius, int height, Predicate<BlockState> match) {
        for (int dy = -1; dy <= height; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (match.test(helper.getBlockState(origin.offset(dx, dy, dz)))) return true;
                }
            }
        }
        return false;
    }

    private FeedbackWorldGameTests() {}
}
