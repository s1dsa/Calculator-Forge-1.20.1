package sonar.calculator.mod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import sonar.calculator.mod.CalculatorContent;

public final class CalculatorSaplingBlock extends SaplingBlock {
    public final int type;
    public CalculatorSaplingBlock(int type) {
        super(new AbstractTreeGrower() {
            @Override protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean flowers) {
                return null;
            }
            @Override public boolean growTree(ServerLevel level, ChunkGenerator generator, BlockPos pos,
                                              BlockState state, RandomSource random) {
                // Feedback #16: mirror the original CalculatorSaplings.generateTree / vanilla
                // AbstractTreeGrower. Clear the sapling before generating and restore this exact
                // state when generation fails, so a successful tree leaves no sapling behind and a
                // failed attempt never consumes or changes the sapling.
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 4);
                if (WorldContent.treeFeature(type).value().place(level, generator, random, pos)) {
                    return true;
                }
                level.setBlock(pos, state, 4);
                return false;
            }
        }, WorldContent.plantProperties());
        this.type = type;
    }
    @Override protected boolean mayPlaceOn(BlockState soil, BlockGetter level, BlockPos pos) {
        return type == 3 ? soil.is(CalculatorContent.block("material_8"))
                : soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.FARMLAND);
    }
    /**
     * Feedback #17: the legacy soil rule must be authoritative. Forge's {@code BushBlock.canSurvive}
     * accepts a soil whose {@code canSustainPlant(...)} allows the plant, and vanilla grass/dirt do -
     * so the diamond sapling could sit on grass even though the original only grows it on the end
     * diamond block. Calling {@link #mayPlaceOn} directly keeps placement and growth on the same rule
     * (and still refuses a normal sapling on the end diamond block).
     */
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mayPlaceOn(level.getBlockState(pos.below()), level, pos);
    }
}
