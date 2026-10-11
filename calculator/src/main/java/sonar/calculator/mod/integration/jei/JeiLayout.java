package sonar.calculator.mod.integration.jei;

/**
 * Immutable slot grid for one JEI category. Coordinates are relative to the category background,
 * matching JEI's slot convention (18x18 slots). Input/output counts mirror the slot counts the
 * machines actually use, so a category can never advertise more slots than the machine has.
 *
 * @param amountUnit English fallback unit for the value line of numeric recipes, or {@code null}
 *                   when the family has no numeric value to show.
 */
public record JeiLayout(int[] inputX, int[] inputY, int[] outputX, int[] outputY,
                        int width, int height, int arrowX, int arrowY, String amountUnit) {

    private static final int SLOT = 18;

    /** One input, one output: processing / restoration / reassembly / conductor mast. */
    public static JeiLayout single(String amountUnit) {
        return new JeiLayout(new int[] {0}, new int[] {0}, new int[] {54}, new int[] {0},
                72, amountUnit == null ? SLOT : SLOT + 10, 24, 1, amountUnit);
    }

    /** One input, two outputs: extraction / precision / stone / algorithm separators. */
    public static JeiLayout dual() {
        return new JeiLayout(new int[] {0}, new int[] {0}, new int[] {54, 82}, new int[] {0, 0},
                100, SLOT, 24, 1, null);
    }

    /** Two inputs, one output: calculator / scientific calculator. */
    public static JeiLayout calculator() {
        return new JeiLayout(new int[] {0, 20}, new int[] {0, 0}, new int[] {82}, new int[] {0},
                100, SLOT, 44, 1, null);
    }

    /** Three inputs, one output: atomic calculator. */
    public static JeiLayout atomic() {
        return new JeiLayout(new int[] {0, 20, 40}, new int[] {0, 0, 0}, new int[] {100}, new int[] {0},
                118, SLOT, 64, 1, null);
    }

    /** Four inputs, one output: flawless calculator. */
    public static JeiLayout flawless() {
        return new JeiLayout(new int[] {0, 20, 40, 60}, new int[] {0, 0, 0, 0}, new int[] {120}, new int[] {0},
                138, SLOT, 84, 1, null);
    }

    /** Fourteen circuit inputs in a five-wide grid, one output: fabrication chamber. */
    public static JeiLayout fabrication() {
        int[] x = {0, 20, 40, 60, 80, 0, 20, 40, 60, 80, 0, 20, 40, 60};
        int[] y = {0, 0, 0, 0, 0, 20, 20, 20, 20, 20, 40, 40, 40, 40};
        return new JeiLayout(x, y, new int[] {110}, new int[] {20}, 140, 3 * 20, -1, 0, null);
    }

    /** One input, no item output, numeric value: starch / redstone / glowstone / health. */
    public static JeiLayout value(String amountUnit) {
        return new JeiLayout(new int[] {0}, new int[] {0}, new int[0], new int[0],
                100, SLOT + 10, -1, 0, amountUnit);
    }

    /** No item input (recipe is keyed by circuit position/value), one output: analysing chamber. */
    public static JeiLayout outputOnly(String amountUnit) {
        return new JeiLayout(new int[0], new int[0], new int[] {0}, new int[] {0},
                110, SLOT + 10, -1, 0, amountUnit);
    }
}
