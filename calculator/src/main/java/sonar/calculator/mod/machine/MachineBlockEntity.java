package sonar.calculator.mod.machine;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.machine.environment.GreenhouseLogic;
import sonar.core.energy.DischargeValues;
import sonar.core.energy.StoredEnergy;
import sonar.core.inventory.PersistentInventory;

/** Shared persistent state and capability boundary for all enabled Calculator machines. */
public class MachineBlockEntity extends BlockEntity implements MenuProvider {
    private final String id;
    final MachineSpec spec;
    public final PersistentInventory inventory;
    public final StoredEnergy energy;
    public final PersistentInventory upgrades;
    public CompoundTag state = new CompoundTag();
    public int progress;
    public int processTime;
    private int ticks;
    private boolean suppressDrops;
    private boolean clientSyncDirty;
    private CompoundTag lastTickState = new CompoundTag();
    private int lastProgress, lastProcessTime;
    private float previousLidAngle, clientLidAngle;
    private boolean lidInitialized;
    private final java.util.Set<MachineMenu> chestMenus = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private long syncCount;
    public long syncCount() { return syncCount; }
    void registerChestMenu(MachineMenu menu) { if (id.equals("reinforcedchest") && level != null && !level.isClientSide) chestMenus.add(menu); }
    void removeChestMenu(MachineMenu menu) { chestMenus.remove(menu); }
    public int chestOpenerCount() {
        chestMenus.removeIf(menu -> !menu.isChestOpener());
        return chestMenus.size();
    }
    /** Advance previous/current exactly once per client world tick, never from render or packet callbacks. */
    public static void clientTick(Level level, BlockPos pos, BlockState blockState, MachineBlockEntity machine) {
        if (!machine.id.equals("reinforcedchest") || machine.isRemoved()) return;
        float target = net.minecraft.util.Mth.clamp(machine.state.getInt("lidAngle"), 0, 1000) / 1000F;
        if (!machine.lidInitialized) {
            machine.previousLidAngle = machine.clientLidAngle = target;
            machine.lidInitialized = true;
        } else {
            machine.previousLidAngle = machine.clientLidAngle;
            machine.clientLidAngle = target;
        }
    }
    public float getLidAngle(float partialTick) {
        if (!lidInitialized) return net.minecraft.util.Mth.clamp(state.getInt("lidAngle"), 0, 1000) / 1000F;
        return net.minecraft.util.Mth.lerp(net.minecraft.util.Mth.clamp(partialTick, 0F, 1F), previousLidAngle, clientLidAngle);
    }
    @Override public void setChanged() { super.setChanged(); clientSyncDirty = true; }
    private final List<LazyOptional<?>> capabilities = new ArrayList<>();
    private LazyOptional<IItemHandler> itemCapability;
    private final java.util.EnumMap<Direction, LazyOptional<IItemHandler>> sidedItems = new java.util.EnumMap<>(Direction.class);
    private final java.util.EnumMap<Direction, LazyOptional<IEnergyStorage>> sidedEnergy = new java.util.EnumMap<>(Direction.class);
    private LazyOptional<IEnergyStorage> energyCapability;

