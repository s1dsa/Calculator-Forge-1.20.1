package sonar.core.energy;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.energy.EnergyStorage;

/** Forge Energy storage with bounded internal operations and persistent dirty tracking. */
public class StoredEnergy extends EnergyStorage {
    private final Runnable dirty;

    public StoredEnergy(int capacity, int maxTransfer, Runnable dirty) {
        this(capacity, maxTransfer, maxTransfer, dirty);
    }

    public StoredEnergy(int capacity, int maxReceive, int maxExtract, Runnable dirty) {
        super(nonNegative(capacity), nonNegative(maxReceive), nonNegative(maxExtract));
        this.dirty = Objects.requireNonNull(dirty, "dirty");
    }

    public StoredEnergy(int capacity, Runnable dirty) {
        this(capacity, capacity, dirty);
    }

    private static int nonNegative(int value) {
        if (value < 0) throw new IllegalArgumentException("Energy limits cannot be negative");
        return value;
    }

    public void setEnergy(int amount) {
        int next = Math.max(0, Math.min(capacity, amount));
        if (next != energy) {
            energy = next;
            dirty.run();
        }
    }

    /** Internal machine consumption, independent of the external extraction rate. */
    public int consume(int amount, boolean simulate) {
        int consumed = Math.min(energy, Math.max(0, amount));
        if (!simulate && consumed != 0) setEnergy(energy - consumed);
        return consumed;
    }

    public int consume(int amount) { return consume(amount, false); }

    /** Internal generation, independent of the external reception rate. */
    public int generate(int amount, boolean simulate) {
        int generated = Math.min(capacity - energy, Math.max(0, amount));
        if (!simulate && generated != 0) setEnergy(energy + generated);
        return generated;
    }

    public int generate(int amount) { return generate(amount, false); }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        return generate(Math.min(maxReceive, Math.max(0, amount)), simulate);
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        return consume(Math.min(maxExtract, Math.max(0, amount)), simulate);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Energy", energy);
        return tag;
    }

    @Override
    public void deserializeNBT(Tag tag) {
        // Capacity and transfer limits are code/config controlled, never restored from untrusted NBT.
        int saved = tag instanceof CompoundTag compound ? compound.getInt("Energy")
                : tag instanceof NumericTag number ? number.getAsInt() : 0;
        energy = Math.max(0, Math.min(capacity, saved));
    }
}
