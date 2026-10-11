package sonar.calculator.mod.item.tools;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class WrenchItem extends Item {
    public WrenchItem() { super(new Properties()); }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (player == null || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) return InteractionResult.FAIL;
        Object target = level.getBlockEntity(pos);
        if (!(target instanceof WrenchTarget)) target = level.getBlockState(pos).getBlock();
        if (target instanceof WrenchTarget wrenchable) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            return wrenchable.onWrenched(level, pos, player, context.getClickedFace(), player.isShiftKeyDown()) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }
}
