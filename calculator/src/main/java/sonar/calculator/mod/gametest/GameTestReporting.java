package sonar.calculator.mod.gametest;

import java.io.File;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "calculator", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GameTestReporting {
    @SubscribeEvent
    public static void configure(ServerAboutToStartEvent event) {
        String target = System.getProperty("forge.gameTestServer.report");
        if (target == null || !(event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) return;
        File report = new File(target);
        File directory = report.getAbsoluteFile().getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create GameTest report directory: " + directory);
        }
        try {
            GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(report));
        } catch (javax.xml.parsers.ParserConfigurationException exception) {
            throw new IllegalStateException("Cannot initialize native GameTest reporter", exception);
        }
    }
    private GameTestReporting() {}
}
