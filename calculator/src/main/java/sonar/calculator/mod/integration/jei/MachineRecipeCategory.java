package sonar.calculator.mod.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import sonar.calculator.mod.recipe.MachineRecipe;

/**
 * One JEI category for a {@link MachineRecipe} family.
 *
 * <p>Inputs are shown with their required count and, when the recipe demands it, the exact NBT the
 * machine validates. Outputs are shown with the recipe's own preview stack (count and NBT
 * preserved) plus a note when the legacy random roll only produces them with a probability, or
 * picks one of several circuit variants. Numeric families additionally show their value and, where
 * applicable, the analysing chamber's circuit slot / required value.
 *
 * <p>The category is fully data-driven: every slot corresponds to a real ingredient or result of
 * the loaded recipe, so no category can be registered as an empty shell.
 */
public final class MachineRecipeCategory extends AbstractRecipeCategory<MachineRecipe> {
    private final JeiLayout layout;
    private final String machineType;
    private final IDrawable background;

    public MachineRecipeCategory(RecipeType<MachineRecipe> type, Component title, IDrawable icon,
                                 IGuiHelper guiHelper, JeiLayout layout, String machineType) {
        super(type, title, icon, layout.width(), layout.height() + 32);
        this.layout = layout;
        this.machineType = machineType;
        this.background = guiHelper.createBlankDrawable(layout.width(), layout.height() + 32);
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe, IFocusGroup focuses) {
        RecipeInfo info = RecipeInfo.of(recipe);
        List<Component> valueNotes = valueNotes(recipe, info);
        List<MachineRecipe.Input> inputs = recipe.getInputs();
        boolean valueOnInput = layout.outputX().length == 0;
        for (int i = 0; i < inputs.size() && i < layout.inputX().length; i++) {
            MachineRecipe.Input input = inputs.get(i);
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, layout.inputX()[i], layout.inputY()[i]);
            List<ItemStack> displayInputs = new ArrayList<>();
            for (ItemStack choice : input.ingredient().getItems()) {
                ItemStack display = choice.copy();
                display.setCount(input.count());
                if (input.exactNbt() != null) display.setTag(input.exactNbt());
                displayInputs.add(display);
            }
            slot.addItemStacks(displayInputs);
            List<Component> notes = inputNotes(input);
            if (i == 0 && valueOnInput) notes.addAll(valueNotes);
            if (!notes.isEmpty()) slot.addTooltipCallback((view, tooltip) -> tooltip.addAll(notes));
        }
        List<ItemStack> outputs = recipe.getOutputs();
        boolean valueOnOutput = layout.inputX().length == 0;
        for (int i = 0; i < outputs.size() && i < layout.outputX().length; i++) {
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, layout.outputX()[i], layout.outputY()[i]);
            slot.addItemStack(outputs.get(i));
            List<Component> notes = outputNotes(info, i, outputs.get(i));
            if (i == 0 && valueOnOutput) notes.addAll(valueNotes);
            if (!notes.isEmpty()) slot.addTooltipCallback((view, tooltip) -> tooltip.addAll(notes));
        }
    }

    @Override
    public void draw(MachineRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     net.minecraft.client.gui.GuiGraphics graphics, double mouseX, double mouseY) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        List<Component> notes = valueNotes(recipe, RecipeInfo.of(recipe));
        for (int i = 0; i < notes.size(); i++) {
            graphics.drawString(font, font.plainSubstrByWidth(notes.get(i).getString(), layout.width()),
                    0, layout.height() + 2 + i * 10, 0x404040, false);
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MachineRecipe recipe, IFocusGroup focuses) {
        boolean arrow = layout.arrowX() >= 0 && layout.inputX().length > 0 && layout.outputX().length > 0;
        if (arrow) builder.addAnimatedRecipeArrow(200).setPosition(layout.arrowX(), layout.arrowY());
    }

    private List<Component> inputNotes(MachineRecipe.Input input) {
        List<Component> notes = new ArrayList<>();
        if (input.count() > 1) notes.add(JeiText.inputCount(input.count()));
        CompoundTag nbt = input.exactNbt();
        if (nbt != null && !nbt.isEmpty()) notes.add(JeiText.exactNbt(nbt.toString()));
        return notes;
    }

    private List<Component> outputNotes(RecipeInfo info, int index, ItemStack output) {
        List<Component> notes = new ArrayList<>();
        int bound = info.chanceBound(index);
        if (bound > 0) {
            // The legacy roll is nextInt(bound) == equals, so the success odds are 1 / bound
            // regardless of which value is targeted.
            notes.add(JeiText.chance("1/" + bound, percent(1.0 / bound)));
            int equals = info.chanceEquals(index);
            if (equals != 0) notes.add(JeiText.circuitValue(equals));
        }
        int choices = info.choiceCount(index);
        if (choices > 1) notes.add(JeiText.variants(choices));
        if (output.hasTag() && !output.getTag().isEmpty()) notes.add(JeiText.nbt(output.getTag().toString()));
        return notes;
    }

    /** Numeric value, energy cost and analysing keys for families that carry them. */
    private List<Component> valueNotes(MachineRecipe recipe, RecipeInfo info) {
        List<Component> notes = new ArrayList<>();
        String unit = layout.amountUnit();
        if (unit != null && recipe.getValue() > 0) notes.add(JeiText.amount(unit, recipe.getValue()));
        if (recipe.getEnergy() > 0) notes.add(JeiText.energyCost(recipe.getEnergy()));
        int[] values = info.inputValues();
        if (values.length >= 2) {
            notes.add(JeiText.circuitPosition(values[0]));
            notes.add(JeiText.circuitValue(values[1]));
        }
        return notes;
    }

    private static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.2f%%", fraction * 100.0);
    }

    /** The {@code RecipeContent.MACHINE_TYPES} id this category displays; used by the runtime self-check. */
    public String machineType() {
        return machineType;
    }
}
