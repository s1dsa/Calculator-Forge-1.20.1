package sonar.calculator.mod.machine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import sonar.core.inventory.PersistentInventory;

/** Storage chamber slots retain an int count: ItemStack's byte Count is never used for bulk storage. */
final class MachineInventory extends PersistentInventory {
    private final MachineBlockEntity machine;
    MachineInventory(MachineBlockEntity machine, int slots) { super(slots, machine::setChanged); this.machine = machine; }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return machine.canInsert(slot, stack); }
    @Override public int getSlotLimit(int slot) {
        if (machine == null) return 64;
        return machine.id().equals("storagechamber") ? 1024 : machine.id().equals("calculatorplug") || machine.id().equals("magneticflux") || machine.id().equals("moduleworkstation") ? 1 : 64;
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!machine.id().equals("storagechamber")) return super.insertItem(slot, stack, simulate);
        validateSlotIndex(slot);
        if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
        ItemStack present = stacks.get(slot);
        if (!present.isEmpty() && !ItemStack.isSameItemSameTags(present, stack)) return stack;
        int added = Math.min(stack.getCount(), 1024 - present.getCount());
        if (added <= 0) return stack;
        if (!simulate) { ItemStack result = stack.copy(); result.setCount(present.getCount() + added); stacks.set(slot, result); onContentsChanged(slot); }
        ItemStack remainder = stack.copy(); remainder.shrink(added); return remainder;
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!machine.id().equals("storagechamber")) return super.extractItem(slot, amount, simulate);
        validateSlotIndex(slot);
        ItemStack present = stacks.get(slot);
        if (present.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int removed = Math.min(Math.min(amount, present.getCount()), present.getMaxStackSize());
        ItemStack result = present.copy(); result.setCount(removed);
        if (!simulate) { ItemStack rest = present.copy(); rest.shrink(removed); stacks.set(slot, rest); onContentsChanged(slot); }
        return result;
    }
    @Override public CompoundTag serializeNBT() {
        if (!machine.id().equals("storagechamber")) return super.serializeNBT();
        CompoundTag tag = new CompoundTag(); ListTag items = new ListTag();
        for (int i = 0; i < getSlots(); i++) if (!stacks.get(i).isEmpty()) {
            ItemStack sample = stacks.get(i).copy(); sample.setCount(1);
            CompoundTag entry = sample.save(new CompoundTag()); entry.putInt("Slot", i); entry.putInt("StoredCount", stacks.get(i).getCount()); items.add(entry);
        }
        tag.put("Items", items); return tag;
    }
    @Override public void deserializeNBT(CompoundTag tag) {
        if (!machine.id().equals("storagechamber")) { super.deserializeNBT(tag); return; }
        for (int i = 0; i < getSlots(); i++) stacks.set(i, ItemStack.EMPTY);
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i); int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= getSlots()) continue;
            ItemStack stack = ItemStack.of(entry); stack.setCount(Math.max(0, Math.min(1024, entry.getInt("StoredCount")))); stacks.set(slot, stack);
        }
    }
}
