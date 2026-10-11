package sonar.calculator.mod.recipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class RecipeContent {
    public static final List<String> MACHINE_TYPES = List.of("calculator", "scientific", "atomic", "flawless",
            "stone", "algorithm", "extraction", "restoration", "reassembly", "precision", "processing",
            "analysing", "fabrication", "starch", "redstone", "glowstone", "conductor_mast", "health", "harvest");
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, "calculator");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "calculator");
    private static final DeferredRegister<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType> LOOT_FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, "calculator");
    public static final RegistryObject<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType> CROP_SEEDS = LOOT_FUNCTIONS.register("legacy_crop_seeds", () -> new net.minecraft.world.level.storage.loot.functions.LootItemFunctionType(new LegacyCropSeedsFunction.Serializer()));
    private static final Map<String, RegistryObject<RecipeType<MachineRecipe>>> TYPE_MAP = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<MachineRecipeSerializer>> SERIALIZER_MAP = new LinkedHashMap<>();
    static {
        for (String name : MACHINE_TYPES) {
            TYPE_MAP.put(name, TYPES.register(name, () -> new RecipeType<MachineRecipe>() {
                @Override public String toString() { return "calculator:" + name; }
            }));
            SERIALIZER_MAP.put(name, SERIALIZERS.register(name, () -> new MachineRecipeSerializer(name)));
        }
    }
    private RecipeContent() {}
    public static void register(IEventBus bus) { TYPES.register(bus); SERIALIZERS.register(bus); LOOT_FUNCTIONS.register(bus); }
    public static RecipeType<MachineRecipe> type(String name) {
        if (!TYPE_MAP.containsKey(name)) throw new IllegalArgumentException("Unknown machine recipe type " + name);
        return TYPE_MAP.get(name).get();
    }
    public static MachineRecipeSerializer serializer(String name) {
        if (!SERIALIZER_MAP.containsKey(name)) throw new IllegalArgumentException("Unknown machine recipe serializer " + name);
        return SERIALIZER_MAP.get(name).get();
    }
}
