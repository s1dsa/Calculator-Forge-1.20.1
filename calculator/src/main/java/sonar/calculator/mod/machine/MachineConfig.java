package sonar.calculator.mod.machine;

import java.lang.reflect.Field;
import sonar.calculator.mod.CalculatorConfig;

/** Reads coordinator-owned configuration fields without duplicating its config schema. */
final class MachineConfig {
    private MachineConfig() {}
    static int integer(String name, int fallback) {
        try {
            Field field = CalculatorConfig.class.getField(name);
            Object value = field.get(null);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return fallback;
        }
    }
    static float decimal(String name, float fallback) {
        try {
            Field field = CalculatorConfig.class.getField(name);
            Object value = field.get(null);
            return value instanceof Number number ? number.floatValue() : fallback;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return fallback;
        }
    }
}
