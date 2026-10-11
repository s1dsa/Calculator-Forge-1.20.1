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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.machine.MachineBlockEntity;
import sonar.calculator.mod.machine.MachineMenu;

/**
 * Feedback-round machine regressions (user feedback #3, #5/#7/#10, #6, #11, #13, #9).
 *
 * <p>These tests exercise real registered blocks, real world ticks and server-authoritative actions through the
 * same entry points the network layer uses. They never launch a client, so client rendering (world lightning
 * colour, chest lid interpolation, GUI pixels) is <b>not</b> covered here and stays a client-owner run check.
 */
@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
public final class FeedbackMachineGameTests {
    /** Upstream crank handle box (3/16, 0, 3/16, 13/16, 0.625, 13/16). */
    private static final double CRANK_MIN = 3.0 / 16.0;
    private static final double CRANK_MAX_Y = 10.0 / 16.0;
    /** Upstream legacy chest box (1, 0, 1, 15, 14, 15). */
    private static final double CHEST_MIN = 1.0 / 16.0;
    private static final double CHEST_MAX_Y = 14.0 / 16.0;

    /** A real server player standing on the machine, without installing a menu (the caller does that). */
    private static ServerPlayer mockPlayer(GameTestHelper helper, BlockPos relative, EmbeddedChannel[] channelOut) {
        BlockPos pos = helper.absolutePos(relative);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "machine-feedback"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        connection.setProtocol(ConnectionProtocol.PLAY);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player);
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        channelOut[0] = channel;
        return player;
    }

    private static void release(GameTestHelper helper, ServerPlayer player, Object previousMenu, EmbeddedChannel channel) {
        player.containerMenu = (net.minecraft.world.inventory.AbstractContainerMenu) previousMenu;
        player.getTextFilter().leave();
        channel.finishAndReleaseAll();
    }

    private static int countItems(GameTestHelper helper, BlockPos absoluteCentre, double radius, Item item) {
        int total = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absoluteCentre).inflate(radius))) {
            if (entity.getItem().is(item)) total += entity.getItem().getCount();
        }
        return total;
    }

    // --- #11 crank handle placement and neighbour removal ---------------------------------------------
    @GameTest(template = "test_empty")
    public static void crankHandleRequiresGeneratorAndDropsWhenGeneratorRemoved(GameTestHelper helper) {
        BlockPos ground = new BlockPos(2, 1, 2);
        BlockState crankState = CalculatorContent.block("crankhandle").defaultBlockState();
        helper.assertFalse(CalculatorContent.block("crankhandle").canSurvive(crankState, helper.getLevel(), helper.absolutePos(ground)),
                "#11: crank handle reported it could survive on bare ground");

        MachineGameTests.place(helper, ground, "handcrankedgenerator");
        BlockPos crank = ground.above();
        helper.assertTrue(CalculatorContent.block("crankhandle").canSurvive(crankState, helper.getLevel(), helper.absolutePos(crank)),
                "#11: crank handle refused a hand cranked generator");
        helper.setBlock(crank, CalculatorContent.block("crankhandle"));

        VoxelShape shape = helper.getBlockState(crank).getShape(helper.getLevel(), helper.absolutePos(crank));
        helper.assertTrue(Math.abs(shape.min(Direction.Axis.X) - CRANK_MIN) < 1.0E-4
                        && Math.abs(shape.max(Direction.Axis.Y) - CRANK_MAX_Y) < 1.0E-4
                        && Math.abs(shape.max(Direction.Axis.Z) - (1.0 - CRANK_MIN)) < 1.0E-4,
                "#11: crank handle shape is not the upstream half-height box: " + shape);

        // Removing the generator must take the crank with it and return it once.
        helper.getLevel().removeBlock(helper.absolutePos(ground), false);
        helper.assertTrue(helper.getBlockState(crank).isAir(), "#11: crank handle stayed after the generator was removed");
        helper.assertTrue(countItems(helper, helper.absolutePos(crank), 3.0, CalculatorContent.block("crankhandle").asItem()) == 1,
                "#11: removing the generator did not drop exactly one crank handle");
        helper.succeed();
    }

    // --- #6 reinforced chest shape --------------------------------------------------------------------
    @GameTest(template = "test_empty")
    public static void reinforcedChestUsesLegacyChestShapes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MachineGameTests.place(helper, pos, "reinforcedchest");
        BlockState state = helper.getBlockState(pos);
        VoxelShape shape = state.getShape(helper.getLevel(), helper.absolutePos(pos));
        VoxelShape collision = state.getCollisionShape(helper.getLevel(), helper.absolutePos(pos));
        helper.assertTrue(Math.abs(shape.min(Direction.Axis.X) - CHEST_MIN) < 1.0E-4
                        && Math.abs(shape.max(Direction.Axis.Y) - CHEST_MAX_Y) < 1.0E-4
                        && Math.abs(shape.max(Direction.Axis.Z) - (1.0 - CHEST_MIN)) < 1.0E-4,
                "#6: reinforced chest selection shape is not the legacy chest box: " + shape);
        helper.assertTrue(Math.abs(collision.max(Direction.Axis.Y) - CHEST_MAX_Y) < 1.0E-4,
                "#6: reinforced chest collision shape is not chest shaped: " + collision);
        // The lid state the renderer interpolates is carried in the synced machine state.
        MachineBlockEntity chest = (MachineBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(chest.getUpdateTag().getCompound("MachineState").contains("lidAngle"),
                "#6: synced machine state does not expose lidAngle for the lid animation");
        helper.succeed();
    }

    // --- #3 advanced power cube face gesture + synced side field ---------------------------------------
    @GameTest(template = "test_empty")
    public static void advancedCubeFaceGesturePublishesSyncedSideField(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MachineBlockEntity cube = MachineGameTests.place(helper, pos, "advancedpowercube");
        EmbeddedChannel[] channel = new EmbeddedChannel[1];
        ServerPlayer player = mockPlayer(helper, pos, channel);
        Object previous = player.containerMenu;
        player.containerMenu = new MachineMenu(1, player.getInventory(), cube);
        try {
            helper.assertTrue(cube.energySideMode(Direction.UP) == MachineBlockEntity.SIDE_INPUT, "#3: cube did not start on INPUT");
            helper.assertTrue(cube.cycleEnergySide(Direction.UP), "#3: cube rejected the world face-cycle gesture");
            helper.assertTrue(cube.energySideMode(Direction.UP) == MachineBlockEntity.SIDE_OUTPUT, "#3: gesture did not advance to OUTPUT");
            // The packet the world renderer and the GUI buttons both read.
            helper.assertTrue(cube.getUpdateTag().getCompound("MachineState").getInt("side" + Direction.UP.get3DDataValue())
                            == MachineBlockEntity.SIDE_OUTPUT,
                    "#3: cycle did not publish the per-face mode into the synced MachineState");
            MachineMenu menu = (MachineMenu) player.containerMenu;
            helper.assertTrue(menu.getEnergySideMode(Direction.UP) == MachineBlockEntity.SIDE_OUTPUT,
                    "#3: menu getter disagrees with the cube's synced face mode");
            helper.assertTrue(menu.getEnergySideMode(Direction.DOWN) == MachineBlockEntity.SIDE_INPUT,
                    "#3: cycling one face changed another");
        } finally {
            release(helper, player, previous, channel[0]);
        }
        MachineBlockEntity other = MachineGameTests.place(helper, new BlockPos(3, 1, 2), "stoneseparator");
        helper.assertFalse(other.cycleEnergySide(Direction.UP), "#3: a machine without per-face configuration accepted the face gesture");
        helper.succeed();
    }

    // --- #5/#7/#10 pause and Remove Circuits (upstream buttons.circuits) -------------------------------
    @GameTest(template = "test_empty")
    public static void pauseAndRemoveCircuitsButtonsMatchUpstream(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MachineBlockEntity furnace = MachineGameTests.place(helper, pos, "reinforcedfurnace");
        helper.assertTrue(furnace.supportsUpgrades(), "#7: reinforced furnace exposes no upgrade grid");
        EmbeddedChannel[] channel = new EmbeddedChannel[1];
        ServerPlayer player = mockPlayer(helper, pos, channel);
        Object previous = player.containerMenu;
        player.containerMenu = new MachineMenu(1, player.getInventory(), furnace);
        try {
            MachineMenu menu = (MachineMenu) player.containerMenu;
            helper.assertTrue(menu.clickMenuButton(player, 0), "#7: pause button was rejected");
            helper.assertTrue(furnace.state.getBoolean("paused"), "#7: pause button did not pause processing");
            helper.assertTrue(menu.isPaused(), "#7: menu pause getter disagrees with the machine");
            helper.assertTrue(menu.clickMenuButton(player, 0), "#7: resume button was rejected");
            helper.assertFalse(furnace.state.getBoolean("paused"), "#7: pause button did not resume processing");

            furnace.upgrades.setStackInSlot(0, CalculatorContent.stack("speedupgrade", 3));
            helper.assertTrue(menu.clickMenuButton(player, 12), "#7: Remove Circuits button was rejected");
            helper.assertTrue(furnace.upgrades.getStackInSlot(0).isEmpty(), "#7: Remove Circuits left the upgrade installed");
            helper.assertTrue(countItems(helper, helper.absolutePos(pos), 3.0, CalculatorContent.item("speedupgrade")) == 3,
                    "#7: Remove Circuits did not drop the installed upgrade circuit");
        } finally {
            release(helper, player, previous, channel[0]);
        }
        helper.succeed();
    }

    // --- #13 height multiblock dismantling -------------------------------------------------------------
    @GameTest(template = "test_empty")
    public static void breakingAnyTowerPartCleansUpWithoutDuplicationOrForeignRemoval(GameTestHelper helper) {
        BlockPos base = new BlockPos(1, 1, 1);
        MachineBlockEntity mast = MachineGameTests.place(helper, base, "conductormast");
        mast.placeAuxiliary();
        for (int y = 1; y <= 3; y++) {
            helper.assertTrue(helper.getBlockState(base.above(y)).getBlock() == CalculatorContent.block("conductormastblock"),
                    "#13: conductormast placeholder missing at +" + y);
        }
        // A player-placed block that the cleanup must never touch.
        BlockPos foreign = new BlockPos(3, 1, 1);
        helper.setBlock(foreign, Blocks.STONE);

        mast.inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));

        // Break the middle placeholder rather than the functional machine.
        helper.getLevel().removeBlock(helper.absolutePos(base.above(2)), false);

        helper.assertTrue(helper.getBlockState(base).isAir(), "#13: breaking a placeholder left the owner machine standing (orphan)");
        for (int y = 1; y <= 3; y++) {
            helper.assertTrue(helper.getBlockState(base.above(y)).isAir(), "#13: placeholder survived at +" + y);
        }
        helper.assertTrue(helper.getBlockState(foreign).getBlock() == Blocks.STONE, "#13: cleanup destroyed a neighbouring player build");
        BlockPos centre = helper.absolutePos(base);
        helper.assertTrue(countItems(helper, centre, 4.0, CalculatorContent.block("conductormast").asItem()) == 1,
                "#13: the machine item must be returned exactly once");
        helper.assertTrue(countItems(helper, centre, 4.0, Items.DIAMOND) == 5,
                "#13: stored inventory must be dropped exactly once");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void standaloneMachineWithoutStructureDropsItsOwnContentsOnce(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MachineBlockEntity furnace = MachineGameTests.place(helper, pos, "reinforcedfurnace");
        furnace.inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
        helper.getLevel().removeBlock(helper.absolutePos(pos), false);
        helper.assertTrue(countItems(helper, helper.absolutePos(pos), 3.0, Items.DIAMOND) == 7,
                "#13: standalone machine dropped the wrong number of items");
        helper.assertTrue(countItems(helper, helper.absolutePos(pos), 3.0, CalculatorContent.block("reinforcedfurnace").asItem()) == 0,
                "#13: removeBlock must not additionally spawn a machine item");
        helper.succeed();
    }

    // --- #9 docking station mounting ---------------------------------------------------------------
    @GameTest(template = "test_empty")
    public static void dockingStationWithholdsItsMenuUntilACalculatorIsMounted(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MachineBlockEntity dock = MachineGameTests.place(helper, pos, "dockingstation");
        EmbeddedChannel[] channel = new EmbeddedChannel[1];
        ServerPlayer player = mockPlayer(helper, pos, channel);
        Object previous = player.containerMenu;
        player.containerMenu = new MachineMenu(1, player.getInventory(), dock);
        try {
            MachineMenu menu = (MachineMenu) player.containerMenu;
            helper.assertTrue(dock.refusesMenu(), "#9: docking station opened its menu with no calculator mounted");
            helper.assertTrue(menu.getMountedCalculator().isEmpty(), "#9: mounted-calculator getter reported a stack when none is mounted");
            dock.inventory.setStackInSlot(MachineBlockEntity.RENDER_CALCULATOR_SLOT, CalculatorContent.stack("calculator", 1));
            helper.assertFalse(dock.refusesMenu(), "#9: docking station kept refusing the menu after a calculator was mounted");
            helper.assertTrue(ItemStack.matches(menu.getMountedCalculator(), CalculatorContent.stack("calculator", 1)),
                    "#9: mounted-calculator getter did not report the installed calculator");
            helper.assertTrue(menu.getMachineSlotCount() == dock.inventory.getSlots(),
                    "#9: machine slot count getter disagrees with the docking station inventory");
        } finally {
            release(helper, player, previous, channel[0]);
        }

        BlockPos otherPos = new BlockPos(3, 1, 2);
        MachineBlockEntity other = MachineGameTests.place(helper, otherPos, "stoneseparator");
        EmbeddedChannel[] channel2 = new EmbeddedChannel[1];
        ServerPlayer player2 = mockPlayer(helper, otherPos, channel2);
        Object previous2 = player2.containerMenu;
        player2.containerMenu = new MachineMenu(2, player2.getInventory(), other);
        try {
            helper.assertTrue(((MachineMenu) player2.containerMenu).getMountedCalculator().isEmpty(),
                    "#9: non-docking machine reported a mounted calculator");
        } finally {
            release(helper, player2, previous2, channel2[0]);
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 120)
    public static void gasLanternRealTickPreservesFuelNbtAcrossRegistryStates(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MachineBlockEntity lantern = MachineGameTests.place(helper, pos, "gaslanternoff");
        helper.assertTrue(lantern.inventory.getSlots() == 1, "Crash regression: off lantern must have one fuel slot");
        ItemStack fuel = new ItemStack(Items.COAL, 3);
        fuel.getOrCreateTag().putString("round2", "fuel-nbt");
        lantern.inventory.setStackInSlot(0, fuel);
        lantern.state.putString("custom", "preserve");
        helper.runAfterDelay(3, () -> {
            MachineBlockEntity live = (MachineBlockEntity)helper.getBlockEntity(pos);
            helper.assertTrue(helper.getBlockState(pos).is(CalculatorContent.block("gaslanternon")), "Real tick did not swap off to on registry state");
            helper.assertTrue(live == lantern && !live.isRemoved(), "Registry transition replaced or removed the persistent entity");
            helper.assertTrue(live.inventory.getSlots() == 1 && live.inventory.getStackInSlot(0).getCount() == 2,
                    "Fuel was not consumed exactly once");
            helper.assertTrue("fuel-nbt".equals(live.inventory.getStackInSlot(0).getTag().getString("round2")), "Fuel NBT lost");
            helper.assertTrue("preserve".equals(live.state.getString("custom")), "Machine NBT lost");
            helper.assertTrue(countItems(helper, helper.absolutePos(pos), 2, Items.COAL) == 0, "Transition duplicated fuel drops");
            live.state.putInt("burnTime", live.state.getInt("maxBurnTime") - 1);
            ItemStack remainingFuel = live.inventory.extractItem(0, 64, false);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(helper.getBlockState(pos).is(CalculatorContent.block("gaslanternoff")), "Real fuel expiration did not swap on to off");
                live.inventory.setStackInSlot(0, remainingFuel);
                live.setActive(true);
                live.setActive(false);
                helper.assertTrue(helper.getBlockState(pos).is(CalculatorContent.block("gaslanternoff")), "On-to-off registry swap failed");
                helper.assertTrue(helper.getBlockEntity(pos) == live && live.inventory.getSlots() == 1, "Off transition lost entity/slot");
                var saved = live.saveWithoutMetadata();
                MachineBlockEntity restored = new MachineBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
                restored.load(saved);
                helper.assertTrue(ItemStack.matches(restored.inventory.getStackInSlot(0), live.inventory.getStackInSlot(0))
                        && restored.state.equals(live.state), "Save/load did not preserve fuel and machine NBT");
                int remaining = live.inventory.getStackInSlot(0).getCount();
                helper.getLevel().removeBlock(helper.absolutePos(pos), false);
                helper.assertTrue(countItems(helper, helper.absolutePos(pos), 2, Items.COAL) == remaining, "Break must drop remaining fuel once");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "test_empty")
    public static void round2MenuUsesOriginalPlayerCoordinatesAndSeparateUpgradeSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        EmbeddedChannel[] channel = new EmbeddedChannel[1];
        ServerPlayer player = mockPlayer(helper, pos, channel);
        Object previous = player.containerMenu;
        try {
            for (String id : new String[]{"reinforcedfurnace", "stoneseparator", "algorithmseparator", "extractionchamber",
                    "precisionchamber", "restorationchamber", "reassemblychamber", "processingchamber"}) {
                MachineBlockEntity m = MachineGameTests.place(helper, pos, id);
                MachineMenu menu = new MachineMenu(1, player.getInventory(), m);
                helper.assertTrue(menu.playerInventoryY() == 84 && menu.getImageHeight() == 166, id + " wrong upstream GUI height");
                int start = menu.getPlayerSlotStart();
                helper.assertTrue(start == menu.getMachineSlotCount() + 4 && menu.slots.get(start).y == 84,
                        id + " wrong slot index boundary");
                for (int i = menu.getMachineSlotCount(); i < start; i++) helper.assertTrue(menu.slots.get(i).x < 0,
                        id + " upgrade overlaps machine/player artwork");
                helper.assertTrue(menu.slots.get(start + 27).y == 142, id + " wrong hotbar Y");
            }
            for (String id : new String[]{"basicgreenhouse", "advancedgreenhouse", "flawlessgreenhouse", "reinforcedchest"}) {
                MachineMenu menu = new MachineMenu(1, player.getInventory(), MachineGameTests.place(helper, pos, id));
                helper.assertTrue(menu.playerInventoryY() == (id.equals("reinforcedchest") ? 192 : 110), id + " special height lost");
            }
        } finally { release(helper, player, previous, channel[0]); }
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void round2OpenModelsDoNotBlockLightAndExtractorsMatchShape(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        for (String id : new String[]{"conductormast", "conductormastblock", "scarecrow", "scarecrowblock", "transmitter",
                "weatherstation", "extractionchamber", "restorationchamber", "lantern", "gaslanternoff"}) {
            MachineGameTests.place(helper, pos, id);
            BlockState state = helper.getBlockState(pos);
            helper.assertTrue(!state.canOcclude() && state.getLightBlock(helper.getLevel(), helper.absolutePos(pos)) == 0
                    && state.getOcclusionShape(helper.getLevel(), helper.absolutePos(pos)).isEmpty(), id + " blocks open model lighting");
        }
        for (String id : new String[]{"redstoneextractor", "glowstoneextractor"}) {
            MachineGameTests.place(helper, pos, id);
            VoxelShape shape = helper.getBlockState(pos).getShape(helper.getLevel(), helper.absolutePos(pos));
            helper.assertTrue(Math.abs(shape.min(Direction.Axis.X) - 1.0/16) < 1e-6
                    && Math.abs(shape.max(Direction.Axis.Y) - 15.0/16) < 1e-6, id + " shape differs from upstream bounds");
        }
        helper.succeed();
    }

    private FeedbackMachineGameTests() {}
}
