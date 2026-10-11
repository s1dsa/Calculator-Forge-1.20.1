package sonar.calculator.mod.machine;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.item.modules.FlawlessCalculatorItem;
import sonar.calculator.mod.item.modules.ModuleItems;

/** Persistent machine slots and vanilla server-validated container button actions. */
public class MachineMenu extends AbstractContainerMenu {
    public final MachineBlockEntity machine;
    private final Player owner;
    private boolean closed;
    boolean isChestOpener() {
        return !closed && owner.containerMenu == this && !owner.isSpectator() && owner.isAlive()
                && (!(owner instanceof ServerPlayer server) || server.connection != null && server.connection.connection.isConnected())
                && stillValid(owner);
    }
    @Override public void removed(Player player) {
        closed = true;
        machine.removeChestMenu(this);
        super.removed(player);
    }
    public static final int FABRICATION_BUTTON_BASE = 1000, FABRICATION_BUTTON_LIMIT = 10000;
    private final java.util.List<sonar.calculator.mod.recipe.MachineRecipe> fabricationRecipes;
    private Slot fabricationTargetSlot;
    private int machineSlots;
    private int playerStart;
    private final int inventorySlots;
    private final int[] synced = new int[VALUES.length];
    private static final String[] VALUES = {"energy", "capacity", "progress", "processTime", "houseState", "carbon", "houseSize", "plantsGrown", "plantsHarvested", "burnTime", "maxBurnTime", "gasAdd", "itemLevel", "points", "size", "stability", "currentGen", "weatherType", "paused", "weatherOn", "whitelist", "exact", "fabricating", "side0", "side1", "side2", "side3", "side4", "side5"};

