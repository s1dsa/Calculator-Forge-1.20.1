package sonar.calculator.mod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class CalculatorConnectedBlock extends Block implements sonar.core.api.blocks.IStableBlock {
    public final int target;
    private final boolean glass;
    public CalculatorConnectedBlock(int target, boolean glass, BlockBehaviour.Properties properties) {
        super(properties); this.target = target; this.glass = glass;
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : PipeBlock.PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }
    protected boolean connects(BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockState(pos.relative(direction)).getBlock() instanceof CalculatorConnectedBlock other && target == other.target;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), connects(context.getLevel(), context.getClickedPos(), direction));
        return state;
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState adjacent, LevelAccessor level, BlockPos pos, BlockPos adjacentPos) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), connects(level, pos, direction));
    }
    @Override public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return glass && adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.PROPERTY_BY_DIRECTION.values().toArray(BooleanProperty[]::new));
    }
}
