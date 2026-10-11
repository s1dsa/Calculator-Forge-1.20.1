package sonar.calculator.mod.gametest;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.recipe.CalculatorRecipes;
import sonar.calculator.mod.recipe.MachineRecipe;
import sonar.calculator.mod.recipe.RecipeContent;

/** Expectations transcribed from immutable upstream common/recipes sources, not target JSON. */
@GameTestHolder("calculator")
@PrefixGameTestTemplate(false)
public final class RecipeGameTests {
    @GameTest(template = "test_empty")
    public static void loadedRecipesConsumeAndReturnExactOutputs(GameTestHelper helper) {
        check(helper, "calculator", List.of(new ItemStack(Items.GOLD_INGOT, 3), new ItemStack(Items.REDSTONE, 3)),
                List.of(CalculatorContent.stack("enrichedgold", 4)));
        check(helper, "scientific", List.of(new ItemStack(Items.REDSTONE, 3), new ItemStack(Items.IRON_INGOT, 3)),
                List.of(CalculatorContent.stack("redstoneingot", 1)));
        check(helper, "atomic", List.of(new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.REDSTONE, 3)),
                List.of(CalculatorContent.stack("scientificcalculator", 1)));
        Item[] logs = {Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG};
        Item[] planks = {Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS};
        for (int i = 0; i < logs.length; i++)
            check(helper, "stone", List.of(new ItemStack(logs[i], 3)), List.of(new ItemStack(planks[i], 4), new ItemStack(Items.STICK, 2)));
        for (int meta = 0; meta < 14; meta++) {
            String suffix = meta == 0 ? "" : "_" + meta;
            for (String type : List.of("restoration", "reassembly", "processing")) {
                for (String input : type.equals("processing") ? List.of("circuitdirty", "circuitdamaged")
                        : List.of(type.equals("restoration") ? "circuitdirty" : "circuitdamaged")) {
                    check(helper, type, List.of(CalculatorContent.stack(input + suffix, 3)),
                            List.of(CalculatorContent.stack("circuitboard" + suffix, 1)));
                }
            }
        }
        var input = new ItemStack(Items.IRON_INGOT, 3);
        helper.assertTrue(CalculatorRecipes.find(helper.getLevel(), "atomic", List.of(input, input, new ItemStack(Items.REDSTONE))) == null,
                "Aliased mutable input is counted twice");
        helper.assertTrue(CalculatorRecipes.find(helper.getLevel(), "unknown_type", List.of(input)) == null, "Unknown machine type resolved");
        JsonObject report = TestReports.report("65 loaded upstream recipe cases: inputs, output counts, no mutation, consumption and serializer round trip");
        report.addProperty("passed_cases", 65);
        TestReports.write("recipe-behavior", report);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, String type, List<ItemStack> inputs, List<ItemStack> expected) {
        List<ItemStack> before = inputs.stream().map(ItemStack::copy).toList();
        MachineRecipe recipe = CalculatorRecipes.find(helper.getLevel(), type, inputs);
        helper.assertTrue(recipe != null, "Loaded recipe missing for " + type + " " + inputs);
        helper.assertTrue(recipe.matches(inputs), "find returned non-matching recipe");
        equal(helper, inputs, before, "Lookup consumed inputs");
        equal(helper, recipe.getOutputs(), expected, "Upstream outputs differ: " + recipe.getId());
        equal(helper, recipe.rollOutputs(RandomSource.create(73)), expected, "Deterministic outputs differ");
        List<ItemStack> previews = recipe.getOutputs();
        if (!previews.isEmpty()) { previews.get(0).setCount(1); previews.get(0).getOrCreateTag().putInt("test_mutation", 42); }
        equal(helper, recipe.getOutputs(), expected, "Preview aliases recipe state");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RecipeContent.serializer(type).toNetwork(buffer, recipe);
            MachineRecipe restored = RecipeContent.serializer(type).fromNetwork(recipe.getId(), buffer);
            helper.assertTrue(restored.matches(inputs), "Network round trip lost ingredient semantics");
            equal(helper, restored.getOutputs(), expected, "Network round trip lost outputs");
        } finally { buffer.release(); }
        var invalid = new ArrayList<>(inputs.stream().map(ItemStack::copy).toList());
        invalid.set(0, new ItemStack(Items.BARRIER));
        List<ItemStack> invalidBefore = invalid.stream().map(ItemStack::copy).toList();
        helper.assertFalse(recipe.consumeInputs(invalid), "Invalid ingredients consumed");
        equal(helper, invalid, invalidBefore, "Failed consume changed another stack");
        helper.assertTrue(recipe.consumeInputs(inputs), "Valid consume failed");
        for (int i = 0; i < inputs.size(); i++) helper.assertTrue(inputs.get(i).getCount() == before.get(i).getCount() - 1, "Recipe consumed incorrect input count");
    }

    @GameTest(template = "test_empty")
    public static void extractionUsesExactLegacyRandomSequence(GameTestHelper helper) {
        int attempts = 12600;
        JsonObject report = TestReports.report("Loaded extraction recipes compared to upstream nextInt(9)==8 then nextInt(14), seed 0xCA1C");
        for (boolean dirt : new boolean[]{true, false}) {
            var inputs = List.of(new ItemStack(dirt ? Items.DIRT : Items.COBBLESTONE, 2));
            MachineRecipe recipe = CalculatorRecipes.find(helper.getLevel(), "extraction", inputs);
            helper.assertTrue(recipe != null, "Loaded extraction recipe missing");
            RandomSource actualRandom = RandomSource.create(0xCA1CL);
            RandomSource expectedRandom = RandomSource.create(0xCA1CL);
            int successes = 0;
            int[] variants = new int[14];
            for (int i = 0; i < attempts; i++) {
                recipe.getOutputs(); // Preview must not advance the supplied random sequence.
                List<ItemStack> actual = recipe.rollOutputs(actualRandom);
                helper.assertTrue(actual.size() == 2, "Extraction output arity differs");
                helper.assertTrue(ItemStack.matches(actual.get(0), CalculatorContent.stack(dirt ? "soil" : "smallstone", 1)), "Guaranteed extraction output differs");
                boolean success = expectedRandom.nextInt(9) == 8;
                ItemStack expected = ItemStack.EMPTY;
                if (success) {
                    int variant = expectedRandom.nextInt(14);
                    successes++;
                    variants[variant]++;
                    expected = CalculatorContent.stack((dirt ? "circuitdirty" : "circuitdamaged") + (variant == 0 ? "" : "_" + variant), 1);
                }
                helper.assertTrue(ItemStack.matches(actual.get(1), expected), "Extraction probability/call order differs at draw " + i);
            }
            for (int count : variants) helper.assertTrue(count > 0, "Seed did not exercise every circuit variant");
            helper.assertTrue(inputs.get(0).getCount() == 2, "Rolling outputs consumed input");
            report.addProperty(dirt ? "dirt_successes" : "cobblestone_successes", successes);
        }
        report.addProperty("draws_per_recipe", attempts);
        report.addProperty("expected_probability", "1/9; each circuit variant conditional probability 1/14");
        TestReports.write("extraction-probability", report);
        helper.succeed();
    }

    private static void equal(GameTestHelper helper, List<ItemStack> actual, List<ItemStack> expected, String message) {
        helper.assertTrue(actual.size() == expected.size(), message + " (arity)");
        for (int i = 0; i < actual.size(); i++) helper.assertTrue(ItemStack.matches(actual.get(i), expected.get(i)), message + " slot=" + i + " actual=" + actual.get(i) + " expected=" + expected.get(i));
    }
    private RecipeGameTests() {}
}
