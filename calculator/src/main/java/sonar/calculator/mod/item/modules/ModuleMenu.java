package sonar.calculator.mod.item.modules;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/** A read-only held-item session. No client-provided module names or item stacks are accepted. */
public final class ModuleMenu extends AbstractContainerMenu {
    private final Player owner;
    private final InteractionHand hand;
    private final int heldSlot;
    private final UUID identity;
    private final ItemStack boundStack;
    private final boolean info;
    private final long revision;

    public static void open(ServerPlayer player, InteractionHand hand, boolean info) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getCount() != 1 || !(info ? stack.getItem() instanceof ModuleItems.InfoItem : stack.getItem() instanceof FlawlessCalculatorItem)) return;
        if (!stack.getOrCreateTag().hasUUID("PortableIdentity")) stack.getOrCreateTag().putUUID("PortableIdentity", UUID.randomUUID());
        UUID identity = stack.getTag().getUUID("PortableIdentity");
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new ModuleMenu(id, inv, hand, slot, identity, info), stack.getHoverName()),
                buf -> { buf.writeEnum(hand); buf.writeVarInt(slot); buf.writeUUID(identity); buf.writeBoolean(info); });
    }
    public ModuleMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readEnum(InteractionHand.class), buf.readVarInt(), buf.readUUID(), buf.readBoolean());
    }
    public ModuleMenu(int id, Inventory inventory, InteractionHand hand, int slot, UUID identity, boolean info) {
        super(ModuleItems.MODULE_MENU.get(), id);
        owner = inventory.player;
        this.hand = hand;
        heldSlot = slot;
        this.identity = identity;
        this.info = info;
        if (slot < 0 || slot >= inventory.getContainerSize()) throw new IllegalArgumentException("Invalid held slot");
        boundStack = inventory.getItem(slot);
        if (!(info ? boundStack.getItem() instanceof ModuleItems.InfoItem : boundStack.getItem() instanceof FlawlessCalculatorItem)) throw new IllegalArgumentException("Invalid module menu item");
        revision = boundStack.hasTag() ? boundStack.getTag().getLong("ModuleRevision") : 0;
    }
    public boolean isInfo() { return info; }
    public ItemStack getBoundStack() { return boundStack; }
    public String moduleName(int slot) { return ModuleItems.moduleName(boundStack, slot); }
    public int currentSlot() { return boundStack.getItem() instanceof FlawlessCalculatorItem item ? item.currentSlot(boundStack) : -1; }
    @Override public boolean stillValid(Player player) {
        if (player != owner || !player.isAlive()) return false;
        if (owner.level().isClientSide) return true;
        ItemStack held = player.getInventory().getItem(heldSlot);
        return held == boundStack && player.getItemInHand(hand) == boundStack && held.getCount() == 1
                && held.hasTag() && held.getTag().hasUUID("PortableIdentity") && identity.equals(held.getTag().getUUID("PortableIdentity"))
                && held.getTag().getLong("ModuleRevision") == revision
                && (info ? held.getItem() instanceof ModuleItems.InfoItem : held.getItem() instanceof FlawlessCalculatorItem);
    }
    @Override public boolean clickMenuButton(Player player, int slot) {
        if (info || !(player instanceof ServerPlayer server) || server.containerMenu != this || !stillValid(player)
                || slot < 0 || slot >= FlawlessCalculatorItem.MODULE_CAPACITY || moduleName(slot).equals("Empty")) return false;
        boolean selected = ((FlawlessCalculatorItem) boundStack.getItem()).select(server, boundStack, slot);
        if (selected) player.getInventory().setChanged();
        return selected;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) { /* This menu has no inventory slots. */ }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
