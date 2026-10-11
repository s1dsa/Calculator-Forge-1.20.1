package sonar.calculator.mod.world;

import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class CalculatorLogBlock extends RotatedPillarBlock {
    public CalculatorLogBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.7F, 0.14F)
                .sound(SoundType.WOOD).ignitedByLava());
    }
}
