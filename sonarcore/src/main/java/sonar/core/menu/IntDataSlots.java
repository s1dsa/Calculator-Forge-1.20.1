package sonar.core.menu;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.world.inventory.ContainerData;

/** ContainerData integers are truncated to shorts on the wire; these pairs preserve all 32 bits. */
public final class IntDataSlots {
    private IntDataSlots() { }

    public static ContainerData of(IntSupplier getter, IntConsumer setter) {
        return new ContainerData() {
            @Override public int get(int index) {
                check(index);
                return (getter.getAsInt() >>> (index * 16)) & 0xffff;
            }
            @Override public void set(int index, int value) {
                check(index);
                int shift = index * 16;
                setter.accept((getter.getAsInt() & ~(0xffff << shift)) | ((value & 0xffff) << shift));
            }
            @Override public int getCount() { return 2; }
            private void check(int index) {
                if (index < 0 || index > 1) throw new IndexOutOfBoundsException(index);
            }
        };
    }

    /** Client buffer paired with a server-side instance of of(). */
    public static ContainerData buffer() {
        int[] value = {0};
        return of(() -> value[0], next -> value[0] = next);
    }

    public static int get(ContainerData pair) { return (pair.get(0) & 0xffff) | (pair.get(1) << 16); }
}
