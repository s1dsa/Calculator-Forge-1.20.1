package sonar.calculator.mod.machine;

/** Immutable slot/config layout, using the original registry identifiers and configuration names. */
public record MachineSpec(String config, String recipe, int inputs, int outputs, int battery, int slots,
                          int capacity, int transfer, int duration, int cost, boolean generator) {
    static MachineSpec of(String id) {
        return switch (id) {
            case "reinforcedfurnace" -> process("REINFORCED_FURNACE", "smelting", 1, 1, 200, 500);
            case "stoneseparator" -> process("STONE_SEPERATOR", "stone", 1, 2, 200, 500);
            case "algorithmseparator" -> process("ALGORITHM_SEPERATOR", "algorithm", 1, 2, 200, 5000);
            case "extractionchamber" -> process("EXTRACTION_CHAMBER", "extraction", 1, 2, 1000, 5000);
            case "restorationchamber" -> process("RESTORATION_CHAMBER", "restoration", 1, 1, 1000, 1000);
            case "reassemblychamber" -> process("REASSEMBLY_CHAMBER", "reassembly", 1, 1, 1000, 1000);
            case "precisionchamber" -> process("PRECISION_CHAMBER", "precision", 1, 2, 500, 5000);
            case "processingchamber" -> process("PROCESSING_CHAMBER", "processing", 1, 1, 500, 1000);
            case "dockingstation" -> new MachineSpec("DOCKING_STATION", "docking", 4, 1, 4, 7, 50000, 3200, 25, 10, false);
            case "atomiccalculator" -> new MachineSpec("", "atomic", 3, 1, -1, 4, 0, 0, 1, 0, false);
            case "dynamiccalculator" -> new MachineSpec("", "dynamic", 7, 3, -1, 10, 0, 0, 1, 0, false);
            case "atomicmultiplier" -> new MachineSpec("ATOMIC_MULTIPLIER", "multiply", 8, 1, 9, 10, 1500000000, Integer.MAX_VALUE, 1000, 1500000000, false);
            case "analysingchamber" -> new MachineSpec("ANALYSING_CHAMBER", "analysing", 1, 6, 1, 8, 100000, 12800, 1, 0, true);
            case "fabricationchamber" -> new MachineSpec("", "fabrication", 0, 1, -1, 1, 0, 0, 300, 0, false);
            case "starchextractor" -> extractor("STARCH_EXTRACTOR", "starch");
            case "redstoneextractor" -> extractor("REDSTONE_EXTRACTOR", "redstone");
            case "glowstoneextractor" -> extractor("GLOWSTONE_EXTRACTOR", "glowstone");
            case "powercube" -> power("POWER_CUBE", 50000, 400, false);
            case "advancedpowercube" -> power("ADVANCED_POWER_CUBE", 100000, 64000, true);
            case "creativepowercube" -> power("CREATIVE_POWER_CUBE", Integer.MAX_VALUE, Integer.MAX_VALUE, true);
            case "handcrankedgenerator" -> new MachineSpec("HAND_CRANK", "", 0, 0, -1, 0, 1000, 400, 1, 0, true);
            case "conductormast" -> new MachineSpec("CONDUCTOR_MAST", "conductor_mast", 1, 1, -1, 2, 50000000, Integer.MAX_VALUE, 50, 0, true);
            case "calculatorlocator" -> new MachineSpec("CALCULATOR_LOCATOR", "", 1, 0, 0, 2, 50000000, Integer.MAX_VALUE, 1, 0, true);
            case "co2generator" -> new MachineSpec("C02_GENERATOR", "", 1, 0, 1, 2, 1000000, 64000, 10000, 100000, false);
            case "basicgreenhouse" -> greenhouse("BASIC_GREENHOUSE", 4, 14, 350000, 400);
            case "advancedgreenhouse" -> greenhouse("ADVANCED_GREENHOUSE", 7, 17, 350000, 3200);
            case "flawlessgreenhouse" -> greenhouse("FLAWLESS_GREENHOUSE", 0, 10, 500000, 64000);
            case "weathercontroller" -> new MachineSpec("WEATHER_CONTROLLER", "", 0, 0, 0, 1, 1000000, 64000, 100, 250000, false);
            case "storagechamber" -> plain(14);
            case "reinforcedchest" -> plain(81);
            case "algorithmassimilator" -> plain(27);
            case "stoneassimilator", "calculatorplug" -> plain(1);
            case "healthprocessor", "hungerprocessor" -> plain(2);
            case "gaslanternon", "gaslanternoff" -> plain(1);
            case "magneticflux" -> plain(8);
            case "moduleworkstation" -> plain(2);
            default -> plain(0);
        };
    }
    private static MachineSpec process(String config, String recipe, int inputs, int outputs, int duration, int cost) {
        return new MachineSpec(config, recipe, inputs, outputs, inputs, inputs + outputs + 1, 50000, 3200, duration, cost, false);
    }
    private static MachineSpec extractor(String config, String recipe) {
        return new MachineSpec(config, recipe, 2, 0, -1, 2, 1000000, 3200, 1, 0, true);
    }
    private static MachineSpec power(String config, int capacity, int transfer, boolean generator) {
        return new MachineSpec(config, "", 0, 0, 1, 2, capacity, transfer, 1, 0, generator);
    }
    private static MachineSpec greenhouse(String config, int battery, int slots, int capacity, int transfer) {
        return new MachineSpec(config, "", 0, 0, battery, slots, capacity, transfer, 1, 0, false);
    }
    private static MachineSpec plain(int slots) { return new MachineSpec("", "", slots, 0, -1, slots, 0, 0, 1, 0, false); }
    int configured(String suffix, int fallback) { return config.isEmpty() ? fallback : MachineConfig.integer(config + "_" + suffix, fallback); }
    int configuredCapacity() { return configured("STORAGE", capacity); }
    int configuredTransfer() { return configured("TRANSFER_RATE", transfer); }
    int outputStart() { return battery == inputs ? inputs + 1 : inputs; }
}
