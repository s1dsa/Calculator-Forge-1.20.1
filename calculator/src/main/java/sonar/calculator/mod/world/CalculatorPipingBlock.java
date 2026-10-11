package sonar.calculator.mod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import sonar.calculator.mod.CalculatorContent;

/** Legacy Piping is decorative W.I.P.; its narrowed collision boxes were commented out. */
public final class CalculatorPipingBlock extends CalculatorConnectedBlock {
    private final boolean hunger;
    public CalculatorPipingBlock(boolean hunger) {
        super(hunger ? -1 : -2, false, BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                .strength(1, 20).noOcclusion());
        this.hunger = hunger;
    }
    @Override protected boolean connects(BlockGetter level, BlockPos pos, Direction direction) {
        var state = level.getBlockState(pos.relative(direction));
        return state.is(CalculatorContent.block(hunger ? "amethystlog" : "tanzanitelog"))
                || state.is(CalculatorContent.block(hunger ? "hungerprocessor" : "healthprocessor"))
                || super.connects(level, pos, direction);
    }
}
