package sonar.calculator.mod.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.machine.MachineBlockEntity;
import sonar.calculator.mod.machine.MachineContent;

@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
public final class MachineGameTests {
    static MachineBlockEntity place(GameTestHelper helper, BlockPos relative, String id) {
        helper.setBlock(relative, CalculatorContent.block(id));
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        helper.assertTrue(entity instanceof MachineBlockEntity, "No real machine BlockEntity for " + id);
        MachineBlockEntity machine = (MachineBlockEntity) entity;
        helper.assertTrue(machine.getType() == MachineContent.MACHINE.get() && machine.id().equals(id), "Wrong registered machine type or id");
        return machine;
    }

    @GameTest(template = "test_empty")
    public static void machineNbtRoundTripAndCapabilities(GameTestHelper helper) {
        MachineBlockEntity original = place(helper, new BlockPos(1, 1, 1), "stoneseparator");
        original.inventory.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 7));
        original.energy.setEnergy(12345);
        original.progress = 37;
        original.state.putBoolean("paused", true);
        original.upgrades.setStackInSlot(0, CalculatorContent.stack("speedupgrade", 3));
        CompoundTag saved = original.saveWithFullMetadata();
        BlockEntity loaded = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), saved);
        helper.assertTrue(loaded instanceof MachineBlockEntity, "Registered BE factory failed NBT load");
        MachineBlockEntity restored = (MachineBlockEntity) loaded;
        helper.assertTrue(restored.id().equals("stoneseparator") && restored.progress == 37 && restored.energy.getEnergyStored() == 12345,
                "Machine id/progress/energy lost during NBT round trip");
        helper.assertTrue(ItemStack.matches(restored.inventory.getStackInSlot(0), new ItemStack(Items.OAK_LOG, 7)), "Machine inventory did not round trip");
        helper.assertTrue(restored.upgrades.getStackInSlot(0).getCount() == 3 && restored.state.getBoolean("paused"), "Upgrade/pause state did not round trip");
        restored.inventory.getStackInSlot(0).shrink(1);
        restored.state.putBoolean("paused", false);
        helper.assertTrue(original.inventory.getStackInSlot(0).getCount() == 7 && original.state.getBoolean("paused"), "Loaded machine aliases original state");
        var cap = original.getCapability(ForgeCapabilities.ENERGY);
        helper.assertTrue(cap.isPresent(), "Machine energy capability missing");
        original.invalidateCaps();
        helper.assertFalse(cap.isPresent(), "Removed capability remains live");
        original.reviveCaps();
        helper.assertTrue(original.getCapability(ForgeCapabilities.ENERGY).isPresent(), "Revived machine lacks energy capability");
        TestReports.write("machine-nbt", TestReports.report("Real registered machine NBT serialization, alias isolation, capability invalidation; not disk/chunk restart"));
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 280)
    public static void worldTickerProcessesOneLogAndIdlePreservesEnergy(GameTestHelper helper) {
        MachineBlockEntity active = place(helper, new BlockPos(1, 1, 1), "stoneseparator");
        MachineBlockEntity idle = place(helper, new BlockPos(3, 1, 1), "stoneseparator");
        active.inventory.setStackInSlot(0, new ItemStack(Items.OAK_LOG));
        active.energy.setEnergy(10000);
        idle.energy.setEnergy(10000);
        helper.runAfterDelay(230, () -> {
            helper.assertTrue(active.tickCount() >= 200 && idle.tickCount() >= 200, "World did not tick placed machines");
            helper.assertTrue(active.inventory.getStackInSlot(0).isEmpty(), "World ticker did not consume log");
            helper.assertTrue(ItemStack.matches(active.inventory.getStackInSlot(2), new ItemStack(Items.OAK_PLANKS, 4))
                    && ItemStack.matches(active.inventory.getStackInSlot(3), new ItemStack(Items.STICK, 2)), "Stone separator outputs differ from upstream");
            helper.assertTrue(active.energy.getEnergyStored() == 9500, "One unupgraded upstream operation must consume 500 RF at default config");
            helper.assertTrue(idle.energy.getEnergyStored() == 10000 && idle.progress == 0 && !idle.isActive(), "Idle machine consumed energy or advanced progress");
            var report = TestReports.report("Actual server world ticks: one oak log -> 4 oak planks + 2 sticks, 500 energy; default configuration required");
            report.addProperty("observed_machine_ticks", active.tickCount());
            report.addProperty("energy_consumed", 10000 - active.energy.getEnergyStored());
            TestReports.write("machine-world-tick", report);
            helper.succeed();
        });
    }
    private MachineGameTests() {}
}
