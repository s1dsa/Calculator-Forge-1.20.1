package sonar.calculator.mod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import sonar.calculator.mod.CalculatorContent;

/** Upstream CalculatorTreeBuilder, called only by a sapling. */
public final class CalculatorTreeFeature extends Feature<NoneFeatureConfiguration> {
    private final int type;
    public CalculatorTreeFeature(int type) { super(NoneFeatureConfiguration.CODEC); this.type = type; }
    private boolean replaceable(BlockState state) {
        return state.isAir() || state.is(BlockTags.LEAVES) || state.getBlock() instanceof CalculatorLeavesBlock
                || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(BlockTags.LOGS)
                || state.getBlock() instanceof CalculatorLogBlock || state.is(BlockTags.SAPLINGS)
                || state.getBlock() instanceof CalculatorSaplingBlock || state.is(Blocks.VINE);
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level(); BlockPos pos = context.origin(); RandomSource random = context.random();
        int height = 4 + random.nextInt(2) + random.nextInt(3);
        if (pos.getY() < level.getMinBuildHeight() + 1 || pos.getY() + height + 1 >= level.getMaxBuildHeight()) return false;
        for (int y = 0; y <= height + 1; y++) {
            int radius = y == 0 ? 0 : y >= height - 1 ? 2 : 1;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
                if (!replaceable(level.getBlockState(pos.offset(x, y, z)))) return false;
        }
        BlockState soil = level.getBlockState(pos.below());
        // The original builder asks canSustainPlant, even for diamond. MaterialBlock never
        // implemented it, so the legacy diamond sapling could be planted on the end diamond block
        // but its tree generation always failed ("planted but never grows"). Feedback #17 asks for
        // that old bug to be corrected, so the diamond soil is now accepted explicitly while the
        // other trees keep the vanilla soils.
        if (type == 3) {
            if (!soil.is(CalculatorContent.block("material_8"))) return false;
        } else if (!(soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.FARMLAND))) {
            return false;
        }
        // Soil conversion matches CalculatorTreeBuilder: the diamond tree replaces its own soil
        // (the end diamond block) with grass, exactly as the original builder did; the other trees
        // keep the port's existing dirt conversion.
        level.setBlock(pos.below(), (type == 3 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 3);
        BlockState leaf = CalculatorContent.block(WorldContent.treeName(type) + "leaves").defaultBlockState();
        for (int y = height - 3; y <= height; y++) {
            int delta = y - height, radius = 1 - delta / 2;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) == radius && Math.abs(z) == radius && !(random.nextInt(2) != 0 && delta != 0)) continue;
                BlockPos at = pos.offset(x, y, z); BlockState old = level.getBlockState(at);
                if (old.isAir() || old.is(BlockTags.LEAVES) || old.getBlock() instanceof CalculatorLeavesBlock) level.setBlock(at, leaf, 3);
            }
        }
        for (int y = 0; y < height; y++) {
            BlockPos at = pos.above(y); BlockState old = level.getBlockState(at);
            // Feedback #16: the trunk also has to replace the sapling it grew from, so a successful
            // tree can never leave the sapling behind even if the caller did not clear it first.
            if (old.isAir() || old.is(BlockTags.LEAVES) || old.getBlock() instanceof CalculatorLeavesBlock
                    || old.getBlock() instanceof CalculatorSaplingBlock)
                level.setBlock(at, CalculatorContent.block(WorldContent.treeName(type) + "log").defaultBlockState(), 3);
        }
        return true;
    }
}
