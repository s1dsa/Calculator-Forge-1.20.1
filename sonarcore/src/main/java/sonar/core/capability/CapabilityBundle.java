package sonar.core.capability;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import javax.annotation.Nullable;

/** Owns LazyOptional lifecycles for a block entity. Delegate invalidateCaps/reviveCaps explicitly. */
public final class CapabilityBundle {
    private final IItemHandler items;
    private final IEnergyStorage energy;
    private final IFluidHandler fluids;
    private LazyOptional<IItemHandler> itemCapability;
    private LazyOptional<IEnergyStorage> energyCapability;
    private LazyOptional<IFluidHandler> fluidCapability;

    public CapabilityBundle(@Nullable IItemHandler items, @Nullable IEnergyStorage energy, @Nullable IFluidHandler fluids) {
        this.items = items;
        this.energy = energy;
        this.fluids = fluids;
        revive();
    }

    public <T> LazyOptional<T> get(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
        if (capability == ForgeCapabilities.ENERGY) return energyCapability.cast();
        if (capability == ForgeCapabilities.FLUID_HANDLER) return fluidCapability.cast();
        return LazyOptional.empty();
    }

    public void invalidate() {
        itemCapability.invalidate();
        energyCapability.invalidate();
        fluidCapability.invalidate();
    }

    public void revive() {
        if (itemCapability != null) invalidate();
        itemCapability = items == null ? LazyOptional.empty() : LazyOptional.of(() -> items);
        energyCapability = energy == null ? LazyOptional.empty() : LazyOptional.of(() -> energy);
        fluidCapability = fluids == null ? LazyOptional.empty() : LazyOptional.of(() -> fluids);
    }
}
