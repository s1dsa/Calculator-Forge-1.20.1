package sonar.calculator.mod.machine;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Machine registrations are evaluated after the block registry is populated. */
public final class MachineContent {
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "calculator");
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "calculator");
    public static final RegistryObject<BlockEntityType<MachineBlockEntity>> MACHINE = ENTITIES.register("machine", () ->
            BlockEntityType.Builder.of(MachineBlockEntity::new, ForgeRegistries.BLOCKS.getValues().stream()
                    .filter(block -> block instanceof MachineBlock).toArray(Block[]::new)).build(null));
    public static final RegistryObject<MenuType<MachineMenu>> MENU = MENUS.register("machine", () -> IForgeMenuType.create(MachineMenu::new));
    private MachineContent() {}
    public static void register(IEventBus bus) { ENTITIES.register(bus); MENUS.register(bus); }
}
