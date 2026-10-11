package sonar.calculator.mod.item.misc;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class CircuitItem extends Item {
    private final boolean board;
    private final int variant;
    public CircuitItem(boolean board, int variant) { super(new Properties().stacksTo(1)); this.board = board; this.variant = variant; }
    public int variant() { return variant; }
    public boolean isBoard() { return board; }
    public static boolean isStable(ItemStack stack) { return stack.hasTag() && stack.getTag().getInt("Stable") == 1; }
    public static boolean isAnalysed(ItemStack stack) { return stack.hasTag() && stack.getTag().getBoolean("Analysed"); }
    public static void setData(ItemStack stack, RandomSource random) {
        if (stack.hasTag()) return;
        var tag = stack.getOrCreateTag();
        tag.putInt("Energy", 1 + random.nextInt(200));
        int[] bounds = {50, 100, 1000, 2000, 10000, 20000};
        for (int i = 0; i < bounds.length; i++) tag.putInt("Item" + (i + 1), 1 + random.nextInt(bounds[i]));
        tag.putInt("Stable", 1 + random.nextInt(6));
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (board && !level.isClientSide) setData(stack, level.random);
    }
    @Override public boolean isFoil(ItemStack stack) { return board && isStable(stack); }
    @Override public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        return !board || !isAnalysed(stack) ? name : name.copy().append(isStable(stack) ? "A" : "B");
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        if (board) lines.add(!isAnalysed(stack)
                ? Component.translatable("circuit.notAnalysed")
                : Component.literal(isStable(stack) ? "A: " : "B: ").append(Component.translatable(isStable(stack) ? "circuit.stable" : "circuit.analysed")));
    }
}
