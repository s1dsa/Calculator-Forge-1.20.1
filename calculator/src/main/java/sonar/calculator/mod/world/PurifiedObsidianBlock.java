package sonar.calculator.mod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class PurifiedObsidianBlock extends CalculatorConnectedBlock {
    public PurifiedObsidianBlock() {
        super(4, false, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1, 6000000));
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) { return false; }
}
