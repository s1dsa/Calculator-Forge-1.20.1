package sonar.calculator.mod.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.client.screen.MachineScreen;
import sonar.calculator.mod.client.screen.PortableScreen;
import sonar.calculator.mod.client.screen.ModuleScreen;
import sonar.calculator.mod.item.modules.ModuleItems;
import sonar.calculator.mod.client.render.MachineRenderer;
import sonar.calculator.mod.item.ItemFactory;
import sonar.calculator.mod.machine.MachineContent;

/** Only the client mod bus loads screens, models and renderer classes. */
@Mod.EventBusSubscriber(modid = "calculator", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CalculatorClient {
    /**
     * Blocks whose world model samples a texture that contains transparent pixels, so the holes have
     * to be alpha-tested instead of drawn opaque. Forge 1.20.1 has no model {@code render_type}, so the
     * layer must be registered here. Covers feedback #1 (flawless glass), #6 (reinforced chest),
     * #10/#13 (the five chambers plus the analysing/fabrication chambers) and #15 (crops and saplings).
     * The set is exactly the blocks {@code tools/audit_render_layers.py} proves to have holes in their
     * world-model textures, so nothing opaque is given a needless alpha pass.
     */
    private static final String[] CUTOUT_BLOCKS = {
            "lantern", "gaslanternon", "gaslanternoff",
            "flawlessglass",
            "extractionchamber", "precisionchamber", "reassemblychamber", "restorationchamber",
            "processingchamber", "analysingchamber", "fabricationchamber",
            "atomicmultiplier", "magneticflux", "stoneassimilator", "algorithmassimilator",
            "amethystsapling", "tanzanitesapling", "pearsapling", "diamondsapling",
            "cropbroccoli", "cropprunae", "cropfiddledew"
    };
    /** Leaves are drawn with the mipmapped alpha test, matching vanilla (#15). */
    private static final String[] CUTOUT_MIPPED_BLOCKS = {
            "amethystleaves", "tanzaniteleaves", "pearleaves", "diamondleaves"
    };

    private CalculatorClient() {}

    /** Registers the chunk render layers that carry the transparent parts of the block models. */
    private static void registerCutoutLayers() {
        for (String id : CUTOUT_BLOCKS) ItemBlockRenderTypes.setRenderLayer(CalculatorContent.block(id), RenderType.cutout());
        for (String id : CUTOUT_MIPPED_BLOCKS) ItemBlockRenderTypes.setRenderLayer(CalculatorContent.block(id), RenderType.cutoutMipped());
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(MachineContent.MENU.get(), MachineScreen::new);
            MenuScreens.register(ItemFactory.PORTABLE_MENU.get(), PortableScreen::new);
            MenuScreens.register(ModuleItems.MODULE_MENU.get(), ModuleScreen::new);
            registerCutoutLayers();
        });
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MachineContent.MACHINE.get(), MachineRenderer::new);
        event.registerEntityRenderer(ItemFactory.SOIL.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ItemFactory.STONE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ItemFactory.BABY_GRENADE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ItemFactory.GRENADE.get(), ThrownItemRenderer::new);
    }
}
