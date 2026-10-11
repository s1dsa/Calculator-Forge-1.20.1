package sonar.calculator.mod.integration.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.recipe.RecipeType;
import sonar.calculator.mod.recipe.MachineRecipe;
import sonar.calculator.mod.recipe.RecipeContent;

/**
 * JEI {@link RecipeType}s for the machine recipe families registered by
 * {@link RecipeContent#MACHINE_TYPES}.
 *
 * <p>Every id below is copied verbatim from {@code RecipeContent.MACHINE_TYPES}; the class is derived
 * from that list at class-init time, so adding or removing a family upstream is a compile-time
 * visible change here rather than a silent mismatch. There is deliberately no {@code research}
 * type: the data-pack converter excludes the research section (see {@code notes/recipes.md}), so no
 * research recipe can exist and none can be shown by mistake.
 */
public final class JeiRecipeTypes {
    public static final RecipeType<MachineRecipe> PROCESSING = create("processing");
    public static final RecipeType<MachineRecipe> RESTORATION = create("restoration");
    public static final RecipeType<MachineRecipe> REASSEMBLY = create("reassembly");
    public static final RecipeType<MachineRecipe> EXTRACTION = create("extraction");
    public static final RecipeType<MachineRecipe> PRECISION = create("precision");
    public static final RecipeType<MachineRecipe> STONE = create("stone");
    public static final RecipeType<MachineRecipe> ALGORITHM = create("algorithm");
    public static final RecipeType<MachineRecipe> CALCULATOR = create("calculator");
    public static final RecipeType<MachineRecipe> SCIENTIFIC = create("scientific");
    public static final RecipeType<MachineRecipe> ATOMIC = create("atomic");
    public static final RecipeType<MachineRecipe> FLAWLESS = create("flawless");
    public static final RecipeType<MachineRecipe> HEALTH = create("health");
    public static final RecipeType<MachineRecipe> CONDUCTOR_MAST = create("conductor_mast");
    public static final RecipeType<MachineRecipe> FABRICATION = create("fabrication");
    public static final RecipeType<MachineRecipe> HARVEST = create("harvest");
    public static final RecipeType<MachineRecipe> ANALYSING = create("analysing");
    public static final RecipeType<MachineRecipe> STARCH = create("starch");
    public static final RecipeType<MachineRecipe> REDSTONE = create("redstone");
    public static final RecipeType<MachineRecipe> GLOWSTONE = create("glowstone");

    /** Machine-recipe id to JEI type, in {@link RecipeContent#MACHINE_TYPES} order. */
    public static final Map<String, RecipeType<MachineRecipe>> BY_ID;

    static {
        Map<String, RecipeType<MachineRecipe>> byId = new LinkedHashMap<>();
        byId.put("processing", PROCESSING);
        byId.put("restoration", RESTORATION);
        byId.put("reassembly", REASSEMBLY);
        byId.put("extraction", EXTRACTION);
        byId.put("precision", PRECISION);
        byId.put("stone", STONE);
        byId.put("algorithm", ALGORITHM);
        byId.put("calculator", CALCULATOR);
        byId.put("scientific", SCIENTIFIC);
        byId.put("atomic", ATOMIC);
        byId.put("flawless", FLAWLESS);
        byId.put("health", HEALTH);
        byId.put("conductor_mast", CONDUCTOR_MAST);
        byId.put("fabrication", FABRICATION);
        byId.put("harvest", HARVEST);
        byId.put("analysing", ANALYSING);
        byId.put("starch", STARCH);
        byId.put("redstone", REDSTONE);
        byId.put("glowstone", GLOWSTONE);
        List<String> known = new ArrayList<>(RecipeContent.MACHINE_TYPES);
        Collections.sort(known);
        List<String> mapped = new ArrayList<>(byId.keySet());
        Collections.sort(mapped);
        if (!known.equals(mapped)) {
            throw new IllegalStateException("JEI recipe types do not match RecipeContent.MACHINE_TYPES: "
                    + known + " != " + mapped);
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private static RecipeType<MachineRecipe> create(String id) {
        return RecipeType.create("calculator", id, MachineRecipe.class);
    }

    private JeiRecipeTypes() {}
}
