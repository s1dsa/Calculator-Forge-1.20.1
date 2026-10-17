package sonar.calculator.mod.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
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
 * <p>The category background is a crop of the real machine / portable-calculator GUI artwork, so
 * the slot row, operator glyphs and progress arrow match the in-game screen. The crop deliberately
 * excludes the machine title, player inventory and energy read-out; JEI supplies the category title
 * itself and no fake energy value is drawn. Inputs are shown with their required count and exact
 * NBT, outputs keep their preview count/NBT, and probability or variant notes are added when the
 * legacy recipe rolls them. Numeric families additionally show their value and analysing keys.</p>
 *
 * <p>The category is fully data-driven: every slot corresponds to a real ingredient or result of
 * the loaded recipe, so no category can be registered as an empty shell.</p>
 */
public final class MachineRecipeCategory extends AbstractRecipeCategory<MachineRecipe> {
    private final JeiLayout layout;
    private final String machineType;
    private final IDrawable background;
    private final IDrawableAnimated arrow;

    public MachineRecipeCategory(RecipeType<MachineRecipe> type, Component title, IDrawable icon,
                                 IGuiHelper guiHelper, JeiLayout layout, String machineType) {
        super(type, title, icon, layout.width(), layout.categoryHeight());
        this.layout = layout;
        this.machineType = machineType;
        this.background = guiHelper.drawableBuilder(layout.texture(), layout.u(), layout.v(),
                layout.width(), layout.height()).setTextureSize(256, 256).build();
        JeiLayout.Arrow arrowInfo = layout.arrow();
        if (arrowInfo == null) {
            this.arrow = null;
        } else {
            IDrawableStatic staticArrow = guiHelper.drawableBuilder(arrowInfo.texture(), arrowInfo.u(), arrowInfo.v(),
                    arrowInfo.width(), arrowInfo.height()).setTextureSize(256, 256).build();
            this.arrow = guiHelper.createAnimatedDrawable(staticArrow, arrowInfo.ticks(),
                    IDrawableAnimated.StartDirection.LEFT, false);
        }
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
        if (arrow != null) arrow.draw(graphics, layout.arrow().x(), layout.arrow().y());
        var font = net.minecraft.client.Minecraft.getInstance().font;
        List<Component> notes = valueNotes(recipe, RecipeInfo.of(recipe));
        for (int i = 0; i < notes.size(); i++) {
            graphics.drawString(font, font.plainSubstrByWidth(notes.get(i).getString(), layout.width()),
                    0, layout.height() + 2 + i * 10, 0x404040, false);
        }
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
