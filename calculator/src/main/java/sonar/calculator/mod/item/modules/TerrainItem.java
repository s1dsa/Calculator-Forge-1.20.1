package sonar.calculator.mod.item.modules;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import sonar.calculator.mod.CalculatorConfig;
import sonar.calculator.mod.item.PortableItem;
import sonar.calculator.mod.item.StackEnergy;

public final class TerrainItem extends PortableItem {
    private final boolean advanced;
    private final Block[] blocks;
    public TerrainItem(boolean advanced) {
        super("terrain", () -> advanced ? CalculatorConfig.ADVANCED_TERRAIN_MODULE_STORAGE : CalculatorConfig.TERRAIN_MODULE_STORAGE);
        this.advanced = advanced;
        blocks = advanced ? new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SAND, Blocks.COBBLESTONE}
                : new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE};
    }
    @Override public int energyTransfer(ItemStack stack) { return energyCapacity(stack); }
    public int currentMode(ItemStack stack) { return Math.floorMod(stack.hasTag() ? stack.getTag().getInt("Mode") : 0, blocks.length); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) { return InteractionResultHolder.pass(player.getItemInHand(hand)); }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer(); Level level = context.getLevel(); ItemStack stack = context.getItemInHand();
        if (player == null || !level.mayInteract(player, context.getClickedPos()) || !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), stack)) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            stack.getOrCreateTag().putInt("Mode", (currentMode(stack) + 1) % blocks.length);
            player.displayClientMessage(blocks[currentMode(stack)].getName(), true);
        } else {
            Block old = level.getBlockState(context.getClickedPos()).getBlock(), next = blocks[currentMode(stack)];
            int usage = advanced ? CalculatorConfig.ADVANCED_TERRAIN_MODULE_USAGE : CalculatorConfig.TERRAIN_MODULE_USAGE;
            if (old != next && java.util.Arrays.asList(blocks).contains(old)) {
                if (player.getAbilities().instabuild || StackEnergy.consume(stack, usage, true)) {
                    if (level.setBlockAndUpdate(context.getClickedPos(), next.defaultBlockState()) && !player.getAbilities().instabuild) StackEnergy.consume(stack, usage, false);
                } else player.displayClientMessage(Component.translatable("energy.noEnergy"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("calc.mode").append(": ").append(blocks[currentMode(stack)].getName()));
    }
}
