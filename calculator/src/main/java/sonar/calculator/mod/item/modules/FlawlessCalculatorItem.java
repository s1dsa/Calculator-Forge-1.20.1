package sonar.calculator.mod.item.modules;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import sonar.calculator.mod.CalculatorConfig;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.item.ItemFactory;
import sonar.calculator.mod.item.PortableItem;
import sonar.calculator.mod.item.PortableMenu;
import sonar.calculator.mod.item.entity.CalculatorProjectile;

public final class FlawlessCalculatorItem extends PortableItem implements sonar.calculator.mod.item.StackEnergy.Delegate {
    public static final int MODULE_CAPACITY = 16;
    public static final Map<String, String> MODULE_ITEMS = Map.ofEntries(
            Map.entry("Flawless", "flawlessassembly"), Map.entry("Dynamic", "atomicassembly"),
            Map.entry("Crafting", "craftingcalculator"), Map.entry("Storage", "storagemodule"),
            Map.entry("Calculator", "calculator"), Map.entry("Scientific", "scientificcalculator"),
            Map.entry("Energy Module", "energymodule"), Map.entry("Grenade", "grenade"),
            Map.entry("Ender", "minecraft:ender_pearl"), Map.entry("Teleport", "warpmodule"), Map.entry("Jump", "jumpmodule"));
    public FlawlessCalculatorItem() { super("flawless", () -> CalculatorConfig.ENERGY_MODULE_STORAGE); }
    private CompoundTag root(ItemStack stack, boolean create) {
        CompoundTag root = create ? stack.getOrCreateTag() : stack.getTag();
        if (root == null) return new CompoundTag();
        if (!root.getBoolean("ModulesInitialized") && create) {
            boolean legacy = false;
            for (int i = 0; i < MODULE_CAPACITY; i++) legacy |= root.contains("slot" + i);
            if (!legacy) {
                String[] defaults = {"Flawless", "Dynamic", "Crafting", "Storage", "Energy Module"};
                for (int i = 0; i < defaults.length; i++) root.putString("slot" + i, defaults[i]);
            }
            root.putBoolean("ModulesInitialized", true);
        }
        return root;
    }
    public String moduleName(ItemStack stack, int slot) {
        if (slot < 0 || slot >= MODULE_CAPACITY) return "Empty";
        CompoundTag tag = root(stack, false);
        if (!tag.getBoolean("ModulesInitialized") && !tag.contains("slot0")) {
            return switch (slot) { case 0 -> "Flawless"; case 1 -> "Dynamic"; case 2 -> "Crafting"; case 3 -> "Storage"; case 4 -> "Energy Module"; default -> "Empty"; };
        }
        String name = tag.getString("slot" + slot);
        return MODULE_ITEMS.containsKey(name) ? name : "Empty";
    }
    public int currentSlot(ItemStack stack) { return Math.floorMod(root(stack, false).getInt("slot"), MODULE_CAPACITY); }
    public CompoundTag getModuleTag(ItemStack stack, int slot, boolean create) {
        CompoundTag root = root(stack, create), tag = root.getCompound(Integer.toString(slot));
        if (create) root.put(Integer.toString(slot), tag);
        return tag;
    }
    public boolean select(ServerPlayer player, ItemStack stack, int slot) {
        if (stack.getItem() != this || slot < 0 || slot >= MODULE_CAPACITY || moduleName(stack, slot).equals("Empty")
                || !(player.containerMenu instanceof ModuleMenu menu) || menu.isInfo()
                || menu.getBoundStack() != stack || !menu.stillValid(player)) return false;
        player.closeContainer(); root(stack, true).putInt("slot", slot); return true;
    }
    @Override public String mode(ItemStack stack) {
        return switch (moduleName(stack, currentSlot(stack))) {
            case "Flawless" -> "flawless"; case "Dynamic" -> "dynamic"; case "Crafting" -> "crafting";
            case "Storage" -> "storage"; case "Calculator" -> "calculator"; case "Scientific" -> "scientific";
            default -> "";
        };
    }
    @Override public CompoundTag inventoryTag(ItemStack stack, String mode, boolean create) {
        CompoundTag module = getModuleTag(stack, currentSlot(stack), create);
        CompoundTag inv = module.getCompound("PortableInventories"); CompoundTag contents = inv.getCompound(mode);
        if (create) { inv.put(mode, contents); module.put("PortableInventories", inv); }
        return contents;
    }
    @Override public String inventorySessionKey(ItemStack stack, String mode) {
        CompoundTag tag = getModuleTag(stack, currentSlot(stack), false);
        return currentSlot(stack) + ":" + moduleName(stack, currentSlot(stack)) + ":" + root(stack, false).getLong("ModuleRevision") + ":" + mode;
    }
    @Override @Nullable public CompoundTag energyTag(ItemStack stack, boolean create) {
        // PortableItem hook, never the Flawless root tag. Aggregate transfers use the capability below.
        for (int i = 0; i < MODULE_CAPACITY; i++) if (moduleName(stack, i).equals("Energy Module")) return getModuleTag(stack, i, create);
        return null;
    }
    @Override public int energyCapacity(ItemStack stack) {
        long capacity = 0;
        for (int i = 0; i < MODULE_CAPACITY; i++) if (moduleName(stack, i).equals("Energy Module")) capacity += CalculatorConfig.ENERGY_MODULE_STORAGE;
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }
    public int totalEnergy(ItemStack stack) {
        long energy = 0;
        for (int i = 0; i < MODULE_CAPACITY; i++) if (moduleName(stack, i).equals("Energy Module")) energy += Math.max(0, getModuleTag(stack, i, false).getLong("Energy"));
        return (int) Math.min(Integer.MAX_VALUE, energy);
    }
    public int transferEnergy(ItemStack stack, int amount, boolean receive, boolean simulate, boolean limited) {
        int remaining = Math.max(0, amount);
        for (int i = 0; i < MODULE_CAPACITY && remaining > 0; i++) {
            if (!moduleName(stack, i).equals("Energy Module")) continue;
            CompoundTag tag = getModuleTag(stack, i, !simulate);
            int energy = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, tag.getLong("Energy")));
            int available = receive ? Math.max(0, CalculatorConfig.ENERGY_MODULE_STORAGE - energy) : energy;
            int moved = Math.min(remaining, limited ? Math.min(available, CalculatorConfig.ENERGY_MODULE_STORAGE / 10) : available);
            if (!simulate && moved > 0) tag.putLong("Energy", (long) energy + (receive ? moved : -moved));
            remaining -= moved;
        }
        return Math.max(0, amount) - remaining;
    }
    @Override public int storedEnergy(ItemStack stack) { return totalEnergy(stack); }
    @Override public int receiveEnergy(ItemStack stack, int amount, boolean simulate) {
        return transferEnergy(stack, amount, true, simulate, false);
    }
    @Override public int extractEnergy(ItemStack stack, int amount, boolean simulate) {
        return transferEnergy(stack, amount, false, simulate, false);
    }
    @Override public boolean consumeEnergy(ItemStack stack, int amount, boolean simulate) {
        if (amount < 0 || totalEnergy(stack) < amount) return false;
        return transferEnergy(stack, amount, false, simulate, false) == amount;
    }
    @Override public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ICapabilityProvider() {
            private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> new IEnergyStorage() {
                public int receiveEnergy(int amount, boolean simulate) { return transferEnergy(stack, amount, true, simulate, true); }
                public int extractEnergy(int amount, boolean simulate) { return transferEnergy(stack, amount, false, simulate, true); }
                public int getEnergyStored() { return totalEnergy(stack); }
                public int getMaxEnergyStored() { return energyCapacity(stack); }
                public boolean canExtract() { return getMaxEnergyStored() > 0; }
                public boolean canReceive() { return getMaxEnergyStored() > 0; }
            });
            @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
                return cap == ForgeCapabilities.ENERGY ? energy.cast() : LazyOptional.empty();
            }
        };
    }
    public static String moduleFor(ItemStack stack) {
        if (stack.is(Items.ENDER_PEARL)) return "Ender";
        for (var entry : MODULE_ITEMS.entrySet()) if (!entry.getValue().contains(":") && stack.is(CalculatorContent.item(entry.getValue()))) return entry.getKey();
        return "Empty";
    }
    /** Called only by a validated server-owned workstation transaction. Consumes the actual input stack. */
    public boolean install(ItemStack calculator, int slot, ItemStack input) {
        if (calculator.getItem() != this || slot < 0 || slot >= MODULE_CAPACITY || !moduleName(calculator, slot).equals("Empty") || input.isEmpty()) return false;
        String module = moduleFor(input); if (module.equals("Empty")) return false;
        CompoundTag root = root(calculator, true), copy = input.hasTag() ? input.getTag().copy() : new CompoundTag();
        copy.remove("PortableIdentity");
        root.putString("slot" + slot, module); root.put(Integer.toString(slot), copy);
        root.putLong("ModuleRevision", root.getLong("ModuleRevision") + 1); input.shrink(1); return true;
    }
    /** Caller must reserve output space before invoking; this removes the original nested inventory. */
    public ItemStack remove(ItemStack calculator, int slot) {
        if (calculator.getItem() != this) return ItemStack.EMPTY;
        String module = moduleName(calculator, slot); if (module.equals("Empty")) return ItemStack.EMPTY;
        ItemStack result = module.equals("Ender") ? new ItemStack(Items.ENDER_PEARL) : CalculatorContent.stack(MODULE_ITEMS.get(module), 1);
        result.setTag(getModuleTag(calculator, slot, false).copy());
        CompoundTag root = root(calculator, true); root.remove(Integer.toString(slot)); root.putString("slot" + slot, "");
        root.putLong("ModuleRevision", root.getLong("ModuleRevision") + 1); return result;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) return InteractionResultHolder.success(stack);
        root(stack, true);
        if (player.isShiftKeyDown()) { ModuleMenu.open(server, hand, false); return InteractionResultHolder.success(stack); }
        String module = moduleName(stack, currentSlot(stack)), mode = mode(stack);
        if (!mode.isEmpty()) {
            if (!(mode.equals("calculator") || mode.equals("scientific") || mode.equals("crafting")) || ModuleItems.hasEnergy(stack, player, 1)) PortableMenu.open(server, hand, mode);
        } else if (module.equals("Jump") || module.equals("Teleport")) TravelItem.activate(stack, getModuleTag(stack, currentSlot(stack), true), server, module.equals("Jump"));
        else if (module.equals("Energy Module")) player.displayClientMessage(Component.translatable("calc.energyModule", getModuleTag(stack, currentSlot(stack), false).getLong("Energy")), true);
        else if (module.equals("Grenade") || module.equals("Ender")) {
            int cost = module.equals("Grenade") ? CalculatorConfig.GRENADE_MODULE_USAGE : CalculatorConfig.ENDER_PEARL_MODULE_USAGE;
            if (module.equals("Grenade") && !CalculatorConfig.enableGrenades) player.displayClientMessage(Component.translatable("calc.ban"), true);
            else if (ModuleItems.hasEnergy(stack, player, cost)) {
                if (module.equals("Grenade")) {
                    CalculatorProjectile projectile = new CalculatorProjectile(ItemFactory.GRENADE.get(), player, level);
                    projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.5F, 1F); level.addFreshEntity(projectile);
                } else {
                    // EntityEnderPearl(world, player) never called shoot in the upstream module.
                    level.addFreshEntity(new ThrownEnderpearl(level, player));
                }
                level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.TNT_PRIMED, net.minecraft.sounds.SoundSource.NEUTRAL, .5F, .8F);
                ModuleItems.consumeEnergy(stack, player, cost);
            }
        }
        return InteractionResultHolder.success(stack);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (moduleName(stack, currentSlot(stack)).equals("Teleport") && TravelItem.bind(context, getModuleTag(stack, currentSlot(stack), !context.getLevel().isClientSide))) return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        return InteractionResult.PASS;
    }
    @Override public boolean isFoil(ItemStack stack) { return true; }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("calc.currentModule", moduleName(stack, currentSlot(stack))));
        lines.add(Component.translatable("energy.stored").append(": " + totalEnergy(stack) + " RF"));
    }
}
