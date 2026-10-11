package sonar.calculator.mod.item.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import sonar.calculator.mod.CalculatorContent;

public final class ObsidianKeyItem extends Item {
    public ObsidianKeyItem() { super(new Properties().durability(1000)); }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()) || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.OBSIDIAN) && !sonar.calculator.mod.world.WorldContent.canKeyDrop(state)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            Block.dropResources(state, level, pos, level.getBlockEntity(pos), player, context.getItemInHand());
            level.removeBlock(pos, false);
            context.getItemInHand().hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
