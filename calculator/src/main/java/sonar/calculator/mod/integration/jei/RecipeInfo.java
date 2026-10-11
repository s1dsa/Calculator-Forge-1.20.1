package sonar.calculator.mod.integration.jei;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.RecipeSerializer;
import sonar.calculator.mod.recipe.MachineRecipe;

/**
 * Read-only projection of a {@link MachineRecipe} definition for display.
 *
 * <p>{@code MachineRecipe} is intentionally read-only for this integration and does not expose the
 * per-output probability or the randomised choice variants through its public API. This adapter
 * recovers the original data-pack JSON through the recipe's own public serializer
 * ({@link MachineRecipe#getSerializer()} + {@link RecipeSerializer#toNetwork}), which is the exact
 * payload the server already synchronises to the client. Nothing is written back and no recipe
 * class is modified; when the definition cannot be recovered the adapter degrades to "no extra
 * notes" instead of failing the JEI reload.
 */
public final class RecipeInfo {
    private static final RecipeInfo EMPTY = new RecipeInfo(null);

    @Nullable
    private final JsonObject definition;

    private RecipeInfo(@Nullable JsonObject definition) {
        this.definition = definition;
    }

    public static RecipeInfo of(MachineRecipe recipe) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            @SuppressWarnings("unchecked")
            RecipeSerializer<MachineRecipe> serializer = (RecipeSerializer<MachineRecipe>) recipe.getSerializer();
            serializer.toNetwork(buffer, recipe);
            JsonObject json = JsonParser.parseString(buffer.readUtf(1048576)).getAsJsonObject();
            return new RecipeInfo(json);
        } catch (RuntimeException exception) {
            return EMPTY;
        } finally {
            buffer.release();
        }
    }

    private JsonArray results() {
        if (definition == null || !definition.has("results")) return new JsonArray();
        JsonElement results = definition.get("results");
        return results.isJsonArray() ? results.getAsJsonArray() : new JsonArray();
    }

    @Nullable
    private JsonObject result(int index) {
        JsonArray results = results();
        if (index < 0 || index >= results.size()) return null;
        JsonElement element = results.get(index);
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /** {@code chance_bound} of the legacy random output, or 0 when the output is guaranteed. */
    public int chanceBound(int index) {
        JsonObject result = result(index);
        return result != null && result.has("chance_bound") ? result.get("chance_bound").getAsInt() : 0;
    }

    /** {@code chance_equals} compared against {@code nextInt(chance_bound)}; only meaningful when bound &gt; 0. */
    public int chanceEquals(int index) {
        JsonObject result = result(index);
        return result != null && result.has("chance_equals") ? result.get("chance_equals").getAsInt() : 0;
    }

    /** Number of distinct stacks a randomised output may roll (0 or 1 means a single deterministic stack). */
    public int choiceCount(int index) {
        JsonObject result = result(index);
        if (result == null || !result.has("choices")) return 0;
        JsonElement choices = result.get("choices");
        return choices.isJsonArray() ? choices.getAsJsonArray().size() : 0;
    }

    /** {@code input_values} for numeric recipes (analysing chamber), or an empty array. */
    public int[] inputValues() {
        if (definition == null || !definition.has("input_values")) return new int[0];
        JsonElement values = definition.get("input_values");
        if (!values.isJsonArray()) return new int[0];
        JsonArray array = values.getAsJsonArray();
        int[] result = new int[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsInt();
        return result;
    }
}
