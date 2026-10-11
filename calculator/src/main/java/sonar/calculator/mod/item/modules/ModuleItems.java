package sonar.calculator.mod.item.modules;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import sonar.calculator.mod.CalculatorConfig;
import sonar.calculator.mod.item.PortableItem;
import sonar.calculator.mod.item.StackEnergy;

public final class ModuleItems {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "calculator");
    public static final RegistryObject<MenuType<ModuleMenu>> MODULE_MENU = MENUS.register("modules", () -> IForgeMenuType.create(ModuleMenu::new));
    public static void register(IEventBus bus) { MENUS.register(bus); }
    @Nullable public static Item create(String id) {
        return switch (id) {
            case "flawlesscalculator" -> new FlawlessCalculatorItem();
            case "hungermodule" -> new NutritionItem("hunger");
            case "healthmodule" -> new NutritionItem("health");
            case "nutritionmodule" -> new NutritionItem("nutrition");
            case "terrainmodule" -> new TerrainItem(false);
            case "advancedterrainmodule" -> new TerrainItem(true);
            case "warpmodule" -> new TravelItem(false);
            case "jumpmodule" -> new TravelItem(true);
            case "energymodule" -> new PortableItem("energy", () -> CalculatorConfig.ENERGY_MODULE_STORAGE) {
                @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                    ItemStack stack = player.getItemInHand(hand);
                    if (!level.isClientSide) player.displayClientMessage(Component.translatable("calc.energyModule", StackEnergy.get(stack)), true);
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
            };
            case "locatormodule" -> new LocatorItem();
            case "infocalculator" -> new InfoItem();
            default -> null;
        };
    }
    public static boolean supportsNutrition(ItemStack stack, boolean hunger) { return stack.getItem() instanceof NutritionItem item && item.supports(hunger); }
    public static int getNutrition(ItemStack stack, boolean hunger) { return stack.getItem() instanceof NutritionItem item ? item.points(stack, hunger) : 0; }
    public static int getNutritionCapacity(ItemStack stack, boolean hunger) { return supportsNutrition(stack, hunger) ? ((NutritionItem) stack.getItem()).capacity(hunger) : 0; }
    public static void setNutrition(ItemStack stack, boolean hunger, int amount) { if (stack.getItem() instanceof NutritionItem item) item.setPoints(stack, hunger, amount); }
    public static void transferNutrition(ItemStack stack, boolean hunger, int signedAmount) { if (stack.getItem() instanceof NutritionItem item) item.transfer(stack, hunger, signedAmount); }
    public static boolean installModule(ItemStack calculator, int slot, ItemStack input) { return calculator.getItem() instanceof FlawlessCalculatorItem item && item.install(calculator, slot, input); }
    public static ItemStack removeModule(ItemStack calculator, int slot) { return calculator.getItem() instanceof FlawlessCalculatorItem item ? item.remove(calculator, slot) : ItemStack.EMPTY; }
    public static String moduleName(ItemStack calculator, int slot) { return calculator.getItem() instanceof FlawlessCalculatorItem item ? item.moduleName(calculator, slot) : "Empty"; }
    public static int getEnergy(ItemStack stack) { return stack.getItem() instanceof FlawlessCalculatorItem item ? item.totalEnergy(stack) : StackEnergy.get(stack); }
    public static boolean hasEnergy(ItemStack stack, Player player, int amount) {
        boolean available = player.getAbilities().instabuild || getEnergy(stack) >= amount;
        if (!available && !player.level().isClientSide) player.displayClientMessage(Component.translatable("energy.notEnough"), true);
        return available;
    }
    public static boolean consumeEnergy(ItemStack stack, Player player, int amount) {
        if (player.level().isClientSide || amount < 0) return false;
        if (player.getAbilities().instabuild) return true;
        if (getEnergy(stack) < amount) return false;
        if (stack.getItem() instanceof FlawlessCalculatorItem item) return item.transferEnergy(stack, amount, false, false, false) == amount;
        return StackEnergy.consume(stack, amount, false);
    }
    @Nullable public static String locatorOwner(ItemStack stack) {
        if (!(stack.getItem() instanceof LocatorItem) || !stack.hasTag()) return null;
        String name = stack.getTag().getString("Player"); return name.isBlank() || name.equals("None") ? null : name;
    }
    @Nullable public static UUID locatorOwnerId(ItemStack stack) { return stack.getItem() instanceof LocatorItem && stack.hasTag() && stack.getTag().hasUUID("PlayerUUID") ? stack.getTag().getUUID("PlayerUUID") : null; }
    private static final class LocatorItem extends Item {
        LocatorItem() { super(new Properties().stacksTo(1)); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide) {
                stack.getOrCreateTag().putString("Player", player.getGameProfile().getName());
                stack.getOrCreateTag().putUUID("PlayerUUID", player.getUUID());
                player.displayClientMessage(Component.translatable("locator.owner").append(": " + player.getGameProfile().getName()), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack stack, @Nullable Level level, java.util.List<Component> lines, TooltipFlag flag) {
            String owner = locatorOwner(stack); lines.add(Component.translatable("locator.owner").append(": ").append(owner == null ? Component.translatable("locator.none") : Component.literal(owner)));
        }
    }
    static final class InfoItem extends Item {
        InfoItem() { super(new Properties().stacksTo(1)); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer server) ModuleMenu.open(server, hand, true);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
    }
    private ModuleItems() {}
}