    public MachineMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, resolve(inventory, buf));
    }
    private static MachineBlockEntity resolve(Inventory inventory, FriendlyByteBuf buf) {
        var entity = inventory.player.level().getBlockEntity(buf.readBlockPos());
        if (!(entity instanceof MachineBlockEntity machine)) throw new IllegalStateException("Missing Calculator machine for menu");
        return machine;
    }
    public MachineMenu(int id, Inventory inventory, MachineBlockEntity machine) {
        super(MachineContent.MENU.get(), id);
        this.machine = machine;
        this.owner = inventory.player;
        machine.registerChestMenu(this);
        fabricationRecipes = machine.id().equals("fabricationchamber") ? MachineProcessing.fabricationRecipes(machine) : java.util.List.of();
        this.machineSlots = machine.inventory.getSlots();
        if (machine.id().equals("moduleworkstation")) {
            // The upstream workstation exposes the calculator and sixteen module slots. Module
            // slots are a server-owned view of the calculator tag; they never hold duplicate stacks.
            for (int slot = 0; slot < FlawlessCalculatorItem.MODULE_CAPACITY; slot++) {
                final int moduleSlot = slot;
                addSlot(new Slot(new SimpleContainer(1), 0, 10 + slot % 8 * 20, 40 + slot / 8 * 22) {
                @Override public ItemStack getItem() { return workstationModule(moduleSlot); }
                @Override public boolean hasItem() { return !getItem().isEmpty(); }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public void set(ItemStack stack) { if (machine.getLevel().isClientSide) super.set(stack); }
            });
            }
            addSlot(new SlotItemHandler(machine.inventory, 1, 8, 8) {
                @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof FlawlessCalculatorItem; }
                @Override public boolean mayPickup(Player player) { return stillValid(player); }
            });
            machineSlots = 17;
        } else for (int slot = 0; slot < machineSlots; slot++) {
            int[] xy = slotPosition(slot);
            addSlot(new SlotItemHandler(machine.inventory, slot, xy[0], xy[1]) {
                @Override public boolean mayPlace(ItemStack stack) { return machine.canInsert(getSlotIndex(), stack); }
                @Override public boolean mayPickup(Player player) { return stillValid(player); }
                @Override public void setChanged() { machine.setChanged(); }
            });
        }
        inventorySlots = machineSlots;
        if (machine.supportsUpgrades()) for (int upgrade = 0; upgrade < 4; upgrade++) addSlot(new SlotItemHandler(machine.upgrades, upgrade, -20, 20 + upgrade * 18) {
            @Override public boolean mayPickup(Player player) { return stillValid(player); }
        });
        machineSlots = slots.size();
        playerStart = slots.size();
        int y = playerInventoryY();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, y + 58));
        // A read-only menu slot synchronizes the server-owned selected stack to every viewer.
        // It is outside both transfer ranges and never stores an extractable item.
        if (machine.id().equals("fabricationchamber")) {
            fabricationTargetSlot = addSlot(new Slot(new SimpleContainer(1), 0, 104, 89) {
                @Override public ItemStack getItem() { return machine.getLevel().isClientSide ? super.getItem() : MachineProcessing.fabricationTarget(machine); }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public void set(ItemStack stack) { if (machine.getLevel().isClientSide) super.set(stack); }
            });
        }
        // Container data packets carry signed shorts: split each full-width value into two halves.
        addDataSlots(new ContainerData() {
            public int getCount() { return VALUES.length * 2; }
            public int get(int index) { return value(index / 2) >>> (index % 2 * 16) & 65535; }
            public void set(int index, int value) { int at = index / 2, shift = index % 2 * 16; synced[at] = synced[at] & ~(65535 << shift) | (value & 65535) << shift; }
        });
    }
    private int value(int index) {
        if (machine.getLevel().isClientSide) return synced[index];
        return switch (index) {
            case 0 -> machine.getEnergyStored(); case 1 -> machine.getEnergyCapacity(); case 2 -> machine.progress; case 3 -> machine.processTime;
            case 18,19,20,21,22 -> machine.state.getBoolean(VALUES[index]) ? 1 : 0;
            default -> machine.state.getInt(VALUES[index]);
        };
    }
    public java.util.List<sonar.calculator.mod.recipe.MachineRecipe> getFabricationRecipes() { return fabricationRecipes; }
    public ItemStack getFabricationTarget() { return fabricationTargetSlot == null ? ItemStack.EMPTY : fabricationTargetSlot.getItem(); }
    public int getEnergySideMode(int side) { return getStateValue("side" + side); }
    public int getStateValue(String key) { for (int i=0;i<VALUES.length;i++) if (VALUES[i].equals(key)) return value(i); return machine.getStateValue(key); }
    public MachineBlockEntity getMachine() { return machine; }
    public String getMachineId() { return machine.id(); }
    public int getEnergy() { return value(0); }
    public int getEnergyStored() { return value(0); }
    public int getEnergyCapacity() { return value(1); }
    public int getProgress() { return value(2); }
    public int getProcessTime() { return value(3); }
    // --- Stable read-only protocol consumed by the client screens and the JEI integration -------------
    /**
     * Historical name kept for the 32-item regression baseline and any external caller. It returns the index at
     * which the player inventory begins, i.e. the number of non-player container slots (the machine inventory
     * plus its upgrade slots). It is <b>not</b> the machine's own slot count ({@link #getMachineSlotCount()}) and
     * not the total slot count; it is equivalent to {@link #getPlayerSlotStart()}. The value is captured before the
     * player slots are added, so {@code quickMoveStack}'s {@code index < getMachineSlots()} bound is correct.
     */
    public int getMachineSlots() { return playerStart; }
    public int getImageHeight() { return playerInventoryY() + 82; }
    /** Machine-owned inventory slots only (excludes upgrade and player slots). */
    public int getMachineSlotCount() { return inventorySlots; }
    /** First index of the player inventory inside this menu's slot list. */
    public int getPlayerSlotStart() { return playerStart; }
    /** Upgrades installed in this machine (0..3); -1 for machines without an upgrade grid. */
    public int getUpgradeSlotCount() { return machine.supportsUpgrades() ? machine.upgrades.getSlots() : -1; }
    /** Stack currently mounted in a docking station's calculator slot, or EMPTY for every other machine (#9). */
    public ItemStack getMountedCalculator() {
        return machine.id().equals("dockingstation") ? machine.inventory.getStackInSlot(MachineBlockEntity.RENDER_CALCULATOR_SLOT) : ItemStack.EMPTY;
    }
    /** Server-authoritative per-face energy mode for the advanced power cube (#3): 0 input, 1 output, 2 disabled. */
    public int getEnergySideMode(Direction side) { return getStateValue("side" + side.get3DDataValue()); }
    /** Whether processing is paused through the {@code 0} container button (#5/#7/#10). */
    public boolean isPaused() { return getStateValue("paused") != 0; }
    /** Human-readable owner of a calculator locator, or "" when unowned (#12). */
    public String getMachineOwner() { return machine.state.getString("owner"); }
    /** Live block activity flag; the client already receives the ACTIVE block state and server-side ticks. */
    public boolean isMachineActive() { return machine.isActive(); }
    /** Locator plug-ring radius discovered by the server (0 when no valid structure surrounds the locator). */
    public int getLocatorSize() { return getStateValue("size"); }
    /** Locator stability as a percentage of the fully built plug ring (#12). */
    public int getStabilityPercent() {
        int size = getStateValue("size");
        int total = (2 * size + 1) * (2 * size + 1);
        return total <= 0 ? 0 : getStateValue("stability") * 100 / total;
    }
    public int playerInventoryY() {
        return switch (machine.id()) {
            case "reinforcedchest" -> 192;
            case "storagechamber" -> 101;
            case "fabricationchamber" -> 110;
            default -> machine.id().endsWith("greenhouse") ? 110 : 84;
        };
    }
    /**
     * Upstream exposes a different docking station slot layout per mounted calculator
     * ({@code ContainerDockingStation.addSlots}); the port keeps a fixed seven-slot inventory, so the
     * coordinates track the mounted calculator and unused input slots fall back to the flawless grid.
     */
    private int[] dockingSlot(int slot) {
        int[][] flawless = {{16,33},{48,33},{80,33},{112,33},{28,60},{144,33},{8,8}};
        return switch (MachineBlockEntity.itemId(machine.inventory.getStackInSlot(MachineBlockEntity.RENDER_CALCULATOR_SLOT))) {
            case "atomiccalculator" -> switch (slot) { case 0 -> new int[]{21,30}; case 1 -> new int[]{53,30}; case 2 -> new int[]{85,30}; case 4 -> new int[]{28,60}; case 5 -> new int[]{135,30}; case 6 -> new int[]{8,8}; default -> flawless[slot]; };
            case "calculator", "scientificcalculator" -> switch (slot) { case 0 -> new int[]{26,30}; case 1 -> new int[]{80,30}; case 4 -> new int[]{28,60}; case 5 -> new int[]{135,30}; case 6 -> new int[]{8,8}; default -> flawless[slot]; };
            default -> flawless[slot];
        };
    }
    private int[] slotPosition(int slot) {
        if (machine.id().equals("dockingstation")) return dockingSlot(slot);
        if (machine.id().endsWith("greenhouse")) {
            int offset = machine.spec.battery() + 1;
            if (slot >= offset) return new int[]{8 + (slot - offset) * 18,88};
            if (slot == machine.spec.battery()) return new int[]{machine.id().equals("flawlessgreenhouse") ? 26 : 80,61};
            if (machine.id().equals("basicgreenhouse")) return new int[]{26 + slot % 2 * 18,22 + slot / 2 * 18};
            int[][] coordinates = {{35,11},{17,29},{35,29},{53,29},{17,47},{35,47},{53,47}};
            return coordinates[slot];
        }
        int[][] coordinates = switch (machine.id()) {
            case "atomicmultiplier" -> new int[][]{{54,16},{26,41},{44,41},{62,41},{80,41},{98,41},{116,41},{134,41},{108,16},{20,62}};
            case "analysingchamber" -> new int[][]{{16,6},{28,60},{35,34},{53,34},{71,34},{89,34},{107,34},{125,34}};
            case "dockingstation" -> new int[][]{{16,33},{48,33},{80,33},{112,33},{28,60},{144,33},{8,8}};
            case "atomiccalculator" -> new int[][]{{20,35},{52,35},{84,35},{134,35}};
            case "dynamiccalculator" -> new int[][]{{25,9},{79,9},{134,9},{25,35},{79,35},{134,35},{20,61},{52,61},{84,61},{134,61}};
            case "storagechamber" -> new int[][]{{26,23},{62,23},{98,23},{134,23},{8,49},{44,49},{80,49},{116,49},{152,49},{8,75},{44,75},{80,75},{116,75},{152,75}};
            case "fabricationchamber" -> new int[][]{{133,89}};
            case "starchextractor", "redstoneextractor", "glowstoneextractor" -> new int[][]{{8,38},{8,14}};
            case "powercube", "advancedpowercube", "creativepowercube" -> new int[][]{{80,34},{28,60}};
            case "calculatorlocator" -> new int[][]{{28,60},{132,60}};
            case "co2generator" -> new int[][]{{80,22},{28,60}};
            case "conductormast" -> new int[][]{{54,22},{108,22}};
            case "healthprocessor", "hungerprocessor" -> new int[][]{{55,34},{104,35}};
            case "weathercontroller" -> new int[][]{{28,60}};
            case "stoneassimilator", "calculatorplug", "gaslanternon", "gaslanternoff" -> new int[][]{{80,34}};
            default -> machine.spec.outputs() == 2 ? new int[][]{{39,24},{28,60},{93,24},{121,24}}
                : machine.spec.outputs() == 1 ? new int[][]{{53,24},{28,60},{107,24}} : null;
        };
        if (coordinates != null && slot < coordinates.length) return coordinates[slot];
        if (machine.id().equals("magneticflux")) return new int[]{26 + slot * 18,61};
        return new int[]{8 + slot % 9 * 18,24 + slot / 9 * 18};
    }
    /** Vanilla PICKUP and bounded button packets share the same server transaction. */
    private boolean workstationAction(Player player, int module, boolean install) {
        if (!(player instanceof ServerPlayer server) || server.containerMenu != this || !stillValid(player)
                || !machine.id().equals("moduleworkstation") || module < 0 || module >= 16
                || !player.mayBuild() || !player.level().mayInteract(player, machine.getBlockPos())) return false;
        ItemStack calculator = machine.inventory.getStackInSlot(1);
        if (calculator.getCount() != 1 || !(calculator.getItem() instanceof FlawlessCalculatorItem)) return false;
        if (install) {
            if (!ModuleItems.installModule(calculator, module, getCarried())) return false;
            if (getCarried().isEmpty()) setCarried(ItemStack.EMPTY);
        } else {
            if (!getCarried().isEmpty()) return false;
            ItemStack removed = ModuleItems.removeModule(calculator, module);
            if (removed.isEmpty()) return false;
            setCarried(removed);
        }
        machine.setChanged(); broadcastChanges(); return true;
    }
    private ItemStack workstationModule(int module) {
        ItemStack calculator = machine.inventory.getStackInSlot(1);
        if (!(calculator.getItem() instanceof FlawlessCalculatorItem item)) return ItemStack.EMPTY;
        String name = ModuleItems.moduleName(calculator, module);
        if (name.equals("Empty")) return ItemStack.EMPTY;
        ItemStack result = name.equals("Ender") ? new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL) : CalculatorContent.stack(FlawlessCalculatorItem.MODULE_ITEMS.get(name), 1);
        result.setTag(item.getModuleTag(calculator, module, false).copy()); return result;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        if (fabricationTargetSlot != null && slot == fabricationTargetSlot.index) return;
        if (machine.id().equals("moduleworkstation") && slot >= 0 && slot < 16) {
            if (type == ClickType.PICKUP && (button == 0 || button == 1)) workstationAction(player, slot, !getCarried().isEmpty());
            return;
        }
        super.clicked(slot, button, type, player);
    }
    @Override public boolean stillValid(Player player) { return machine.usableBy(player); }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || server.containerMenu != this || !stillValid(player)
                || !player.mayBuild() || !player.level().mayInteract(player, machine.getBlockPos())) return false;
        if (button >= FABRICATION_BUTTON_BASE && button < FABRICATION_BUTTON_BASE + FABRICATION_BUTTON_LIMIT) {
            if (!machine.id().equals("fabricationchamber")) return false;
            int index = button - FABRICATION_BUTTON_BASE;
            var current = MachineProcessing.fabricationRecipes(machine);
            if (index >= fabricationRecipes.size() || index >= current.size()
                    || !fabricationRecipes.get(index).getId().equals(current.get(index).getId())) return false;
            if (!MachineProcessing.selectFabricationTarget(machine, index)) return false;
            broadcastChanges(); return true;
        }
        if (button >= 400 && button < 432) return workstationAction(player, (button - 400) % 16, button < 416);
        // 0 pause, 1 build, 2 demolish, 12 eject installed upgrade circuits (#5/#7/#10); packed ranges keep
        // value-bearing actions server-bounded.
        if (button == 0 || button == 1 || button == 2 || button == 10 || button == 11 || button == 12) machine.handleAction(server,button,0);
        else if (button >= 100 && button <= 102) machine.handleAction(server,10,button-100);
        else if (button >= 200 && button <= 203) machine.handleAction(server,20,button-200);
        else if (button >= 300 && button <= 305) machine.handleAction(server,30,button-300);
        else return false;
        broadcastChanges(); return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!player.level().isClientSide && player.containerMenu != this) return ItemStack.EMPTY;
        if (!stillValid(player) || index < 0 || index >= slots.size() || slots.get(index) == fabricationTargetSlot) return ItemStack.EMPTY;
        if (machine.id().equals("moduleworkstation")) {
            if (index < 16) return ItemStack.EMPTY;
            Slot source = slots.get(index); ItemStack input = source.getItem(); ItemStack before = input.copy();
            if (index == 16) { if (!moveItemStackTo(input, machineSlots, slots.size(), true)) return ItemStack.EMPTY; }
            else if (index >= 17 && index < 21) { if (!moveItemStackTo(input, 21, slots.size(), true)) return ItemStack.EMPTY; }
            else if (input.getItem() instanceof FlawlessCalculatorItem) { if (!moveItemStackTo(input, 16, 17, false)) return ItemStack.EMPTY; }
            else {
                if (!player.mayBuild() || !player.level().mayInteract(player, machine.getBlockPos())) return ItemStack.EMPTY;
                ItemStack calculator = machine.inventory.getStackInSlot(1);
                if (calculator.getCount() != 1) return ItemStack.EMPTY;
                boolean installed = false;
                for (int module = 0; module < 16 && !input.isEmpty(); module++) installed |= ModuleItems.installModule(calculator, module, input);
                if (!installed) return ItemStack.EMPTY;
            }
            if (input.isEmpty()) source.set(ItemStack.EMPTY); else source.setChanged();
            machine.setChanged(); broadcastChanges(); return before;
        }
        Slot slot = slots.get(index); if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), before = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack,machineSlots,slots.size(),true)) return ItemStack.EMPTY;
        } else {
            // Use the inventory's actual limits, including storage chamber's 1024-stack slots.
            ItemStack upgradeRemainder = stack;
            if (machine.supportsUpgrades()) upgradeRemainder = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(machine.upgrades, stack, false);
            ItemStack remainder = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(machine.inventory,upgradeRemainder,false);
            if (remainder.getCount() == stack.getCount()) return ItemStack.EMPTY;
            stack.setCount(remainder.getCount());
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player,stack); machine.setChanged(); return before;
    }
}
