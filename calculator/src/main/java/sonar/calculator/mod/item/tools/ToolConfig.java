package sonar.calculator.mod.item.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;

/** Original per-material configuration, loaded independently of the machine config. */
public final class ToolConfig {
    public static final ForgeConfigSpec SPEC;
    private static final Map<String, Values> MATERIALS = new LinkedHashMap<>();
    public record Stats(int level, int uses, float speed, float damage, int enchantment) {}
    private record Values(Stats defaults, ForgeConfigSpec.IntValue level, ForgeConfigSpec.IntValue uses,
                          ForgeConfigSpec.DoubleValue speed, ForgeConfigSpec.DoubleValue damage,
                          ForgeConfigSpec.IntValue enchantment) {
        Stats read() {
            return SPEC.isLoaded() ? new Stats(level.get(), uses.get(), speed.get().floatValue(), damage.get().floatValue(), enchantment.get()) : defaults;
        }
    }
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        add(builder, "reinforced", 1, 250, 5F, 1.5F, 5);
        add(builder, "enrichedgold", 3, 1000, 8F, 0F, 20);
        add(builder, "reinforcediron", 2, 400, 7F, 2F, 10);
        add(builder, "redstone", 2, 800, 7.5F, 2.5F, 18);
        add(builder, "weakeneddiamond", 3, 1400, 8F, 3F, 10);
        add(builder, "flawlessdiamond", 3, 1800, 14F, 5F, 30);
        add(builder, "firediamond", 3, 2600, 16F, 7F, 30);
        add(builder, "electric", 4, 10000, 18F, 10F, 30);
        add(builder, "endforged", 6, -1, 50F, 16F, 30);
        SPEC = builder.build();
    }
    private static void add(ForgeConfigSpec.Builder builder, String name, int level, int uses, float speed, float damage, int enchantment) {
        builder.push(name);
        MATERIALS.put(name, new Values(new Stats(level, uses, speed, damage, enchantment),
                builder.defineInRange("harvest_level", level, 0, Integer.MAX_VALUE),
                builder.defineInRange("max_uses", uses, -1, Integer.MAX_VALUE),
                builder.defineInRange("efficiency", (double) speed, 0, 1024),
                builder.defineInRange("damage", (double) damage, 0, 1024),
                builder.defineInRange("enchantability", enchantment, 0, Integer.MAX_VALUE)));
        builder.pop();
    }
    public static Stats get(String name) { return MATERIALS.get(name).read(); }
    private ToolConfig() {}
}
