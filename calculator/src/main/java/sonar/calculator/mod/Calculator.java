package sonar.calculator.mod;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import sonar.calculator.mod.item.ItemFactory;
import sonar.calculator.mod.machine.MachineContent;
import sonar.calculator.mod.recipe.RecipeContent;
import sonar.calculator.mod.world.WorldContent;

@Mod(Calculator.MODID)
public final class Calculator {
    public static final String MODID = "calculator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Calculator() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CalculatorConfig.SPEC, "calculator-common.toml");
        bus.addListener(CalculatorConfig::onConfig);
        CalculatorContent.register(bus);
        RecipeContent.register(bus);
        WorldContent.register(bus);
        MachineContent.register(bus);
        ItemFactory.register(bus);
    }
}
