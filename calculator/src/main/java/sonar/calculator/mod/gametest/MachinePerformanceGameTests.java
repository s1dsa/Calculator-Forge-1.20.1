package sonar.calculator.mod.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import sonar.calculator.mod.machine.MachineBlockEntity;

/** Measures actual scheduled server ticks; no synthetic ticker loop and no client FPS estimates. */
@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = "calculator", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MachinePerformanceGameTests {
    private static final int WARMUP = 100, SAMPLES = 300;
    private static Session session;

    @GameTest(template = "test_perf", batch = "calculator_performance", timeoutTicks = 2300)
    public static void realWorld64And256MachineSampling(GameTestHelper helper) {
        helper.assertTrue(session == null, "Performance sampler already running; run this test alone");
        session = new Session(helper);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        Session current = session;
        if (current == null || event.getServer() != current.helper.getLevel().getServer()) return;
        if (event.phase == TickEvent.Phase.START) {
            current.started = System.nanoTime();
            return;
        }
        if (current.started == 0) return;
        long elapsed = System.nanoTime() - current.started;
        current.started = 0;
        try { current.endTick(elapsed); }
        catch (Throwable failure) {
            session = null;
            current.cleanup();
            current.helper.runAfterDelay(1, () -> current.helper.fail("Performance sampling failed: " + failure));
        }
    }

    private static final class Session {
        final GameTestHelper helper;
        final List<MachineBlockEntity> machines = new ArrayList<>();
        final List<BlockPos> positions = new ArrayList<>();
        final JsonArray scenarios = new JsonArray();
        final long[] timings = new long[SAMPLES];
        int phase, ticks, operations, activeObservations;
        long started;
        Session(GameTestHelper helper) { this.helper = helper; }
        int count() { return phase == 0 ? 0 : phase < 3 ? 64 : 256; }
        boolean active() { return phase == 2 || phase == 4; }

        void prepare() {
            for (int i = 0; i < count(); i++) {
                BlockPos pos = new BlockPos(1 + i % 16, 1, 1 + i / 16);
                positions.add(pos);
                MachineBlockEntity machine = MachineGameTests.place(helper, pos, "stoneseparator");
                machine.energy.setEnergy(40000);
                if (active()) machine.inventory.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 64));
                machines.add(machine);
            }
        }

        void endTick(long elapsed) {
            ticks++;
            if (ticks > WARMUP) timings[ticks - WARMUP - 1] = elapsed;
            for (MachineBlockEntity machine : machines) {
                helper.assertTrue(!machine.isRemoved() && machine.tickCount() > 0, "Placed machine did not receive world ticks");
                if (!active()) {
                    helper.assertTrue(machine.energy.getEnergyStored() == 40000 && machine.progress == 0 && !machine.isActive(), "Idle machine state changed");
                } else {
                    if (ticks > WARMUP && machine.isActive()) activeObservations++;
                    if (!machine.inventory.getStackInSlot(2).isEmpty()) {
                        helper.assertTrue(machine.inventory.getStackInSlot(2).is(Items.OAK_PLANKS)
                                && machine.inventory.getStackInSlot(2).getCount() == 4
                                && machine.inventory.getStackInSlot(3).is(Items.STICK)
                                && machine.inventory.getStackInSlot(3).getCount() == 2, "Sampled active machine produced incorrect output");
                        if (ticks > WARMUP) operations++;
                        machine.inventory.setStackInSlot(2, ItemStack.EMPTY);
                        machine.inventory.setStackInSlot(3, ItemStack.EMPTY);
                    }
                }
            }
            if (ticks != WARMUP + SAMPLES) return;
            if (active()) helper.assertTrue(operations >= count() && activeObservations >= count() * (SAMPLES - 10), "Active workload did not remain active or complete work");
            JsonObject scenario = new JsonObject();
            scenario.addProperty("machine_count", count());
            scenario.addProperty("workload", phase == 0 ? "empty_test_structure" : active() ? "active_stone_separator" : "idle_stone_separator");
            scenario.addProperty("warmup_ticks", WARMUP);
            scenario.addProperty("sample_ticks", SAMPLES);
            scenario.addProperty("completed_operations_in_sample", operations);
            scenario.addProperty("active_machine_tick_observations", activeObservations);
            JsonArray raw = new JsonArray();
            for (long value : timings) raw.add(value);
            scenario.add("server_tick_ns", raw);
            long[] sorted = timings.clone(); Arrays.sort(sorted);
            scenario.addProperty("mean_ms", Arrays.stream(timings).average().orElseThrow() / 1000000.0);
            scenario.addProperty("p50_ms", sorted[149] / 1000000.0);
            scenario.addProperty("p95_ms", sorted[284] / 1000000.0);
            scenario.addProperty("p99_ms", sorted[296] / 1000000.0);
            scenario.addProperty("max_ms", sorted[299] / 1000000.0);
            scenarios.add(scenario);
            cleanup();
            phase++;
            if (phase == 5) {
                JsonObject report = TestReports.report("Forge server START->END event wall time; actual world-scheduled machines; not isolated per-machine CPU time or FPS");
                report.addProperty("measurement_note", "100 warmup ticks + 300 samples each. Empty control then idle/active 64 and 256. Run alone: other server work/GC contributes. Fixture placement and output draining occur outside timed interval. No universal performance pass threshold.");
                report.addProperty("processors", Runtime.getRuntime().availableProcessors());
                report.addProperty("max_heap_bytes", Runtime.getRuntime().maxMemory());
                report.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
                report.add("scenarios", scenarios);
                TestReports.write("machine-performance", report);
                session = null;
                helper.runAfterDelay(1, helper::succeed);
            } else {
                ticks = 0; operations = 0; activeObservations = 0;
                prepare();
            }
        }

        void cleanup() {
            for (MachineBlockEntity machine : machines) machine.clearForRemoval();
            for (BlockPos pos : positions) helper.setBlock(pos, Blocks.AIR);
            machines.clear(); positions.clear();
        }
    }
    private MachinePerformanceGameTests() {}
}
