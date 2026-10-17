package sonar.calculator.mod.machine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import sonar.calculator.mod.CalculatorConfig;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.world.WorldContent;
import sonar.calculator.mod.item.modules.FlawlessCalculatorItem;
import sonar.calculator.mod.item.modules.ModuleItems;
import sonar.core.api.blocks.IStableBlock;
import sonar.core.api.blocks.IStableGlass;

final class MachineEnvironment {
    private MachineEnvironment() {}
    static void tick(MachineBlockEntity m) {
        switch (m.id()) {
            case "powercube", "advancedpowercube", "creativepowercube" -> {
                if (m.id().equals("creativepowercube")) m.energy.setEnergy(m.energy.getMaxEnergyStored());
                m.chargeItem(0, m.spec.configured("CHARGING_RATE", m.id().equals("powercube") ? 4 : 100000));
            }
            case "calculatorplug" -> m.setActive(stable(m.inventory.getStackInSlot(0)));
            case "calculatorlocator" -> locator(m);
            case "conductormast" -> conductor(m);
            case "calculatorscreenblock" -> calculatorScreen(m);
            case "reinforcedchest" -> {
                boolean open = m.chestOpenerCount() > 0;
                int old = m.state.getInt("lidAngle");
                int next = Math.max(0, Math.min(1000, old + (open ? 100 : -100)));
                m.state.putInt("lidAngle", next);
                if (open && old == 0 || !open && old >= 500 && next < 500) {
                    m.getLevel().playSound(null, m.getBlockPos(), open ? net.minecraft.sounds.SoundEvents.CHEST_OPEN : net.minecraft.sounds.SoundEvents.CHEST_CLOSE,
                            net.minecraft.sounds.SoundSource.BLOCKS, .5F, .9F + m.getLevel().random.nextFloat() * .1F);
                }
            }
            case "weatherstation" -> weatherStation(m);
            case "transmitter" -> m.state.putInt("rotation", (m.state.getInt("rotation") + 1) % 360);
            case "crankhandle" -> { int angle = m.state.getInt("angle"); if (angle > 0) m.state.putInt("angle", angle >= 36 ? 0 : angle + 1); }
            case "handcrankedgenerator" -> {
                MachineBlockEntity crank = machine(m.getLevel(), m.getBlockPos().above(), "crankhandle");
                if (crank != null && crank.state.getInt("angle") > 0) {
                    int ticks = m.state.getInt("powerTicks"); if (ticks == 0) m.energy.generate(CalculatorConfig.HAND_CRANK_PER_TICK); m.state.putInt("powerTicks", (ticks + 1) % 2);
                }
            }
            case "rainsensor" -> m.setActive(m.getLevel().isRaining() && m.getLevel().canSeeSky(m.getBlockPos().above()));
            case "weathercontroller" -> weather(m);
            case "magneticflux" -> magnet(m);
            case "hungerprocessor", "healthprocessor" -> nutrition(m);
            case "stoneassimilator", "algorithmassimilator" -> assimilator(m);
            case "co2generator" -> carbon(m);
            case "gaslanternon", "gaslanternoff" -> lantern(m);
            case "scarecrow" -> m.state.putInt("Grow", (m.state.getInt("Grow") + 1) % Math.max(1, CalculatorConfig.SCARECROW_TICK_RATE));
            default -> {}
        }
    }
    static MachineBlockEntity machine(Level level, BlockPos pos, String id) {
        if (!level.hasChunkAt(pos)) return null;
        return level.getBlockEntity(pos) instanceof MachineBlockEntity m && m.id().equals(id) ? m : null;
    }
    static boolean stable(ItemStack stack) { return MachineBlockEntity.itemId(stack).equals("flawlessdiamond") || MachineBlockEntity.circuit(stack) && stack.hasTag() && stack.getTag().getInt("Stable") == 1; }
    static boolean interact(MachineBlockEntity m, Player player, InteractionHand hand) {
        if (m.id().equals("crankhandle")) {
            if (m.state.getInt("angle") == 0) {
                m.state.putInt("angle", 1);
                // Optional upstream fidelity (CrankHandle.onBlockActivated): a one-in-a-hundred crank breaks,
                // dropping two sticks from the handle itself.
                if (m.getLevel().random.nextInt(100) < 1) {
                    Block.popResource(m.getLevel(), m.getBlockPos(), new ItemStack(Items.STICK, 2));
                    m.getLevel().removeBlock(m.getBlockPos(), false);
                    return true;
                }
            }
            m.sync(); return true;
        }
        ItemStack held = player.getItemInHand(hand);
        if (m.supportsUpgrades()) for (int slot = 0; slot < m.upgrades.getSlots(); slot++) if (m.upgrades.isItemValid(slot, held)) {
            ItemStack remain = m.upgrades.insertItem(slot, held, false); if (remain.getCount() != held.getCount()) { player.setItemInHand(hand, remain); return true; }
        }
        if (m.id().equals("dockingstation") && m.inventory.getStackInSlot(6).isEmpty() && m.canInsert(6, held)) {
            ItemStack installed = held.copy(); installed.setCount(1); m.inventory.setStackInSlot(6, installed); held.shrink(1); m.sync(); return true;
        }
        return false;
    }
    static void handleAction(MachineBlockEntity m, ServerPlayer player, int action, int value) {
        if (m.id().equals("weathercontroller")) { if (action == 10 && value >= 0 && value <= 2) m.state.putInt("weatherType", value); if (action == 11) m.state.putBoolean("weatherOn", !m.state.getBoolean("weatherOn")); }
        if (m.id().equals("magneticflux")) { if (action == 10) m.state.putBoolean("whitelist", !m.state.getBoolean("whitelist")); if (action == 11) m.state.putBoolean("exact", !m.state.getBoolean("exact")); }
        if (m.id().equals("fabricationchamber") && action == 10) MachineProcessing.startFabrication(m);
        if (action == 20 && m.supportsUpgrades() && value >= 0 && value < 4) { ItemStack upgrade = m.upgrades.extractItem(value, 64, false); if (!player.getInventory().add(upgrade)) player.drop(upgrade, false); }
        // #5/#7/#10 "移除提取物" (upstream buttons.circuits / TileEntityProcess.readPacket id 0 "Remove Circuits"):
        // eject every installed upgrade circuit so it drops as an item, exactly as the original single button did.
        if (action == 12 && m.supportsUpgrades()) {
            for (int slot = 0; slot < m.upgrades.getSlots(); slot++) {
                ItemStack stack = m.upgrades.getStackInSlot(slot);
                if (!stack.isEmpty()) { m.upgrades.setStackInSlot(slot, ItemStack.EMPTY); Block.popResource(m.getLevel(), m.getBlockPos(), stack); }
            }
        }
        if (m.id().equals("advancedpowercube") && action == 30 && value >= 0 && value < 6) { String key = "side" + value; m.state.putInt(key, (m.energySideMode(Direction.from3DDataValue(value)) + 1) % 3); }
    }
    private static void weather(MachineBlockEntity m) {
        ServerLevel level = (ServerLevel)m.getLevel(); int type = m.state.getInt("weatherType"); boolean on = m.state.getBoolean("weatherOn");
        int cooldown = m.state.getInt("cooldown"); if (cooldown > 0) { m.state.putInt("cooldown", cooldown - 1); return; }
        boolean needed = type == 0 ? level.isDay() == on : type == 1 ? level.isRaining() != on : level.isThundering() != on;
        if (m.progress == 0 && (!needed || !level.hasNeighborSignal(m.getBlockPos()) || m.energy.getEnergyStored() < CalculatorConfig.WEATHER_CONTROLLER_USAGE)) return;
        int cost = CalculatorConfig.WEATHER_CONTROLLER_USAGE / 100;
        if (m.energy.getEnergyStored() < cost) return;
        m.energy.consume(cost); m.progress++; m.processTime = 100; m.setActive(true);
        if (m.progress >= 100) {
            if (type == 0) { long old = level.getDayTime(), next = old + 24000 - (old + 24000) % 24000; level.setDayTime(on ? next - 12000 > old ? next - 12000 : old + 12000 : next); }
            else level.setWeatherParameters(on ? 0 : 6000, 6000, on, type == 2 && on || type == 1 && level.isThundering());
            m.progress = 0; m.state.putInt("cooldown", 30); m.setActive(false);
        }
    }
    private static void magnet(MachineBlockEntity m) {
        if (m.getLevel().hasNeighborSignal(m.getBlockPos())) { m.setActive(false); return; } m.setActive(true);
        for (ItemEntity entity : m.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(m.getBlockPos()).inflate(10))) {
            ItemStack stack = entity.getItem(); boolean matches = false;
            for (int i = 0; i < m.inventory.getSlots(); i++) { ItemStack filter = m.inventory.getStackInSlot(i); if (filter.isEmpty()) continue;
                boolean loose = m.state.getBoolean("exact");
                if (loose ? filter.is(stack.getItem()) || filter.getTags().anyMatch(stack::is) : ItemStack.isSameItemSameTags(filter, stack)) { matches = true; break; }
            }
            if (matches != m.state.getBoolean("whitelist")) continue;
            Vec3 delta = new Vec3(m.getBlockPos().getX() + .5, m.getBlockPos().getY() + .2, m.getBlockPos().getZ() + .5).subtract(entity.position());
            double distance = delta.length();
            if (distance < 1.5) { IItemHandler target = m.adjacent(Direction.DOWN); if (target != null) { ItemStack rest = ItemHandlerHelper.insertItemStacked(target, stack, false); if (rest.isEmpty()) entity.discard(); else entity.setItem(rest); } }
            else { double speed = entity.isOnFire() ? 5.2 : .1; Vec3 velocity = entity.getDeltaMovement().add(delta.x / distance * speed, delta.y * speed, delta.z / distance * speed); if (delta.y > 0) velocity = new Vec3(velocity.x, .1, velocity.z); entity.setDeltaMovement(velocity); }
        }
    }
    static boolean nutritionValid(MachineBlockEntity m, int slot, ItemStack stack) {
        boolean hunger = m.id().equals("hungerprocessor"); if (nutritionCapacity(stack, hunger) != 0) return true;
        return slot == 0 && (hunger ? stack.isEdible() : MachineProcessing.value(m, "health", stack) > 0);
    }
    private static int nutritionCapacity(ItemStack stack, boolean hunger) {
        String name = MachineBlockEntity.itemId(stack);
        if (name.equals("nutritionmodule")) return hunger ? CalculatorConfig.NUTRITION_MODULE_HUNGER_CAPACITY : CalculatorConfig.NUTRITION_MODULE_HEALTH_CAPACITY;
        if (name.equals(hunger ? "hungermodule" : "healthmodule")) return hunger ? CalculatorConfig.HUNGER_MODULE_CAPACITY : CalculatorConfig.HEALTH_MODULE_CAPACITY;
        return 0;
    }
    private static int chargeNutrition(ItemStack stack, boolean hunger, int available) {
        int capacity = nutritionCapacity(stack, hunger); if (capacity == 0 || available <= 0) return 0;
        String key = hunger ? "hunger" : "health"; CompoundTag tag = stack.getOrCreateTag(); int stored = Math.max(0, tag.getInt(key));
        int added = capacity < 0 ? 4 : Math.min(4, Math.max(0, capacity - stored));
        if (added > 0) tag.putInt(key, (int)Math.min(Integer.MAX_VALUE, (long)stored + added));
        // Upstream rounds the final partial batch up to the four-point charging speed.
        return Math.min(available, added);
    }
    private static void nutrition(MachineBlockEntity m) {
        boolean hunger = m.id().equals("hungerprocessor"); String key = hunger ? "hunger" : "health";
        ItemStack input = m.inventory.getStackInSlot(0); int points = m.state.getInt("points");
        if (!input.isEmpty()) {
            int value = hunger && input.isEdible() ? input.getFoodProperties(null).getNutrition() : hunger ? 0 : MachineProcessing.value(m, "health", input);
            if (value > 0) { points = (int)Math.min(Integer.MAX_VALUE, (long)points + value); m.inventory.extractItem(0, 1, false); }
            else if (nutritionCapacity(input, hunger) != 0) { CompoundTag tag = input.getOrCreateTag(); int old = Math.max(0, tag.getInt(key)); int removed = Math.min(4, old); tag.putInt(key, old - removed); points = (int)Math.min(Integer.MAX_VALUE, (long)points + removed); }
        }
        points -= chargeNutrition(m.inventory.getStackInSlot(1), hunger, points); m.state.putInt("points", Math.max(0, points));
    }
    private static void assimilator(MachineBlockEntity m) {
        if (m.id().equals("stoneassimilator")) {
            for (boolean hunger : new boolean[]{true,false}) { String key = hunger ? "hunger" : "health"; int points = m.state.getInt(key); points -= chargeNutrition(m.inventory.getStackInSlot(0), hunger, points); m.state.putInt(key, Math.max(0, points)); }
        }
        if (m.tickCount() % 31 != 0) return;
        BlockPos centre = m.getBlockPos().relative(m.facing().getOpposite());
        for (int y = 0; y < 3; y++) if (!m.getLevel().getBlockState(centre.above(y)).is(net.minecraft.tags.BlockTags.LOGS)) return;
        for (int x = -2; x < 3; x++) for (int z = -2; z < 3; z++) for (int y = 1; y < 8; y++) {
            BlockPos pos = centre.offset(x,y,z); if (!m.getLevel().hasChunkAt(pos) || !WorldContent.isLeafReady(m.getLevel().getBlockState(pos))) continue;
            if (m.id().equals("stoneassimilator")) {
                for (boolean hunger : new boolean[]{true,false}) if (WorldContent.harvestNutrition(m.getLevel(), pos, hunger) > 0) { String key = hunger ? "hunger" : "health"; m.state.putInt(key, m.state.getInt(key) + 1); return; }
            } else { List<ItemStack> harvest = WorldContent.harvestLeaves(m.getLevel(), pos, m.getLevel().random.nextBoolean()); if (!harvest.isEmpty()) { harvest.forEach(m::addHarvest); return; } }
        }
    }
    private static void carbon(MachineBlockEntity m) {
        MachineBlockEntity greenhouse = machine(m.getLevel(), m.getBlockPos().relative(m.facing().getOpposite().getClockWise().getOpposite(), 3), "flawlessgreenhouse");
        int max = m.state.getInt("maxBurnTime"), burn = m.state.getInt("burnTime");
        ItemStack fuel = m.inventory.getStackInSlot(0);
        if (max == 0 && greenhouse != null && m.energy.getEnergyStored() >= 100000) {
            int duration = net.minecraftforge.common.ForgeHooks.getBurnTime(fuel, net.minecraft.world.item.crafting.RecipeType.SMELTING);
            if (duration > 0) { max = 10000; m.state.putInt("gasAdd", duration / 100); m.state.putBoolean("controlled", MachineBlockEntity.itemId(fuel).equals("controlledfuel")); m.consumeOne(0); m.energy.consume(100000); }
        }
        if (max > 0) {
            if (!m.state.getBoolean("controlled")) burn++;
            else if (greenhouse != null) {
                boolean control = m.state.getBoolean("control"); int carbon = greenhouse.state.getInt("carbon");
                if (control && carbon < 100000) { burn++; m.state.putInt("gasAdd", 800); }
                else if (carbon >= 100000) control = false;
                if (!control && carbon <= 92000) control = true;
                else if (!control) m.state.putInt("gasAdd", 0);
                m.state.putBoolean("control", control);
            }
            if (burn >= max) { burn = 0; max = 0; m.state.putInt("gasAdd", 0); }
        }
        m.state.putInt("burnTime", burn); m.state.putInt("maxBurnTime", max); m.progress = burn; m.processTime = 10000; m.setActive(max > 0);
    }
    private static void lantern(MachineBlockEntity m) {
        int burn = m.state.getInt("burnTime"), max = m.state.getInt("maxBurnTime");
        if (max == 0) { int value = net.minecraftforge.common.ForgeHooks.getBurnTime(m.inventory.getStackInSlot(0), net.minecraft.world.item.crafting.RecipeType.SMELTING); if (value > 0) { max = value * 10; m.inventory.extractItem(0, 1, false); } }
        if (max > 0) burn++; if (burn >= max) { burn = 0; max = 0; }
        m.state.putInt("burnTime", burn); m.state.putInt("maxBurnTime", max); m.setActive(max > 0);
    }
    private static void conductor(MachineBlockEntity m) {
        int timer = m.state.getInt("lightningTimer"), delay = m.state.getInt("lightningDelay"), light = m.state.getInt("lightTicks");
        if (delay == 0 && m.energy.getEnergyStored() < m.energy.getMaxEnergyStored()) {
            int transmitters = nearby(m, "transmitter", 20).size();
            delay = m.getLevel().random.nextInt(300) + (transmitters > 9 ? 250 : 1500 - 135 * transmitters);
            // Upstream rolls the wait-message index together with each new delay; the GUI only
            // shows it during the first 500 ticks of a delay of at least 600 ticks.
            m.state.putInt("lightningRandom", m.getLevel().random.nextInt(ConductorStatus.MESSAGE_COUNT));
            m.state.putInt("strikes", transmitters > 9 ? transmitters / 4 : 1);
        }
        if (delay > 0 && ++timer >= delay) {
            timer = 0; delay = 0; light = 200; int stations = 0;
            for (MachineBlockEntity station : nearby(m, "weatherstation", 10)) if (station.state.getLong("mast") == m.getBlockPos().asLong()) stations++;
            int amount = (CalculatorConfig.CONDUCTOR_MAST_PER_TICK / 200 + stations * CalculatorConfig.WEATHER_STATION_PER_TICK / 200) * m.state.getInt("strikes") * 4;
            m.state.putInt("strikeRate", amount); m.state.putInt("rfPerStrike", amount * 200);
            var lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(m.getLevel()); if (lightning != null) { lightning.setPos(m.getBlockPos().getX(), m.getBlockPos().getY() + 4, m.getBlockPos().getZ()); lightning.setVisualOnly(true); m.getLevel().addFreshEntity(lightning); }
        }
        if (light > 0) { m.energy.generate(m.state.getInt("strikeRate")); light--; }
        m.state.putInt("lightningTimer", timer); m.state.putInt("lightningDelay", delay); m.state.putInt("lightTicks", light);
    }
    private static void calculatorScreen(MachineBlockEntity m) {
        BlockPos targetPos = m.getBlockPos().relative(m.facing().getOpposite());
        if (!m.getLevel().hasChunkAt(targetPos)) return;
        BlockEntity target = m.getLevel().getBlockEntity(targetPos);
        if (target == null) return;
        target.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, m.facing()).ifPresent(handler -> {
            m.state.putInt("latestEnergy", handler.getEnergyStored());
            m.state.putInt("latestMax", handler.getMaxEnergyStored());
        });
    }
    private static void weatherStation(MachineBlockEntity m) {
        if (m.tickCount() % 26 != 1) return;
        List<MachineBlockEntity> masts = nearby(m, "conductormast", 10);
        if (masts.isEmpty()) { m.state.putInt("angle", 1000); m.state.remove("mast"); return; }
        MachineBlockEntity nearest = masts.stream().min(java.util.Comparator.comparingDouble(a -> a.getBlockPos().distSqr(m.getBlockPos()))).orElseThrow();
        m.state.putLong("mast", nearest.getBlockPos().asLong());
        m.state.putInt("angle", (int)(Math.toDegrees(Math.atan2(nearest.getBlockPos().getZ() - m.getBlockPos().getZ(), nearest.getBlockPos().getX() - m.getBlockPos().getX())) - 90));
    }
    private static List<MachineBlockEntity> nearby(MachineBlockEntity m, String id, int radius) {
        List<MachineBlockEntity> result = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) { MachineBlockEntity found = machine(m.getLevel(), m.getBlockPos().offset(x,0,z), id); if (found != null) result.add(found); }
        return result;
    }
    private static void locator(MachineBlockEntity m) {
        if (m.tickCount() % 26 == 1) {
            int size = 0; for (int i = 1; i < 12; i++) if (locatorStructure(m, i)) { size = i; break; }
            int stability = 0; for (MachineBlockEntity plug : nearby(m, "calculatorplug", size)) if (stable(plug.inventory.getStackInSlot(0))) stability++;
            m.state.putInt("size", size); m.state.putInt("stability", stability);
        }
        ItemStack module = m.inventory.getStackInSlot(1); String owner = module.hasTag() ? module.getTag().getString("player") : "";
        if (owner.isEmpty() && module.hasTag()) owner = module.getTag().getString("Player");
        m.state.putString("owner", owner);
        Player player = null; for (Player p : m.getLevel().players()) if (p.getGameProfile().getName().equals(owner)) { player = p; break; }
        int size = m.state.getInt("size"), stability = m.state.getInt("stability");
        boolean active = size > 0 && !owner.isEmpty() && (stability >= 7 || player != null) && m.energy.getEnergyStored() < m.energy.getMaxEnergyStored();
        m.setActive(active);
        if (active) {
            int percent = stability * 100 / ((2 * size + 1) * (2 * size + 1));
            int output = (int)((5 + (int)(1000 * Math.sqrt(size * 1.8) - 100 * Math.sqrt(100 - percent)) / (int)(11 - Math.sqrt(percent)) * size) * CalculatorConfig.CALCULATOR_LOCATOR_MULTIPLIER);
            m.energy.generate(Math.max(0, output)); m.state.putInt("currentGen", output);
            if (CalculatorConfig.CALCULATOR_LOCATOR_CAN_CHANGE_TIME && stability < 5) ((ServerLevel)m.getLevel()).setDayTime(m.getLevel().getDayTime() + 100);
            int luck = m.state.getInt("luckTicks") + 1; if (luck >= 51) { luck = 0; if (player != null) locatorEffect(m, player, stability); } m.state.putInt("luckTicks", luck);
        }
        m.chargeItem(0, CalculatorConfig.CALCULATOR_LOCATOR_CHARGING_RATE);
    }
    private static boolean locatorStructure(MachineBlockEntity m, int size) {
        Level level = m.getLevel(); BlockPos base = m.getBlockPos();
        if (!level.hasChunksAt(base.offset(-size-1,-1,-size-1), base.offset(size+1,0,size+1))) return false;
        for (int x = -size; x <= size; x++) for (int z = -size; z <= size; z++) if (x != 0 || z != 0) {
            if (!(level.getBlockState(base.offset(x,-1,z)).getBlock() instanceof IStableBlock) || machine(level, base.offset(x,0,z), "calculatorplug") == null) return false;
        }
        for (int i = -size; i <= size; i++) for (int y = -1; y <= 0; y++) for (BlockPos p : List.of(base.offset(i,y,size+1),base.offset(i,y,-size-1),base.offset(size+1,y,i),base.offset(-size-1,y,i))) if (!(level.getBlockState(p).getBlock() instanceof IStableBlock)) return false;
        return true;
    }
    private static void locatorEffect(MachineBlockEntity m, Player player, int stability) {
        int luck = 1 + m.getLevel().random.nextInt(40 * (stability + 1) - 1);
        if (stability == 0 || stability < 2 && luck == 1) { m.getLevel().explode(player, player.getX(), player.getY(), player.getZ(), 4, Level.ExplosionInteraction.BLOCK); player.setHealth(player.getHealth() - 4); }
        if (stability < 4) {
            if (luck == 2) player.setSecondsOnFire(400);
            if (luck == 3 || luck == 4) m.getLevel().explode(player, player.getX(), player.getY(), player.getZ(), luck == 3 ? 8 : 6, Level.ExplosionInteraction.BLOCK);
        }
        MobEffect effect = switch (luck) { case 5 -> stability < 4 ? MobEffects.CONFUSION : null; case 6 -> stability < 4 ? MobEffects.BLINDNESS : null; case 10 -> stability < 6 ? MobEffects.JUMP : null; case 11 -> stability < 6 ? MobEffects.WATER_BREATHING : null; case 12 -> stability < 6 ? MobEffects.MOVEMENT_SLOWDOWN : null; case 13 -> stability < 6 ? MobEffects.DAMAGE_BOOST : null; case 14 -> stability < 7 ? MobEffects.WITHER : null; default -> null; };
        if (effect != null) player.addEffect(new MobEffectInstance(effect,1000,1));
        ItemStack reward = stability < 6 && luck >= 7 && luck <= 9 ? CalculatorContent.stack(luck == 7 ? "grenade" : luck == 8 ? "scientificcalculator" : "calculator", 1) : stability < 7 && luck == 15 ? new ItemStack(Items.MILK_BUCKET) : ItemStack.EMPTY;
        if (!reward.isEmpty() && !player.getInventory().add(reward)) player.drop(reward, false);
    }
    static boolean dynamicStructure(MachineBlockEntity m) {
        BlockPos centre = m.getBlockPos().relative(m.facing().getOpposite(), 3); Level level = m.getLevel();
        if (!level.hasChunksAt(centre.offset(-3,-3,-3),centre.offset(3,3,3))) return false;
        for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++) {
            BlockPos p = centre.offset(x,y,z); if (p.equals(m.getBlockPos())) continue; BlockState state = level.getBlockState(p);
            boolean edgeX = Math.abs(x) == 3, edgeY = Math.abs(y) == 3, edgeZ = Math.abs(z) == 3;
            if (edgeY && (edgeX || edgeZ) || edgeX && edgeZ) { if (!(state.getBlock() instanceof IStableBlock)) return false; }
            else if (edgeX || edgeY || edgeZ) { if (!(state.getBlock() instanceof IStableGlass)) return false; }
            else if (!state.isAir()) return false;
        }
        return true;
    }
    static List<MachineBlockEntity> connectedStorage(MachineBlockEntity m) {
        List<MachineBlockEntity> result = new ArrayList<>(); Set<BlockPos> seen = new HashSet<>(); ArrayDeque<BlockPos> queue = new ArrayDeque<>(); queue.add(m.getBlockPos()); seen.add(m.getBlockPos());
        while (!queue.isEmpty() && result.size() < 256) {
            BlockPos current = queue.remove();
            for (Direction side : Direction.values()) {
                BlockPos next = current.relative(side); if (!seen.add(next)) continue;
                MachineBlockEntity storage = machine(m.getLevel(), next, "storagechamber");
                if (storage != null) { result.add(storage); queue.add(next); }
                if (result.size() == 256) break;
            }
        }
        return result;
    }
    static int auxiliaryHeight(String id) { return switch (id) { case "conductormast" -> 3; case "scarecrow" -> 2; case "weatherstation", "transmitter" -> 1; default -> 0; }; }
    static void placeAuxiliary(MachineBlockEntity m) {
        int height = auxiliaryHeight(m.id()); if (height == 0) return;
        for (int y = 1; y <= height; y++) if (!m.getLevel().hasChunkAt(m.getBlockPos().above(y)) || !m.getLevel().getBlockState(m.getBlockPos().above(y)).canBeReplaced()) return;
        for (int y = 1; y <= height; y++) {
            BlockPos part = m.getBlockPos().above(y);
            m.getLevel().setBlock(part, CalculatorContent.block(m.id() + "block").defaultBlockState().setValue(MachineBlock.FACING, m.facing()), Block.UPDATE_ALL);
            if (m.getLevel().getBlockEntity(part) instanceof MachineBlockEntity auxiliary) {
                auxiliary.state.putLong("Controller", m.getBlockPos().asLong());
                auxiliary.setChanged(); auxiliary.sync();
            }
        }
    }
    static MachineBlockEntity controller(MachineBlockEntity part) {
        if (!isAuxiliary(part.id())) return part;
        Level level = part.getLevel(); if (level == null || part.isRemoved()) return null;
        String base = baseOf(part.id());
        BlockPos origin = part.getBlockPos();
        for (int y = 1; y <= auxiliaryHeight(base); y++) {
            BlockPos candidate = origin.below(y);
            if (!level.hasChunkAt(candidate)) return null;
            if (part.state.contains("Controller") && part.state.getLong("Controller") != candidate.asLong()) continue;
            MachineBlockEntity main = machine(level, candidate, base);
            if (main == null || main.isRemoved()) continue;
            boolean owned = true;
            for (int i = 1; i <= y; i++) {
                if (!(level.getBlockEntity(candidate.above(i)) instanceof MachineBlockEntity link) || !link.id().equals(base + "block")
                        || link.state.contains("Controller") && link.state.getLong("Controller") != candidate.asLong()) { owned = false; break; }
            }
            if (owned) return main;
        }
        return null;
    }
    static void removeAuxiliary(MachineBlockEntity m) {
        for (BlockPos pos : towerParts(m.getLevel(), m.getBlockPos(), m.id())) {
            if (!pos.equals(m.getBlockPos()) && m.getLevel().hasChunkAt(pos)) m.getLevel().removeBlock(pos, false);
        }
    }

    // --- #13 multiblock dismantling -------------------------------------------------------------------
    /** Positions whose removal is currently being orchestrated by {@link #dismantle}, to stop re-entrant cleanup. */
    private static final ThreadLocal<Player> REMOVING_PLAYER = new ThreadLocal<>();
    static boolean playerRemoval(Player player, java.util.function.BooleanSupplier action) {
        Player previous = REMOVING_PLAYER.get();
        REMOVING_PLAYER.set(player);
        try { return action.getAsBoolean(); }
        finally { if (previous == null) REMOVING_PLAYER.remove(); else REMOVING_PLAYER.set(previous); }
    }
    private static final ThreadLocal<Set<Long>> DISMANTLING = ThreadLocal.withInitial(HashSet::new);
    static boolean cleaning(BlockPos pos) { return DISMANTLING.get().contains(pos.asLong()); }
    /** Strips the placeholder suffix: "conductormastblock" -> "conductormast". */
    private static String baseOf(String id) { return id.endsWith("block") ? id.substring(0, id.length() - "block".length()) : id; }
    /** True when the identifier is a decorative sub-block of a height multiblock rather than a standalone machine. */
    static boolean isAuxiliary(String id) { return id.endsWith("block") && auxiliaryHeight(baseOf(id)) > 0; }

    /**
     * The ordered column of a height multiblock: element 0 is the functional machine, the remaining entries are
     * its placeholder sub-blocks. A standalone machine (or an orphaned sub-block with no machine beneath it)
     * yields a single position, so a caller can treat {@code size <= 1} as "there is no structure to clean up".
     * Every probe stays inside the source block's chunk column, so no chunk is ever force-loaded.
     */
    static List<BlockPos> towerParts(Level level, BlockPos pos, String id) {
        String base = baseOf(id);
        int height = auxiliaryHeight(base);
        if (height == 0) return List.of(pos);
        BlockPos main;
        if (id.equals(base)) {
            main = pos;
        } else {
            // The part being dismantled is already gone, so the column can contain a hole where it used
            // to be. Look for the functional machine within the column height below the broken part
            // instead of trusting that the placeholder blocks are still contiguous (#13: breaking any
            // part must clean the whole column).
            main = null;
            for (int y = 1; y <= height && main == null; y++) {
                BlockPos candidate = pos.below(y);
                if (!level.hasChunkAt(candidate)) break;
                if (level.getBlockEntity(pos) instanceof MachineBlockEntity broken && broken.state.contains("Controller") && broken.state.getLong("Controller") != candidate.asLong()) continue;
                if (!(level.getBlockEntity(candidate) instanceof MachineBlockEntity owner) || !owner.id().equals(base)) continue;
                // A legacy untagged column must still be contiguous up to the removed source.
                boolean valid = true;
                for (int step = 1; step < y; step++) {
                    if (!(level.getBlockEntity(candidate.above(step)) instanceof MachineBlockEntity link) || !link.id().equals(base + "block")
                            || link.state.contains("Controller") && link.state.getLong("Controller") != candidate.asLong()) { valid = false; break; }
                }
                if (valid) main = candidate;
            }
            if (main == null) return List.of(pos);
        }
        List<BlockPos> parts = new ArrayList<>(height + 1);
        parts.add(main);
        String aux = base + "block";
        for (int y = 1; y <= height; y++) {
            BlockPos p = main.above(y);
            // The source has already changed state in LevelChunk.onRemove. It is the only allowed hole;
            // other gaps or foreign-owned placeholders terminate this structure's column.
            if (!level.hasChunkAt(p)) break;
            if (p.equals(pos)) continue;
            if (!MachineBlockEntity.blockId(level.getBlockState(p)).equals(aux)) break;
            if (level.getBlockEntity(p) instanceof MachineBlockEntity link && link.state.contains("Controller") && link.state.getLong("Controller") != main.asLong()) break;
            parts.add(p);
        }
        return parts;
    }

    /**
     * Removes every part of the height multiblock that owns {@code pos}. The functional machine's inventory is
     * dropped exactly once; if the player broke a placeholder rather than the machine, the machine item itself is
     * returned exactly once so nothing is lost. Only blocks whose live id still matches the expected placeholder
     * id are touched, so a neighbouring player build is never removed. {@link MachineBlockEntity#clearForRemoval}
     * plus the drop guard make repeated cleanup idempotent, and the whole column shares the broken block's chunk,
     * so unloaded chunks are never touched.
     */
    static void dismantle(Level level, BlockPos pos, MachineBlockEntity broken) {
        List<BlockPos> parts = towerParts(level, pos, broken.id());
        if (parts.size() == 1 && parts.get(0).equals(pos)) { broken.dropContents(); return; }
        BlockPos mainPos = parts.get(0);
        MachineBlockEntity main = level.getBlockEntity(mainPos) instanceof MachineBlockEntity m ? m : null;
        boolean brokeMain = mainPos.equals(pos);
        Set<Long> guard = DISMANTLING.get();
        List<Long> marked = new ArrayList<>(parts.size());
        try {
            for (BlockPos part : parts) { marked.add(part.asLong()); guard.add(part.asLong()); }
            if (main != null) {
                main.dropContents(); // idempotent (suppressDrops), so the machine's own onRemove cannot drop twice
                if (!brokeMain) {
                    // A placeholder was broken, not the functional machine: return the machine item exactly once
                    // and destroy the owning machine too, so no orphan machine block is left standing (#13). The
                    // re-entrant guard above makes this extra removal skip cleanup instead of looping.
                    Player player = REMOVING_PLAYER.get();
                    if (player == null || !player.isCreative()) Block.popResource(level, mainPos, new ItemStack(main.getBlockState().getBlock()));
                    if (level.hasChunkAt(mainPos)) level.removeBlock(mainPos, false);
                }
            }
            String aux = baseOf(broken.id()) + "block";
            for (BlockPos part : parts) {
                if (part.equals(pos) || !level.hasChunkAt(part)) continue;
                if (!MachineBlockEntity.blockId(level.getBlockState(part)).equals(aux)) continue; // never remove foreign blocks
                if (level.getBlockEntity(part) instanceof MachineBlockEntity be) be.clearForRemoval();
                level.removeBlock(part, false);
            }
        } finally {
            for (Long mark : marked) guard.remove(mark);
        }
    }
}
