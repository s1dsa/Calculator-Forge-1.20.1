package sonar.calculator.mod.world;

import java.util.List;
import java.util.ArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import sonar.calculator.mod.CalculatorContent;

public final class CalculatorCropBlock extends CropBlock {
    public final int crop, greenhouseTier;
    private final String seed, produce;
    public CalculatorCropBlock(int crop, int tier, String seed, String produce) {
        super(WorldContent.plantProperties());
        this.crop = crop; this.greenhouseTier = tier; this.seed = seed; this.produce = produce;
    }
    public boolean isUseable(int tier) { return tier >= greenhouseTier; }
    @Override protected ItemLike getBaseSeedId() { return CalculatorContent.item(seed); }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        drops.add(CalculatorContent.stack(isMaxAge(state) ? produce : seed, 1));
        if (isMaxAge(state)) {
            ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
            int fortune = tool == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
            for (int i = 0; i < 3 + fortune; i++)
                if (params.getLevel().random.nextInt(2 * getMaxAge()) <= getAge(state)) drops.add(CalculatorContent.stack(seed, 1));
        }
        return drops;
    }
}
