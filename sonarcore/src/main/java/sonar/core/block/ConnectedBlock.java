package sonar.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import sonar.core.api.blocks.IConnectedBlock;
import sonar.core.api.blocks.IStableBlock;
import sonar.core.api.blocks.IStableGlass;

/** Six neighbor flags replace 1.12 getActualState and retain all original texture combinations. */
public class ConnectedBlock extends Block implements IConnectedBlock, IStableBlock {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    private final int connection;

    public ConnectedBlock(Properties properties, int connection) {
        super(properties);
        this.connection = connection;
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    public static BooleanProperty property(Direction side) {
        return switch (side) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    public boolean connects(BlockState neighbor) {
        if (neighbor.getBlock() instanceof IConnectedBlock other) {
            for (int ours : getConnections()) for (int theirs : other.getConnections())
                if (ours == theirs) return true;
        }
        return false;
    }

    @Override
    public int[] getConnections() { return new int[] { connection }; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.values())
            state = state.setValue(property(side), connects(context.getLevel()
                    .getBlockState(context.getClickedPos().relative(side))));
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(property(side), connects(neighbor));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState result = state;
        for (Direction side : Direction.Plane.HORIZONTAL)
            result = result.setValue(property(rotation.rotate(side)), state.getValue(property(side)));
        return result;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState result = state;
        for (Direction side : Direction.Plane.HORIZONTAL)
            result = result.setValue(property(mirror.mirror(side)), state.getValue(property(side)));
        return result;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    public static final class Glass extends ConnectedBlock implements IStableGlass {
        public Glass(Properties properties, int connection) { super(properties, connection); }

        @Override
        public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
            return connects(adjacent) || super.skipRendering(state, adjacent, side);
        }

        @Override
        public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1.0F; }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }
    }
}
