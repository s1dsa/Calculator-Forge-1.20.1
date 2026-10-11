package sonar.calculator.mod.gametest;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.machine.MachineBlock;
import sonar.calculator.mod.machine.MachineBlockEntity;
import sonar.calculator.mod.machine.MachineContent;
import sonar.calculator.mod.machine.MachineMenu;
import sonar.calculator.mod.world.CalculatorConnectedBlock;
import sonar.calculator.mod.world.WorldContent;
import sonar.core.api.blocks.IStableBlock;

@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
public final class BehaviorFixGameTests {
    @GameTest(template = "test_empty")
    public static void advancedCubeSidesCycleAndPersist(GameTestHelper helper) {
        MachineBlockEntity cube = MachineGameTests.place(helper, new BlockPos(1, 1, 1), "advancedpowercube");
        Map<Direction, IEnergyStorage> views = new EnumMap<>(Direction.class);
        for (Direction side : Direction.values()) {
            IEnergyStorage view = cube.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
            helper.assertTrue(view != null, "Missing advanced cube energy capability on " + side);
            views.put(side, view);
            assertEnergyMode(helper, cube, view, side, MachineBlockEntity.SIDE_INPUT);
        }

        // The public BE entry point requires a nearby server player with this machine's menu.
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "cube-test"));
        net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
        connection.setProtocol(net.minecraft.network.ConnectionProtocol.PLAY);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = cube.getBlockPos();
        player.moveTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        var previousMenu = player.containerMenu;
        player.containerMenu = new MachineMenu(1, player.getInventory(), cube);
        try {
            helper.assertTrue(cube.usableBy(player) && player.mayBuild()
                    && helper.getLevel().mayInteract(player, pos), "GameTest player cannot operate cube");
            for (Direction side : Direction.values()) {
                cube.handleAction(player, 30, side.get3DDataValue());
                assertEnergyMode(helper, cube, views.get(side), side, MachineBlockEntity.SIDE_OUTPUT);
                cube.handleAction(player, 30, side.get3DDataValue());
                assertEnergyMode(helper, cube, views.get(side), side, MachineBlockEntity.SIDE_DISABLED);
                cube.handleAction(player, 30, side.get3DDataValue());
                assertEnergyMode(helper, cube, views.get(side), side, MachineBlockEntity.SIDE_INPUT);
                for (Direction other : Direction.values()) {
                    assertEnergyMode(helper, cube, views.get(other), other, MachineBlockEntity.SIDE_INPUT);
                }
            }
            // Persist a mixture, so swapped direction indices cannot pass the round trip.
            cube.handleAction(player, 30, Direction.UP.get3DDataValue());
            cube.handleAction(player, 30, Direction.NORTH.get3DDataValue());
            cube.handleAction(player, 30, Direction.NORTH.get3DDataValue());
            cube.handleAction(player, 30, Direction.EAST.get3DDataValue());
            cube.energy.setEnergy(4321);
            CompoundTag saved = cube.saveWithFullMetadata();
            BlockEntity loaded = BlockEntity.loadStatic(pos, cube.getBlockState(), saved);
            helper.assertTrue(loaded instanceof MachineBlockEntity, "Registered cube NBT factory failed");
            MachineBlockEntity restored = (MachineBlockEntity) loaded;
            helper.assertTrue(restored.energy.getEnergyStored() == 4321, "Cube energy lost on NBT restore");
            for (Direction side : Direction.values()) {
                int expected = side == Direction.UP || side == Direction.EAST ? MachineBlockEntity.SIDE_OUTPUT
                        : side == Direction.NORTH ? MachineBlockEntity.SIDE_DISABLED : MachineBlockEntity.SIDE_INPUT;
                IEnergyStorage view = restored.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
                helper.assertTrue(view != null, "Restored cube capability missing on " + side);
                assertEnergyMode(helper, restored, view, side, expected);
            }
            restored.state.putInt("side" + Direction.NORTH.get3DDataValue(), MachineBlockEntity.SIDE_INPUT);
            helper.assertTrue(cube.energySideMode(Direction.NORTH) == MachineBlockEntity.SIDE_DISABLED,
                    "Restored cube aliases original side NBT");
        } finally {
            player.containerMenu = previousMenu;
            player.getTextFilter().leave();
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    private static void assertEnergyMode(GameTestHelper helper, MachineBlockEntity cube,
                                        IEnergyStorage view, Direction side, int expected) {
        String context = "advancedpowercube " + side + " mode " + expected;
        helper.assertTrue(cube.energySideMode(side) == expected, context + ": wrong side mode");
        helper.assertTrue(view.canReceive() == (expected == MachineBlockEntity.SIDE_INPUT), context + ": receive flag");
        helper.assertTrue(view.canExtract() == (expected == MachineBlockEntity.SIDE_OUTPUT), context + ": extract flag");
        cube.energy.setEnergy(1000);
        int received = view.receiveEnergy(7, false);
        int extracted = view.extractEnergy(3, false);
        helper.assertTrue(received == (expected == MachineBlockEntity.SIDE_INPUT ? 7 : 0), context + ": actual receive");
        helper.assertTrue(extracted == (expected == MachineBlockEntity.SIDE_OUTPUT ? 3 : 0), context + ": actual extract");
        helper.assertTrue(cube.energy.getEnergyStored() == 1000 + received - extracted, context + ": stored energy");
    }

    @GameTest(template = "test_empty")
    public static void connectedBlocksExposeStableMarker(GameTestHelper helper) {
        helper.assertTrue(IStableBlock.class.isAssignableFrom(CalculatorConnectedBlock.class),
                "CalculatorConnectedBlock must expose the stable multiblock marker");
        BlockPos pos = new BlockPos(1, 1, 1);
        for (String id : new String[]{"purifiedobsidian", "flawlessglass"}) {
            helper.setBlock(pos, CalculatorContent.block(id));
            var state = helper.getBlockState(pos);
            helper.assertTrue(state.getBlock() instanceof CalculatorConnectedBlock, id + ": wrong registered block family");
            helper.assertTrue(state.getBlock() instanceof IStableBlock, id + ": stable marker missing");
            helper.assertTrue(WorldContent.isStableBlock(state), id + ": world stability detection disagrees with marker");
        }
        helper.assertFalse(Blocks.OBSIDIAN instanceof IStableBlock, "Vanilla obsidian incorrectly has Calculator stable marker");
        helper.assertFalse(Blocks.GLASS instanceof IStableBlock, "Vanilla glass incorrectly has Calculator stable marker");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void renderSyncRestoresOnlyVisibleSlotsWithoutAliasing(GameTestHelper helper) {
        Map<String, Set<Integer>> visible = Map.of(
                "dockingstation", Set.of(6), "analysingchamber", Set.of(0),
                "extractionchamber", Set.of(0, 2), "precisionchamber", Set.of(0, 2),
                "reassemblychamber", Set.of(0, 2), "restorationchamber", Set.of(0, 2),
                "processingchamber", Set.of(0, 2));
        Set<String> tested = new HashSet<>();
        // Exercise every registered machine, including inventories with no render-visible slots.
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            if (!(block instanceof MachineBlock machineBlock)) continue;
            String id = machineBlock.id();
            MachineBlockEntity server = MachineGameTests.place(helper, new BlockPos(1, 1, 1), id);
            Set<Integer> expectedSlots = visible.getOrDefault(id, Set.of());
            verifyRenderSync(helper, server, expectedSlots);
            server.clearForRemoval();
            tested.add(id);
        }
        helper.assertTrue(tested.containsAll(visible.keySet()) && tested.contains("reinforcedchest"),
                "Render sync did not exercise all visible machine families and private chest inventory");
        helper.succeed();
    }

    private static void verifyRenderSync(GameTestHelper helper, MachineBlockEntity server, Set<Integer> expectedSlots) {
        String id = server.id();
        ItemStack[] originals = new ItemStack[server.inventory.getSlots()];
        for (int slot = 0; slot < originals.length; slot++) {
            ItemStack stack = new ItemStack(Items.DIAMOND, 4);
            stack.getOrCreateTag().putString("PrivateSlot", id + "/" + slot + "/private");
            server.inventory.setStackInSlot(slot, stack);
            originals[slot] = stack.copy();
        }
        server.upgrades.setStackInSlot(0, CalculatorContent.stack("speedupgrade", 3));
        server.progress = 17;
        server.processTime = 91;
        server.state.putBoolean("paused", true);
        if (server.energy.getMaxEnergyStored() > 0) server.energy.setEnergy(1234);
        CompoundTag update = server.getUpdateTag();
        helper.assertTrue(update.getBoolean("RenderSync"), id + ": missing render sync discriminator");
        helper.assertFalse(update.contains("Inventory") || update.contains("Upgrades"), id + ": private inventories leaked");
        ListTag entries = update.getList("RenderItems", Tag.TAG_COMPOUND);
        Set<Integer> actualSlots = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.getInt("Slot");
            helper.assertTrue(expectedSlots.contains(slot) && actualSlots.add(slot), id + ": unexpected or duplicate render slot " + slot);
            helper.assertTrue(ItemStack.matches(ItemStack.of(entry), originals[slot]), id + ": render stack NBT lost for " + slot);
        }
        helper.assertTrue(actualSlots.equals(expectedSlots), id + ": missing visible render slots");
        // Detect hidden stack payloads anywhere in the update, not only under the old Inventory key.
        String serialized = update.toString();
        for (int slot = 0; slot < originals.length; slot++) {
            if (!expectedSlots.contains(slot)) helper.assertFalse(serialized.contains(id + "/" + slot + "/private"),
                    id + ": private slot payload leaked " + slot);
        }

        // A registered, detached BE receives the exact client handler; no client-only classes or fake BE.
        MachineBlockEntity client = MachineContent.MACHINE.get().create(server.getBlockPos(), server.getBlockState());
        helper.assertTrue(client != null, id + ": registered render receiver factory failed");
        client.handleUpdateTag(update);
        helper.assertTrue(client.progress == 17 && client.processTime == 91 && client.state.getBoolean("paused")
                && client.energy.getEnergyStored() == server.energy.getEnergyStored(), id + ": render scalar state not restored");
        for (int slot = 0; slot < originals.length; slot++) {
            ItemStack received = client.inventory.getStackInSlot(slot);
            if (expectedSlots.contains(slot)) {
                helper.assertTrue(ItemStack.matches(received, originals[slot]), id + ": client visible stack not restored " + slot);
                received.shrink(1);
                received.getOrCreateTag().putString("PrivateSlot", "client-mutated");
                helper.assertTrue(ItemStack.matches(server.inventory.getStackInSlot(slot), originals[slot]), id + ": client aliases server stack " + slot);
            } else helper.assertTrue(received.isEmpty(), id + ": client received hidden inventory slot " + slot);
        }
        for (int slot = 0; slot < client.upgrades.getSlots(); slot++) {
            helper.assertTrue(client.upgrades.getStackInSlot(slot).isEmpty(), id + ": client received upgrades");
        }
        client.state.putBoolean("paused", false);
        helper.assertTrue(server.state.getBoolean("paused") && update.getCompound("MachineState").getBoolean("paused"),
                id + ": client state aliases server or packet NBT");
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.getInt("Slot");
            helper.assertTrue(ItemStack.matches(ItemStack.of(entry), originals[slot]), id + ": client changed packet stack");
            server.inventory.getStackInSlot(slot).shrink(1);
            server.inventory.getStackInSlot(slot).getOrCreateTag().putString("PrivateSlot", "server-mutated");
            helper.assertTrue(ItemStack.matches(ItemStack.of(entry), originals[slot]), id + ": packet aliases server stack");
            entry.getCompound("tag").putString("PrivateSlot", "packet-mutated");
            helper.assertTrue(client.inventory.getStackInSlot(slot).getTag().getString("PrivateSlot").equals("client-mutated")
                    && server.inventory.getStackInSlot(slot).getTag().getString("PrivateSlot").equals("server-mutated"),
                    id + ": packet aliases reconstructed stack NBT");
        }
        // Empty visible slots must clear previous rendered items rather than leave stale client models.
        for (int slot : expectedSlots) server.inventory.setStackInSlot(slot, ItemStack.EMPTY);
        client.handleUpdateTag(server.getUpdateTag());
        for (int slot : expectedSlots) helper.assertTrue(client.inventory.getStackInSlot(slot).isEmpty(), id + ": stale client render stack " + slot);
    }

    private BehaviorFixGameTests() {}
}
