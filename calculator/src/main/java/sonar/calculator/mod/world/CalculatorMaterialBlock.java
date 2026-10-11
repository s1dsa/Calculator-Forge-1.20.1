package sonar.calculator.mod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class CalculatorMaterialBlock extends Block {
    public final int variant;
    public CalculatorMaterialBlock(int variant) {
        // No hardness was assigned in MaterialBlock or CalculatorBlocks.
        super(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(0));
        this.variant = variant;
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (variant != 8) return;
        for (int i = 0; i < 3; i++) {
            int x = random.nextInt(2) * 2 - 1, z = random.nextInt(2) * 2 - 1;
            level.addParticle(ParticleTypes.PORTAL, pos.getX() + .5 + .25 * x, pos.getY() + random.nextFloat(),
                    pos.getZ() + .5 + .25 * z, random.nextFloat() * x,
                    (random.nextFloat() - .5) * .125, random.nextFloat() * z);
        }
    }
}
