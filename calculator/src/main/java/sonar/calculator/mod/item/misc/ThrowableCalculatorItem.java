package sonar.calculator.mod.item.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import sonar.calculator.mod.CalculatorConfig;
import sonar.calculator.mod.item.ItemFactory;
import sonar.calculator.mod.item.entity.CalculatorProjectile;

public final class ThrowableCalculatorItem extends Item {
    private final String kind;
    public ThrowableCalculatorItem(String kind) { super(new Properties()); this.kind = kind; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean grenade = kind.equals("grenade") || kind.equals("babygrenade");
        if (grenade && !CalculatorConfig.enableGrenades) return InteractionResultHolder.fail(stack);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), grenade ? SoundEvents.TNT_PRIMED : SoundEvents.SNOWBALL_THROW,
                SoundSource.NEUTRAL, 0.5F, 0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            if (kind.equals("enddiamond")) {
                ThrownEnderpearl pearl = new ThrownEnderpearl(level, player);
                pearl.shootFromRotation(player, player.getXRot(), player.getYRot(), 0F, 1.5F, 1F);
                level.addFreshEntity(pearl);
            } else {
                var type = switch (kind) {
                    case "soil" -> ItemFactory.SOIL.get();
                    case "babygrenade" -> ItemFactory.BABY_GRENADE.get();
                    case "grenade" -> ItemFactory.GRENADE.get();
                    default -> ItemFactory.STONE.get();
                };
                CalculatorProjectile projectile = new CalculatorProjectile(type, player, level);
                projectile.setItem(stack);
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0F, 1.5F, 1F);
                if (level.addFreshEntity(projectile) && !player.getAbilities().instabuild) stack.shrink(1);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (!kind.equals("soil") && !kind.equals("smallstone")) return InteractionResult.PASS;
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()) || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.DIRT) && !state.is(Blocks.GRASS_BLOCK)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, (kind.equals("soil") ? Blocks.FARMLAND : Blocks.GRAVEL).defaultBlockState());
            if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
