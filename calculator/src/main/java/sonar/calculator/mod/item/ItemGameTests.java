package sonar.calculator.mod.item;

import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import sonar.calculator.mod.CalculatorContent;

/** These tests need the complete registry and are run by the integration GameTest server. */
@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
public final class ItemGameTests {
    @GameTest(template = "test_empty")
    public static void energySurvivesSerializationAndSimulation(GameTestHelper helper) {
        ItemStack first = CalculatorContent.stack("calculator", 1);
        ItemStack second = CalculatorContent.stack("calculator", 1);
        var energy = first.getCapability(ForgeCapabilities.ENERGY).orElseThrow(() -> new IllegalStateException("Missing energy capability"));
        int accepted = energy.receiveEnergy(100, true);
        helper.assertTrue(accepted > 0 && StackEnergy.get(first) == 0, "Simulation must not change stack NBT");
        helper.assertTrue(energy.receiveEnergy(100, false) == accepted, "Real receive differs from simulation");
        helper.assertTrue(StackEnergy.get(second) == 0, "Distinct item stacks share an energy buffer");
        ItemStack restored = ItemStack.of(first.save(new CompoundTag()));
        helper.assertTrue(StackEnergy.get(restored) == accepted, "Energy lost during save/load");
        helper.assertTrue(StackEnergy.consume(restored, accepted, false), "Saved energy cannot be consumed");
        helper.assertTrue(StackEnergy.get(first) == accepted && StackEnergy.get(restored) == 0, "Restored stack aliases the original NBT");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void storagePersistsAndCannotSwapItsHost(GameTestHelper helper) {
        var player = helper.makeMockSurvivalPlayer();
        ItemStack stack = CalculatorContent.stack("storagemodule", 1);
        UUID identity = UUID.randomUUID();
        stack.getOrCreateTag().putUUID("PortableIdentity", identity);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, stack);
        PortableMenu menu = new PortableMenu(1, player.getInventory(), InteractionHand.MAIN_HAND, 0, identity, "storage");
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 7));
        menu.clicked(0, 0, ClickType.SWAP, player);
        helper.assertTrue(player.getMainHandItem() == stack && menu.getSlot(0).getItem().getCount() == 7, "Hotbar swap moved the live inventory host");
        helper.assertFalse(menu.getSlot(1).mayPlace(stack), "Nested host can be inserted into itself");
        menu.removed(player);
        ItemStack restored = ItemStack.of(stack.save(new CompoundTag()));
        player.getInventory().setItem(0, restored);
        PortableMenu reopened = new PortableMenu(2, player.getInventory(), InteractionHand.MAIN_HAND, 0, identity, "storage");
        helper.assertTrue(reopened.getSlot(0).getItem().is(Items.DIAMOND) && reopened.getSlot(0).getItem().getCount() == 7, "Storage content did not survive reopening");
        player.getInventory().setItem(40, restored);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        helper.assertFalse(reopened.stillValid(player), "Moving the host to the other hand leaves an active menu");
        reopened.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(reopened.getCarried().isEmpty(), "Invalidated menu yielded a duplicate stack");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void craftingConsumesInputsAndDoesNotPersistPreview(GameTestHelper helper) {
        var player = helper.makeMockSurvivalPlayer();
        ItemStack stack = CalculatorContent.stack("craftingcalculator", 1);
        UUID identity = UUID.randomUUID();
        stack.getOrCreateTag().putUUID("PortableIdentity", identity);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, stack);
        PortableMenu menu = new PortableMenu(3, player.getInventory(), InteractionHand.MAIN_HAND, 0, identity, "crafting");
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG, 2));
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS), "Vanilla crafting preview not resolved");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.OAK_PLANKS) && menu.getCarried().getCount() == 4, "Craft did not return exactly one recipe result");
        helper.assertTrue(menu.getSlot(1).getItem().getCount() == 1, "Craft did not consume one ingredient");
        var items = stack.getTag().getCompound("PortableInventories").getCompound("crafting").getList("Items", 10);
        for (int i = 0; i < items.size(); i++) helper.assertTrue(items.getCompound(i).getInt("Slot") != 0, "Output preview was serialized as a real item");
        ItemStack before = menu.getCarried().copy();
        player.getInventory().setItem(0, stack.copy());
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(ItemStack.matches(before, menu.getCarried()), "Replacing the host by a UUID-identical copy bypassed the lock");
        helper.succeed();
    }
    private ItemGameTests() {}
}
