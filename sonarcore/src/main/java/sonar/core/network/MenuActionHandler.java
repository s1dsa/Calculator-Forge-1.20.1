package sonar.core.network;

import net.minecraft.server.level.ServerPlayer;

/** Implement on a menu; validate action IDs, value ranges and any cost in the handler. */
public interface MenuActionHandler {
    void handleMenuAction(ServerPlayer player, int action, int value);
}
