package sonar.core;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import sonar.core.network.SonarNetwork;

/** Modern Forge entry point. Original content and artwork by Ollie Lansdell (MIT). */
@Mod(SonarCore.MODID)
public final class SonarCore {
    public static final String MODID = "sonarcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SonarCore() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        SonarContent.register(bus);
        SonarNetwork.register();
    }
}
