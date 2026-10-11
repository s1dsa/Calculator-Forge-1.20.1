package sonar.core.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import sonar.core.SonarCore;
import java.util.function.Supplier;

/** Server-authoritative menu actions. Packets cannot choose an inventory, arbitrary NBT, or world position. */
public final class SonarNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SonarCore.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    private SonarNetwork() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(MenuAction.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MenuAction::encode).decoder(MenuAction::decode)
                .consumerMainThread(MenuAction::handle).add();
    }

    public static void open(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        NetworkHooks.openScreen(player, provider, pos);
    }

    public static void sendMenuAction(int containerId, int action, int value) {
        CHANNEL.sendToServer(new MenuAction(containerId, action, value));
    }

    private record MenuAction(int containerId, int action, int value) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(containerId);
            buffer.writeVarInt(action);
            buffer.writeInt(value);
        }
        static MenuAction decode(FriendlyByteBuf buffer) {
            return new MenuAction(buffer.readVarInt(), buffer.readVarInt(), buffer.readInt());
        }
        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player == null || !player.isAlive() || player.isSpectator()) return;
            if (player.containerMenu.containerId != containerId || !player.containerMenu.stillValid(player)) return;
            if (player.containerMenu instanceof MenuActionHandler handler) {
                handler.handleMenuAction(player, action, value);
                player.containerMenu.broadcastChanges();
            }
        }
    }
}
