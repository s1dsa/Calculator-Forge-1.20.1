package sonar.calculator.mod.integration.jei;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable display layout for one JEI category, cropped directly from the matching machine or
 * portable-calculator GUI artwork.
 *
 * <p>Slot coordinates are relative to the cropped background and follow JEI's 18x18 slot
 * convention. Every crop is taken from the working area of the real GUI (slot row / progress
 * arrow), never from its title, energy read-out or player-inventory area, so the JEI page shows the
 * same artwork, operators and progress hint as the machine without duplicating the machine title or
 * inventing an energy value. Crops use legacy 256x256 GUI coordinates; the drawables are built with
 * {@code setTextureSize(256, 256)} so the same coordinates remain correct for the upscaled 512x512
 * port textures.</p>
 *
 * @param amountUnit English fallback unit for the value line of numeric recipes, or {@code null}
 *                   when the family has no numeric value to show.
 */
public record JeiLayout(ResourceLocation texture, int u, int v, int width, int height,
                        int[] inputX, int[] inputY, int[] outputX, int[] outputY,
                        @Nullable Arrow arrow, @Nullable String amountUnit) {

    private static final int SLOT = 18;

    /** Optional progress arrow cut from the same machine texture. */
    public record Arrow(ResourceLocation texture, int u, int v, int width, int height, int x, int y, int ticks) {}

    public JeiLayout {
        if (inputX.length != inputY.length || outputX.length != outputY.length) {
            throw new IllegalArgumentException("JEI slot coordinate arrays must be paired");
        }
    }

    private static ResourceLocation gui(String name) {
        return new ResourceLocation("calculator", "textures/gui/" + name);
    }

    /** One input, one output: processing / restoration / reassembly (all use this machine artwork). */
    public static JeiLayout single(String texture, @Nullable String amountUnit) {
        ResourceLocation background = gui(texture);
        return new JeiLayout(background, 48, 19, 80, 27,
                new int[] {4}, new int[] {4}, new int[] {58}, new int[] {4},
                new Arrow(background, 177, 10, 22, 15, 29, 5, 100), amountUnit);
    }

    /** One input, two outputs: extraction / precision / stone / algorithm separators. */
    public static JeiLayout dual(String texture) {
        ResourceLocation background = gui(texture);
        return new JeiLayout(background, 34, 19, 108, 27,
                new int[] {4}, new int[] {4}, new int[] {58, 86}, new int[] {4, 4},
                new Arrow(background, 177, 10, 22, 15, 29, 5, 100), null);
    }

    /** Two inputs, one output: calculator / scientific calculator. */
    public static JeiLayout calculator(String texture) {
        return new JeiLayout(gui(texture), 20, 30, 135, 27,
                new int[] {4, 58}, new int[] {4, 4}, new int[] {113}, new int[] {4},
                null, null);
    }

    /** Three inputs, one output: atomic calculator. */
    public static JeiLayout atomic() {
        return new JeiLayout(gui("atomiccalculator.png"), 19, 30, 136, 27,
                new int[] {0, 32, 64}, new int[] {4, 4, 4}, new int[] {114}, new int[] {4},
                null, null);
    }

    /**
     * Four inputs, one output: flawless calculator. The crop contains the legacy
     * {@code + ÷ + =} operator glyphs between the four input slots; JEI places the slots on top of
     * the same strip, matching the original JEI page.
     */
    public static JeiLayout flawless() {
        return new JeiLayout(gui("flawlesscalculator.png"), 16, 34, 146, 18,
                new int[] {0, 32, 64, 96}, new int[] {0, 0, 0, 0}, new int[] {128}, new int[] {0},
                null, null);
    }

    /** One input, one output: conductor mast, with the animated lightning progress arrow. */
    public static JeiLayout conductor() {
        ResourceLocation background = gui("conductormast.png");
        return new JeiLayout(background, 49, 17, 80, 26,
                new int[] {4}, new int[] {4}, new int[] {58}, new int[] {4},
                new Arrow(background, 176, 0, 18, 8, 30, 9, 100), "RF");
    }

    /** Fourteen circuit inputs in a five-wide grid, one output: fabrication chamber. */
    public static JeiLayout fabrication() {
        int[] x = {0, 18, 36, 54, 72, 0, 18, 36, 54, 72, 0, 18, 36, 54};
        int[] y = {0, 0, 0, 0, 0, 18, 18, 18, 18, 18, 36, 36, 36, 36};
        ResourceLocation background = gui("fabrication_chamber_jei.png");
        return new JeiLayout(background, 0, 0, 151, 55,
                x, y, new int[] {129}, new int[] {20},
                new Arrow(background, 0, 56, 20, 16, 95, 20, 100), null);
    }

    /** One input, two outputs: sickle harvesting, using the portable tool's JEI artwork. */
    public static JeiLayout harvest() {
        return new JeiLayout(gui("sickle_harvesting.png"), 52, 23, 64, 36,
                new int[] {0}, new int[] {10}, new int[] {46, 46}, new int[] {0, 18}, null, null);
    }

    /** No item input, one output: analysing chamber (the circuit slot/value are recipe metadata). */
    public static JeiLayout analysing() {
        return new JeiLayout(gui("analysischamber.png"), 30, 29, 26, 26,
                new int[0], new int[0], new int[] {4}, new int[] {4}, null, null);
    }

    /** One item input, numeric value: health processor (its "+" slot from the machine GUI). */
    public static JeiLayout health() {
        return inputOnly("hungerprocessor.png", 50, 29, 26, 26, "Health Points");
    }

    /** One item input, numeric value: starch / redstone / glowstone extractor input slot. */
    public static JeiLayout extractor(String texture, String amountUnit) {
        return inputOnly(texture, 3, 9, 26, 26, amountUnit);
    }

    private static JeiLayout inputOnly(String texture, int u, int v, int w, int h, String amountUnit) {
        return new JeiLayout(gui(texture), u, v, w, h,
                new int[] {4}, new int[] {4}, new int[0], new int[0], null, amountUnit);
    }

    /** True when the layout is safe to hand to JEI: every slot and the arrow stay inside the crop. */
    public boolean isValid() {
        if (width <= 0 || height <= 0 || texture == null) return false;
        for (int i = 0; i < inputX.length; i++) if (!inside(inputX[i], inputY[i])) return false;
        for (int i = 0; i < outputX.length; i++) if (!inside(outputX[i], outputY[i])) return false;
        if (arrow != null) {
            if (arrow.width() <= 0 || arrow.height() <= 0 || arrow.x() < 0 || arrow.y() < 0
                    || arrow.x() + arrow.width() > width || arrow.y() + arrow.height() > height) return false;
        }
        return true;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x + SLOT <= width && y + SLOT <= height;
    }

    /** Height reserved by the category: the artwork plus the tooltip-note lines below it. */
    public int categoryHeight() {
        return height + 32;
    }
}
