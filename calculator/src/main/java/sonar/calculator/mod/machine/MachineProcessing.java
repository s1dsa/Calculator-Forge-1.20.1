package sonar.calculator.mod.machine;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;
import sonar.calculator.mod.CalculatorConfig;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.recipe.CalculatorRecipes;
import sonar.calculator.mod.recipe.MachineRecipe;

/** Processing transactions never mutate recipe-owned or simulated stacks. */
final class MachineProcessing {
    private MachineProcessing() {}
    static void tick(MachineBlockEntity m) {
        if (m.state.getBoolean("paused")) { m.setActive(false); return; }
        if (m.id().endsWith("extractor")) { extractor(m); return; }
        switch (m.id()) {
            case "analysingchamber" -> analyse(m);
            case "atomicmultiplier" -> multiply(m);
            case "fabricationchamber" -> fabricate(m);
            case "dynamiccalculator" -> dynamic(m);
            default -> { if (!m.spec.recipe().isEmpty()) process(m); }
        }
    }
    static int value(MachineBlockEntity m, String type, ItemStack stack) {
        if (m.getLevel() == null || stack.isEmpty()) return 0;
        MachineRecipe recipe = CalculatorRecipes.find(m.getLevel(), type, List.of(stack)); return recipe == null ? 0 : recipe.getValue();
    }
    static int dockingInputs(MachineBlockEntity m) {
        String id = MachineBlockEntity.itemId(m.inventory.getStackInSlot(6));
        return switch (id) { case "calculator", "scientificcalculator" -> 2; case "atomiccalculator" -> 3; case "flawlesscalculator" -> 4; default -> 0; };
    }
    private static String recipeType(MachineBlockEntity m) {
        if (!m.id().equals("dockingstation")) return m.spec.recipe();
        return switch (MachineBlockEntity.itemId(m.inventory.getStackInSlot(6))) { case "calculator" -> "calculator"; case "scientificcalculator" -> "scientific"; case "atomiccalculator" -> "atomic"; case "flawlesscalculator" -> "flawless"; default -> ""; };
    }
    private static List<ItemStack> inputs(MachineBlockEntity m, int count) {
        List<ItemStack> list = new ArrayList<>(); for (int i = 0; i < count; i++) list.add(m.inventory.getStackInSlot(i).copy()); return list;
    }
    private static void process(MachineBlockEntity m) {
        String type = recipeType(m); if (type.isEmpty()) { reset(m); return; }
        int count = m.id().equals("dockingstation") ? dockingInputs(m) : m.spec.inputs();
        List<ItemStack> inputs = inputs(m, count); List<ItemStack> outputs;
        MachineRecipe recipe = null;
        if (type.equals("smelting")) {
            var smelting = m.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(inputs.get(0)), m.getLevel());
            if (smelting.isEmpty()) { reset(m); return; }
            outputs = List.of(smelting.get().getResultItem(m.getLevel().registryAccess()).copy());
        } else {
            recipe = CalculatorRecipes.find(m.getLevel(), type, inputs);
            if (recipe == null) { reset(m); return; }
            outputs = recipe.getOutputs();
        }
        int baseTime = m.spec.configured("SPEED", m.spec.duration());
        int speed = m.upgradeCount(0), efficiency = m.upgradeCount(1);
        m.processTime = speed == 16 ? 8 : Math.max(4, (int)(baseTime - speed / 17.0 * baseTime));
        if (m.id().equals("dockingstation")) m.processTime = Math.max(1, m.processTime / 8);
        if (m.id().equals("atomiccalculator")) m.processTime = 1;
        int baseCost = m.spec.configured("USAGE", m.spec.cost());
        int cost = (int)(baseCost + (speed - efficiency) / 16.0F * baseCost);
        if (m.id().equals("dockingstation")) cost = 10;
        if (m.id().equals("conductormast") && recipe != null) cost = recipe.getValue();
        if (!fits(m, outputs) || m.progress == 0 && m.energy.getEnergyStored() < cost) { reset(m); return; }
        if (m.progress < m.processTime) {
            int oldPaid = (int)((long)cost * m.progress / m.processTime);
            int newPaid = (int)((long)cost * (m.progress + 1) / m.processTime);
            int debit = newPaid - oldPaid;
            if (m.energy.getEnergyStored() < debit) { m.setActive(false); return; }
            m.energy.consume(debit); m.progress++; m.setActive(true); return;
        }
        List<ItemStack> results = recipe == null ? outputs : recipe.rollOutputs(m.getLevel().random);
        if (!fits(m, results)) { m.setActive(false); return; }
        if (recipe == null) inputs.get(0).shrink(1);
        else if (!recipe.consumeInputs(inputs)) { reset(m); return; }
        for (int i = 0; i < count; i++) m.inventory.setStackInSlot(i, inputs.get(i));
        commit(m, results); reset(m);
    }
    private static void dynamic(MachineBlockEntity m) {
        if (!MachineEnvironment.dynamicStructure(m)) return;
        craftSlots(m, "calculator", new int[]{0,1}, 2);
        craftSlots(m, "scientific", new int[]{3,4}, 5);
        craftSlots(m, "atomic", new int[]{6,7,8}, 9);
    }
    private static void craftSlots(MachineBlockEntity m, String type, int[] slots, int output) {
        List<ItemStack> input = new ArrayList<>(); for (int slot : slots) input.add(m.inventory.getStackInSlot(slot).copy());
        MachineRecipe recipe = CalculatorRecipes.find(m.getLevel(), type, input); if (recipe == null) return;
        List<ItemStack> outputs = recipe.getOutputs(); if (outputs.isEmpty() || !fitsSlot(m, output, outputs.get(0))) return;
        if (!recipe.consumeInputs(input)) return;
        for (int i = 0; i < slots.length; i++) m.inventory.setStackInSlot(slots[i], input.get(i));
        merge(m, output, outputs.get(0));
    }
    private static boolean fits(MachineBlockEntity m, List<ItemStack> outputs) {
        if (outputs.size() > m.spec.outputs()) return false;
        for (int i = 0; i < outputs.size(); i++) if (!fitsSlot(m, m.spec.outputStart() + i, outputs.get(i))) return false;
        return true;
    }
    private static boolean fitsSlot(MachineBlockEntity m, int slot, ItemStack result) {
        if (result.isEmpty()) return true;
        ItemStack present = m.inventory.getStackInSlot(slot);
        return (present.isEmpty() || ItemStack.isSameItemSameTags(present, result)) && present.getCount() + result.getCount() <= result.getMaxStackSize();
    }
    private static void commit(MachineBlockEntity m, List<ItemStack> outputs) {
        for (int i = 0; i < outputs.size(); i++) {
            if (i == 0 && m.upgradeCount(3) > 0 && (m.id().equals("extractionchamber") || m.id().equals("precisionchamber"))) continue;
            ItemStack result = outputs.get(i).copy(); initializeCircuit(result, m);
            merge(m, m.spec.outputStart() + i, result);
        }
    }
    static void initializeCircuit(ItemStack stack, MachineBlockEntity m) {
        if (!MachineBlockEntity.circuit(stack) || stack.hasTag()) return;
        CompoundTag tag = stack.getOrCreateTag(); int[] ranges = {50,100,1000,2000,10000,20000};
        tag.putInt("Energy", 1 + m.getLevel().random.nextInt(200));
        for (int i = 0; i < ranges.length; i++) tag.putInt("Item" + (i + 1), 1 + m.getLevel().random.nextInt(ranges[i]));
        tag.putInt("Stable", 1 + m.getLevel().random.nextInt(6));
    }
    private static void merge(MachineBlockEntity m, int slot, ItemStack result) {
        if (result.isEmpty()) return;
        ItemStack current = m.inventory.getStackInSlot(slot).copy();
        if (current.isEmpty()) current = result.copy(); else current.grow(result.getCount());
        m.inventory.setStackInSlot(slot, current);
    }
    private static void reset(MachineBlockEntity m) { m.progress = 0; m.setActive(false); }
    static boolean canMultiply(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() < 4) return false;
        String key = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        return !CalculatorConfig.ATOMIC_BLACKLIST.get().contains(key);
    }
    private static void multiply(MachineBlockEntity m) {
        ItemStack input = m.inventory.getStackInSlot(0);
        if (!canMultiply(input)) { reset(m); return; }
        for (int i = 1; i < 8; i++) if (!MachineBlockEntity.circuit(m.inventory.getStackInSlot(i))) { reset(m); return; }
        // Legacy multiplication intentionally creates a fresh stack, not a duplicate of input NBT.
        ItemStack result = new ItemStack(input.getItem(), 4); if (input.isDamageableItem()) result.setDamageValue(input.getDamageValue());
        m.processTime = CalculatorConfig.ATOMIC_MULTIPLIER_SPEED;
        if (!fitsSlot(m, 8, result) || m.progress == 0 && m.energy.getEnergyStored() < CalculatorConfig.ATOMIC_MULTIPLIER_USAGE) { reset(m); return; }
        int cost = CalculatorConfig.ATOMIC_MULTIPLIER_USAGE / Math.max(1, m.processTime);
        if (m.energy.getEnergyStored() < cost) { reset(m); return; }
        m.energy.consume(cost); m.progress++; m.setActive(true);
        if (m.progress >= m.processTime) { for (int i = 0; i < 8; i++) m.inventory.extractItem(i, 1, false); merge(m, 8, result); reset(m); }
    }
    private static void analyse(MachineBlockEntity m) {
        ItemStack circuit = m.inventory.getStackInSlot(0);
        m.chargeItem(1, m.spec.configuredTransfer());
        if (!MachineBlockEntity.circuit(circuit)) return;
        initializeCircuit(circuit, m); CompoundTag tag = circuit.getOrCreateTag(); if (tag.getBoolean("Analysed")) return;
        for (int i = 2; i < 8; i++) if (!m.inventory.getStackInSlot(i).isEmpty()) return;
        int[] energy = {0,1000,500,250,10000,5000,100000,100,175,400,750,800};
        int index = tag.getInt("Energy"); if (index >= 0 && index < energy.length) m.energy.generate(energy[index]);
        for (int i = 1; i <= 6; i++) {
            MachineRecipe recipe = CalculatorRecipes.findValues(m.getLevel(), "analysing", i, tag.getInt("Item" + i));
            if (recipe != null && (i > 2 || m.upgradeCount(3) == 0) && !recipe.getOutputs().isEmpty()) {
                ItemStack output = recipe.getOutputs().get(0).copy(); if (!output.isEmpty()) output.setCount(1); m.inventory.setStackInSlot(i + 1, output);
            }
            tag.remove("Item" + i);
        }
        tag.remove("Energy"); tag.putBoolean("Analysed", true); if (tag.getInt("Stable") != 1) tag.putInt("Stable", 0);
        m.inventory.setStackInSlot(0, circuit.copy());
    }
    private static void extractor(MachineBlockEntity m) {
        int level = m.state.getInt("itemLevel"); int capacity = m.spec.configured("GENERATOR_CAPACITY", 5000);
        int add = value(m, m.spec.recipe(), m.inventory.getStackInSlot(1));
        if (add > 0 && (long)level + add <= capacity) { m.inventory.extractItem(1, 1, false); level += add; }
        int required = m.spec.configured("GENERATOR_REQUIRED", 400);
        int burn = m.state.getInt("burnTime"), max = m.state.getInt("maxBurnTime");
        if (burn == 0 && level >= required && m.energy.getEnergyStored() < m.energy.getMaxEnergyStored()) {
            int duration = net.minecraftforge.common.ForgeHooks.getBurnTime(m.inventory.getStackInSlot(0), RecipeType.SMELTING);
            if (duration > 0) { max = duration; burn = 1; m.consumeOne(0); }
        }
        if (burn > 0) {
            int rate = m.spec.configured("PER_TICK", m.id().equals("starchextractor") ? 40 : m.id().equals("redstoneextractor") ? 80 : 160);
            if (burn < max) { m.energy.generate(rate); burn++; }
            if (burn >= max) { m.energy.generate(rate); burn = 0; level = Math.max(0, level - required); }
        }
        m.state.putInt("itemLevel", level); m.state.putInt("burnTime", burn); m.state.putInt("maxBurnTime", max);
        m.progress = burn; m.processTime = Math.max(1, max); m.setActive(burn > 0);
    }
    static List<MachineRecipe> fabricationRecipes(MachineBlockEntity m) {
        return m.getLevel().getRecipeManager().getAllRecipesFor(sonar.calculator.mod.recipe.RecipeContent.type("fabrication")).stream()
                .sorted(java.util.Comparator.comparingInt(MachineRecipe::getOrder).thenComparing(recipe -> recipe.getId().toString()))
                .limit(MachineMenu.FABRICATION_BUTTON_LIMIT).toList();
    }
    static ItemStack fabricationTarget(MachineBlockEntity m) {
        return ItemStack.of(m.state.getCompound("selected"));
    }
    static MachineRecipe selectedFabricationRecipe(MachineBlockEntity m) {
        ItemStack selected = fabricationTarget(m);
        if (selected.isEmpty()) return null;
        // Resolve against the current manager on every operation, including after a data-pack reload.
        return fabricationRecipes(m).stream().filter(recipe -> !recipe.getOutputs().isEmpty()
                && ItemStack.isSameItemSameTags(selected, recipe.getOutputs().get(0))
                && selected.getCount() == recipe.getOutputs().get(0).getCount()).findFirst().orElse(null);
    }
    static boolean selectFabricationTarget(MachineBlockEntity m, int index) {
        List<MachineRecipe> recipes = fabricationRecipes(m);
        if (index < 0 || index >= recipes.size()) return false;
        ItemStack output = recipes.get(index).getOutputs().stream().findFirst().orElse(ItemStack.EMPTY);
        if (output.isEmpty()) return false;
        m.state.put("selected", output.save(new CompoundTag()));
        m.setChanged(); m.sync(); return true;
    }
    /** Plan all circuit debits on copies, including counts split between connected chambers. */
    private static List<List<ItemStack>> fabricationPlan(List<MachineBlockEntity> chambers, MachineRecipe recipe) {
        List<List<ItemStack>> plan = new ArrayList<>();
        for (MachineBlockEntity chamber : chambers) {
            List<ItemStack> stacks = new ArrayList<>();
            for (int slot = 0; slot < chamber.inventory.getSlots(); slot++) stacks.add(chamber.inventory.getStackInSlot(slot).copy());
            plan.add(stacks);
        }
        for (MachineRecipe.Input input : recipe.getInputs()) {
            int needed = input.count();
            for (List<ItemStack> stacks : plan) for (ItemStack stack : stacks) {
                if (needed == 0) break;
                if (!MachineBlockEntity.circuit(stack) || !input.ingredient().test(stack)
                        || input.exactNbt() != null && !java.util.Objects.equals(input.exactNbt(), stack.getTag())) continue;
                int taken = Math.min(needed, stack.getCount()); stack.shrink(taken); needed -= taken;
            }
            if (needed != 0) return null;
        }
        return plan;
    }
    static boolean startFabrication(MachineBlockEntity m) {
        if (m.state.getBoolean("fabricating")) return false;
        MachineRecipe recipe = selectedFabricationRecipe(m);
        if (recipe == null || fabricationPlan(MachineEnvironment.connectedStorage(m), recipe) == null) return false;
        m.state.putBoolean("fabricating", true); m.progress = 0; return true;
    }
    private static void fabricate(MachineBlockEntity m) {
        if (!m.state.getBoolean("fabricating")) return;
        m.processTime = 300; m.progress++;
        // Fifty ticks travelling out, two hundred fabricating in position, fifty returning.
        m.state.putInt("currentMoveTime", m.progress <= 50 ? m.progress : m.progress <= 250 ? 50 : Math.max(0, 300 - m.progress));
        m.setActive(true); if (m.progress < 250) return;
        if (m.progress == 250) {
            List<MachineBlockEntity> chambers = MachineEnvironment.connectedStorage(m);
            MachineRecipe recipe = selectedFabricationRecipe(m);
            if (recipe != null && !recipe.getOutputs().isEmpty() && fitsSlot(m, 0, recipe.getOutputs().get(0))) {
                List<List<ItemStack>> plan = fabricationPlan(chambers, recipe);
                if (plan != null) {
                    for (int chamber = 0; chamber < chambers.size(); chamber++) for (int slot = 0; slot < plan.get(chamber).size(); slot++)
                        chambers.get(chamber).inventory.setStackInSlot(slot, plan.get(chamber).get(slot));
                    merge(m, 0, recipe.getOutputs().get(0));
                }
            }
        }
        if (m.progress >= 300) { m.state.putBoolean("fabricating", false); reset(m); }
    }
}
