package sonar.calculator.mod.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.network.NetworkHooks;
import sonar.calculator.mod.recipe.CalculatorRecipes;
import sonar.calculator.mod.recipe.MachineRecipe;

/** Persistent portable inventories. Output slots are previews; only the server commits a craft. */
public class PortableMenu extends AbstractContainerMenu {
    private static final Set<String> MODES = Set.of("calculator", "scientific", "flawless", "dynamic", "crafting", "storage");
    private final Player owner;
    private final InteractionHand hand;
    private final int heldSlot;
    private final ItemStack boundStack;
    private final UUID identity;
    private final String mode;
    private final String sessionKey;
    private final CompoundTag persistentInventory;
    private final SimpleContainer contents;
    private final List<CraftRow> rows = new ArrayList<>();
    private final int inventorySize;
    private boolean updating;
    private int syncedEnergy;

    private record CraftRow(String recipeType, int[] inputs, int output) {}

    public static void open(ServerPlayer player, InteractionHand hand, String mode) {
        if (!MODES.contains(mode)) throw new IllegalArgumentException("Unknown portable mode " + mode);
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return;
        if (!stack.getOrCreateTag().hasUUID("PortableIdentity")) stack.getOrCreateTag().putUUID("PortableIdentity", UUID.randomUUID());
        UUID identity = stack.getTag().getUUID("PortableIdentity");
        int heldSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) ->
                new PortableMenu(id, inventory, hand, heldSlot, identity, mode), stack.getHoverName()),
                buf -> { buf.writeEnum(hand); buf.writeVarInt(heldSlot); buf.writeUUID(identity); buf.writeUtf(mode, 32); });
    }

    public PortableMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readEnum(InteractionHand.class), buf.readVarInt(), buf.readUUID(), buf.readUtf(32));
    }

    public PortableMenu(int id, Inventory inventory, InteractionHand hand, int heldSlot, UUID identity, String mode) {
        super(ItemFactory.PORTABLE_MENU.get(), id);
        if (!MODES.contains(mode)) throw new IllegalArgumentException("Unknown portable mode " + mode);
        this.owner = inventory.player;
        this.hand = hand;
        this.heldSlot = heldSlot;
        this.boundStack = inventory.getItem(heldSlot);
        this.identity = identity;
        this.mode = mode;
        if (!(boundStack.getItem() instanceof PortableItem portable)) throw new IllegalArgumentException("PortableMenu requires PortableItem");
        this.sessionKey = portable.inventorySessionKey(boundStack, mode);
        this.persistentInventory = portable.inventoryTag(boundStack, mode, !owner.level().isClientSide);
        this.inventorySize = switch (mode) { case "storage" -> 54; case "crafting", "dynamic" -> 10; case "flawless" -> 5; default -> 3; };
        switch (mode) {
            case "calculator", "scientific" -> rows.add(new CraftRow(mode, new int[]{0, 1}, 2));
            case "flawless" -> rows.add(new CraftRow(mode, new int[]{0, 1, 2, 3}, 4));
            case "dynamic" -> {
                rows.add(new CraftRow("calculator", new int[]{0, 1}, 2));
                rows.add(new CraftRow("scientific", new int[]{3, 4}, 5));
                rows.add(new CraftRow("atomic", new int[]{6, 7, 8}, 9));
            }
            case "crafting" -> rows.add(new CraftRow("crafting", new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, 0));
        }
        this.contents = new SimpleContainer(inventorySize) {
            @Override public void setChanged() { super.setChanged(); if (!updating) contentsChanged(); }
        };
        load();
        for (int slot = 0; slot < inventorySize; slot++) {
            int x, y;
            if (mode.equals("storage")) { x = 8 + slot % 9 * 18; y = 18 + slot / 9 * 18; }
            else if (mode.equals("crafting")) { x = slot == 0 ? 124 : 30 + (slot - 1) % 3 * 18; y = slot == 0 ? 35 : 17 + (slot - 1) / 3 * 18; }
            else if (mode.equals("dynamic")) {
                if (slot < 6) { x = slot % 3 == 2 ? 134 : 25 + slot % 3 * 54; y = 9 + slot / 3 * 26; }
                else { x = slot == 9 ? 134 : 20 + (slot - 6) * 32; y = 61; }
            } else if (mode.equals("flawless")) { x = 17 + slot * 32; y = 35; }
            else { x = slot == 2 ? 134 : 25 + slot * 54; y = 35; }
            final boolean result = isOutput(slot);
            addSlot(new Slot(contents, slot, x, y) {
                @Override public boolean mayPlace(ItemStack stack) { return !result && canStore(stack); }
                @Override public boolean mayPickup(Player player) { return !result && stillValid(player); }
            });
        }
        int playerY = mode.equals("storage") ? 140 : 84;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addPlayerSlot(inventory, 9 + col + row * 9, 8 + col * 18, playerY + row * 18);
        for (int col = 0; col < 9; col++) addPlayerSlot(inventory, col, 8 + col * 18, playerY + 58);
        addDataSlots(new ContainerData() {
            @Override public int get(int index) { int value = owner.level().isClientSide ? syncedEnergy : StackEnergy.get(boundStack); return value >>> (index * 16) & 65535; }
            @Override public void set(int index, int value) { int shift = index * 16; syncedEnergy = (syncedEnergy & ~(65535 << shift)) | ((value & 65535) << shift); }
            @Override public int getCount() { return 2; }
        });
        refreshResults();
    }

    private void addPlayerSlot(Inventory inventory, int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override public boolean mayPickup(Player player) { return index != heldSlot && super.mayPickup(player); }
            @Override public boolean mayPlace(ItemStack stack) { return index != heldSlot && super.mayPlace(stack); }
        });
    }

    public String getMode() { return mode; }
    public int getInventorySize() { return inventorySize; }
    public int getEnergy() { return owner.level().isClientSide ? syncedEnergy : StackEnergy.get(boundStack); }
    public ItemStack getBoundStack() { return boundStack; }
    public int getImageHeight() { return mode.equals("storage") ? 222 : 166; }
    private boolean isOutput(int slot) { return rows.stream().anyMatch(row -> row.output == slot); }
    private boolean canStore(ItemStack stack) {
        return stack != boundStack && stack.getItem() != boundStack.getItem()
                && (!stack.hasTag() || !stack.getTag().hasUUID("PortableIdentity")
                    || !identity.equals(stack.getTag().getUUID("PortableIdentity")));
    }

    @Override public boolean stillValid(Player player) {
        if (player != owner || !player.isAlive()) return false;
        if (owner.level().isClientSide) return true;
        ItemStack held = player.getInventory().getItem(heldSlot);
        return held == boundStack && player.getItemInHand(hand) == boundStack && held.getCount() == 1
                && held.hasTag() && held.getTag().hasUUID("PortableIdentity") && identity.equals(held.getTag().getUUID("PortableIdentity"))
                && held.getItem() instanceof PortableItem portable && sessionKey.equals(portable.inventorySessionKey(held, mode));
    }

    private void load() {
        updating = true;
        {
            ListTag list = persistentInventory.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int slot = entry.getInt("Slot");
                if (slot >= 0 && slot < inventorySize && !isOutput(slot)) contents.setItem(slot, ItemStack.of(entry));
            }
        }
        updating = false;
    }

    private void persist() {
        if (owner.level().isClientSide || !stillValid(owner)) return;
        ListTag list = new ListTag();
        for (int slot = 0; slot < inventorySize; slot++) if (!isOutput(slot) && !contents.getItem(slot).isEmpty()) {
            CompoundTag entry = contents.getItem(slot).save(new CompoundTag());
            entry.putInt("Slot", slot);
            list.add(entry);
        }
        // Re-resolve the live subtag so a capability replacing a compound cannot detach writes.
        ((PortableItem) boundStack.getItem()).inventoryTag(boundStack, mode, true).put("Items", list);
        owner.getInventory().setChanged();
    }

    private void contentsChanged() { persist(); refreshResults(); }
    private List<ItemStack> inputs(CraftRow row) {
        List<ItemStack> input = new ArrayList<>();
        for (int slot : row.inputs) input.add(contents.getItem(slot));
        return input;
    }
    private CraftingContainer crafting(CraftRow row) {
        CraftingContainer matrix = new TransientCraftingContainer(this, 3, 3);
        for (int i = 0; i < row.inputs.length; i++) matrix.setItem(i, contents.getItem(row.inputs[i]).copy());
        return matrix;
    }
    private void refreshResults() {
        if (owner.level().isClientSide || updating) return;
        updating = true;
        for (CraftRow row : rows) {
            ItemStack preview = ItemStack.EMPTY;
            if (row.recipeType.equals("crafting")) {
                CraftingContainer matrix = crafting(row);
                var recipe = owner.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, matrix, owner.level());
                if (recipe.isPresent()) preview = recipe.get().assemble(matrix, owner.level().registryAccess());
            } else {
                MachineRecipe recipe = CalculatorRecipes.find(owner.level(), row.recipeType, inputs(row));
                if (recipe != null && !recipe.getOutputs().isEmpty()) preview = recipe.getOutputs().get(0).copy();
            }
            contents.setItem(row.output, preview);
        }
        updating = false;
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        if (type == ClickType.SWAP && (button == heldSlot || button == 40 && hand == InteractionHand.OFF_HAND)) return;
        if (slot >= 0 && slot < slots.size() && slots.get(slot).container == player.getInventory() && slots.get(slot).getContainerSlot() == heldSlot) return;
        if (slot >= 0 && slot < inventorySize && isOutput(slot)) {
            if (!owner.level().isClientSide && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE))
                craft(rows.stream().filter(row -> row.output == slot).findFirst().orElseThrow(), type == ClickType.QUICK_MOVE);
            return;
        }
        super.clicked(slot, button, type, player);
        if (!owner.level().isClientSide) { contentsChanged(); broadcastChanges(); }
    }

    private boolean canAccept(ItemStack output, boolean shift) {
        if (output.isEmpty()) return false;
        if (!shift) {
            ItemStack carried = getCarried();
            return carried.isEmpty() || ItemStack.isSameItemSameTags(carried, output) && carried.getCount() + output.getCount() <= carried.getMaxStackSize();
        }
        int room = 0;
        for (int i = 0; i < 36; i++) {
            if (i == heldSlot) continue;
            ItemStack target = owner.getInventory().getItem(i);
            if (target.isEmpty()) room += output.getMaxStackSize();
            else if (ItemStack.isSameItemSameTags(target, output)) room += Math.max(0, target.getMaxStackSize() - target.getCount());
            if (room >= output.getCount()) return true;
        }
        return false;
    }

    private boolean craft(CraftRow row, boolean shift) {
        if (!stillValid(owner) || owner.level().isClientSide) return false;
        refreshResults();
        ItemStack preview = contents.getItem(row.output);
        if (!canAccept(preview, shift)) return false;
        int cost = mode.equals("calculator") || mode.equals("scientific") ? preview.getCount() : 0;
        if (!owner.getAbilities().instabuild && !StackEnergy.consume(boundStack, cost, true)) return false;
        List<ItemStack> output;
        List<ItemStack> remainders = new ArrayList<>();
        updating = true;
        if (row.recipeType.equals("crafting")) {
            CraftingContainer matrix = crafting(row);
            CraftingRecipe recipe = owner.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, matrix, owner.level()).orElse(null);
            if (recipe == null) { updating = false; return false; }
            output = List.of(recipe.assemble(matrix, owner.level().registryAccess()));
            remainders.addAll(recipe.getRemainingItems(matrix));
            for (int slot : row.inputs) if (!contents.getItem(slot).isEmpty()) contents.getItem(slot).shrink(1);
            owner.awardRecipes(List.of(recipe));
        } else {
            MachineRecipe recipe = CalculatorRecipes.find(owner.level(), row.recipeType, inputs(row));
            if (recipe == null || !recipe.matches(inputs(row))) { updating = false; return false; }
            List<ItemStack> before = inputs(row).stream().map(ItemStack::copy).toList();
            if (!recipe.consumeInputs(inputs(row))) { updating = false; return false; }
            for (int i = 0; i < row.inputs.length; i++) {
                ItemStack ingredient = before.get(i);
                int consumed = ingredient.getCount() - contents.getItem(row.inputs[i]).getCount();
                ItemStack remainder = consumed > 0 && ingredient.hasCraftingRemainingItem() ? ingredient.getCraftingRemainingItem() : ItemStack.EMPTY;
                if (!remainder.isEmpty()) remainder.setCount(remainder.getCount() * consumed);
                remainders.add(remainder);
            }
            output = recipe.rollOutputs(owner.getRandom());
        }
        if (!owner.getAbilities().instabuild) StackEnergy.consume(boundStack, cost, false);
        for (int i = 0; i < row.inputs.length && i < remainders.size(); i++) {
            ItemStack remainder = remainders.get(i);
            if (remainder.isEmpty()) continue;
            ItemStack ingredient = contents.getItem(row.inputs[i]);
            if (ingredient.isEmpty()) contents.setItem(row.inputs[i], remainder);
            else if (ItemStack.isSameItemSameTags(ingredient, remainder) && ingredient.getCount() + remainder.getCount() <= ingredient.getMaxStackSize()) ingredient.grow(remainder.getCount());
            else give(remainder);
        }
        for (int i = 0; i < output.size(); i++) {
            ItemStack result = output.get(i).copy();
            if (result.isEmpty()) continue;
            result.onCraftedBy(owner.level(), owner, result.getCount());
            if (i == 0 && !shift && canAccept(result, false)) {
                if (getCarried().isEmpty()) setCarried(result); else getCarried().grow(result.getCount());
            } else give(result);
        }
        updating = false;
        contentsChanged();
        broadcastChanges();
        return true;
    }

    private void give(ItemStack stack) { if (!owner.getInventory().add(stack)) owner.drop(stack, false); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        if (index < inventorySize && isOutput(index)) {
            ItemStack copy = slots.get(index).getItem().copy();
            return craft(rows.stream().filter(row -> row.output == index).findFirst().orElseThrow(), true) ? copy : ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), before = stack.copy();
        if (index < inventorySize) {
            if (!moveItemStackTo(stack, inventorySize, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!canStore(stack)) return ItemStack.EMPTY;
            int start = 0, end = inventorySize;
            if (mode.equals("dynamic")) {
                CraftRow active = rows.stream().filter(row -> inputs(row).stream().anyMatch(s -> !s.isEmpty())).findFirst().orElse(rows.get(0));
                start = active.inputs[0]; end = active.output;
            }
            if (!moveItemStackTo(stack, start, end, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        contentsChanged();
        return before;
    }

    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.container != contents || !isOutput(slot.getContainerSlot()); }
    @Override public void removed(Player player) { persist(); super.removed(player); }
}
