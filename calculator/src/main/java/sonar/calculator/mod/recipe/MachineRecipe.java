package sonar.calculator.mod.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import com.google.gson.JsonObject;

/** Data-pack machine recipe. Inspection never consumes items or advances the random source. */
public final class MachineRecipe implements Recipe<Container> {
    public record Input(Ingredient ingredient, int count, @Nullable CompoundTag exactNbt) {
        public Input {
            if (count < 1) throw new IllegalArgumentException("Input count must be positive");
            exactNbt = exactNbt == null ? null : exactNbt.copy();
        }
        boolean test(ItemStack stack) {
            return !stack.isEmpty() && stack.getCount() >= count && ingredient.test(stack)
                    && (exactNbt == null || Objects.equals(exactNbt, stack.getTag()));
        }
        @Override public CompoundTag exactNbt() { return exactNbt == null ? null : exactNbt.copy(); }
    }

    static final class Output {
        final ItemStack preview;
        final List<ItemStack> choices;
        final int chanceBound, chanceEquals;
        Output(ItemStack preview, List<ItemStack> choices, int chanceBound, int chanceEquals) {
            this.preview = preview.copy();
            this.choices = choices.stream().map(ItemStack::copy).toList();
            this.chanceBound = chanceBound;
            this.chanceEquals = chanceEquals;
        }
        ItemStack roll(RandomSource random) {
            // Legacy extraction first rolls nextInt(9)==8, then (only on success) nextInt(14).
            if (chanceBound > 0 && random.nextInt(chanceBound) != chanceEquals) return ItemStack.EMPTY;
            return choices.isEmpty() ? preview.copy() : choices.get(random.nextInt(choices.size())).copy();
        }
    }

    private final ResourceLocation id;
    private final String machineType;
    private final List<Input> inputs;
    private final List<Output> outputs;
    private final int[] inputValues;
    private final int value, energy, processTime, order;
    private final boolean shapeless, allowExtraInputs;
    final JsonObject definition;

    MachineRecipe(ResourceLocation id, String machineType, List<Input> inputs, List<Output> outputs,
                  int[] inputValues, int value, int energy, int processTime, int order,
                  boolean shapeless, boolean allowExtraInputs, JsonObject definition) {
        this.id = id;
        this.machineType = machineType;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.inputValues = inputValues.clone();
        this.value = value;
        this.energy = energy;
        this.processTime = processTime;
        this.order = order;
        this.shapeless = shapeless;
        this.allowExtraInputs = allowExtraInputs;
        this.definition = definition.deepCopy();
    }

    public String getMachineType() { return machineType; }
    public int getValue() { return value; }
    /** Zero means use the machine's configuration. */
    public int getEnergy() { return energy; }
    /** Zero means use the machine's configuration. */
    public int getProcessTime() { return processTime; }
    public int getOrder() { return order; }
    public int[] getInputValues() { return inputValues.clone(); }
    public List<Input> getInputs() { return inputs; }
    public boolean matchesValues(int... values) { return inputValues.length > 0 && Arrays.equals(inputValues, values); }

    /** All returned stacks are independent copies, including their NBT. */
    public List<ItemStack> getOutputs() { return outputs.stream().map(output -> output.preview.copy()).toList(); }
    /** Call once when a machine commits a completed operation; retain the resulting list if output is blocked. */
    public List<ItemStack> rollOutputs(RandomSource random) {
        List<ItemStack> result = new ArrayList<>(outputs.size());
        for (Output output : outputs) result.add(output.roll(random));
        return result;
    }

    @Nullable private int[] assignment(List<ItemStack> stacks) {
        if (inputs.isEmpty() || inputValues.length != 0) return null;
        int occupied = 0;
        for (ItemStack stack : stacks) if (!stack.isEmpty()) occupied++;
        if (occupied < inputs.size() || !allowExtraInputs && occupied != inputs.size()) return null;
        int[] slots = new int[inputs.size()];
        Arrays.fill(slots, -1);
        if (!shapeless) {
            if (stacks.size() < inputs.size()) return null;
            for (int i = 0; i < inputs.size(); i++) {
                if (!inputs.get(i).test(stacks.get(i))) return null;
                slots[i] = i;
            }
            return slots;
        }
        return assign(0, stacks, new boolean[stacks.size()], slots) ? slots : null;
    }

    private boolean assign(int index, List<ItemStack> stacks, boolean[] used, int[] slots) {
        if (index == inputs.size()) return true;
        for (int i = 0; i < stacks.size(); i++) {
            if (used[i] || !inputs.get(index).test(stacks.get(i))) continue;
            // A caller may accidentally provide the same mutable stack twice; never count it twice.
            boolean aliased = false;
            for (int j = 0; j < index; j++) if (stacks.get(slots[j]) == stacks.get(i)) aliased = true;
            if (aliased) continue;
            used[i] = true;
            slots[index] = i;
            if (assign(index + 1, stacks, used, slots)) return true;
            used[i] = false;
            slots[index] = -1;
        }
        return false;
    }

    public boolean matches(List<ItemStack> stacks) { return assignment(stacks) != null; }
    /** Validates every ingredient, count and NBT before changing any stack. */
    public boolean consumeInputs(List<ItemStack> stacks) {
        int[] slots = assignment(stacks);
        if (slots == null) return false;
        for (int i = 0; i < slots.length; i++) stacks.get(slots[i]).shrink(inputs.get(i).count());
        return true;
    }

    @Override public boolean matches(Container container, Level level) {
        List<ItemStack> stacks = new ArrayList<>(container.getContainerSize());
        for (int i = 0; i < container.getContainerSize(); i++) stacks.add(container.getItem(i));
        return matches(stacks);
    }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return getResultItem(access); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= inputs.size(); }
    @Override public ItemStack getResultItem(RegistryAccess access) { return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).preview.copy(); }
    @Override public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> result = NonNullList.create();
        for (Input input : inputs) result.add(input.ingredient());
        return result;
    }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return RecipeContent.serializer(machineType); }
    @Override public RecipeType<?> getType() { return RecipeContent.type(machineType); }
    @Override public boolean isSpecial() { return true; }
}
