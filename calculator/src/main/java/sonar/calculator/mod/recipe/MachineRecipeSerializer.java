package sonar.calculator.mod.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.ForgeRegistries;

/** The same validated schema is used for data loading and server-to-client synchronization. */
public final class MachineRecipeSerializer implements RecipeSerializer<MachineRecipe> {
    private final String machineType;
    public MachineRecipeSerializer(String machineType) { this.machineType = machineType; }

    @Override public MachineRecipe fromJson(ResourceLocation id, JsonObject json) {
        List<MachineRecipe.Input> inputs = new ArrayList<>();
        for (JsonElement element : array(json, "ingredients")) {
            JsonObject input = element.getAsJsonObject();
            inputs.add(new MachineRecipe.Input(Ingredient.fromJson(input.get("ingredient")),
                    positive(input, "count", 1), input.has("nbt") ? tag(input.get("nbt")) : null));
        }
        List<MachineRecipe.Output> outputs = new ArrayList<>();
        for (JsonElement element : array(json, "results")) {
            JsonObject output = element.getAsJsonObject();
            ItemStack preview = stack(output);
            List<ItemStack> choices = new ArrayList<>();
            for (JsonElement choice : array(output, "choices")) choices.add(stack(choice.getAsJsonObject()));
            int bound = GsonHelper.getAsInt(output, "chance_bound", 0);
            int target = GsonHelper.getAsInt(output, "chance_equals", 0);
            if (bound < 0 || target < 0 || bound > 0 && target >= bound)
                throw new JsonSyntaxException("Invalid integer output probability in " + id);
            outputs.add(new MachineRecipe.Output(preview, choices, bound, target));
        }
        JsonArray values = array(json, "input_values");
        int[] inputValues = new int[values.size()];
        for (int i = 0; i < values.size(); i++) inputValues[i] = values.get(i).getAsInt();
        if (inputs.isEmpty() == (inputValues.length == 0))
            throw new JsonSyntaxException("Exactly one of ingredients/input_values is required: " + id);
        return new MachineRecipe(id, machineType, inputs, outputs, inputValues,
                nonNegative(json, "value"), nonNegative(json, "energy"), nonNegative(json, "process_time"),
                GsonHelper.getAsInt(json, "order", 0), GsonHelper.getAsBoolean(json, "shapeless", false),
                GsonHelper.getAsBoolean(json, "allow_extra_inputs", false), json);
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) ? GsonHelper.getAsJsonArray(object, key) : new JsonArray();
    }
    private static int positive(JsonObject object, String key, int fallback) {
        int value = GsonHelper.getAsInt(object, key, fallback);
        if (value < 1) throw new JsonSyntaxException(key + " must be positive");
        return value;
    }
    private static int nonNegative(JsonObject object, String key) {
        int value = GsonHelper.getAsInt(object, key, 0);
        if (value < 0) throw new JsonSyntaxException(key + " cannot be negative");
        return value;
    }
    private static CompoundTag tag(JsonElement element) {
        try { return TagParser.parseTag(element.isJsonPrimitive() ? element.getAsString() : element.toString()); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { throw new JsonSyntaxException("Invalid recipe NBT", exception); }
    }
    private static ItemStack stack(JsonObject json) {
        ResourceLocation itemId = new ResourceLocation(GsonHelper.getAsString(json, "item"));
        if (!ForgeRegistries.ITEMS.containsKey(itemId)) throw new JsonSyntaxException("Unknown recipe item " + itemId);
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(itemId), positive(json, "count", 1));
        if (stack.isEmpty()) throw new JsonSyntaxException("Empty recipe output " + itemId);
        if (json.has("nbt")) stack.setTag(tag(json.get("nbt")));
        return stack;
    }
    @Override public MachineRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
        return fromJson(id, JsonParser.parseString(buffer.readUtf(1048576)).getAsJsonObject());
    }
    @Override public void toNetwork(FriendlyByteBuf buffer, MachineRecipe recipe) {
        buffer.writeUtf(recipe.definition.toString(), 1048576);
    }
}
