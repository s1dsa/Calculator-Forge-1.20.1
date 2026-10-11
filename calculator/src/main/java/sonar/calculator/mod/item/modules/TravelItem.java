package sonar.calculator.mod.item.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import sonar.calculator.mod.CalculatorConfig;
import sonar.calculator.mod.item.PortableItem;
import sonar.calculator.mod.item.StackEnergy;
import sonar.calculator.mod.world.WorldContent;

public final class TravelItem extends PortableItem {
    private final boolean jump;
    public TravelItem(boolean jump) {
        super(jump ? "jump" : "warp", () -> jump ? CalculatorConfig.JUMP_MODULE_STORAGE : CalculatorConfig.WARP_MODULE_STORAGE);
        this.jump = jump;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) activate(stack, stack.getOrCreateTag(), server, jump);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        return !jump && bind(context, context.getItemInHand().getOrCreateTag()) ? InteractionResult.sidedSuccess(context.getLevel().isClientSide) : InteractionResult.PASS;
    }
    public static boolean bind(UseOnContext context, CompoundTag tag) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return false;
        if (context.getLevel().isClientSide) return true;
        if (!context.getLevel().mayInteract(player, context.getClickedPos()) || !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), context.getItemInHand())) return false;
        if (!WorldContent.isStableBlock(context.getLevel().getBlockState(context.getClickedPos()))) {
            player.displayClientMessage(Component.translatable("calc.stableStone"), true); return true;
        }
        BlockPos pos = context.getClickedPos();
        tag.putInt("TeleX", pos.getX()); tag.putInt("TeleY", pos.getY()); tag.putInt("TeleZ", pos.getZ());
        tag.putString("DimensionKey", context.getLevel().dimension().location().toString()); tag.putBoolean("Tele", true);
        player.displayClientMessage(Component.translatable("calc.position"), true); return true;
    }
    private static boolean clear(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos) && !level.isOutsideBuildHeight(pos.above(2))
                && level.isEmptyBlock(pos.above()) && level.isEmptyBlock(pos.above(2))
                && level.noCollision(player, player.getBoundingBox().move(pos.getX() + .5 - player.getX(), pos.getY() + 1 - player.getY(), pos.getZ() + .5 - player.getZ()));
    }
    public static void activate(ItemStack stack, CompoundTag tag, ServerPlayer player, boolean jump) {
        Level level = player.level(); BlockPos target;
        if (jump) {
            long last = tag.getLong("last"), now = level.getDayTime();
            if (!(now < last || now > last + 50)) { player.displayClientMessage(Component.translatable("calc.cooldown", (now - last) * 2), true); return; }
            tag.putLong("last", now);
            Vec3 eye = player.getEyePosition();
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(500)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) return;
            target = hit.getBlockPos();
            if (!clear(player, target)) target = target.relative(hit.getDirection());
            if (target.getY() <= 0 || !clear(player, target)) { player.displayClientMessage(Component.translatable("calc.targetBlocked"), true); return; }
        } else {
            if (tag.getBoolean("click")) { tag.putBoolean("click", false); return; }
            if (!tag.getBoolean("Tele")) { player.displayClientMessage(Component.translatable("calc.noPosition"), true); return; }
            if (!level.dimension().location().toString().equals(tag.getString("DimensionKey"))) { player.displayClientMessage(Component.translatable("calc.dimension"), true); return; }
            target = new BlockPos(tag.getInt("TeleX"), tag.getInt("TeleY"), tag.getInt("TeleZ"));
            if (!clear(player, target)) { player.displayClientMessage(Component.translatable("calc.destinationBlocked"), true); return; }
            if (!WorldContent.isStableBlock(level.getBlockState(target))) { player.displayClientMessage(Component.translatable("calc.stableStone"), true); return; }
        }
        if (!ModuleItems.hasEnergy(stack, player, 1000)) return;
        player.teleportTo(target.getX() + .5, target.getY() + 1, target.getZ() + .5);
        if (jump) {
            ModuleItems.consumeEnergy(stack, player, 1000);
            level.playSound(null, target, SoundEvents.ENDER_EYE_LAUNCH, SoundSource.NEUTRAL, .5F, .8F);
            player.serverLevel().sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 32, .5, 1, .5, 0);
        } else player.displayClientMessage(Component.translatable("calc.teleported"), true);
        // WarpModule only checks energy; the source never extracts its 1000 RF.
    }
}
