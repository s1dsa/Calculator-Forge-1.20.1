package sonar.core.gametest;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;
import sonar.core.energy.ItemEnergyStorage;
import sonar.core.energy.StoredEnergy;
import sonar.core.fluids.PersistentFluidTank;
import sonar.core.inventory.PersistentInventory;
import sonar.core.menu.IntDataSlots;
import sonar.core.recipe.RecipeTools;

/** Real Forge/Minecraft objects; the bundled sonarcore:empty template is required to run these tests. */
@GameTestHolder("sonarcore")
@PrefixGameTestTemplate(false)
public final class CoreGameTests {
    private CoreGameTests() { }

    @GameTest(template = "empty")
    public static void energySaturatesWithoutOverflow(GameTestHelper helper) {
        int[] dirty = {0};
        StoredEnergy energy = new StoredEnergy(Integer.MAX_VALUE, 7, 11, () -> dirty[0]++);
        energy.setEnergy(Integer.MAX_VALUE - 3);
        equal(helper, energy.generate(Integer.MAX_VALUE, true), 3, "simulated saturation");
        equal(helper, energy.getEnergyStored(), Integer.MAX_VALUE - 3, "simulation must preserve energy");
        equal(helper, dirty[0], 1, "simulation must not dirty");
        equal(helper, energy.generate(Integer.MAX_VALUE), 3, "generation saturates without overflow");
        equal(helper, energy.generate(1), 0, "full storage rejects energy");
        equal(helper, energy.extractEnergy(Integer.MAX_VALUE, true), 11, "external extract rate");
        equal(helper, energy.consume(Integer.MAX_VALUE, true), Integer.MAX_VALUE, "internal consumption ignores rate");
        equal(helper, dirty[0], 2, "no-op and simulation must not dirty");
        equal(helper, energy.consume(Integer.MAX_VALUE), Integer.MAX_VALUE, "consume down to zero");
        equal(helper, energy.receiveEnergy(Integer.MAX_VALUE, false), 7, "external receive rate");
        equal(helper, energy.getEnergyStored(), 7, "stored amount after receiving");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void energyRejectsNegativeAndClampsNbt(GameTestHelper helper) {
        int[] dirty = {0};
        StoredEnergy energy = new StoredEnergy(100, () -> dirty[0]++);
        energy.setEnergy(40);
        for (boolean simulate : new boolean[] {true, false}) {
            equal(helper, energy.receiveEnergy(Integer.MIN_VALUE, simulate), 0, "negative receive");
            equal(helper, energy.extractEnergy(-1, simulate), 0, "negative extract");
            equal(helper, energy.generate(-1, simulate), 0, "negative generation");
            equal(helper, energy.consume(-1, simulate), 0, "negative consumption");
        }
        equal(helper, energy.getEnergyStored(), 40, "negative operations preserve state");
        equal(helper, dirty[0], 1, "negative operations do not dirty");
        CompoundTag saved = new CompoundTag();
        saved.putInt("Energy", Integer.MAX_VALUE);
        saved.putInt("Capacity", Integer.MAX_VALUE);
        energy.deserializeNBT(saved);
        equal(helper, energy.getEnergyStored(), 100, "NBT energy bounded by constructor capacity");
        equal(helper, energy.getMaxEnergyStored(), 100, "NBT cannot change capacity");
        energy.deserializeNBT(IntTag.valueOf(-1));
        equal(helper, energy.getEnergyStored(), 0, "legacy numeric NBT clamps negative energy");
        energy.deserializeNBT(IntTag.valueOf(53));
        equal(helper, energy.serializeNBT().getInt("Energy"), 53, "legacy numeric NBT remains readable");
        equal(helper, dirty[0], 1, "loading does not dirty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void itemEnergyTracksLiveStackAndRejectsEmpty(GameTestHelper helper) {
        ItemEnergyStorage empty = new ItemEnergyStorage(ItemStack.EMPTY, 100, 100, 100);
        equal(helper, empty.receiveEnergy(25, true), 0, "empty stack simulated receive");
        equal(helper, empty.receiveEnergy(25, false), 0, "empty stack cannot accept lost energy");
        ItemStack stack = new ItemStack(Items.STICK);
        ItemEnergyStorage energy = new ItemEnergyStorage(stack, 100, 30, 20);
        equal(helper, energy.receiveEnergy(80, true), 30, "item simulated rate limit");
        helper.assertTrue(!stack.hasTag(), "simulation must not create item NBT");
        equal(helper, energy.receiveEnergy(80, false), 30, "item receive rate limit");
        ItemStack copy = stack.copy();
        stack.getOrCreateTag().putInt("SonarEnergy", 90);
        equal(helper, energy.getEnergyStored(), 90, "wrapper reads live NBT");
        equal(helper, new ItemEnergyStorage(copy, 100, 30, 20).getEnergyStored(), 30, "copied stack is independent");
        equal(helper, energy.generate(Integer.MAX_VALUE, false), 10, "item generation saturates");
        equal(helper, energy.extractEnergy(-1, false), 0, "negative item extraction");
        stack.setCount(0);
        equal(helper, energy.generate(10, false), 0, "previously bound stack became empty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryNbtCannotResizeOrOverfill(GameTestHelper helper) {
        int[] dirty = {0};
        PersistentInventory inventory = new PersistentInventory(2, () -> dirty[0]++);
        inventory.setStackInSlot(1, new ItemStack(Items.DIRT));
        CompoundTag saved = new CompoundTag();
        saved.putInt("Size", Integer.MAX_VALUE);
        ListTag items = new ListTag();
        items.add(itemEntry(0, new ItemStack(Items.STONE, 100)));
        items.add(itemEntry(-1, new ItemStack(Items.DIAMOND)));
        items.add(itemEntry(2, new ItemStack(Items.DIAMOND)));
        saved.put("Items", items);
        inventory.deserializeNBT(saved);
        equal(helper, inventory.getSlots(), 2, "NBT Size cannot resize inventory");
        equal(helper, inventory.getStackInSlot(0).getCount(), 64, "NBT stack respects item and slot limits");
        helper.assertTrue(inventory.getStackInSlot(1).isEmpty(), "loading clears stale slots");
        equal(helper, dirty[0], 1, "NBT load must not dirty");
        PersistentInventory restored = new PersistentInventory(2, () -> { });
        restored.deserializeNBT(inventory.serializeNBT());
        helper.assertTrue(ItemStack.matches(inventory.getStackInSlot(0), restored.getStackInSlot(0)), "inventory NBT round trip");
        inventory.deserializeNBT(new CompoundTag());
        equal(helper, inventory.getSlots(), 2, "missing Size preserves constructor size");
        helper.assertTrue(inventory.getStackInSlot(0).isEmpty(), "missing Items clears inventory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventorySimulationAndSingleStackLimit(GameTestHelper helper) {
        int[] dirty = {0};
        PersistentInventory inventory = new PersistentInventory(1, () -> dirty[0]++);
        ItemStack input = new ItemStack(Items.STONE, 64);
        helper.assertTrue(inventory.insertItem(0, input, true).isEmpty(), "simulated insertion fits");
        helper.assertTrue(inventory.getStackInSlot(0).isEmpty(), "simulated insertion leaves slot empty");
        equal(helper, dirty[0], 0, "simulated insertion does not dirty");
        inventory.insertItem(0, input, false);
        equal(helper, inventory.extractItem(0, 12, true).getCount(), 12, "simulated extraction amount");
        equal(helper, inventory.getStackInSlot(0).getCount(), 64, "simulated extraction preserves stack");
        equal(helper, input.getCount(), 64, "handler does not mutate caller stack");
        CompoundTag saved = new CompoundTag();
        ListTag items = new ListTag();
        items.add(itemEntry(0, new ItemStack(Items.IRON_SWORD, 12)));
        saved.put("Items", items);
        inventory.deserializeNBT(saved);
        equal(helper, inventory.getStackInSlot(0).getCount(), 1, "unstackable NBT clamps to one");
        equal(helper, dirty[0], 1, "simulations and loading do not add dirty callbacks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overlappingIngredientsRequireReassignment(GameTestHelper helper) {
        PersistentInventory inventory = new PersistentInventory(3, () -> { });
        inventory.setStackInSlot(0, new ItemStack(Items.STONE));
        inventory.setStackInSlot(1, new ItemStack(Items.DIRT));
        inventory.setStackInSlot(2, new ItemStack(Items.COBBLESTONE));
        List<Ingredient> recipe = List.of(Ingredient.of(Items.STONE, Items.DIRT),
                Ingredient.of(Items.STONE, Items.COBBLESTONE), Ingredient.of(Items.STONE, Items.COBBLESTONE));
        int[] match = RecipeTools.matchShapeless(recipe, inventory).orElseThrow(() -> new AssertionError("valid overlap needs reassignment"));
        for (int i = 0; i < match.length; i++)
            helper.assertTrue(recipe.get(i).test(inventory.getStackInSlot(match[i])), "assignment must satisfy original ingredient order");
        for (int i = 0; i < 3; i++) equal(helper, inventory.getStackInSlot(i).getCount(), 1, "matching is read-only");
        helper.assertTrue(RecipeTools.consumeMatched(inventory, match), "matched recipe can consume");
        for (int i = 0; i < 3; i++) helper.assertTrue(inventory.getStackInSlot(i).isEmpty(), "each ingredient consumed exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void matchingCountsAndPreflightPreventPartialConsumption(GameTestHelper helper) {
        PersistentInventory inventory = new PersistentInventory(2, () -> { });
        List<Ingredient> recipe = List.of(Ingredient.of(Items.STONE), Ingredient.of(Items.STONE));
        inventory.setStackInSlot(0, new ItemStack(Items.STONE));
        helper.assertTrue(RecipeTools.matchShapeless(recipe, inventory).isEmpty(), "one item cannot satisfy two ingredients");
        inventory.setStackInSlot(0, new ItemStack(Items.STONE, 2));
        int[] match = RecipeTools.matchShapeless(recipe, inventory).orElseThrow();
        equal(helper, match[0], 0, "first ingredient shares stack");
        equal(helper, match[1], 0, "second ingredient shares stack");
        helper.assertTrue(!RecipeTools.consumeMatched(inventory, new int[] {0, 1}), "missing second slot fails preflight");
        equal(helper, inventory.getStackInSlot(0).getCount(), 2, "failed preflight does not consume earlier slot");
        inventory.extractItem(0, 1, false);
        helper.assertTrue(!RecipeTools.consumeMatched(inventory, match), "stale count fails preflight");
        equal(helper, inventory.getStackInSlot(0).getCount(), 1, "stale match does not partly consume");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void outputCapacityReservesAcrossAllOutputs(GameTestHelper helper) {
        PersistentInventory inventory = new PersistentInventory(1, () -> { });
        inventory.setStackInSlot(0, new ItemStack(Items.STONE, 60));
        List<ItemStack> fits = List.of(new ItemStack(Items.STONE, 2), new ItemStack(Items.STONE, 2));
        List<ItemStack> excess = List.of(new ItemStack(Items.STONE, 3), new ItemStack(Items.STONE, 2));
        helper.assertTrue(RecipeTools.canInsertAll(inventory, fits), "duplicate outputs exactly fill remaining space");
        helper.assertTrue(!RecipeTools.canInsertAll(inventory, excess), "duplicate outputs cannot reserve same free space twice");
        equal(helper, inventory.getStackInSlot(0).getCount(), 60, "output preflight is read-only");
        List<ItemStack> remainder = RecipeTools.insertAll(inventory, excess);
        equal(helper, inventory.getStackInSlot(0).getCount(), 64, "execute respects capacity");
        equal(helper, remainder.size(), 1, "overflow is returned");
        equal(helper, remainder.get(0).getCount(), 1, "overflow count conserved");
        equal(helper, excess.get(0).getCount(), 3, "caller output remains unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void outputCapacityRespectsTagsAndHandlerRestrictions(GameTestHelper helper) {
        ItemStackHandler restricted = new ItemStackHandler(1) {
            @Override public int getSlotLimit(int slot) { return 4; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(Items.STONE); }
        };
        helper.assertTrue(!RecipeTools.canInsertAll(restricted, List.of(new ItemStack(Items.DIRT))), "slot validator rejects output");
        helper.assertTrue(!RecipeTools.canInsertAll(restricted, List.of(new ItemStack(Items.STONE, 3), new ItemStack(Items.STONE, 2))), "custom slot limit reserves duplicate output");
        ItemStack tagged = new ItemStack(Items.STONE, 2);
        tagged.getOrCreateTag().putInt("Variant", 1);
        helper.assertTrue(!RecipeTools.canInsertAll(restricted, List.of(tagged, new ItemStack(Items.STONE))), "different NBT cannot share virtual empty slot");
        PersistentInventory inventory = new PersistentInventory(2, () -> { });
        inventory.setStackInSlot(1, new ItemStack(Items.STONE, 63));
        helper.assertTrue(RecipeTools.canInsertAll(inventory, List.of(new ItemStack(Items.STONE), new ItemStack(Items.DIRT, 64))), "existing stack must fill before empty slot");
        helper.assertTrue(RecipeTools.insertAll(inventory, List.of(new ItemStack(Items.STONE), new ItemStack(Items.DIRT, 64))).isEmpty(), "execution agrees with preflight");
        helper.assertTrue(inventory.getStackInSlot(0).is(Items.DIRT), "empty slot reserved for different item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeHelpersRejectDuplicateSlotsAndBlockedExtraction(GameTestHelper helper) {
        ItemStackHandler locked = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        };
        locked.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        helper.assertTrue(RecipeTools.matchShapeless(List.of(Ingredient.of(Items.STONE)), locked).isEmpty(), "visible but nonextractable items cannot craft");
        boolean rejected = false;
        try { RecipeTools.canInsertAll(locked, List.of(new ItemStack(Items.STONE)), 0, 0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "duplicate output slot cannot inflate capacity");
        rejected = false;
        try { RecipeTools.matchShapeless(List.of(Ingredient.of(Items.STONE)), locked, 0, 0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "duplicate input slot cannot inflate available ingredients");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidNbtAndSimulationRespectCapacityAndValidator(GameTestHelper helper) {
        int[] dirty = {0};
        PersistentFluidTank tank = new PersistentFluidTank(1000, fluid -> fluid.getFluid() == Fluids.WATER, () -> dirty[0]++);
        equal(helper, tank.fill(new FluidStack(Fluids.WATER, 1500), FluidAction.SIMULATE), 1000, "simulated fluid capacity");
        equal(helper, tank.getFluidAmount(), 0, "fluid fill simulation does not mutate");
        equal(helper, dirty[0], 0, "fluid simulation does not dirty");
        equal(helper, tank.fill(new FluidStack(Fluids.WATER, 1500), FluidAction.EXECUTE), 1000, "fluid fill saturates");
        equal(helper, tank.drain(250, FluidAction.SIMULATE).getAmount(), 250, "simulated drain amount");
        equal(helper, tank.getFluidAmount(), 1000, "fluid drain simulation does not mutate");
        equal(helper, dirty[0], 1, "only executed fill dirties");
        tank.deserializeNBT(new FluidStack(Fluids.WATER, Integer.MAX_VALUE).writeToNBT(new CompoundTag()));
        equal(helper, tank.getFluidAmount(), 1000, "fluid NBT clamps capacity");
        PersistentFluidTank restored = new PersistentFluidTank(1000, () -> { });
        restored.deserializeNBT(tank.serializeNBT());
        equal(helper, restored.getFluidAmount(), 1000, "fluid NBT round trip");
        tank.deserializeNBT(new FluidStack(Fluids.LAVA, 500).writeToNBT(new CompoundTag()));
        helper.assertTrue(tank.isEmpty(), "invalid saved fluid is rejected");
        equal(helper, tank.fill(new FluidStack(Fluids.LAVA, 500), FluidAction.EXECUTE), 0, "runtime validator rejects lava");
        equal(helper, dirty[0], 1, "fluid NBT loading is clean");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void intDataSlotsSurviveSignedShortWireAndUpdateOrder(GameTestHelper helper) {
        int[] value = {0};
        ContainerData source = IntDataSlots.of(() -> value[0], next -> value[0] = next);
        ContainerData destination = IntDataSlots.buffer();
        equal(helper, source.getCount(), 2, "32 bits use two data slots");
        int[] values = {0, 32767, 32768, 65535, 65536, 0x1234abcd, Integer.MAX_VALUE, Integer.MIN_VALUE, -1, 0};
        for (int next : values) {
            value[0] = next;
            destination.set(0, (short) source.get(0));
            destination.set(1, (short) source.get(1));
            equal(helper, IntDataSlots.get(destination), next, "low-first signed-short wire round trip");
            destination.set(0, 0xaaaa);
            destination.set(1, 0x5555);
            destination.set(1, (short) source.get(1));
            destination.set(0, (short) source.get(0));
            equal(helper, IntDataSlots.get(destination), next, "high-first signed-short wire round trip");
        }
        helper.succeed();
    }

    private static CompoundTag itemEntry(int slot, ItemStack stack) {
        CompoundTag tag = stack.save(new CompoundTag());
        tag.putInt("Slot", slot);
        return tag;
    }

    private static void equal(GameTestHelper helper, int actual, int expected, String message) {
        helper.assertTrue(actual == expected, message + ": expected " + expected + ", got " + actual);
    }
}
