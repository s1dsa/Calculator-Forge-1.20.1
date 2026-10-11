package sonar.calculator.mod.item.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import sonar.calculator.mod.CalculatorContent;

public final class CalculatorScreenItem extends Item {
    public CalculatorScreenItem() { super(new Properties()); }
    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        Direction face = context.getClickedFace();
        BlockPos source = context.getClickedPos(), target = source.relative(face);
        if (player == null || face.getAxis().isVertical() || !player.mayUseItemAt(target, face, context.getItemInHand()) || !level.mayInteract(player, target)) return InteractionResult.PASS;
        var blockEntity = level.getBlockEntity(source);
        if (blockEntity == null || !blockEntity.getCapability(ForgeCapabilities.ENERGY, face).isPresent() || !level.isEmptyBlock(target)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            BlockState state = CalculatorContent.block("calculatorscreenblock").defaultBlockState();
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, face);
            else if (state.hasProperty(BlockStateProperties.FACING)) state = state.setValue(BlockStateProperties.FACING, face);
            if (level.setBlockAndUpdate(target, state) && !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
