package sonar.core.energy;

import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

/** Stack-backed FE: each operation reads the current tag, so copied/reloaded stacks cannot use stale energy. */
public final class ItemEnergyStorage implements IEnergyStorage {
    private final ItemStack stack;
    private final int capacity;
    private final int maxReceive;
    private final int maxExtract;
    private final String key;

    public ItemEnergyStorage(ItemStack stack, int capacity, int maxReceive, int maxExtract) {
        this(stack, capacity, maxReceive, maxExtract, "SonarEnergy");
    }

    public ItemEnergyStorage(ItemStack stack, int capacity, int maxReceive, int maxExtract, String key) {
        if (capacity < 0 || maxReceive < 0 || maxExtract < 0) throw new IllegalArgumentException("Negative energy limits");
        this.stack = stack;
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
        this.key = key;
    }

    @Override
    public int getEnergyStored() {
        if (stack.isEmpty() || !stack.hasTag()) return 0;
        return Math.max(0, Math.min(capacity, stack.getTag().getInt(key)));
    }

    public void setEnergy(int energy) {
        if (!stack.isEmpty()) stack.getOrCreateTag().putInt(key, Math.max(0, Math.min(capacity, energy)));
    }

    public int consume(int amount, boolean simulate) {
        int energy = getEnergyStored();
        int used = Math.min(energy, Math.max(0, amount));
        if (!simulate && used > 0) setEnergy(energy - used);
        return used;
    }

    public int generate(int amount, boolean simulate) {
        if (stack.isEmpty()) return 0;
        int energy = getEnergyStored();
        int gained = Math.min(capacity - energy, Math.max(0, amount));
        if (!simulate && gained > 0) setEnergy(energy + gained);
        return gained;
    }

    @Override public int receiveEnergy(int amount, boolean simulate) { return generate(Math.min(maxReceive, Math.max(0, amount)), simulate); }
    @Override public int extractEnergy(int amount, boolean simulate) { return consume(Math.min(maxExtract, Math.max(0, amount)), simulate); }
    @Override public int getMaxEnergyStored() { return capacity; }
    @Override public boolean canExtract() { return maxExtract > 0; }
    @Override public boolean canReceive() { return maxReceive > 0; }
}
