package sonar.calculator.mod.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.CalculatorConfig;

/** The active upstream InfoRegistry entries, in their original order. */
public final class InfoCatalog {
    public enum Category {
        All, Calculators, Modules, Circuits, Nutrition, Generation, Machines, Tools, Items, Blocks;

        /** Translation key for the category filter button tooltip. */
        public String translationKey() {
            return this == Generation ? "info.generators" : "info." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
    public record Entry(Category category, String id, Component text, List<String> related) {
        public ItemStack stack() { return CalculatorContent.stack(id, 1); }
    }
    public static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        add(entries, Category.Calculators, "calculator", "info.Calculator.name", "powercube", "researchchamber", "scientificcalculator");
        add(entries, Category.Calculators, "craftingcalculator", "info.CraftingCalculator.name", "powercube");
        add(entries, Category.Calculators, "scientificcalculator", "info.ScientificCalculator.name", "powercube", "atomiccalculator");
        add(entries, Category.Calculators, "flawlesscalculator", "info.FlawlessCalculator.name", "powercube");
        add(entries, Category.Calculators, "atomiccalculator", "info.AtomicCalculator.name");
        add(entries, Category.Calculators, "dynamiccalculator", "info.DynamicCalculator.name");
        add(entries, Category.Calculators, "infocalculator", "info.InfoCalculator.name");
        add(entries, Category.Modules, "terrainmodule", "info.BasicTerrainModule.name", "advancedterrainmodule");
        add(entries, Category.Modules, "advancedterrainmodule", "info.AdvancedTerrainModule.name", "terrainmodule");
        add(entries, Category.Modules, "energymodule", Component.translatable("info.EnergyModule.name",
                CalculatorConfig.ENERGY_MODULE_STORAGE, CalculatorConfig.ENERGY_MODULE_STORAGE / 4), "powercube", "flawlesscalculator");
        add(entries, Category.Modules, "storagemodule", "info.StorageModule.name", "flawlesscalculator");
        add(entries, Category.Modules, "locatormodule", "info.LocatorModule.name", "calculatorlocator");
        add(entries, Category.Modules, "jumpmodule", "info.JumpModule.name", "flawlesscalculator");
        add(entries, Category.Modules, "warpmodule", "info.WarpModule.name", "flawlesscalculator");
        add(entries, Category.Modules, "moduleworkstation", "info.ModuleWorkstation.name", "flawlesscalculator");
        add(entries, Category.Circuits, "circuitboard", "info.CircuitBoard.name", "circuitdamaged", "circuitdirty", "analysingchamber", "storagechamber", "fabricationchamber", "calculatorlocator");
        add(entries, Category.Circuits, "circuitdamaged", "info.DirtyCircuit.name","precisionchamber", "extractionchamber","reassemblychamber","processingchamber", "storagechamber", "circuitboard");
        add(entries, Category.Circuits, "circuitdirty", "info.DamagedCircuit.name", "precisionchamber", "extractionchamber","restorationchamber","processingchamber", "storagechamber", "circuitboard");
        add(entries, Category.Circuits, "atomicassembly", "info.AtomicAssembly.name", "atomiccalculator", "atomicmultiplier");
        add(entries, Category.Circuits, "energyupgrade", "info.EnergyUpgrade.name","speedupgrade","voidupgrade","transferupgrade");
        add(entries, Category.Circuits, "speedupgrade", "info.SpeedUpgrade.name","energyupgrade","voidupgrade","transferupgrade");
        add(entries, Category.Circuits, "voidupgrade", "info.VoidUpgrade.name","energyupgrade","speedupgrade","transferupgrade");
        add(entries, Category.Circuits, "transferupgrade", "info.TransferUpgrade.name","energyupgrade","speedupgrade","voidupgrade");
        add(entries, Category.Nutrition, "scarecrow", "info.Scarecrow.name", "basicgreenhouse", "advancedgreenhouse", "flawlessgreenhouse");
        add(entries, Category.Nutrition, "amethystsapling", "info.AmethystSapling.name", "hungerprocessor");
        add(entries, Category.Nutrition, "tanzanitesapling", "info.TanzaniteSapling.name", "healthprocessor");
        add(entries, Category.Nutrition, "pearsapling", "info.PearSapling.name", "rottenpear");
        add(entries, Category.Nutrition, "diamondsapling", "info.DiamondSapling.name");
        add(entries, Category.Nutrition, "fiddledewfruit", "info.FiddledewFruit.name", "flawlessgreenhouse");
        add(entries, Category.Nutrition, "prunaeseeds", "info.PrunaeSeeds.name", "advancedgreenhouse");
        add(entries, Category.Nutrition, "broccoli", "info.Broccoli.name", "cookedbroccoli");
        add(entries, Category.Nutrition, "cookedbroccoli", "info.CookedBroccoli.name","broccoli");
        add(entries, Category.Nutrition, "rottenpear", "info.RottenPear.name","pearsapling");
        add(entries, Category.Nutrition, "amethystpiping", "info.AmethystPiping.name", "amethystsapling", "hungerprocessor");
        add(entries, Category.Nutrition, "tanzanitepiping", "info.TanzanitePiping.name","tanzanitesapling", "healthprocessor");
        add(entries, Category.Nutrition, "hungerprocessor", "info.HungerProcessor.name", "amethystpiping");
        add(entries, Category.Nutrition, "healthprocessor", "info.HealthProcessor.name", "tanzanitepiping");
        add(entries, Category.Nutrition, "hungermodule", "info.HungerModule.name","amethystsapling", "hungerprocessor", "nutritionmodule");
        add(entries, Category.Nutrition, "healthmodule", "info.HealthModule.name", "tanzanitesapling", "healthprocessor", "nutritionmodule");
        add(entries, Category.Nutrition, "nutritionmodule", "info.NutritionModule.name", "healthmodule", "hungermodule", "amethystsapling","tanzanitesapling", "hungerprocessor", "healthprocessor");
        add(entries, Category.Nutrition, "basicgreenhouse", "info.BasicGreenhouse.name","advancedgreenhouse");
        add(entries, Category.Nutrition, "advancedgreenhouse", "info.AdvancedGreenhouse.name", "flawlessgreenhouse", "prunaeseeds");
        add(entries, Category.Nutrition, "flawlessgreenhouse", "info.FlawlessGreenhouse.name", "flawlessglass", "fiddledewfruit");
        add(entries, Category.Nutrition, "co2generator", "info.CarbonDioxideGenerator.name");
        add(entries, Category.Generation, "handcrankedgenerator", "info.HandCrankedGenerator.name","crankhandle");
        add(entries, Category.Generation, "crankhandle", "info.CrankHandle.name","handcrankedgenerator");
        add(entries, Category.Generation, "starchextractor", "info.StarchExtractor.name","redstoneextractor","glowstoneextractor");
        add(entries, Category.Generation, "redstoneextractor", "info.RedstoneExtractor.name","starchextractor","glowstoneextractor");
        add(entries, Category.Generation, "glowstoneextractor", "info.GlowstoneExtractor.name","starchextractor","redstoneextractor");
        add(entries, Category.Generation, "conductormast", "info.ConductorMast.name", "transmitter", "weatherstation");
        add(entries, Category.Generation, "weatherstation", "info.WeatherStation.name", "conductormast", "transmitter");
        add(entries, Category.Generation, "transmitter", "info.Transmitter.name","conductormast", "weatherstation");
        add(entries, Category.Generation, "calculatorlocator", "info.CalculatorLocator.name","calculatorplug", "locatormodule");
        add(entries, Category.Generation, "calculatorplug", "info.CalculatorPlug.name","circuitboard","analysingchamber", "calculatorlocator");
        add(entries, Category.Machines, "powercube", "info.PowerCube.name", "advancedpowercube");
        add(entries, Category.Machines, "advancedpowercube", "info.AdvancedPowerCube.name", "powercube");
        add(entries, Category.Machines, "reinforcedfurnace", "info.ReinforcedFurnace.name");
        add(entries, Category.Machines, "stoneseparator", "info.StoneSeparator.name", "amethystsapling");
        add(entries, Category.Machines, "algorithmseparator", "info.AlgorithmSeparator.name", "tanzanitesapling");
        add(entries, Category.Machines, "extractionchamber", "info.ExtractionChamber.name", "precisionchamber");
        add(entries, Category.Machines, "reassemblychamber", "info.ReassemblyChamber.name", "restorationchamber", "processingchamber");
        add(entries, Category.Machines, "restorationchamber", "info.RestorationChamber.name", "reassemblychamber", "processingchamber");
        add(entries, Category.Machines, "precisionchamber", "info.PrecisionChamber.name", "restorationchamber");
        add(entries, Category.Machines, "processingchamber", "info.ProcessingChamber.name", "restorationchamber", "reassemblychamber");
        add(entries, Category.Machines, "analysingchamber", "info.AnalysingChamber.name", "circuitboard");
        add(entries, Category.Machines, "storagechamber", "info.StorageChamber.name");
        add(entries, Category.Machines, "fabricationchamber", "info.FabricationChamber.name");
        add(entries, Category.Machines, "rainsensor", "info.RainSensor.name");
        add(entries, Category.Machines, "weatherstation", "info.WeatherStationMachine.name");
        add(entries, Category.Machines, "magneticflux", "info.MagneticFlux.name");
        add(entries, Category.Machines, "stoneassimilator", "info.StoneAssimilator.name");
        add(entries, Category.Machines, "algorithmassimilator", "info.AlgorithmAssimilator.name");
        add(entries, Category.Machines, "atomicmultiplier", Component.translatable("info.AtomicMultiplier.name",
                CalculatorConfig.ATOMIC_MULTIPLIER_USAGE, CalculatorConfig.ATOMIC_MULTIPLIER_USAGE / 4));
        add(entries, Category.Tools, "wrench", "info.Wrench.name");
        add(entries, Category.Tools, "sickle", "info.Sickle.name");
        add(entries, Category.Tools, "obsidiankey", "info.ObsidianKey.name");
        add(entries, Category.Tools, "endforgedsword", "info.EndForgedSword.name", "enddiamond");
        add(entries, Category.Tools, "endforgedhoe", "info.EndForgedHoe.name", "enddiamond");
        add(entries, Category.Items, "babygrenade", "info.BabyGrenade.name");
        add(entries, Category.Items, "grenade", "info.Grenade.name");
        add(entries, Category.Items, "soil", "info.Soil.name");
        add(entries, Category.Items, "smallstone", "info.SmallStone.name");
        add(entries, Category.Items, "enddiamond", "info.EndDiamond.name", "endforgedsword");
        add(entries, Category.Items, "controlledfuel", "info.ControlledFuel.name", "co2generator");
        add(entries, Category.Items, "gaslanternoff", "info.GasLantern.name","lantern");
        add(entries, Category.Items, "lantern", "info.BasicLantern.name", "gaslanternoff");
        add(entries, Category.Items, "calculatorscreen", "info.CalculatorScreenBlock.name");
        add(entries, Category.Blocks, "purifiedobsidian", "info.PurifiedObsidian.name", "obsidiankey");
        add(entries, Category.Blocks, "flawlessglass", "info.FlawlessGlass.name", "flawlessgreenhouse");
        add(entries, Category.Blocks, "reinforcedchest", "info.ReinforcedChest.name");
        return List.copyOf(entries);
    }
    private static void add(List<Entry> entries, Category category, String id, String textKey, String... related) {
        entries.add(new Entry(category, id, Component.translatable(textKey), List.of(related)));
    }
    private static void add(List<Entry> entries, Category category, String id, Component text, String... related) {
        entries.add(new Entry(category, id, text, List.of(related)));
    }
    private InfoCatalog() {}
}
