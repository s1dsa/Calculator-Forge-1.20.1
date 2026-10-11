package sonar.core.block;

import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

/** Original stone fences connect to stone fences, dirt fences to dirt fences, and both to gates. */
public final class SonarFenceBlock extends FenceBlock {
    private final boolean dirt;

    public SonarFenceBlock(Properties properties, boolean dirt) {
        super(properties);
        this.dirt = dirt;
    }

    @Override
    public boolean connectsTo(BlockState state, boolean solid, Direction side) {
        if (state.getBlock() instanceof SonarFenceBlock fence) return dirt == fence.dirt;
        if (state.getBlock() instanceof FenceGateBlock) return true;
        return !(state.getBlock() instanceof FenceBlock) && super.connectsTo(state, solid, side);
    }
}
