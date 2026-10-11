package sonar.core.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Common shift-click behavior: add machine slots first, then the player inventory exactly once. */
public abstract class SonarMenu extends AbstractContainerMenu {
    private int machineSlots = -1;

    protected SonarMenu(MenuType<?> type, int containerId) { super(type, containerId); }

    protected final void addPlayerInventory(Inventory inventory, int x, int y) {
        if (machineSlots != -1) throw new IllegalStateException("Player inventory already added");
        machineSlots = slots.size();
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column, x + column * 18, y + 58));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (machineSlots < 0 || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            int hotbarStart = machineSlots + 27;
            if (index < hotbarStart) {
                if (!moveItemStackTo(stack, hotbarStart, slots.size(), false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, machineSlots, hotbarStart, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
