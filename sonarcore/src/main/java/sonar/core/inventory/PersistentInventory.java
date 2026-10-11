package sonar.core.inventory;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/** A fixed-size persistent inventory. Callers must use handler methods to mutate stacks. */
public class PersistentInventory extends ItemStackHandler {
    private final Runnable dirty;
    private boolean loading;

    public PersistentInventory(int slots, Runnable dirty) {
        super(slots);
        this.dirty = Objects.requireNonNull(dirty, "dirty");
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (!loading) dirty.run();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        // ItemStackHandler normally trusts Size. The machine's constructor defines the real size.
        loading = true;
        try {
            for (int slot = 0; slot < getSlots(); slot++) stacks.set(slot, ItemStack.EMPTY);
            ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag entry = items.getCompound(i);
                int slot = entry.getInt("Slot");
                if (slot >= 0 && slot < getSlots()) {
                    ItemStack stack = ItemStack.of(entry);
                    if (!stack.isEmpty()) stack.setCount(Math.min(stack.getCount(),
                            Math.min(getSlotLimit(slot), stack.getMaxStackSize())));
                    stacks.set(slot, stack);
                }
            }
            onLoad();
        } finally {
            loading = false;
        }
    }
}
