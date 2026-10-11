package sonar.core.inventory;

import java.util.Arrays;
import java.util.function.IntPredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** A live sided view: slot mapping is fixed, insertion/extraction policy can change at runtime. */
public final class SidedInventory implements IItemHandler {
    private final IItemHandler inventory;
    private final int[] slots;
    private final IntPredicate insert;
    private final IntPredicate extract;

    public SidedInventory(IItemHandler inventory, int[] slots, IntPredicate insert, IntPredicate extract) {
        this.inventory = inventory;
        this.slots = slots.clone();
        if (Arrays.stream(this.slots).distinct().count() != this.slots.length) throw new IllegalArgumentException("Duplicate slots");
        for (int slot : this.slots) if (slot < 0 || slot >= inventory.getSlots()) throw new IllegalArgumentException("Invalid slot " + slot);
        this.insert = insert;
        this.extract = extract;
    }

    @Override public int getSlots() { return slots.length; }
    private int mapped(int slot) {
        if (slot < 0 || slot >= slots.length) throw new IndexOutOfBoundsException(slot);
        return slots[slot];
    }
    @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(mapped(slot)); }
    @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(mapped(slot)); }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return insert.test(mapped(slot)) && inventory.isItemValid(mapped(slot), stack); }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        int actual = mapped(slot);
        return insert.test(actual) ? inventory.insertItem(actual, stack, simulate) : stack;
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int actual = mapped(slot);
        return extract.test(actual) ? inventory.extractItem(actual, amount, simulate) : ItemStack.EMPTY;
    }
}
