package sonar.calculator.mod.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.item.PortableMenu;
import sonar.calculator.mod.item.StackEnergy;
import sonar.calculator.mod.item.modules.FlawlessCalculatorItem;
import sonar.calculator.mod.item.modules.ModuleItems;
import sonar.calculator.mod.machine.MachineBlockEntity;
import sonar.calculator.mod.machine.MachineMenu;

/** Real registered block entities, server players and server menu transactions. */
@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
public final class WorkstationGameTests {
    private record Session(MachineBlockEntity machine, ServerPlayer player, MachineMenu menu,
                           ItemStack calculator) {}

    private static void withSession(GameTestHelper helper, Consumer<Session> test) {
        MachineBlockEntity machine = MachineGameTests.place(helper, new BlockPos(1, 1, 1), "moduleworkstation");
        // The vanilla helper enters Forge's login hooks with an unactivated Connection.
        // Use an unregistered real player; EmbeddedChannel fires Connection.channelActive.
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "workstation-test"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.assertTrue(connection.channel() == channel && connection.isConnected(),
                    "Fixture must activate its real server connection");
            connection.setProtocol(ConnectionProtocol.PLAY);
            new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player);
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            BlockPos pos = machine.getBlockPos();
            player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
            ItemStack calculator = CalculatorContent.stack("flawlesscalculator", 1);
            calculator.getOrCreateTag().putBoolean("ModulesInitialized", true);
            machine.inventory.setStackInSlot(1, calculator);
            MachineMenu menu = new MachineMenu(1, player.getInventory(), machine);
            player.containerMenu = menu;
            helper.assertTrue(menu.stillValid(player) && player.gameMode.isSurvival() && player.mayBuild()
                    && player.level().mayInteract(player, pos), "Fixture must permit a real server transaction");
            test.accept(new Session(machine, player, menu, calculator));
            channel.runPendingTasks();
            channel.checkException();
        } finally {
            // Do not close a menu with test-owned cursor items (vanilla may drop them).
            player.containerMenu = player.inventoryMenu;
            player.getInventory().clearContent();
            try {
                player.getTextFilter().leave();
            } finally {
                try {
                    // No PlayerList.remove: this player was never registered or added to the level.
                    channel.finishAndReleaseAll();
                } finally {
                    machine.clearForRemoval();
                }
            }
        }
        helper.succeed();
    }

    private static long revision(ItemStack calculator) {
        return calculator.getOrCreateTag().getLong("ModuleRevision");
    }

    private static CompoundTag payload(String mode) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Energy", 1234L);
        tag.putString("WorkstationMarker", "preserve-this-tag");
        tag.putUUID("PortableIdentity", UUID.randomUUID());
        CompoundTag entry = new ItemStack(Items.DIAMOND, 7).save(new CompoundTag());
        entry.putInt("Slot", 0);
        ListTag items = new ListTag();
        items.add(entry);
        CompoundTag contents = new CompoundTag();
        contents.put("Items", items);
        CompoundTag inventories = new CompoundTag();
        inventories.put(mode, contents);
        tag.put("PortableInventories", inventories);
        return tag;
    }

    private static CompoundTag installedPayload(CompoundTag input) {
        CompoundTag expected = input.copy();
        expected.remove("PortableIdentity");
        return expected;
    }

    @GameTest(template = "test_empty")
    public static void pickupInstallsTaggedModuleConsumesExactlyOneAndCannotRepeat(GameTestHelper helper) {
        withSession(helper, s -> {
            ItemStack pearls = new ItemStack(Items.ENDER_PEARL, 3);
            CompoundTag inputTag = payload("storage");
            pearls.setTag(inputTag.copy());
            s.menu.setCarried(pearls);
            long before = revision(s.calculator);
            s.menu.clicked(0, 0, ClickType.PICKUP, s.player);
            helper.assertTrue(s.menu.getCarried().getCount() == 2, "PICKUP installation must itself consume exactly one");
            helper.assertTrue(ModuleItems.moduleName(s.calculator, 0).equals("Ender")
                    && revision(s.calculator) == before + 1, "Installation must write module name and increment revision once");
            FlawlessCalculatorItem item = (FlawlessCalculatorItem) s.calculator.getItem();
            CompoundTag expected = installedPayload(inputTag);
            helper.assertTrue(item.getModuleTag(s.calculator, 0, false).equals(expected), "Installation lost module NBT or retained portable identity");
            helper.assertTrue(s.menu.getCarried().getTag().equals(inputTag), "Installation mutated remainder NBT");
            s.menu.clicked(0, 1, ClickType.PICKUP, s.player);
            helper.assertFalse(s.menu.clickMenuButton(s.player, 400), "Occupied module button must reject repeat installation");
            helper.assertTrue(s.menu.getCarried().getCount() == 2 && revision(s.calculator) == before + 1
                    && item.getModuleTag(s.calculator, 0, false).equals(expected), "Repeated installation consumed input or changed calculator");
            s.menu.getCarried().getTag().putLong("Energy", 99);
            helper.assertTrue(item.getModuleTag(s.calculator, 0, false).equals(expected), "Installed NBT aliases cursor remainder");
        });
    }

    @GameTest(template = "test_empty")
    public static void removalPreservesEnergyContentsAndAdvancesRevision(GameTestHelper helper) {
        withSession(helper, s -> {
            FlawlessCalculatorItem item = (FlawlessCalculatorItem) s.calculator.getItem();
            String[] ids = {"energymodule", "storagemodule"};
            for (int slot = 0; slot < ids.length; slot++) {
                ItemStack input = CalculatorContent.stack(ids[slot], 1);
                CompoundTag original = payload("storage");
                input.setTag(original);
                s.menu.setCarried(input);
                long before = revision(s.calculator);
                helper.assertTrue(s.menu.clickMenuButton(s.player, 400 + slot), "Valid module install button rejected");
                helper.assertTrue(s.menu.getCarried().isEmpty() && revision(s.calculator) == before + 1, "Single module input was not consumed exactly once");
                if (slot == 0) helper.assertTrue(ModuleItems.getEnergy(s.calculator) == 1234, "Installed energy was lost");
                CompoundTag installed = item.getModuleTag(s.calculator, slot, false);
                helper.assertTrue(s.menu.clickMenuButton(s.player, 416 + slot), "Empty cursor must accept removal");
                ItemStack removed = s.menu.getCarried();
                helper.assertTrue(removed.is(CalculatorContent.item(ids[slot])) && removed.getCount() == 1
                        && installedPayload(original).equals(removed.getTag()), "Removal must retain complete energy/contents NBT");
                helper.assertTrue(revision(s.calculator) == before + 2 && ModuleItems.moduleName(s.calculator, slot).equals("Empty")
                        && !s.calculator.getTag().contains(Integer.toString(slot)), "Removal must delete nested contents and increment revision once");
                if (slot == 0) helper.assertTrue(ModuleItems.getEnergy(s.calculator) == 0, "Removed energy remains owned by calculator");
                s.menu.setCarried(ItemStack.EMPTY);
                helper.assertFalse(s.menu.clickMenuButton(s.player, 416 + slot), "Repeated removal must not produce a duplicate");
                helper.assertTrue(s.menu.getCarried().isEmpty() && revision(s.calculator) == before + 2, "Repeated removal changed state");
                installed.putLong("Energy", 55);
                helper.assertTrue(removed.getTag().getLong("Energy") == 1234, "Removed item aliases detached module tag");
            }
        });
    }

    @GameTest(template = "test_empty")
    public static void virtualSlotsNeverPersistOrShiftOutDuplicateModules(GameTestHelper helper) {
        withSession(helper, s -> {
            ItemStack module = CalculatorContent.stack("storagemodule", 1);
            module.setTag(payload("storage"));
            s.menu.setCarried(module);
            helper.assertTrue(s.menu.clickMenuButton(s.player, 400), "Setup installation failed");
            CompoundTag before = s.machine.saveWithFullMetadata();
            ItemStack view = s.menu.getSlot(0).getItem();
            helper.assertTrue(view.is(CalculatorContent.item("storagemodule")) && view.getCount() == 1, "Module view missing");
            view.getOrCreateTag().putLong("Energy", 99);
            s.menu.getSlot(0).set(new ItemStack(Items.DIAMOND));
            helper.assertTrue(s.menu.quickMoveStack(s.player, 0).isEmpty(), "Virtual module shift must reject extraction");
            s.menu.clicked(0, 0, ClickType.QUICK_MOVE, s.player);
            s.menu.clicked(0, 0, ClickType.CLONE, s.player);
            helper.assertTrue(s.menu.getCarried().isEmpty() && s.player.getInventory().isEmpty()
                    && s.machine.saveWithFullMetadata().equals(before), "Virtual slot operation duplicated or mutated stored module");
            ListTag entries = before.getCompound("Inventory").getList("Items", Tag.TAG_COMPOUND);
            helper.assertTrue(entries.size() == 1 && entries.getCompound(0).getInt("Slot") == 1,
                    "Machine must persist only physical calculator, never virtual module stacks");
            BlockEntity loaded = BlockEntity.loadStatic(s.machine.getBlockPos(), s.machine.getBlockState(), before);
            helper.assertTrue(loaded instanceof MachineBlockEntity, "Real registered workstation NBT factory failed");
            MachineBlockEntity restored = (MachineBlockEntity) loaded;
            helper.assertTrue(restored.inventory.getSlots() == s.machine.inventory.getSlots(), "NBT changed physical inventory size");
            for (int slot = 0; slot < restored.inventory.getSlots(); slot++) {
                helper.assertTrue(slot == 1 ? ItemStack.matches(restored.inventory.getStackInSlot(slot), s.calculator)
                        : restored.inventory.getStackInSlot(slot).isEmpty(), "Restored machine contains duplicate virtual item at " + slot);
            }
            restored.inventory.getStackInSlot(1).getOrCreateTag().putLong("ModuleRevision", 99);
            helper.assertTrue(revision(s.calculator) == 1, "Restored calculator aliases original NBT");
        });
    }

    @GameTest(template = "test_empty")
    public static void shiftInstallsOnlyAvailableModulesAndDoesNotConsumeTwice(GameTestHelper helper) {
        withSession(helper, s -> {
            s.player.getInventory().setItem(13, new ItemStack(Items.ENDER_PEARL, 3));
            int source = s.menu.getMachineSlots() + 4; // Player inventory slot 13 follows slot 9 by four columns.
            helper.assertTrue(!s.menu.quickMoveStack(s.player, source).isEmpty(), "Valid shift installation rejected");
            helper.assertTrue(s.player.getInventory().getItem(13).isEmpty() && revision(s.calculator) == 3, "Shift must consume three inputs and commit three revisions");
            for (int slot = 0; slot < 16; slot++) helper.assertTrue(ModuleItems.moduleName(s.calculator, slot).equals(slot < 3 ? "Ender" : "Empty"), "Shift installed unexpected module at " + slot);
            helper.assertTrue(s.menu.quickMoveStack(s.player, source).isEmpty() && revision(s.calculator) == 3, "Repeated shift duplicated modules");
            s.player.getInventory().setItem(13, new ItemStack(Items.DIRT, 4));
            CompoundTag before = s.calculator.getTag().copy();
            helper.assertTrue(s.menu.quickMoveStack(s.player, source).isEmpty()
                    && s.player.getInventory().getItem(13).getCount() == 4 && before.equals(s.calculator.getTag()), "Unsupported shift input changed ownership");
        });
    }

    @GameTest(template = "test_empty")
    public static void distantSessionRejectsPickupButtonsAndShift(GameTestHelper helper) {
        withSession(helper, s -> {
            s.menu.setCarried(new ItemStack(Items.ENDER_PEARL, 2));
            s.player.getInventory().setItem(13, new ItemStack(Items.ENDER_PEARL, 3));
            CompoundTag before = s.machine.saveWithFullMetadata();
            s.player.setPos(s.player.getX() + 20, s.player.getY(), s.player.getZ());
            helper.assertFalse(s.menu.stillValid(s.player), "Distance must invalidate menu");
            s.menu.clicked(0, 0, ClickType.PICKUP, s.player);
            helper.assertFalse(s.menu.clickMenuButton(s.player, 400), "Distant button accepted");
            helper.assertTrue(s.menu.quickMoveStack(s.player, s.menu.getMachineSlots() + 4).isEmpty()
                    && s.menu.quickMoveStack(s.player, 16).isEmpty(), "Distant shift accepted");
            helper.assertTrue(s.menu.getCarried().getCount() == 2 && s.player.getInventory().getItem(13).getCount() == 3
                    && before.equals(s.machine.saveWithFullMetadata()), "Invalid distance transaction changed item ownership");
        });
    }

    @GameTest(template = "test_empty")
    public static void inactiveSessionRejectsShiftInstallation(GameTestHelper helper) {
        withSession(helper, s -> {
            s.player.getInventory().setItem(13, new ItemStack(Items.ENDER_PEARL, 3));
            CompoundTag before = s.machine.saveWithFullMetadata();
            s.player.containerMenu = s.player.inventoryMenu;
            helper.assertTrue(s.menu.stillValid(s.player), "Fixture must isolate menu identity from distance validity");
            helper.assertFalse(s.menu.clickMenuButton(s.player, 400), "Inactive button accepted");
            helper.assertTrue(s.menu.quickMoveStack(s.player, s.menu.getMachineSlots() + 4).isEmpty(), "Inactive workstation session accepted shift installation");
            helper.assertTrue(s.player.getInventory().getItem(13).getCount() == 3
                    && before.equals(s.machine.saveWithFullMetadata()), "Inactive session consumed input or created modules");
        });
    }

    @GameTest(template = "test_empty")
    public static void removedStorageRevisionRejectsStalePortableShiftWithoutCopyingContents(GameTestHelper helper) {
        withSession(helper, s -> {
            ItemStack storage = CalculatorContent.stack("storagemodule", 1);
            storage.setTag(payload("storage"));
            s.menu.setCarried(storage);
            helper.assertTrue(s.menu.clickMenuButton(s.player, 400), "Storage setup failed");
            ItemStack held = s.machine.inventory.extractItem(1, 1, false);
            helper.assertTrue(held == s.calculator || ItemStack.matches(held, s.calculator), "Calculator extraction lost contents");
            s.player.getInventory().selected = 0;
            s.player.getInventory().setItem(0, held);
            UUID identity = UUID.randomUUID();
            held.getOrCreateTag().putUUID("PortableIdentity", identity);
            PortableMenu portable = new PortableMenu(2, s.player.getInventory(), InteractionHand.MAIN_HAND, 0, identity, "storage");
            s.player.containerMenu = portable;
            helper.assertTrue(portable.stillValid(s.player) && portable.getSlot(0).getItem().is(Items.DIAMOND)
                    && portable.getSlot(0).getItem().getCount() == 7, "Real portable menu did not load installed contents");
            long before = revision(held);
            ItemStack removed = ModuleItems.removeModule(held, 0);
            helper.assertTrue(removed.is(CalculatorContent.item("storagemodule")) && revision(held) == before + 1, "Storage removal did not invalidate revision");
            CompoundTag removedTag = removed.getTag().copy();
            CompoundTag heldTag = held.getTag().copy();
            helper.assertFalse(portable.stillValid(s.player), "Removed module must invalidate portable session");
            helper.assertTrue(portable.quickMoveStack(s.player, 0).isEmpty(), "Stale portable shift copied removed contents");
            portable.clicked(0, 0, ClickType.QUICK_MOVE, s.player);
            portable.removed(s.player);
            helper.assertTrue(portable.getCarried().isEmpty() && heldTag.equals(held.getTag())
                    && removedTag.equals(removed.getTag()), "Stale shift/close rewrote removed or held module NBT");
            for (int slot = 1; slot < 36; slot++) helper.assertTrue(s.player.getInventory().getItem(slot).isEmpty(), "Stale session duplicated storage contents into player slot " + slot);
        });
    }

    @GameTest(template = "test_empty")
    public static void multipleEnergyModulesAggregateAndConsumeAtomicallyAcrossSlots(GameTestHelper helper) {
        withSession(helper, s -> {
            FlawlessCalculatorItem item = (FlawlessCalculatorItem) s.calculator.getItem();
            int[] amounts = {30, 70};
            for (int slot = 0; slot < amounts.length; slot++) {
                ItemStack module = CalculatorContent.stack("energymodule", 1);
                module.getOrCreateTag().putLong("Energy", amounts[slot]);
                s.menu.setCarried(module);
                helper.assertTrue(s.menu.clickMenuButton(s.player, 400 + slot), "Energy module installation failed");
            }
            helper.assertTrue(StackEnergy.get(s.calculator) == 100 && ModuleItems.getEnergy(s.calculator) == 100,
                    "StackEnergy must aggregate both installed energy modules");
            CompoundTag before = s.calculator.getTag().copy();
            helper.assertTrue(StackEnergy.consume(s.calculator, 80, true), "Simulation must see energy beyond first module");
            helper.assertTrue(before.equals(s.calculator.getTag()), "Successful consumption simulation mutated NBT");
            helper.assertFalse(StackEnergy.consume(s.calculator, 101, true), "Insufficient simulated aggregate consumption accepted");
            helper.assertFalse(StackEnergy.consume(s.calculator, 101, false), "Insufficient real aggregate consumption accepted");
            helper.assertTrue(before.equals(s.calculator.getTag()), "Insufficient consumption partially drained a module");
            helper.assertTrue(StackEnergy.consume(s.calculator, 80, false), "Cross-module consumption failed");
            helper.assertTrue(item.getModuleTag(s.calculator, 0, false).getLong("Energy") == 0
                    && item.getModuleTag(s.calculator, 1, false).getLong("Energy") == 20
                    && StackEnergy.get(s.calculator) == 20, "Cross-module deduction must drain 30 then 50 exactly once");
            helper.assertTrue(revision(s.calculator) == 2, "Energy deduction changed module installation revision");
            before = s.calculator.getTag().copy();
            helper.assertFalse(StackEnergy.consume(s.calculator, -1, false), "Negative consumption accepted");
            helper.assertTrue(StackEnergy.consume(s.calculator, 0, true) && StackEnergy.consume(s.calculator, 0, false)
                    && before.equals(s.calculator.getTag()), "Zero/negative consumption mutated NBT");
            helper.assertTrue(StackEnergy.consume(s.calculator, 20, false) && StackEnergy.get(s.calculator) == 0,
                    "Final consumption did not drain remaining aggregate energy");
            helper.assertFalse(StackEnergy.consume(s.calculator, 1, false), "Empty modules allowed further consumption");
        });
    }

    private WorkstationGameTests() {}
}
