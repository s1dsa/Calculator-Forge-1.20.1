package sonar.calculator.mod.item.modules;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import sonar.calculator.mod.CalculatorConfig;
import sonar.calculator.mod.world.WorldContent;

/** Numeric edge cases intentionally follow NutritionHelper and NutritionModule 1.12.2. */
public final class NutritionItem extends Item {
    private final String kind;
    public NutritionItem(String kind) { super(new Properties().stacksTo(1)); this.kind = kind; }
    public boolean supports(boolean hunger) { return kind.equals("nutrition") || kind.equals(hunger ? "hunger" : "health"); }
    private String key(boolean hunger) { return kind.equals("nutrition") ? hunger ? "hunger" : "health" : "points"; }
    public int points(ItemStack stack, boolean hunger) { return supports(hunger) && stack.hasTag() ? stack.getTag().getInt(key(hunger)) : 0; }
    public int capacity(boolean hunger) {
        return kind.equals("nutrition") ? hunger ? CalculatorConfig.NUTRITION_MODULE_HUNGER_CAPACITY : CalculatorConfig.NUTRITION_MODULE_HEALTH_CAPACITY
                : hunger ? CalculatorConfig.HUNGER_MODULE_CAPACITY : CalculatorConfig.HEALTH_MODULE_CAPACITY;
    }
    public void setPoints(ItemStack stack, boolean hunger, int points) {
        // Original setters reject every nonnegative value for capacity -1; leaf harvesting bypasses setters.
        if (supports(hunger) && points >= 0 && points <= capacity(hunger)) stack.getOrCreateTag().putInt(key(hunger), points);
    }
    public void transfer(ItemStack stack, boolean hunger, int amount) {
        if (!supports(hunger)) return;
        // HealthModule.transferHealth resets points before both ADD and REMOVE in the original.
        int before = kind.equals("health") ? 0 : points(stack, hunger);
        stack.getOrCreateTag().putInt(key(hunger), before + amount);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer(); Level level = context.getLevel(); ItemStack stack = context.getItemInHand();
        if (player == null || !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), stack)
                || !level.mayInteract(player, context.getClickedPos())) return InteractionResult.FAIL;
        if (!level.isClientSide) for (boolean hunger : new boolean[]{true, false}) if (supports(hunger)) {
            int gathered = WorldContent.harvestNutrition(level, context.getClickedPos(), hunger);
            if (gathered != 0) stack.getOrCreateTag().putInt(key(hunger), points(stack, hunger) + gathered);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && !kind.equals("nutrition")) {
            boolean hunger = kind.equals("hunger");
            if (!player.isShiftKeyDown()) player.displayClientMessage(Component.translatable(hunger ? "points.hunger" : "points.health").append(": " + points(stack, hunger)), true);
            else if (hunger) {
                int food = player.getFoodData().getFoodLevel(), points = points(stack, true), used = 20 - food;
                if (food < 20 && points - used > 1) { stack.getOrCreateTag().putInt("points", points - used); player.getFoodData().eat(20, 2F); }
                else if (food < 20 && points - used < 1) { stack.getOrCreateTag().putInt("points", 0); player.getFoodData().eat(points, 2F); }
            } else heal(stack, player, false);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    private void heal(ItemStack stack, Player player, boolean automatic) {
        int points = points(stack, false), current = (int) player.getHealth(), max = (int) player.getMaxHealth();
        if (points == 0 || current >= max) return;
        int used = automatic ? Math.min(max - current, 2) : max - current;
        if (points >= used) { stack.getOrCreateTag().putInt(key(false), points - used); player.setHealth(automatic ? player.getHealth() + used : player.getMaxHealth()); }
        else { stack.getOrCreateTag().putInt(key(false), 0); player.setHealth(current); }
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !kind.equals("nutrition") || !(entity instanceof Player player)) return;
        CompoundTag tag = stack.getOrCreateTag(); int ticks = tag.getInt("ticks");
        if (ticks < 10) { tag.putInt("ticks", ticks + 1); return; }
        tag.putInt("ticks", 0);
        int points = points(stack, true), food = player.getFoodData().getFoodLevel(), used = Math.min(20 - food, 2);
        if (food < 20) {
            if (points - used > 0) { tag.putInt("hunger", points - used); player.getFoodData().eat(food + used, .2F); }
            else { tag.putInt("hunger", 0); player.getFoodData().eat(points, .2F); }
        }
        heal(stack, player, true);
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        for (boolean hunger : new boolean[]{true, false}) if (supports(hunger))
            lines.add(Component.translatable(hunger ? "points.hunger" : "points.health").append(": " + points(stack, hunger)));
    }
}
