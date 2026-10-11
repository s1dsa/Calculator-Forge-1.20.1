package sonar.core.fluids;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/** Forge fluid tank with validated NBT and the same dirty callback contract as item/energy storage. */
public class PersistentFluidTank extends FluidTank implements INBTSerializable<CompoundTag> {
    private final Runnable dirty;

    public PersistentFluidTank(int capacity, Runnable dirty) {
        this(capacity, fluid -> true, dirty);
    }

    public PersistentFluidTank(int capacity, Predicate<FluidStack> validator, Runnable dirty) {
        super(capacity, validator);
        if (capacity < 0) throw new IllegalArgumentException("Negative tank capacity");
        this.dirty = Objects.requireNonNull(dirty, "dirty");
    }

    @Override
    protected void onContentsChanged() { dirty.run(); }

    @Override
    public CompoundTag serializeNBT() { return writeToNBT(new CompoundTag()); }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        FluidStack loaded = FluidStack.loadFluidStackFromNBT(tag);
        if (loaded.isEmpty() || !isFluidValid(loaded)) fluid = FluidStack.EMPTY;
        else {
            loaded.setAmount(Math.min(capacity, Math.max(0, loaded.getAmount())));
            fluid = loaded;
        }
    }
}
