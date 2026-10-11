package sonar.calculator.mod.world;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.IForgeShearable;

/** Non-decaying leaves, with the four original fruit/nutrition growth stages. */
public final class CalculatorLeavesBlock extends Block implements IForgeShearable {
    public static final EnumProperty<LeafGrowth> GROWTH = EnumProperty.create("growth", LeafGrowth.class);
    public final int leafType;

    public enum LeafGrowth implements StringRepresentable {
        FRESH, GROWING, READY, MATURED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean canGrow() {
            return this != MATURED;
        }

        public boolean isReady() {
            return this == READY || this == MATURED;
        }

        public int getMeta() {
            return ordinal();
        }
    }

    public CalculatorLeavesBlock(int type) {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2F, 0.04F)
                .sound(SoundType.STONE).randomTicks().noOcclusion().ignitedByLava()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false));
        leafType = type;
        registerDefaultState(stateDefinition.any().setValue(GROWTH, LeafGrowth.FRESH));
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean exposed = false;
        for (Direction direction : Direction.values()) {
            if (level.isEmptyBlock(pos.relative(direction))) {
                exposed = true;
                break;
            }
        }
        if (exposed && random.nextInt(leafType < 2 ? 8 : leafType == 2 ? 20 : 50) == 1
                && state.getValue(GROWTH).canGrow()) {
            level.setBlock(pos, state.cycle(GROWTH), UPDATE_ALL);
        }
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 1;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return false;
    }

    @Override
    public List<ItemStack> onSheared(Player player, ItemStack tool, Level level, BlockPos pos, int fortune) {
        // No growth state is copied to the item; placing sheared leaves always starts FRESH.
        return List.of(new ItemStack(this));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GROWTH);
    }
}
