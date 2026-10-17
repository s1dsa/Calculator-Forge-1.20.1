package sonar.calculator.mod.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import sonar.calculator.mod.machine.ConductorStatus;
import sonar.calculator.mod.machine.MachineBlockEntity;
import sonar.calculator.mod.machine.MachineContent;
import sonar.calculator.mod.machine.MachineMenu;

/** Conductor Mast wait/status branch and its server-to-client menu synchronisation. */
@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
public final class TeslaStatusGameTests {

    @GameTest(template = "test_empty")
    public static void conductorStatusMatchesOriginalBranchesAndBoundaries(GameTestHelper helper) {
        // Upstream condition: !(delay < 600) && timer < 500 -> humorous message, else seconds.
        helper.assertTrue(ConductorStatus.messageKey(599, 0, 8) == null,
                "delay 599 must use the seconds branch");
        helper.assertTrue("conductor.msg8".equals(ConductorStatus.messageKey(600, 0, 8)),
                "delay 600/timer 0 must show the rolled message");
        helper.assertTrue("conductor.msg8".equals(ConductorStatus.messageKey(600, 499, 8)),
                "timer 499 is still inside the message window");
        helper.assertTrue(ConductorStatus.messageKey(600, 500, 8) == null,
                "timer 500 must switch to seconds");
        helper.assertTrue("conductor.msg".equals(ConductorStatus.messageKey(1800, 0, 0)),
                "message index 0 uses the legacy conductor.msg key");
        helper.assertTrue("conductor.msg5".equals(ConductorStatus.messageKey(1800, 100, 5)),
                "message index must select its matching key");
        helper.assertTrue(ConductorStatus.messageKey(1800, 0, 9) == null,
                "out-of-range message index must not display an unrelated message");
        helper.assertTrue(ConductorStatus.messageKey(1800, 0, -1) == null,
                "negative message index must not display an unrelated message");
        // Original formatting: delay / 20 - timer / 20, including the fractional tick truncation.
        helper.assertTrue(ConductorStatus.remainingSeconds(1800, 0) == 90, "1800 ticks are 90 seconds");
        helper.assertTrue(ConductorStatus.remainingSeconds(1800, 19) == 90, "19 ticks still round down");
        helper.assertTrue(ConductorStatus.remainingSeconds(1800, 20) == 89, "20 ticks subtract one second");
        helper.assertTrue(ConductorStatus.remainingSeconds(0, 0) == 0, "no delay shows zero seconds");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void conductorStateAndThirtyTwoBitMenuSyncSurviveRoundTrip(GameTestHelper helper) {
        MachineBlockEntity mast = MachineGameTests.place(helper, new BlockPos(1, 1, 1), "conductormast");
        mast.state.putInt("lightningDelay", 1800);
        mast.state.putInt("lightningTimer", 0);
        mast.state.putInt("lightningRandom", 8);
        // Full-width values must survive the two unsigned 16-bit halves vanilla uses on the wire.
        helper.assertTrue(MachineMenu.dataHalf(1800, 0) == 1800 && MachineMenu.dataHalf(1800, 1) == 0,
                "32-bit value split into wrong halves");
        helper.assertTrue(MachineMenu.joinDataHalves(MachineMenu.dataHalf(1800, 0), MachineMenu.dataHalf(1800, 1)) == 1800,
                "32-bit value did not recombine");
        helper.assertTrue(MachineMenu.dataHalf(-1, 0) == 65535 && MachineMenu.dataHalf(-1, 1) == 65535
                && MachineMenu.joinDataHalves(65535, 65535) == -1, "signed int halves are not preserved");

        CompoundTag update = mast.getUpdateTag();
        MachineBlockEntity client = MachineContent.MACHINE.get().create(mast.getBlockPos(), mast.getBlockState());
        helper.assertTrue(client != null, "registered client block entity factory failed");
        client.handleUpdateTag(update);
        helper.assertTrue(client.getStateValue("lightningDelay") == 1800
                        && client.getStateValue("lightningTimer") == 0
                        && client.getStateValue("lightningRandom") == 8,
                "client did not receive the complete conductor status state");

        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "tesla-test"));
        net.minecraft.network.Connection connection = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
        connection.setProtocol(net.minecraft.network.ConnectionProtocol.PLAY);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = mast.getBlockPos();
        player.moveTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        var previousMenu = player.containerMenu;
        player.containerMenu = new MachineMenu(1, player.getInventory(), mast);
        try {
            MachineMenu menu = (MachineMenu) player.containerMenu;
            helper.assertTrue(menu.getStateValue("lightningDelay") == 1800
                            && menu.getStateValue("lightningTimer") == 0
                            && menu.getStateValue("lightningRandom") == 8,
                    "menu getters lost the conductor status values");
        } finally {
            player.containerMenu = previousMenu;
            player.getTextFilter().leave();
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void conductorRollsWaitMessageAndCountsTimer(GameTestHelper helper) {
        MachineBlockEntity mast = MachineGameTests.place(helper, new BlockPos(1, 1, 1), "conductormast");
        mast.state.putInt("lightningDelay", 0);
        mast.state.putInt("lightningTimer", 0);
        mast.state.putInt("lightningRandom", 99);
        MachineBlockEntity.serverTick(helper.getLevel(), mast.getBlockPos(), mast.getBlockState(), mast);
        int delay = mast.getStateValue("lightningDelay");
        int random = mast.getStateValue("lightningRandom");
        helper.assertTrue(delay >= 1500 && delay <= 1799, "solo mast delay must stay in the original 1500..1799 range");
        helper.assertTrue(random >= 0 && random < ConductorStatus.MESSAGE_COUNT,
                "new delay must roll a valid wait-message index");
        helper.assertTrue(mast.getStateValue("lightningTimer") == 1, "timer must start counting after a delay is chosen");
        MachineBlockEntity.serverTick(helper.getLevel(), mast.getBlockPos(), mast.getBlockState(), mast);
        helper.assertTrue(mast.getStateValue("lightningTimer") == 2, "timer must advance once per server tick");
        helper.succeed();
    }

    private TeslaStatusGameTests() {}
}
