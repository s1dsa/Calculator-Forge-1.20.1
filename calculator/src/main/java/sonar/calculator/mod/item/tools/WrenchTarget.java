package sonar.calculator.mod.item.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Block or block entity hook for the Calculator wrench. */
public interface WrenchTarget {
    boolean onWrenched(Level level, BlockPos pos, Player player, Direction face, boolean dismantle);
}
