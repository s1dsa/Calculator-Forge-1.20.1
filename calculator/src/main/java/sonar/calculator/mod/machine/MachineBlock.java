package sonar.calculator.mod.machine;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import sonar.calculator.mod.item.tools.WrenchTarget;

/** Common block lifecycle. All mutations and menu creation are owned by the logical server. */
public class MachineBlock extends BaseEntityBlock implements WrenchTarget {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    /** Legacy chest silhouette (upstream {@code BlockChest} box), used as the selection shape of the reinforced chest. */
    private static final VoxelShape CHEST_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);
    /** Upstream {@code CrankHandle.setBlockBounds(3/16,0,3/16,13/16,0.625,13/16)}. */
    private static final VoxelShape CRANK_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 10.0, 13.0);
    /** Upstream {@code DockingStation.setBlockBounds(0,0,0,1,0.95,1)}. */
    private static final VoxelShape DOCKING_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 15.2, 16.0);
    private final String id;

    public MachineBlock(String id, BlockBehaviour.Properties properties) {
        super(properties);
        this.id = id;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, id.equals("gaslanternon")));
    }
    public String id() { return id; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ACTIVE); }
    /** The crank handle may only be attached to the top of a hand cranked generator and inherits its facing. */
    private boolean isGenerator(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof MachineBlock machine && machine.id().equals("handcrankedgenerator");
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (id.equals("crankhandle")) {
            BlockPos below = context.getClickedPos().below();
            if (!isGenerator(context.getLevel(), below)) return null;
            return defaultBlockState().setValue(FACING, context.getLevel().getBlockState(below).getValue(FACING));
        }
        BlockState placed = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        for (int y = 1; y <= MachineEnvironment.auxiliaryHeight(id); y++) {
            BlockPos part = context.getClickedPos().above(y);
            if (!context.getLevel().hasChunkAt(part) || !context.getLevel().getBlockState(part).canBeReplaced()) return null;
        }
        return placed;
    }
    // There is no canPlaceBlockAt(Level, BlockPos) hook in 1.20.1; placement is gated by getStateForPlacement
    // returning null plus canSurvive at placement time (BlockItem#canPlace), so the crank needs no extra override.
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (id.equals("crankhandle")) return isGenerator(level, pos.below());
        return super.canSurvive(state, level, pos);
    }
    // A single removal path: every block change that takes the supporting generator away goes through a neighbour
    // update (Level.updateNeighborsAt, flag UPDATE_NEIGHBORS), so dropping and removing here returns the crank
    // exactly once. Deliberately no updateShape air fallback, which would be a second, non-dropping removal path.
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && id.equals("crankhandle") && !canSurvive(state, level, pos)) {
            Block.dropResources(state, level, pos);
            level.removeBlock(pos, false);
        }
    }
    public static boolean hasOpenGeometry(String id) {
        return switch (id) {
            case "extractionchamber", "precisionchamber", "restorationchamber", "reassemblychamber", "processingchamber",
                 "analysingchamber", "fabricationchamber", "reinforcedchest", "crankhandle", "dockingstation",
                 "conductormast", "conductormastblock", "transmitter", "transmitterblock", "weatherstation", "weatherstationblock",
                 "scarecrow", "scarecrowblock", "lantern", "gaslanternon", "gaslanternoff",
                 "starchextractor", "redstoneextractor", "glowstoneextractor", "magneticflux", "atomicmultiplier" -> true;
            default -> false;
        };
    }
    public static boolean isGasLantern(BlockState state) {
        return state.getBlock() instanceof MachineBlock block && (block.id.equals("gaslanternon") || block.id.equals("gaslanternoff"));
    }
    @Override public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return hasOpenGeometry(id) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }
    @Override public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return hasOpenGeometry(id) ? 0 : super.getLightBlock(state, level, pos);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (id.equals("reinforcedchest")) return CHEST_SHAPE;
        if (id.equals("crankhandle")) return CRANK_SHAPE;
        if (id.equals("dockingstation")) return DOCKING_SHAPE;
        if (id.endsWith("extractor")) return Block.box(1, 1, 1, 15, 15, 15);
        if (id.equals("lantern") || id.equals("gaslanternon") || id.equals("gaslanternoff")) return Block.box(5, 0, 5, 11, 12, 11);
        if (id.equals("scarecrow") || id.equals("scarecrowblock")) {
            double offset = 0;
            if (id.equals("scarecrowblock")) {
                MachineBlockEntity part = level.getBlockEntity(pos) instanceof MachineBlockEntity be ? be : null;
                MachineBlockEntity main = part == null ? null : part.controller();
                offset = -16 * (main == null ? 1 : pos.getY() - main.getBlockPos().getY());
            }
            VoxelShape body = Shapes.or(Block.box(1, offset, 1, 15, offset + 1, 15),
                    Block.box(7, offset + 1, 7, 9, offset + 25, 9),
                    Block.box(1, offset + 7, 3, 15, offset + 23, 13),
                    Block.box(1, offset + 25, 1, 15, offset + 42, 15));
            VoxelShape full = Shapes.or(body, state.getValue(FACING).getAxis() == Direction.Axis.X
                    ? Block.box(7, offset + 20, -6, 9, offset + 22, 22)
                    : Block.box(-6, offset + 20, 7, 22, offset + 22, 9));
            return Shapes.join(full, Block.box(-16, 0, -16, 32, 16, 32), net.minecraft.world.phys.shapes.BooleanOp.AND);
        }
        if (id.equals("conductormast") || id.equals("conductormastblock")) return Block.box(4, 0, 4, 12, 16, 12);
        if (id.equals("transmitter") || id.equals("transmitterblock")) return Block.box(6, 0, 6, 10, 16, 10);
        if (id.equals("weatherstation") || id.equals("weatherstationblock")) return Block.box(2, 0, 2, 14, 16, 14);
        if (java.util.Set.of("extractionchamber", "precisionchamber", "restorationchamber", "reassemblychamber", "processingchamber").contains(id)) {
            VoxelShape frame = Shapes.empty();
            for (int x : new int[]{0, 14}) for (int z : new int[]{0, 14}) frame = Shapes.or(frame, Block.box(x, 0, z, x + 2, 16, z + 2));
            return Shapes.or(frame, Block.box(0, 0, 0, 16, 2, 16), Block.box(0, 14, 0, 16, 16, 16));
        }
        return Shapes.block();
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (id.equals("crankhandle")) return CRANK_SHAPE;
        if (id.equals("dockingstation")) return DOCKING_SHAPE;
        return super.getCollisionShape(state, level, pos, context);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MachineBlockEntity(pos, state); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? (id.equals("reinforcedchest") ? createTickerHelper(type, MachineContent.MACHINE.get(), MachineBlockEntity::clientTick) : null)
                : createTickerHelper(type, MachineContent.MACHINE.get(), MachineBlockEntity::serverTick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity part)) return InteractionResult.PASS;
        MachineBlockEntity machine = part.controller();
        if (machine == null) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (!machine.usableBy(player) || !level.mayInteract(player, pos) || !level.mayInteract(player, machine.getBlockPos())) return InteractionResult.FAIL;
            // #3: the user asked for "empty-hand right-click to switch the face". A non-sneaking, empty-handed
            // right-click therefore cycles the clicked face of the advanced power cube (INPUT -> OUTPUT ->
            // DISABLED, server-authoritative); sneaking with an empty hand opens the menu instead, so the six
            // face buttons and the stored energy stay reachable. Every other machine (and any right-click while
            // holding an item) keeps the plain right-click menu, and the crank handle keeps its no-menu crank.
            // Note: this deliberately swaps upstream AdvancedPowerCube's sneak=cycle / plain=menu mapping to
            // satisfy the explicit user request; see notes/feedback-machine.md.
            if (player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()
                    && machine.cycleEnergySide(hit.getDirection())) return InteractionResult.sidedSuccess(false);
            if (machine.interact(player, hand)) return InteractionResult.CONSUME;
            if (machine.refusesMenu()) { machine.reportMenuRefusal(player); return InteractionResult.sidedSuccess(false); }
            if (player instanceof ServerPlayer server) NetworkHooks.openScreen(server, machine, machine.getBlockPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            machine.restoreFromItem(stack);
            machine.placeAuxiliary();
        }
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean harvest, net.minecraft.world.level.material.FluidState fluid) {
        return MachineEnvironment.playerRemoval(player, () -> {
            playerWillDestroy(level, pos, state, player);
            return level.setBlock(pos, fluid.createLegacyBlock(), level.isClientSide ? 11 : Block.UPDATE_ALL);
        });
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        // Both registry states share the same entity. A fuel transition must not dismantle or drop it.
        if (isGasLantern(state) && isGasLantern(next)) return;
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof MachineBlockEntity machine && !level.isClientSide
                && !MachineEnvironment.cleaning(pos)) {
            MachineEnvironment.dismantle(level, pos, machine);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override public boolean onWrenched(Level level, BlockPos pos, Player player, Direction face, boolean dismantle) {
        if (level.isClientSide || !player.mayBuild() || !level.mayInteract(player, pos)) return false;
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity part)) return false;
        MachineBlockEntity machine = part.controller();
        if (machine == null || !machine.usableBy(player) || !level.mayInteract(player, machine.getBlockPos())) return false;
        BlockPos mainPos = machine.getBlockPos();
        if (dismantle) {
            ItemStack drop = machine.packedStack();
            machine.clearForRemoval();
            level.removeBlock(mainPos, false);
            if (!player.getInventory().add(drop)) player.drop(drop, false);
        } else {
            BlockState rotated = level.getBlockState(mainPos).cycle(FACING);
            level.setBlock(mainPos, rotated, Block.UPDATE_ALL);
            for (BlockPos owned : MachineEnvironment.towerParts(level, mainPos, machine.id())) {
                if (!owned.equals(mainPos)) level.setBlock(owned, level.getBlockState(owned).setValue(FACING, rotated.getValue(FACING)), Block.UPDATE_ALL);
            }
        }
        return true;
    }
    @Override public boolean isSignalSource(BlockState state) { return id.equals("rainsensor"); }
    @Override public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return id.equals("rainsensor") && state.getValue(ACTIVE) ? 15 : 0;
    }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) return 0;
        return machine.comparatorSignal();
    }
}
