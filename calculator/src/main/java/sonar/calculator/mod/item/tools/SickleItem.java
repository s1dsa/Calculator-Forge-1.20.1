package sonar.calculator.mod.item.tools;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.items.ItemHandlerHelper;
import sonar.calculator.mod.world.WorldContent;

public final class SickleItem extends Item {
    public SickleItem() { super(new Properties()); }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !context.getLevel().mayInteract(player, context.getClickedPos()) || !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), context.getItemInHand())) return InteractionResult.FAIL;
        if (!context.getLevel().isClientSide) WorldContent.harvestLeaves(context.getLevel(), context.getClickedPos(), false).forEach(stack -> ItemHandlerHelper.giveItemToPlayer(player, stack));
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
