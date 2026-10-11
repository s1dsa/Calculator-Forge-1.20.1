package sonar.core.energy;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/** Return this from Item.initCapabilities. Energy persists in the ItemStack's ordinary NBT. */
public final class ItemEnergyProvider implements ICapabilityProvider {
    private final ItemEnergyStorage storage;
    private final LazyOptional<IEnergyStorage> capability;

    public ItemEnergyProvider(ItemStack stack, int capacity, int maxReceive, int maxExtract) {
        storage = new ItemEnergyStorage(stack, capacity, maxReceive, maxExtract);
        capability = LazyOptional.of(() -> storage);
    }

    public ItemEnergyStorage storage() { return storage; }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> requested, @Nullable Direction side) {
        return requested == ForgeCapabilities.ENERGY ? capability.cast() : LazyOptional.empty();
    }
}
