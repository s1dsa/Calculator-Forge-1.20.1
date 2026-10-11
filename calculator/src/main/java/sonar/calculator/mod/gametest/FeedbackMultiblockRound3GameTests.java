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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.machine.MachineBlock;
import sonar.calculator.mod.machine.MachineBlockEntity;
import sonar.calculator.mod.machine.MachineMenu;

/** Real player transactions, with a fresh structure and connection for each clicked height/facing. */
@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
public final class FeedbackMultiblockRound3GameTests {
    private static int height(String id) { return switch (id) { case "conductormast" -> 3; case "scarecrow" -> 2; default -> 1; }; }
    private static void scenarios(GameTestHelper h, String id, GameType mode, boolean use) { scenarios(h, id, mode, use, false); }
    private static void scenarios(GameTestHelper h, String id, GameType mode, boolean use, boolean wrench) {
        for (Direction facing : Direction.Plane.HORIZONTAL) for (int y = 0; y <= height(id); y++) {
            BlockPos local = new BlockPos(2, 1, 2);
            MachineBlockEntity main = MachineGameTests.place(h, local, id);
            BlockPos base = main.getBlockPos();
            h.getLevel().setBlock(base, main.getBlockState().setValue(MachineBlock.FACING, facing), 3);
            main.placeAuxiliary();
            BlockPos clicked = base.above(y);
            h.assertTrue(h.getLevel().getBlockState(clicked).getBlock() == CalculatorContent.block(y == 0 ? id : id + "block"), id + " missing part " + y);
            boolean inventory = main.inventory.getSlots() > 0;
            if (inventory) main.inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
            main.upgrades.setStackInSlot(0, CalculatorContent.stack("speedupgrade", 3));
            h.assertTrue(main.getRenderBoundingBox().contains(Vec3.atCenterOf(base.above(height(id)))), "render bounds omit top visible part");
            main.state.putInt("Round3Marker", 731);
            ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "round3-test"));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection);
            try {
                connection.setProtocol(ConnectionProtocol.PLAY);
                new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player);
                player.setGameMode(mode);
                player.setPos(clicked.getX() + 2, clicked.getY() + .5, clicked.getZ() + .5);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                h.assertTrue(connection.isConnected() && player.mayBuild() && h.getLevel().mayInteract(player, clicked), "fixture must permit interaction");
                if (wrench) {
                    h.assertTrue(((MachineBlock)h.getLevel().getBlockState(clicked).getBlock()).onWrenched(h.getLevel(), clicked, player, Direction.EAST, true), "part wrench rejected");
                    for (int part = 0; part <= height(id); part++) h.assertTrue(h.getLevel().getBlockState(base.above(part)).isAir(), "wrench left a part");
                    ItemStack packed = ItemStack.EMPTY;
                    int returned = 0;
                    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                        ItemStack item = player.getInventory().getItem(slot);
                        if (item.is(CalculatorContent.item(id))) { returned += item.getCount(); packed = item; }
                    }
                    h.assertTrue(returned == 1 && packed.hasTag(), "wrench must return one packed controller");
                    MachineBlockEntity restored = new MachineBlockEntity(base, CalculatorContent.block(id).defaultBlockState());
                    restored.restoreFromItem(packed);
                    h.assertTrue(restored.getStateValue("Round3Marker") == 731 && restored.upgrades.getStackInSlot(0).getCount() == 3, "wrench lost state/upgrades");
                    if (inventory) h.assertTrue(restored.inventory.getStackInSlot(0).getCount() == 5, "wrench lost inventory");
                    h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(5)).isEmpty(), "wrench also emitted loose drops");
                } else if (use) {
                    var result = player.gameMode.useItemOn(player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(clicked), Direction.EAST, clicked, false));
                    h.assertTrue(result.consumesAction(), "part right click rejected " + id + "/" + y);
                    h.assertTrue(player.containerMenu instanceof MachineMenu menu && menu.machine == main && menu.stillValid(player), "part opened wrong controller " + id + "/" + y);
                    MachineBlockEntity queried = ((MachineBlockEntity)h.getLevel().getBlockEntity(clicked)).controller();
                    h.assertTrue(queried == main, "part controller query differs");
                    if (main.getEnergyCapacity() > 0) h.assertTrue(((MachineBlockEntity)h.getLevel().getBlockEntity(clicked)).getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).orElse(null)
                            == main.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).orElse(null), "part energy capability differs");
                    h.assertTrue(((MachineMenu)player.containerMenu).machine.getStateValue("Round3Marker") == 731, "controller state differs");
                    player.closeContainer();
                    player.containerMenu = player.inventoryMenu;
                    player.setPos(base.getX() + 40, base.getY(), base.getZ());
                    var denied = player.gameMode.useItemOn(player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(clicked), Direction.EAST, clicked, false));
                    h.assertTrue(!(player.containerMenu instanceof MachineMenu), "remote part opened menu");
                    main.clearForRemoval();
                    h.getLevel().removeBlock(base, false);
                } else {
                    h.assertTrue(player.gameMode.destroyBlock(clicked), "effective destroy rejected " + id + "/" + y);
                    for (int part = 0; part <= height(id); part++) h.assertTrue(h.getLevel().getBlockState(base.above(part)).isAir(), "one destroy left " + id + "/" + part + " when clicking " + y);
                    var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(5));
                    int machines = drops.stream().filter(e -> e.getItem().is(CalculatorContent.item(id))).mapToInt(e -> e.getItem().getCount()).sum();
                    int diamonds = drops.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
                    h.assertTrue(machines == (mode == GameType.CREATIVE ? 0 : 1), "machine conservation " + id + "/" + y + " got " + machines);
                    h.assertTrue(diamonds == (inventory ? 5 : 0), "inventory conservation got " + diamonds);
                    h.assertTrue(drops.stream().filter(e -> e.getItem().is(CalculatorContent.item("speedupgrade"))).mapToInt(e -> e.getItem().getCount()).sum() == 3, "upgrade conservation");
                }
                channel.runPendingTasks(); channel.checkException();
            } finally {
                player.containerMenu = player.inventoryMenu;
                player.getInventory().clearContent();
                player.getTextFilter().leave();
                channel.finishAndReleaseAll();
                h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(5)).forEach(ItemEntity::discard);
            }
        }
        h.succeed();
    }
    @GameTest(template="test_empty") public static void environmentRemovalAndForeignOwnership(GameTestHelper h) {
        for (String id : new String[]{"conductormast", "weatherstation", "transmitter", "scarecrow"}) {
            MachineBlockEntity main = MachineGameTests.place(h, new BlockPos(2,1,2), id);
            main.placeAuxiliary();
            BlockPos base = main.getBlockPos();
            main.upgrades.setStackInSlot(0, CalculatorContent.stack("speedupgrade",3));
            // Explosion-style loot then removal, and a foreign block replacing a structure member.
            h.getLevel().destroyBlock(base.above(1), true);
            for (int y=0;y<=height(id);y++) h.assertTrue(h.getLevel().getBlockState(base.above(y)).isAir(), "environment removal left part");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(base).inflate(5));
            h.assertTrue(drops.stream().filter(e->e.getItem().is(CalculatorContent.item(id))).mapToInt(e->e.getItem().getCount()).sum()==1,"environment machine conservation");
            h.assertTrue(drops.stream().filter(e->e.getItem().is(CalculatorContent.item("speedupgrade"))).mapToInt(e->e.getItem().getCount()).sum()==3,"environment upgrades conservation");
            drops.forEach(ItemEntity::discard);
            main=MachineGameTests.place(h,new BlockPos(2,1,2),id); main.placeAuxiliary();
            h.getLevel().setBlock(base.above(1),Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(h.getLevel().getBlockState(base).isAir() && h.getLevel().getBlockState(base.above(1)).is(Blocks.STONE),"replacement must preserve foreign block");
            h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(base).inflate(5)).forEach(ItemEntity::discard);
            h.getLevel().removeBlock(base.above(1),false);
        }
        h.succeed();
    }
    @GameTest(template="test_empty") public static void orphanPartCannotBindThroughForeignGap(GameTestHelper h) {
        MachineBlockEntity main=MachineGameTests.place(h,new BlockPos(2,1,2),"conductormast");
        BlockPos base=main.getBlockPos();
        h.getLevel().setBlock(base.above(1),Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(base.above(2),CalculatorContent.block("conductormastblock").defaultBlockState(),3);
        MachineBlockEntity orphan=(MachineBlockEntity)h.getLevel().getBlockEntity(base.above(2));
        h.assertTrue(orphan.controller()==null,"orphan resolved controller through foreign gap");
        h.getLevel().removeBlock(base.above(2),false);
        h.assertTrue(h.getLevel().getBlockEntity(base)==main && h.getLevel().getBlockState(base.above(1)).is(Blocks.STONE),"orphan removal touched unrelated controller");
        h.succeed();
    }
    @GameTest(template="test_empty") public static void mastWrench(GameTestHelper h) { scenarios(h,"conductormast",GameType.SURVIVAL,false,true); }
    @GameTest(template="test_empty") public static void weatherWrench(GameTestHelper h) { scenarios(h,"weatherstation",GameType.SURVIVAL,false,true); }
    @GameTest(template="test_empty") public static void transmitterWrench(GameTestHelper h) { scenarios(h,"transmitter",GameType.SURVIVAL,false,true); }
    @GameTest(template="test_empty") public static void scarecrowWrench(GameTestHelper h) { scenarios(h,"scarecrow",GameType.SURVIVAL,false,true); }
    @GameTest(template="test_empty") public static void mastSurvival(GameTestHelper h) { scenarios(h,"conductormast",GameType.SURVIVAL,false); }
    @GameTest(template="test_empty") public static void mastCreative(GameTestHelper h) { scenarios(h,"conductormast",GameType.CREATIVE,false); }
    @GameTest(template="test_empty") public static void mastUse(GameTestHelper h) { scenarios(h,"conductormast",GameType.SURVIVAL,true); }
    @GameTest(template="test_empty") public static void weatherSurvival(GameTestHelper h) { scenarios(h,"weatherstation",GameType.SURVIVAL,false); }
    @GameTest(template="test_empty") public static void weatherCreative(GameTestHelper h) { scenarios(h,"weatherstation",GameType.CREATIVE,false); }
    @GameTest(template="test_empty") public static void weatherUse(GameTestHelper h) { scenarios(h,"weatherstation",GameType.SURVIVAL,true); }
    @GameTest(template="test_empty") public static void transmitterSurvival(GameTestHelper h) { scenarios(h,"transmitter",GameType.SURVIVAL,false); }
    @GameTest(template="test_empty") public static void transmitterCreative(GameTestHelper h) { scenarios(h,"transmitter",GameType.CREATIVE,false); }
    @GameTest(template="test_empty") public static void transmitterUse(GameTestHelper h) { scenarios(h,"transmitter",GameType.SURVIVAL,true); }
    @GameTest(template="test_empty") public static void scarecrowSurvival(GameTestHelper h) { scenarios(h,"scarecrow",GameType.SURVIVAL,false); }
    @GameTest(template="test_empty") public static void scarecrowCreative(GameTestHelper h) { scenarios(h,"scarecrow",GameType.CREATIVE,false); }
    @GameTest(template="test_empty") public static void scarecrowUse(GameTestHelper h) { scenarios(h,"scarecrow",GameType.SURVIVAL,true); }
}
