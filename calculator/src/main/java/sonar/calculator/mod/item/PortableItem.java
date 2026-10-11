package sonar.calculator.mod.item;

import java.util.List;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

public class PortableItem extends Item {
    private final String mode;
    protected final IntSupplier energyCapacity;

    public PortableItem(String mode, IntSupplier energyCapacity) {
        super(new Properties().stacksTo(1));
        this.mode = mode;
        this.energyCapacity = energyCapacity;
    }

    public String mode(ItemStack stack) { return mode; }
    public int energyCapacity(ItemStack stack) { return Math.max(0, energyCapacity.getAsInt()); }
    public int energyTransfer(ItemStack stack) { return energyCapacity(stack) / 10; }

    /** Flawless modules override this to point at the installed energy module's tag. */
    @Nullable public CompoundTag energyTag(ItemStack stack, boolean create) {
        return create ? stack.getOrCreateTag() : stack.getTag();
    }

    /** The returned compound contains only this mode's persistent Items list. */
    public CompoundTag inventoryTag(ItemStack stack, String mode, boolean create) {
        CompoundTag root = create ? stack.getOrCreateTag() : stack.getTag();
        if (root == null) return new CompoundTag();
        CompoundTag inventories = root.getCompound("PortableInventories");
        CompoundTag inventory = inventories.getCompound(mode);
        if (create) { inventories.put(mode, inventory); root.put("PortableInventories", inventories); }
        return inventory;
    }

    /** Flawless must include its selected installed-module slot in this key. */
    public String inventorySessionKey(ItemStack stack, String mode) { return mode; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (energyCapacity(stack) > 0 && StackEnergy.get(stack) == 0 && !player.getAbilities().instabuild) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("energy.required"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) PortableMenu.open(serverPlayer, hand, mode(stack));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new StackEnergy(stack, () -> energyCapacity(stack), () -> energyTransfer(stack));
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        if (energyCapacity(stack) > 0) lines.add(Component.translatable("energy.stored").append(": " + StackEnergy.get(stack) + " / " + energyCapacity(stack) + " FE"));
    }

    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }
}
