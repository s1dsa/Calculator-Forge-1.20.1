package sonar.calculator.mod.item;

import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/** An energy capability backed by the owning stack, never by the singleton Item. */
public final class StackEnergy implements IEnergyStorage, ICapabilityProvider {
    /** Flawless implements this to aggregate several installed energy modules atomically. */
    public interface Delegate {
        int storedEnergy(ItemStack stack);
        int receiveEnergy(ItemStack stack, int amount, boolean simulate);
        int extractEnergy(ItemStack stack, int amount, boolean simulate);
        boolean consumeEnergy(ItemStack stack, int amount, boolean simulate);
    }

    private final ItemStack stack;
    private final IntSupplier capacity;
    private final IntSupplier transfer;
    private final LazyOptional<IEnergyStorage> capability = LazyOptional.of(() -> this);

    public StackEnergy(ItemStack stack, IntSupplier capacity) {
        this(stack, capacity, () -> Math.max(0, capacity.getAsInt() / 10));
    }

    public StackEnergy(ItemStack stack, IntSupplier capacity, IntSupplier transfer) {
        this.stack = stack;
        this.capacity = capacity;
        this.transfer = transfer;
    }

    private static CompoundTag tag(ItemStack stack, boolean create) {
        return stack.getItem() instanceof PortableItem portable ? portable.energyTag(stack, create)
                : create ? stack.getOrCreateTag() : stack.getTag();
    }

    public static int get(ItemStack stack) {
        if (stack.getItem() instanceof Delegate delegate) return Math.max(0, delegate.storedEnergy(stack));
        CompoundTag tag = tag(stack, false);
        return tag == null ? 0 : (int) Math.max(0L, Math.min(Integer.MAX_VALUE, tag.getLong("Energy")));
    }

    /** Internal usage bypasses the per-tick external transfer limit. */
    public static boolean consume(ItemStack stack, int amount, boolean simulate) {
        if (amount < 0) return false;
        if (stack.getItem() instanceof Delegate delegate) return delegate.consumeEnergy(stack, amount, simulate);
        if (get(stack) < amount) return false;
        if (!simulate && amount != 0) {
            CompoundTag tag = tag(stack, true);
            if (tag == null) return false;
            tag.putLong("Energy", (long) get(stack) - amount);
        }
        return true;
    }

    public static int receive(ItemStack stack, int amount, int capacity, boolean simulate) {
        if (stack.getItem() instanceof Delegate delegate) return delegate.receiveEnergy(stack, Math.max(0, amount), simulate);
        int accepted = Math.max(0, Math.min(amount, capacity - get(stack)));
        if (!simulate && accepted != 0) {
            CompoundTag tag = tag(stack, true);
            if (tag == null) return 0;
            tag.putLong("Energy", (long) get(stack) + accepted);
        }
        return accepted;
    }

    @Override public int receiveEnergy(int amount, boolean simulate) {
        if (stack.getItem() instanceof Delegate delegate) return delegate.receiveEnergy(stack, Math.max(0, amount), simulate);
        return receive(stack, Math.min(Math.max(0, amount), Math.max(0, transfer.getAsInt())), getMaxEnergyStored(), simulate);
    }
    @Override public int extractEnergy(int amount, boolean simulate) {
        if (stack.getItem() instanceof Delegate delegate) return delegate.extractEnergy(stack, Math.max(0, amount), simulate);
        int extracted = Math.min(getEnergyStored(), Math.min(Math.max(0, amount), Math.max(0, transfer.getAsInt())));
        consume(stack, extracted, simulate);
        return extracted;
    }
    @Override public int getEnergyStored() { return Math.min(get(stack), getMaxEnergyStored()); }
    @Override public int getMaxEnergyStored() { return Math.max(0, capacity.getAsInt()); }
    @Override public boolean canExtract() { return transfer.getAsInt() > 0; }
    @Override public boolean canReceive() { return transfer.getAsInt() > 0; }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ENERGY ? capability.cast() : LazyOptional.empty();
    }
}
