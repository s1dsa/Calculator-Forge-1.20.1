package sonar.calculator.mod.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import sonar.calculator.mod.machine.MachineBlock;
import sonar.calculator.mod.machine.MachineBlockEntity;
import sonar.calculator.mod.machine.MachineMenu;

@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
public final class ReinforcedChestRound4GameTests {
    private static final class Viewer implements AutoCloseable {
        final ServerPlayer player;
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel channel = new EmbeddedChannel(connection);
        final MachineMenu menu;
        Viewer(GameTestHelper h, MachineBlockEntity chest, int id) {
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "chest-round4"));
            connection.setProtocol(ConnectionProtocol.PLAY);
            new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player);
            BlockPos pos = chest.getBlockPos();
            player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
            menu = new MachineMenu(id, player.getInventory(), chest);
            player.containerMenu = menu;
            h.assertTrue(connection.isConnected() && menu.stillValid(player), "Real PLAY connection/menu fixture invalid");
        }
        public void close() {
            if (player.containerMenu != player.inventoryMenu) player.closeContainer();
            player.containerMenu = player.inventoryMenu;
            player.getTextFilter().leave();
            channel.finishAndReleaseAll();
        }
    }
    private static MachineBlockEntity chest(GameTestHelper h) { return MachineGameTests.place(h, new BlockPos(1, 1, 1), "reinforcedchest"); }
    private static void tick(MachineBlockEntity c) { MachineBlockEntity.serverTick(c.getLevel(), c.getBlockPos(), c.getBlockState(), c); }

    @GameTest(template = "test_empty", timeoutTicks = 300)
    public static void actualTicksOpenCloseThenIdle200WithoutUpdates(GameTestHelper h) {
        MachineBlockEntity c = chest(h);
        c.inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 17));
        Viewer v = new Viewer(h, c, 1);
        h.runAtTickTime(15, () -> {
            h.assertTrue(c.getStateValue("lidAngle") == 1000 && c.chestOpenerCount() == 1, "Live ticks did not fully open");
            v.player.closeContainer();
            h.assertTrue(c.chestOpenerCount() == 0, "Menu removed left opener");
        });
        long[] updates = new long[1];
        h.runAtTickTime(30, () -> {
            h.assertTrue(c.getStateValue("lidAngle") == 0, "Closing must reach exact zero");
            updates[0] = c.syncCount();
        });
        for (int t = 31; t <= 231; t++) h.runAtTickTime(t, () -> {
            h.assertTrue(c.getStateValue("lidAngle") == 0 && c.getUpdatePacket().getTag().getCompound("MachineState").getInt("lidAngle") == 0, "Idle state/packet reopened lid");
            h.assertTrue(c.syncCount() == updates[0], "Idle chest repeatedly sent block updates");
            h.assertTrue(c.inventory.getStackInSlot(0).getCount() == 17, "Menu lifetime duplicated/lost inventory");
        });
        h.runAtTickTime(232, () -> { v.close(); h.succeed(); });
    }
    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void twoViewersCloseOnlyAfterLastRealMenu(GameTestHelper h) {
        MachineBlockEntity c = chest(h);
        Viewer a = new Viewer(h, c, 1), b = new Viewer(h, c, 2);
        h.runAtTickTime(15, () -> {
            h.assertTrue(c.chestOpenerCount() == 2 && c.getStateValue("lidAngle") == 1000, "Two viewers not counted");
            a.player.closeContainer();
        });
        h.runAtTickTime(30, () -> {
            h.assertTrue(c.chestOpenerCount() == 1 && c.getStateValue("lidAngle") == 1000, "Closing one viewer closed shared chest");
            b.player.closeContainer();
        });
        h.runAtTickTime(45, () -> {
            h.assertTrue(c.chestOpenerCount() == 0 && c.getStateValue("lidAngle") == 0, "Last viewer did not close");
            a.close(); b.close(); h.succeed();
        });
    }
    @GameTest(template = "test_empty")
    public static void distanceDisconnectAndRemovalPruneOpeners(GameTestHelper h) {
        MachineBlockEntity c = chest(h);
        try (Viewer a = new Viewer(h, c, 1)) {
            for (int i = 0; i < 10; i++) tick(c);
            a.player.setPos(a.player.getX() + 20, a.player.getY(), a.player.getZ());
            for (int i = 0; i < 10; i++) tick(c);
            h.assertTrue(c.chestOpenerCount() == 0 && c.getStateValue("lidAngle") == 0, "Distance left permanent opener");
        }
        try (Viewer b = new Viewer(h, c, 2)) {
            tick(c); b.channel.close(); tick(c);
            h.assertTrue(c.chestOpenerCount() == 0 && c.getStateValue("lidAngle") == 0, "Disconnected connection left opener");
        }
        try (Viewer d = new Viewer(h, c, 3)) {
            c.clearForRemoval(); h.getLevel().removeBlock(c.getBlockPos(), false);
            h.assertTrue(!d.menu.stillValid(d.player) && c.chestOpenerCount() == 0, "Removed block kept valid menu");
        }
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void constructedUnboundAndRemovedMenusNeverReopen(GameTestHelper h) {
        MachineBlockEntity c = chest(h);
        try (Viewer v = new Viewer(h, c, 1)) {
            v.player.containerMenu = v.player.inventoryMenu;
            tick(c);
            h.assertTrue(c.chestOpenerCount() == 0, "Unbound menu counted as opener");
            MachineMenu unbound = new MachineMenu(2, v.player.getInventory(), c);
            tick(c);
            h.assertTrue(c.chestOpenerCount() == 0 && c.getStateValue("lidAngle") == 0, "Constructor alone opened chest");
            v.player.containerMenu = unbound;
            unbound.removed(v.player);
            tick(c);
            h.assertTrue(c.chestOpenerCount() == 0, "Removed menu still bound reopened lid");
            v.player.containerMenu = v.player.inventoryMenu;
        }
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void closedShapeAllFacingsIsFixedDuringAnimation(GameTestHelper h) {
        MachineBlockEntity c = chest(h);
        AABB expected = new AABB(1D/16, 0, 1D/16, 15D/16, 14D/16, 15D/16);
        for (Direction facing : Direction.Plane.HORIZONTAL) for (int angle : new int[]{0, 100, 500, 1000}) {
            var state = c.getBlockState().setValue(MachineBlock.FACING, facing);
            c.state.putInt("lidAngle", angle);
            h.assertTrue(state.getShape(h.getLevel(), c.getBlockPos()).bounds().equals(expected), "Selection bounds mismatch");
            h.assertTrue(state.getCollisionShape(h.getLevel(), c.getBlockPos(), CollisionContext.empty()).bounds().equals(expected), "Collision bounds mismatch/animated");
            h.assertTrue(state.getOcclusionShape(h.getLevel(), c.getBlockPos()).isEmpty(), "Chest occludes open geometry");
        }
        h.succeed();
    }
    private static void clientTick(MachineBlockEntity c) { MachineBlockEntity.clientTick(null, c.getBlockPos(), c.getBlockState(), c); }
    @GameTest(template = "test_empty")
    public static void interpolationClosesAndDuplicatePacketsStayZero200Ticks(GameTestHelper h) {
        MachineBlockEntity server = chest(h);
        MachineBlockEntity client = new MachineBlockEntity(server.getBlockPos(), server.getBlockState());
        client.handleUpdateTag(server.getUpdateTag()); clientTick(client);
        for (int value = 100; value <= 1000; value += 100) {
            server.state.putInt("lidAngle", value);
            client.onDataPacket(null, server.getUpdatePacket()); clientTick(client);
            h.assertTrue(Math.abs(client.getLidAngle(.5F) - (value - 50) / 1000F) < .00001F, "Opening midpoint/speed incorrect");
        }
        for (int value = 900; value >= 0; value -= 100) {
            server.state.putInt("lidAngle", value);
            client.onDataPacket(null, server.getUpdatePacket()); clientTick(client);
            h.assertTrue(Math.abs(client.getLidAngle(.5F) - (value + 50) / 1000F) < .00001F, "Closing midpoint/speed incorrect");
        }
        for (int t = 0; t < 200; t++) {
            client.handleUpdateTag(server.getUpdateTag()); client.onDataPacket(null, server.getUpdatePacket()); clientTick(client);
            for (float partial : new float[]{0, .1F, .5F, .99F, 1}) h.assertTrue(client.getLidAngle(partial) == 0, "Final previous angle caused repeating sawtooth");
        }
        h.succeed();
    }
    @GameTest(template = "test_empty")
    public static void independentEntitiesSamePositionDoNotShareInterpolation(GameTestHelper h) {
        MachineBlockEntity c = chest(h);
        MachineBlockEntity other = new MachineBlockEntity(c.getBlockPos(), c.getBlockState());
        clientTick(c); clientTick(other);
        c.state.putInt("lidAngle", 1000); clientTick(c);
        for (int i = 0; i < 100; i++) {
            h.assertTrue(c.getLidAngle(.5F) == .5F && other.getLidAngle(.5F) == 0, "Rendering sample mutated/shared interpolation");
        }
        clientTick(c);
        h.assertTrue(c.getLidAngle(0) == 1 && other.getLidAngle(1) == 0, "Tick did not settle independent entity");
        h.succeed();
    }
}
