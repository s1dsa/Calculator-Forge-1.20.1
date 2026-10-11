package sonar.calculator.mod.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import sonar.calculator.mod.recipe.MachineRecipe;
import sonar.calculator.mod.recipe.RecipeContent;

@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
public final class RegistryGameTests {
    @GameTest(template = "test_empty")
    public static void actualRegistryAndLoadedRecipeSnapshot(GameTestHelper helper) {
        JsonObject report = TestReports.report("Live Forge registry and loaded RecipeManager snapshot; registry presence only");
        report.addProperty("baseline_sha256", RegistryExpectations.BASELINE_SHA256);
        JsonArray registries = new JsonArray();
        append(registries, "block", ForgeRegistries.BLOCKS);
        append(registries, "item", ForgeRegistries.ITEMS);
        append(registries, "block_entity", ForgeRegistries.BLOCK_ENTITY_TYPES);
        append(registries, "entity_type", ForgeRegistries.ENTITY_TYPES);
        append(registries, "menu", ForgeRegistries.MENU_TYPES);
        append(registries, "recipe_serializer", ForgeRegistries.RECIPE_SERIALIZERS);
        BuiltInRegistries.RECIPE_TYPE.keySet().stream().filter(RegistryGameTests::owned).sorted()
                .forEach(id -> row(registries, "recipe_type", id, BuiltInRegistries.RECIPE_TYPE.get(id)));
        report.add("registries", registries);
        Set<String> blocks = ids(ForgeRegistries.BLOCKS);
        Set<String> items = ids(ForgeRegistries.ITEMS);
        report.addProperty("calculator_block_count", blocks.size());
        report.addProperty("calculator_item_count", items.size());
        report.addProperty("expected_block_count", 91);
        report.addProperty("expected_item_count", 239);
        JsonArray recipes = new JsonArray();
        JsonObject counts = new JsonObject();
        for (String type : RecipeContent.MACHINE_TYPES) {
            var loaded = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeContent.type(type));
            counts.addProperty(type, loaded.size());
            for (MachineRecipe recipe : loaded) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", recipe.getId().toString());
                entry.addProperty("type", type);
                entry.addProperty("energy", recipe.getEnergy());
                entry.addProperty("process_time", recipe.getProcessTime());
                entry.addProperty("value", recipe.getValue());
                JsonArray ingredients = new JsonArray();
                for (MachineRecipe.Input input : recipe.getInputs()) {
                    JsonObject ingredient = new JsonObject();
                    ingredient.addProperty("count", input.count());
                    if (input.exactNbt() != null) ingredient.addProperty("exact_nbt", input.exactNbt().toString());
                    JsonArray accepted = new JsonArray();
                    for (ItemStack stack : input.ingredient().getItems()) accepted.add(stack.save(new net.minecraft.nbt.CompoundTag()).toString());
                    ingredient.add("resolved_stacks", accepted);
                    ingredients.add(ingredient);
                }
                entry.add("inputs", ingredients);
                JsonArray outputs = new JsonArray();
                for (ItemStack stack : recipe.getOutputs()) outputs.add(stack.save(new net.minecraft.nbt.CompoundTag()).toString());
                entry.add("output_previews_not_probability_evidence", outputs);
                recipes.add(entry);
            }
        }
        report.add("loaded_machine_recipe_counts", counts);
        report.add("recipes", recipes);
        boolean matches = blocks.equals(RegistryExpectations.BLOCKS) && items.equals(RegistryExpectations.ITEMS);
        report.addProperty("registry_expectations_match", matches);
        TestReports.write("registry-snapshot", report); // Dump before assertions so failures remain inspectable.
        helper.assertTrue(blocks.size() == 91 && blocks.equals(RegistryExpectations.BLOCKS), "Calculator block registry differs from 91 expanded upstream IDs");
        helper.assertTrue(items.size() == 239 && items.equals(RegistryExpectations.ITEMS), "Calculator item registry differs from 239 expanded upstream IDs");
        for (String id : RegistryExpectations.BLOCKS) {
            ResourceLocation key = new ResourceLocation(id);
            helper.assertTrue(ForgeRegistries.ITEMS.getValue(key) instanceof BlockItem blockItem
                    && blockItem.getBlock() == ForgeRegistries.BLOCKS.getValue(key), "BlockItem points to wrong block: " + id);
        }
        helper.succeed();
    }

    private static boolean owned(ResourceLocation id) {
        return id.getNamespace().equals("calculator") || id.getNamespace().equals("sonarcore");
    }
    private static <T> Set<String> ids(IForgeRegistry<T> registry) {
        return registry.getKeys().stream().filter(id -> id.getNamespace().equals("calculator"))
                .map(ResourceLocation::toString).collect(Collectors.toCollection(TreeSet::new));
    }
    private static <T> void append(JsonArray rows, String kind, IForgeRegistry<T> registry) {
        registry.getKeys().stream().filter(RegistryGameTests::owned).sorted()
                .forEach(id -> row(rows, kind, id, registry.getValue(id)));
    }
    private static void row(JsonArray rows, String kind, ResourceLocation id, Object value) {
        JsonObject row = new JsonObject();
        row.addProperty("kind", kind);
        row.addProperty("id", id.toString());
        row.addProperty("implementation_class", value.getClass().getName());
        rows.add(row);
    }
    private RegistryGameTests() {}
}
