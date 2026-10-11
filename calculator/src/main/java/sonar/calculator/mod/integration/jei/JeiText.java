package sonar.calculator.mod.integration.jei;

import net.minecraft.network.chat.Component;

/**
 * Tooltip and label text for the JEI categories.
 *
 * <p>Every string is a translation key with an English fallback
 * ({@link Component#translatableWithFallback}), so the JEI integration is readable out of the box
 * and can be localised later by adding keys to {@code assets/calculator/lang/*.json} without any
 * code change. This file deliberately does not edit the shared language files, which belong to the
 * client/resource owner.
 */
public final class JeiText {
    private JeiText() {}

    public static Component category(String id, String fallbackTitle) {
        return Component.translatableWithFallback("jei.calculator.category." + id, fallbackTitle);
    }

    /** "Requires 4 x" style hint for an input slot; count is authoritative from the recipe. */
    public static Component inputCount(int count) {
        return Component.translatableWithFallback("jei.calculator.input.count", "Requires x%s", count);
    }

    public static Component exactNbt(String nbt) {
        return Component.translatableWithFallback("jei.calculator.input.nbt", "Exact NBT: %s", nbt);
    }

    public static Component nbt(String nbt) {
        return Component.translatableWithFallback("jei.calculator.output.nbt", "NBT: %s", nbt);
    }

    public static Component chance(String ratio, String percent) {
        return Component.translatableWithFallback("jei.calculator.output.chance", "Chance: %s (%s)", ratio, percent);
    }

    public static Component variants(int variants) {
        return Component.translatableWithFallback("jei.calculator.output.variants", "Randomised output: one of %s variants", variants);
    }

    public static Component amount(String unit, int value) {
        Component displayUnit = unit.equals("Health Points")
                ? Component.translatableWithFallback("jei.calculator.unit.health", "Health Points")
                : Component.literal(unit);
        return Component.translatableWithFallback("jei.calculator.amount", "Value: %s %s", value, displayUnit);
    }

    public static Component circuitPosition(int position) {
        return Component.translatableWithFallback("jei.calculator.analysing.position", "Circuit slot: %s", position);
    }

    public static Component circuitValue(int value) {
        return Component.translatableWithFallback("jei.calculator.analysing.value", "Required circuit value: %s", value);
    }

    public static Component energyCost(int energy) {
        return Component.translatableWithFallback("jei.calculator.energy", "Machine cost: %s RF", energy);
    }
}
