package sonar.calculator.mod.recipe;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** 1.12 BlockCrops#getDrops: exactly 3+fortune nextInt(15) trials; no guaranteed mature seed. */
public final class LegacyCropSeedsFunction extends LootItemConditionalFunction {
    LegacyCropSeedsFunction(LootItemCondition[] conditions) { super(conditions); }
    @Override public LootItemFunctionType getType() { return RecipeContent.CROP_SEEDS.get(); }
    @Override public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.BLOCK_STATE, LootContextParams.TOOL);
    }
    @Override protected ItemStack run(ItemStack stack, LootContext context) {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null || !(state.getBlock() instanceof CropBlock crop)) { stack.setCount(0); return stack; }
        int age = crop.getAge(state);
        if (age < crop.getMaxAge()) { stack.setCount(1); return stack; }
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        int fortune = tool == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
        int count = 0;
        for (int i = 0; i < 3 + fortune; i++) if (context.getRandom().nextInt(15) <= age) count++;
        stack.setCount(count);
        return stack;
    }
    public static final class Serializer extends LootItemConditionalFunction.Serializer<LegacyCropSeedsFunction> {
        @Override public void serialize(JsonObject json, LegacyCropSeedsFunction function, JsonSerializationContext context) {
            super.serialize(json, function, context);
        }
        @Override public LegacyCropSeedsFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            return new LegacyCropSeedsFunction(conditions);
        }
    }
}
