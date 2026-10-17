package sonar.calculator.mod.integration.jei;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.recipe.MachineRecipe;
import sonar.calculator.mod.recipe.RecipeContent;
import mezz.jei.api.constants.RecipeTypes;

/**
 * Optional JEI integration for the Calculator machine recipe families.
 *
 * <p>Discovered by JEI's own {@code @JeiPlugin} annotation scan; nothing in the common or server
 * code path references this class, so the mod still loads and runs when JEI is absent and no
 * client class is touched on a dedicated server. Only families that actually have loaded recipes
 * are registered, and every category is backed by real recipe data (inputs, counts, NBT,
 * probabilities, values) rather than an empty shell.
 */
@JeiPlugin
public final class CalculatorJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation("calculator", "jei");
    private static final Logger LOGGER = LogUtils.getLogger();

    /** One recipe family: its machine id, display name, icon and JEI catalysts. */
    private record Family(String type, String title, String iconBlock, String iconItem, JeiLayout layout,
                          List<String> catalystBlocks, List<String> catalystItems) {}

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        for (Family family : enabledFamilies()) {
            if (!family.layout().isValid()) {
                throw new IllegalStateException("JEI layout for '" + family.type()
                        + "' leaves its machine-artwork crop: " + family.layout());
            }
            RecipeType<MachineRecipe> type = JeiRecipeTypes.BY_ID.get(family.type());
            registration.addRecipeCategories(new MachineRecipeCategory(type, JeiText.category(family.type(), family.title()),
                    guiHelper.createDrawableItemStack(icon(family)), guiHelper, family.layout(), family.type()));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager manager = manager();
        if (manager == null) {
            LOGGER.warn("Calculator JEI: no client recipe manager available; calculator recipes are not registered this reload");
            return;
        }
        for (Family family : enabledFamilies()) {
            List<MachineRecipe> recipes = sorted(manager.getAllRecipesFor(RecipeContent.type(family.type())));
            if (recipes.isEmpty()) continue;
            registration.addRecipes(JeiRecipeTypes.BY_ID.get(family.type()), recipes);
            LOGGER.info("Calculator JEI: registered {} recipe(s) for '{}'", recipes.size(), family.type());
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (Family family : families()) {
            RecipeType<MachineRecipe> type = JeiRecipeTypes.BY_ID.get(family.type());
            for (String id : family.catalystBlocks()) registration.addRecipeCatalyst(CalculatorContent.block(id), type);
            for (String id : family.catalystItems()) registration.addRecipeCatalyst(CalculatorContent.item(id), type);
        }
        // Vanilla categories the upstream integration also claimed.
        registration.addRecipeCatalyst(CalculatorContent.block("reinforcedfurnace"), RecipeTypes.SMELTING);
        registration.addRecipeCatalyst(CalculatorContent.item("craftingcalculator"), RecipeTypes.CRAFTING);
        registration.addRecipeCatalyst(CalculatorContent.item("flawlesscalculator"), RecipeTypes.CRAFTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Intentionally empty: every calculator machine is served by one shared MachineScreen class
        // whose recipe type depends on the block entity, and JEI's static click-area API binds one
        // fixed RecipeType per screen class. Recipe lookups are instead reachable through the JEI
        // catalyst lists registered above, and the GUI geometry belongs to the client owner, so no
        // coordinate is guessed here.
    }

    /** Families that currently have at least one loaded recipe, in declaration order. */
    private static List<Family> enabledFamilies() {
        RecipeManager manager = manager();
        if (manager == null) return List.of();
        List<Family> enabled = new ArrayList<>();
        for (Family family : families()) {
            if (!manager.getAllRecipesFor(RecipeContent.type(family.type())).isEmpty()) enabled.add(family);
        }
        return enabled;
    }

    private static List<MachineRecipe> sorted(List<MachineRecipe> recipes) {
        List<MachineRecipe> ordered = new ArrayList<>(recipes);
        ordered.sort(Comparator.comparingInt(MachineRecipe::getOrder).thenComparing(recipe -> recipe.getId().toString()));
        return ordered;
    }

    @Nullable
    private static RecipeManager manager() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.getRecipeManager();
    }

    private static ItemStack icon(Family family) {
        if (family.iconBlock() != null) return new ItemStack(CalculatorContent.block(family.iconBlock()));
        return CalculatorContent.stack(family.iconItem(), 1);
    }

    private static List<Family> families() {
        // Every layout is cropped from the matching machine / portable-calculator GUI artwork:
        // legacy 256px GUI coordinates, slot row and progress arrow only (no title, inventory or
        // energy text). See JeiLayout for the crops and MachineScreen for the live screens.
        return List.of(
                new Family("processing", "Processing Chamber", "processingchamber", null,
                        JeiLayout.single("restorationchamber.png", null),
                        List.of("processingchamber"), List.of()),
                new Family("restoration", "Restoration Chamber", "restorationchamber", null,
                        JeiLayout.single("restorationchamber.png", null),
                        List.of("restorationchamber"), List.of()),
                new Family("reassembly", "Reassembly Chamber", "reassemblychamber", null,
                        JeiLayout.single("restorationchamber.png", null),
                        List.of("reassemblychamber"), List.of()),
                new Family("extraction", "Extraction Chamber", "extractionchamber", null,
                        JeiLayout.dual("extractionchamber.png"),
                        List.of("extractionchamber"), List.of()),
                new Family("precision", "Precision Chamber", "precisionchamber", null,
                        JeiLayout.dual("extractionchamber.png"),
                        List.of("precisionchamber"), List.of()),
                new Family("stone", "Stone Separator", "stoneseparator", null,
                        JeiLayout.dual("stoneseperator.png"),
                        List.of("stoneseparator"), List.of()),
                new Family("algorithm", "Algorithm Separator", "algorithmseparator", null,
                        JeiLayout.dual("stoneseperator.png"),
                        List.of("algorithmseparator"), List.of()),
                new Family("calculator", "Calculator", null, "calculator", JeiLayout.calculator("calculator.png"),
                        List.of("dynamiccalculator", "dockingstation"), List.of("calculator", "flawlesscalculator")),
                new Family("scientific", "Scientific Calculator", null, "scientificcalculator",
                        JeiLayout.calculator("scientificcalculator.png"),
                        List.of("dynamiccalculator", "dockingstation"), List.of("scientificcalculator", "flawlesscalculator")),
                new Family("atomic", "Atomic Calculator", "atomiccalculator", null, JeiLayout.atomic(),
                        List.of("atomiccalculator", "dynamiccalculator", "dockingstation"), List.of()),
                new Family("flawless", "Flawless Calculator", null, "flawlesscalculator", JeiLayout.flawless(),
                        List.of("dockingstation"), List.of("flawlesscalculator")),
                new Family("health", "Health Processor", "healthprocessor", null, JeiLayout.health(),
                        List.of("healthprocessor"), List.of()),
                new Family("conductor_mast", "Conductor Mast", "conductormast", null, JeiLayout.conductor(),
                        List.of("conductormast"), List.of()),
                new Family("fabrication", "Fabrication Chamber", "fabricationchamber", null, JeiLayout.fabrication(),
                        List.of("fabricationchamber"), List.of()),
                new Family("harvest", "Sickle Harvesting", null, "sickle", JeiLayout.harvest(),
                        List.of(), List.of("sickle")),
                new Family("analysing", "Analysing Chamber", "analysingchamber", null, JeiLayout.analysing(),
                        List.of("analysingchamber"), List.of()),
                new Family("starch", "Starch Extractor", "starchextractor", null,
                        JeiLayout.extractor("starchgenerator.png", "RF"),
                        List.of("starchextractor"), List.of()),
                new Family("redstone", "Redstone Extractor", "redstoneextractor", null,
                        JeiLayout.extractor("redstonegenerator.png", "RF"),
                        List.of("redstoneextractor"), List.of()),
                new Family("glowstone", "Glowstone Extractor", "glowstoneextractor", null,
                        JeiLayout.extractor("glowstonegenerator.png", "RF"),
                        List.of("glowstoneextractor"), List.of()));
    }

    /** True when {@code research} is (incorrectly) present in the machine types; used by the self-check. */
    public static boolean researchTypeLeaked() {
        return RecipeContent.MACHINE_TYPES.contains("research");
    }
}