    public MachineBlockEntity(BlockPos pos, BlockState blockState) {
        super(MachineContent.MACHINE.get(), pos, blockState);
        id = blockState.getBlock() instanceof MachineBlock machine ? machine.id() : "";
        spec = MachineSpec.of(id);
        inventory = new MachineInventory(this, spec.slots());
        energy = new StoredEnergy(spec.configuredCapacity(), spec.configuredTransfer(), this::setChanged);
        upgrades = new PersistentInventory(4, this::setChanged) {
            @Override public int getSlotLimit(int slot) { return slot < 2 ? 16 : 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                String name = itemId(stack);
                return supportsUpgrades() && switch (slot) {
                    case 0 -> name.equals("speedupgrade"); case 1 -> name.equals("energyupgrade");
                    case 2 -> name.equals("transferupgrade"); case 3 -> name.equals("voidupgrade") && (id.equals("extractionchamber") || id.equals("precisionchamber") || id.equals("analysingchamber")); default -> false;
                };
            }
        };
        processTime = Math.max(1, spec.configured("SPEED", spec.duration()));
        rebuildCapabilities();
    }
    public String id() { return MachineBlock.isGasLantern(getBlockState()) ? ((MachineBlock)getBlockState().getBlock()).id() : id; }
    /** Validated, loaded, local controller; orphan placeholders expose no controller. */
    @Nullable public MachineBlockEntity controller() { return MachineEnvironment.controller(this); }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        int height = MachineEnvironment.auxiliaryHeight(id);
        if (height == 0) return super.getRenderBoundingBox();
        // Covers the complete Techne geometry including the rotating weather dish and scarecrow shoulders.
        return new net.minecraft.world.phys.AABB(worldPosition.getX() - 2, worldPosition.getY() - 1, worldPosition.getZ() - 2,
                worldPosition.getX() + 3, worldPosition.getY() + height + 2, worldPosition.getZ() + 3);
    }
    public String machineId() { return id; }
    public String getMachineId() { return id; }
    public PersistentInventory getInventory() { return inventory; }
    public int getProgress() { return progress; }
    public int getProcessTime() { return processTime; }
    public int getEnergyStored() { return energy.getEnergyStored(); }
    public int getEnergyCapacity() { return energy.getMaxEnergyStored(); }
    public int getStateValue(String name) { return state.getInt(name); }
    public boolean isActive() { return getBlockState().getValue(MachineBlock.ACTIVE); }
    public float getRenderPosition() { float t = progress / (float)Math.max(1, processTime); return t < .5F ? t * 2 : (1 - t) * 2; }
    public Direction facing() { return getBlockState().getValue(MachineBlock.FACING); }
    public int tickCount() { return ticks; }
    public int upgradeCount(int slot) { return upgrades.getStackInSlot(slot).getCount(); }
    public boolean supportsUpgrades() { return spec.battery() == spec.inputs() && !spec.recipe().isEmpty() || id.equals("analysingchamber"); }
    public static String itemId(ItemStack stack) { var key = ForgeRegistries.ITEMS.getKey(stack.getItem()); return key == null ? "" : key.getNamespace().equals("calculator") ? key.getPath() : key.toString(); }
    public static String blockId(BlockState block) { var key = ForgeRegistries.BLOCKS.getKey(block.getBlock()); return key == null ? "" : key.getNamespace().equals("calculator") ? key.getPath() : key.toString(); }
    public static boolean circuit(ItemStack stack) { String name = itemId(stack); return name.equals("circuitboard") || name.startsWith("circuitboard_"); }
    public static int circuitIndex(ItemStack stack) { String name = itemId(stack); int at = name.lastIndexOf('_'); if (at < 0) return 0; try { return Integer.parseInt(name.substring(at + 1)); } catch (NumberFormatException e) { return -1; } }
    public static int circuitType(ItemStack stack) {
        String name = itemId(stack);
        if (name.startsWith("circuitdirty")) return 3;
        if (name.startsWith("circuitdamaged")) return 2;
        if (!circuit(stack) || stack.hasTag() && !stack.getTag().getBoolean("Analysed")) return -1;
        return stack.hasTag() && stack.getTag().getInt("Stable") == 1 ? 1 : 0;
    }
    public boolean canInsert(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (id.equals("moduleworkstation")) return slot == 1 && stack.getItem() instanceof sonar.calculator.mod.item.modules.FlawlessCalculatorItem;
        if (id.equals("storagechamber")) {
            int type = circuitType(stack); if (type < 0 || circuitIndex(stack) != slot) return false;
            for (int i = 0; i < inventory.getSlots(); i++) if (!inventory.getStackInSlot(i).isEmpty() && circuitType(inventory.getStackInSlot(i)) != type) return false;
            return true;
        }
        if (id.equals("calculatorplug")) return circuit(stack) || itemId(stack).equals("flawlessdiamond");
        if (id.equals("gaslanternon") || id.equals("gaslanternoff")) return slot == 0 && net.minecraftforge.common.ForgeHooks.getBurnTime(stack, net.minecraft.world.item.crafting.RecipeType.SMELTING) > 0;
        if (id.equals("fabricationchamber")) return false;
        if (slot == spec.battery()) return stack.getCapability(ForgeCapabilities.ENERGY).isPresent() || DischargeValues.getValue(stack) > 0;
        if (id.equals("analysingchamber")) return slot == 0 && circuit(stack) && (!stack.hasTag() || !stack.getTag().getBoolean("Analysed"));
        if (id.equals("atomicmultiplier")) return slot == 0 ? MachineProcessing.canMultiply(stack) : slot >= 1 && slot <= 7 && circuit(stack);
        if (id.equals("dockingstation")) return dockingCanInsert(slot, stack);
        if (id.endsWith("extractor")) return slot == 0 ? net.minecraftforge.common.ForgeHooks.getBurnTime(stack, net.minecraft.world.item.crafting.RecipeType.SMELTING) > 0 : MachineProcessing.value(this, spec.recipe(), stack) > 0;
        if (id.endsWith("greenhouse")) return GreenhouseLogic.canInsert(this, slot, stack);
        if (id.equals("calculatorlocator")) return slot == 1 && itemId(stack).equals("locatormodule") || slot == 0 && stack.getCapability(ForgeCapabilities.ENERGY).isPresent();
        if (id.endsWith("powercube")) return stack.getCapability(ForgeCapabilities.ENERGY).isPresent() || slot == 1 && DischargeValues.getValue(stack) > 0;
        if (id.equals("co2generator")) return slot == 0 && net.minecraftforge.common.ForgeHooks.getBurnTime(stack, net.minecraft.world.item.crafting.RecipeType.SMELTING) > 0;
        if (id.equals("hungerprocessor") || id.equals("healthprocessor")) return MachineEnvironment.nutritionValid(this, slot, stack);
        if (spec.outputs() > 0 && slot >= spec.outputStart() && slot < spec.outputStart() + spec.outputs()) return false;
        return true;
    }
    public boolean isOutput(int slot) {
        if (id.equals("fabricationchamber")) return slot == 0;
        if (id.equals("dynamiccalculator")) return slot == 2 || slot == 5 || slot == 9;
        return spec.outputs() > 0 && slot >= spec.outputStart() && slot < spec.outputStart() + spec.outputs();
    }
    private boolean externalInsert(int slot, Direction side, ItemStack stack) {
        if (slot == spec.battery() || id.equals("magneticflux") || id.equals("algorithmassimilator")) return false;
        if (id.equals("advancedgreenhouse")) return side == Direction.UP ? slot < 7 && canInsert(slot, stack) : slot >= 8 && canInsert(slot, stack);
        if (id.equals("atomicmultiplier")) return side == Direction.UP ? slot == 0 : side != Direction.DOWN && slot >= 1 && slot <= 7;
        if (id.equals("dockingstation")) {
            if (side.getAxis().isVertical()) return false;
            int mapped = side == facing().getClockWise() ? 0 : side == facing().getCounterClockWise() ? 1 : side == facing().getOpposite() ? 2 : 3;
            return slot == mapped && slot < MachineProcessing.dockingInputs(this);
        }
        if (id.endsWith("extractor")) return side == Direction.UP ? slot == 1 : slot == 0;
        return !isOutput(slot) && canInsert(slot, stack);
    }
    private boolean externalExtract(int slot, Direction side) {
        if (id.equals("storagechamber") || id.equals("reinforcedchest") || id.equals("algorithmassimilator")) return true;
        if (id.endsWith("greenhouse") || id.equals("co2generator") || id.equals("magneticflux")) return false;
        if (id.equals("analysingchamber") && slot == 0) return circuitType(inventory.getStackInSlot(0)) >= 0;
        if (id.endsWith("extractor")) return side == Direction.DOWN && inventory.getStackInSlot(slot).is(net.minecraft.world.item.Items.BUCKET);
        if (id.equals("healthprocessor") || id.equals("hungerprocessor")) return slot == 0 || state.getInt("points") == 0;
        return isOutput(slot);
    }
    public static final int SIDE_INPUT = 0, SIDE_OUTPUT = 1, SIDE_DISABLED = 2;
    /** World directions use the vanilla DOWN, UP, NORTH, SOUTH, WEST, EAST order. */
    public int energySideMode(Direction side) {
        String key = "side" + side.get3DDataValue();
        int mode = state.contains(key, 3) ? state.getInt(key) : SIDE_INPUT;
        return mode >= SIDE_INPUT && mode <= SIDE_DISABLED ? mode : SIDE_INPUT;
    }
    /**
     * Server-authoritative face cycle shared by the world right-click gesture (#3) and the GUI value
     * action (button 300..305). Returning false means the machine has no per-face configuration, so the
     * caller must fall through to its other interaction handling.
     */
    public boolean cycleEnergySide(@Nullable Direction side) {
        if (!id.equals("advancedpowercube") || side == null) return false;
        String key = "side" + side.get3DDataValue();
        state.putInt(key, (energySideMode(side) + 1) % 3);
        setChanged(); sync();
        return true;
    }
    /**
     * A docking station only exposes its menu once a calculator is mounted (upstream
     * {@code DockingStation.onBlockActivated} / {@code docking.noCalculator}). Every other machine opens.
     */
    public boolean refusesMenu() { return id.equals("dockingstation") && inventory.getStackInSlot(RENDER_CALCULATOR_SLOT).isEmpty(); }
    public void reportMenuRefusal(Player player) {
        if (player instanceof ServerPlayer server) server.displayClientMessage(Component.translatable("docking.noCalculator"), true);
    }
    /** The docking station keeps the installed calculator in this persistent slot (upstream {@code calcStack}). */
    public static final int RENDER_CALCULATOR_SLOT = 6;
    private boolean dockingCanInsert(int slot, ItemStack stack) {
        if (slot == RENDER_CALCULATOR_SLOT) return List.of("calculator", "scientificcalculator", "atomiccalculator", "flawlesscalculator").contains(itemId(stack));
        if (slot == 5) return false;
        if (slot == spec.battery()) return stack.getCapability(ForgeCapabilities.ENERGY).isPresent() || DischargeValues.getValue(stack) > 0;
        return slot >= 0 && slot < MachineProcessing.dockingInputs(this);
    }
    private void normalizeEnergySides() {
        if (id.equals("advancedpowercube")) for (Direction side : Direction.values()) state.putInt("side" + side.get3DDataValue(), energySideMode(side));
    }
    /**
     * #6: the values the block entity renderer interpolates have to exist in the very first sync, before
     * the machine has ticked once - the update tag is what the client (and the GameTest) reads.
     */
    private void normalizeRenderState() {
        if (id.equals("reinforcedchest") && !state.contains("lidAngle", 3)) state.putInt("lidAngle", 0);
    }
    private boolean canReceiveEnergy(Direction side) {
        return energy.getMaxEnergyStored() > 0 && (id.equals("advancedpowercube") ? side == null || energySideMode(side) == SIDE_INPUT : !spec.generator());
    }
    private boolean canExtractEnergy(Direction side) {
        return id.equals("advancedpowercube") ? side == null || energySideMode(side) == SIDE_OUTPUT
                : spec.generator() && (side == null || !bottomEnergyOnly() || side == Direction.DOWN);
    }
    private IEnergyStorage energyView(@Nullable Direction side) {
        return new IEnergyStorage() {
            public int receiveEnergy(int amount, boolean simulate) { return canReceive() ? energy.receiveEnergy(amount, simulate) : 0; }
            public int extractEnergy(int amount, boolean simulate) { return canExtract() ? energy.extractEnergy(amount, simulate) : 0; }
            public int getEnergyStored() { return energy.getEnergyStored(); }
            public int getMaxEnergyStored() { return energy.getMaxEnergyStored(); }
            public boolean canExtract() { return canExtractEnergy(side); }
            public boolean canReceive() { return canReceiveEnergy(side); }
        };
    }
    private void rebuildCapabilities() {
        normalizeEnergySides();
        normalizeRenderState();
        itemCapability = LazyOptional.of(() -> inventory); energyCapability = LazyOptional.of(() -> id.equals("advancedpowercube") ? energyView(null) : energy);
        capabilities.add(itemCapability); capabilities.add(energyCapability);
        for (Direction side : Direction.values()) {
            LazyOptional<IItemHandler> items = LazyOptional.of(() -> new IItemHandler() {
                public int getSlots() { return inventory.getSlots(); }
                public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
                public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
                public boolean isItemValid(int slot, ItemStack stack) { return externalInsert(slot, side, stack); }
                public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return externalInsert(slot, side, stack) ? inventory.insertItem(slot, stack, simulate) : stack; }
                public ItemStack extractItem(int slot, int count, boolean simulate) { return externalExtract(slot, side) ? inventory.extractItem(slot, count, simulate) : ItemStack.EMPTY; }
            });
            LazyOptional<IEnergyStorage> power = LazyOptional.of(() -> energyView(side));
            sidedItems.put(side, items); sidedEnergy.put(side, power); capabilities.add(items); capabilities.add(power);
        }
    }
    boolean bottomEnergyOnly() { return id.equals("calculatorlocator") || id.equals("conductormast") || id.equals("analysingchamber"); }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (MachineEnvironment.isAuxiliary(id)) {
            MachineBlockEntity main = controller();
            return main == null ? LazyOptional.empty() : main.getCapability(cap, side);
        }
        if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER && inventory.getSlots() > 0) return (side == null ? itemCapability : sidedItems.get(side)).cast();
        if (!isRemoved() && cap == ForgeCapabilities.ENERGY && energy.getMaxEnergyStored() > 0) return (side == null ? energyCapability : sidedEnergy.get(side)).cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); capabilities.forEach(LazyOptional::invalidate); }
    @Override public void reviveCaps() { super.reviveCaps(); capabilities.clear(); rebuildCapabilities(); }
    public static void serverTick(Level level, BlockPos pos, BlockState blockState, MachineBlockEntity machine) {
        if (level.isClientSide || machine.isRemoved()) return;
        machine.ticks++;
        machine.discharge();
        MachineProcessing.tick(machine);
        MachineEnvironment.tick(machine);
        if (machine.id.endsWith("greenhouse")) GreenhouseLogic.tick(machine);
        if (machine.spec.generator()) machine.sendEnergy();
        if (machine.upgradeCount(2) > 0) machine.transferOutputs();
        if (machine.progress != machine.lastProgress || machine.processTime != machine.lastProcessTime || !machine.state.equals(machine.lastTickState)) {
            machine.lastProgress = machine.progress; machine.lastProcessTime = machine.processTime;
            machine.lastTickState = machine.state.copy(); machine.setChanged();
        }
        int syncInterval = machine.perTickSync() ? 1 : 10;
        if (machine.ticks % syncInterval == 0 && machine.clientSyncDirty) { machine.clientSyncDirty = false; machine.sync(); }
    }
    /**
     * Machines whose visible model animates every tick (#6 chest lid, #11 crank, transmitter, fabrication arm)
     * need per-tick state packets; the default ten-tick batch would make the animation step in half-second jumps.
     */
    private boolean perTickSync() {
        return switch (id) { case "reinforcedchest", "crankhandle", "transmitter", "fabricationchamber" -> true; default -> false; };
    }
    public void setActive(boolean active) {
        if (level == null || level.isClientSide) return;
        if (MachineBlock.isGasLantern(getBlockState())) {
            BlockState next = CalculatorContent.block(active ? "gaslanternon" : "gaslanternoff").defaultBlockState()
                    .setValue(MachineBlock.FACING, facing()).setValue(MachineBlock.ACTIVE, active);
            if (!getBlockState().equals(next)) {
                level.setBlock(worldPosition, next, Block.UPDATE_ALL);
                setBlockState(next);
                setChanged();
            }
        } else if (isActive() != active) level.setBlock(worldPosition, getBlockState().setValue(MachineBlock.ACTIVE, active), Block.UPDATE_ALL);
    }
    private void discharge() {
        int slot = spec.battery(); if (slot < 0 || slot >= inventory.getSlots() || id.equals("analysingchamber") || id.equals("calculatorlocator")) return;
        ItemStack stack = inventory.getStackInSlot(slot); int room = energy.getMaxEnergyStored() - energy.getEnergyStored();
        IEnergyStorage source = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        if (source != null && room > 0) {
            int amount = Math.min(room, spec.configuredTransfer()); int possible = source.extractEnergy(amount, true);
            if (possible > 0) { energy.generate(source.extractEnergy(possible, false)); setChanged(); }
        } else {
            int value = DischargeValues.getValue(stack); if (value > 0 && room >= value) { consumeOne(slot); energy.generate(value); }
        }
    }
    public void chargeItem(int slot, int rate) {
        if (slot < 0 || slot >= inventory.getSlots()) return;
        ItemStack stack = inventory.getStackInSlot(slot); IEnergyStorage target = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        if (target != null) { int accepted = target.receiveEnergy(Math.min(rate, energy.getEnergyStored()), false); energy.consume(accepted); }
    }
    private void sendEnergy() {
        int remaining = spec.configuredTransfer();
        for (Direction side : Direction.values()) {
            if (remaining <= 0 || !canExtractEnergy(side)) continue;
            BlockPos pos = worldPosition.relative(side); if (!level.hasChunkAt(pos)) continue;
            BlockEntity target = level.getBlockEntity(pos); if (target == null) continue;
            IEnergyStorage receiver = target.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).orElse(null);
            if (receiver != null) { int sent = receiver.receiveEnergy(Math.min(remaining, energy.getEnergyStored()), false); energy.consume(sent); remaining -= sent; }
        }
    }
    private void transferOutputs() {
        for (Direction side : Direction.values()) {
            IItemHandler target = adjacent(side); if (target == null) continue;
            for (int slot = 0; slot < inventory.getSlots(); slot++) if (externalExtract(slot, side)) {
                ItemStack source = inventory.extractItem(slot, 4, true); if (source.isEmpty()) continue;
                ItemStack remaining = ItemHandlerHelper.insertItemStacked(target, source, false);
                inventory.extractItem(slot, source.getCount() - remaining.getCount(), false);
            }
        }
    }
    @Nullable public IItemHandler adjacent(Direction side) {
        BlockPos pos = worldPosition.relative(side); if (level == null || !level.hasChunkAt(pos)) return null;
        BlockEntity target = level.getBlockEntity(pos); return target == null ? null : target.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
    }
    public void consumeOne(int slot) {
        ItemStack stack = inventory.getStackInSlot(slot); ItemStack remainder = stack.getCraftingRemainingItem();
        inventory.extractItem(slot, 1, false);
        if (!remainder.isEmpty()) { if (inventory.getStackInSlot(slot).isEmpty()) inventory.setStackInSlot(slot, remainder); else drop(remainder); }
    }
    public void drop(ItemStack stack) { if (level != null && !level.isClientSide && !stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, stack); }
    public void addHarvest(ItemStack stack) {
        ItemStack rest = ItemHandlerHelper.insertItemStacked(inventory, stack, false); IItemHandler chest = adjacent(facing());
        if (chest != null && !rest.isEmpty()) rest = ItemHandlerHelper.insertItemStacked(chest, rest, false);
        if (id.endsWith("greenhouse") && level != null && !rest.isEmpty()) { BlockPos out = worldPosition.relative(facing()).above(); Containers.dropItemStack(level, out.getX(), out.getY(), out.getZ(), rest); }
        else drop(rest);
    }
    public boolean interact(Player player, InteractionHand hand) { return MachineEnvironment.interact(this, player, hand); }
    public void placeAuxiliary() { MachineEnvironment.placeAuxiliary(this); }
    public void removeAuxiliary() { MachineEnvironment.removeAuxiliary(this); }
    public int comparatorSignal() { return energy.getMaxEnergyStored() > 0 ? (int)(15L * energy.getEnergyStored() / energy.getMaxEnergyStored()) : inventory.getSlots() == 0 ? 0 : (int)Math.min(15, inventoryFullness() * 14 + 1); }
    private double inventoryFullness() { double full = 0; for (int i = 0; i < inventory.getSlots(); i++) full += inventory.getStackInSlot(i).getCount() / (double)inventory.getSlotLimit(i); return full / inventory.getSlots(); }
    public boolean usableBy(Player player) { return !isRemoved() && level != null && player.level() == level && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64; }
    @Override public Component getDisplayName() { return Component.translatable("block.calculator." + id); }
    @Override public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) { return new MachineMenu(containerId, playerInventory, this); }
    public void handleAction(ServerPlayer player, int action, int value) {
        if (!usableBy(player) || player.level() != level || !(player.containerMenu instanceof MachineMenu menu) || menu.machine != this || !player.mayBuild() || !level.mayInteract(player, worldPosition)) return;
        if (action == 0) state.putBoolean("paused", !state.getBoolean("paused"));
        else if (action == 1 && id.endsWith("greenhouse")) GreenhouseLogic.requestBuild(this);
        else if (action == 2 && id.endsWith("greenhouse")) GreenhouseLogic.requestDemolish(this);
        else MachineEnvironment.handleAction(this, player, action, value);
        setChanged(); sync();
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.put("Inventory", inventory.serializeNBT()); tag.put("Energy", energy.serializeNBT()); tag.put("Upgrades", upgrades.serializeNBT());
        tag.put("MachineState", state.copy()); tag.putInt("Progress", progress); tag.putInt("ProcessTime", processTime); tag.putInt("Ticks", ticks);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); inventory.deserializeNBT(tag.getCompound("Inventory")); energy.deserializeNBT(tag.get("Energy")); upgrades.deserializeNBT(tag.getCompound("Upgrades"));
        state = tag.getCompound("MachineState").copy(); normalizeEnergySides(); normalizeRenderState(); progress = Math.max(0, tag.getInt("Progress")); processTime = Math.max(1, tag.getInt("ProcessTime")); ticks = Math.max(0, tag.getInt("Ticks"));
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag(); tag.putBoolean("RenderSync", true);
        tag.put("MachineState", state.copy()); tag.putInt("Progress", progress); tag.putInt("ProcessTime", processTime);
        tag.put("Energy", energy.serializeNBT());
        net.minecraft.nbt.ListTag renderItems = new net.minecraft.nbt.ListTag();
        int[] renderSlots = id.equals("dockingstation") ? new int[]{RENDER_CALCULATOR_SLOT}
                : id.equals("analysingchamber") ? new int[]{0}
                : java.util.Set.of("extractionchamber", "precisionchamber", "reassemblychamber", "restorationchamber", "processingchamber").contains(id)
                ? new int[]{0, 2} : new int[0];
        for (int slot : renderSlots) {
            if (slot >= inventory.getSlots()) continue;
            CompoundTag entry = inventory.getStackInSlot(slot).copy().save(new CompoundTag());
            entry.putInt("Slot", slot);
            renderItems.add(entry);
        }
        tag.put("RenderItems", renderItems);
        return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag) { applyClientUpdate(tag); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet) { if (packet.getTag() != null) applyClientUpdate(packet.getTag()); }
    private void applyClientUpdate(CompoundTag tag) {
        if (!tag.getBoolean("RenderSync")) { load(tag); return; }
        state = tag.getCompound("MachineState").copy(); progress = tag.getInt("Progress"); processTime = Math.max(1, tag.getInt("ProcessTime")); energy.deserializeNBT(tag.get("Energy"));
        net.minecraft.nbt.ListTag renderItems = tag.getList("RenderItems", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < renderItems.size(); i++) {
            CompoundTag entry = renderItems.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < inventory.getSlots()) inventory.setStackInSlot(slot, ItemStack.of(entry.copy()));
        }
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    public void sync() { if (level != null && !level.isClientSide) { syncCount++; level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); } }
    public ItemStack packedStack() { ItemStack stack = new ItemStack(getBlockState().getBlock()); stack.getOrCreateTag().put("CalculatorMachine", saveWithoutMetadata()); return stack; }
    public void restoreFromItem(ItemStack stack) { if (stack.hasTag() && stack.getTag().contains("CalculatorMachine", 10)) load(stack.getTag().getCompound("CalculatorMachine")); }
    public void clearForRemoval() { suppressDrops = true; }
    public void dropContents() {
        if (suppressDrops) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) { while (!inventory.getStackInSlot(slot).isEmpty()) drop(inventory.extractItem(slot, 64, false)); }
        for (int slot = 0; slot < upgrades.getSlots(); slot++) drop(upgrades.extractItem(slot, 64, false));
        suppressDrops = true;
    }
}
