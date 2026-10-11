package sonar.core.energy;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** Original consumable energy values, including charcoal (1.12 coal metadata 1). */
public final class DischargeValues {
    private static final Map<Item, Integer> VALUES = new IdentityHashMap<>();
    private DischargeValues() { }

    public static int getValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Integer custom = VALUES.get(stack.getItem());
        if (custom != null) return custom;
        if (stack.is(Items.REDSTONE)) return 1000;
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 500;
        if (stack.is(Items.COAL_BLOCK)) return 4500;
        if (stack.is(Items.REDSTONE_BLOCK)) return 9000;
        return 0;
    }

    public static void register(ItemLike item, int value) {
        if (value < 0) throw new IllegalArgumentException("Negative discharge value");
        VALUES.put(item.asItem(), value);
    }
}
