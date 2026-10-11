package sonar.calculator.mod.recipe;

import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Queries the current RecipeManager so server data-pack reloads take effect immediately. */
public final class CalculatorRecipes {
    private static final Comparator<MachineRecipe> ORDER = Comparator.comparingInt(MachineRecipe::getOrder)
            .thenComparing(recipe -> recipe.getId().toString());
    private CalculatorRecipes() {}
    @Nullable public static MachineRecipe find(Level level, String type, List<ItemStack> inputs) {
        if (!RecipeContent.MACHINE_TYPES.contains(type)) return null;
        return level.getRecipeManager().getAllRecipesFor(RecipeContent.type(type)).stream()
                .filter(recipe -> recipe.matches(inputs)).min(ORDER).orElse(null);
    }
    @Nullable public static MachineRecipe findValues(Level level, String type, int... inputs) {
        if (!RecipeContent.MACHINE_TYPES.contains(type)) return null;
        return level.getRecipeManager().getAllRecipesFor(RecipeContent.type(type)).stream()
                .filter(recipe -> recipe.matchesValues(inputs)).min(ORDER).orElse(null);
    }
}
