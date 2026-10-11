package sonar.core.util;

import java.util.EnumMap;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/** Six independently configurable sides with safe defaults for absent or invalid saved values. */
public final class SideConfig implements INBTSerializable<CompoundTag> {
    public enum Mode {
        INPUT, OUTPUT, BOTH, NONE;
        public boolean input() { return this == INPUT || this == BOTH; }
        public boolean output() { return this == OUTPUT || this == BOTH; }
    }
    private final EnumMap<Direction, Mode> sides = new EnumMap<>(Direction.class);
    private final Mode defaultMode;
    private final Runnable dirty;

    public SideConfig(Mode defaultMode, Runnable dirty) {
        this.defaultMode = defaultMode;
        this.dirty = dirty;
        for (Direction side : Direction.values()) sides.put(side, defaultMode);
    }
    public Mode get(Direction side) { return side == null ? Mode.BOTH : sides.get(side); }
    public void set(Direction side, Mode mode) {
        if (side == null || mode == null) throw new IllegalArgumentException("Side and mode are required");
        if (sides.put(side, mode) != mode) dirty.run();
    }
    public void cycle(Direction side) { set(side, Mode.values()[(get(side).ordinal() + 1) % Mode.values().length]); }
    @Override public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        sides.forEach((side, mode) -> tag.putString(side.getName(), mode.name().toLowerCase(Locale.ROOT)));
        return tag;
    }
    @Override public void deserializeNBT(CompoundTag tag) {
        for (Direction side : Direction.values()) {
            Mode mode;
            try { mode = Mode.valueOf(tag.getString(side.getName()).toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException invalid) { mode = defaultMode; }
            sides.put(side, mode);
        }
    }
}
