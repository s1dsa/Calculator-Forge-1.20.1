package sonar.calculator.mod.item.misc;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.PlantType;
import sonar.calculator.mod.CalculatorContent;

public final class CalculatorSeed extends Item implements IPlantable {
    private final String plantId;
    private final int greenhouseTier;
    public CalculatorSeed(String plantId, int greenhouseTier, boolean food) {
        super(food ? new Properties().food(new FoodProperties.Builder().nutrition(16).saturationMod(0.6F).build()) : new Properties());
        this.plantId = plantId;
        this.greenhouseTier = greenhouseTier;
    }
    public int greenhouseTier() { return greenhouseTier; }
    public boolean canTierUse(int tier) { return tier >= greenhouseTier; }
    @Override public PlantType getPlantType(BlockGetter level, BlockPos pos) { return PlantType.CROP; }
    @Override public BlockState getPlant(BlockGetter level, BlockPos pos) { return CalculatorContent.block(plantId).defaultBlockState(); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (greenhouseTier != 0 || context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos soil = context.getClickedPos(), plant = soil.above();
        if (player == null || !player.mayUseItemAt(plant, Direction.UP, context.getItemInHand()) || !level.mayInteract(player, plant)) return InteractionResult.FAIL;
        BlockState state = level.getBlockState(soil);
        if (!level.isEmptyBlock(plant) || !state.canSustainPlant(level, soil, Direction.UP, this)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlockAndUpdate(plant, getPlant(level, plant));
            if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        if (greenhouseTier > 0) lines.add(Component.translatable("planting." + (greenhouseTier == 2 ? "advanced" : "flawless")));
    }
}
