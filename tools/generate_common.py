from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = ROOT.parent / 'upstream' / 'Calculator' / 'src/main/java/sonar/calculator/mod'
DEST = ROOT / 'calculator/src/main/java/sonar/calculator/mod'


def uncomment(text):
    return re.sub(r'//[^\n]*|/\*.*?\*/', '', text, flags=re.S)


def registry():
    blocks_source = uncomment((UPSTREAM / 'CalculatorBlocks.java').read_text(encoding='utf-8'))
    items_source = uncomment((UPSTREAM / 'CalculatorItems.java').read_text(encoding='utf-8'))
    blocks = []
    for line in blocks_source.splitlines():
        if 'SonarRegister.addBlock' in line:
            assignment = re.search(r'(\w+)\s*=\s*SonarRegister\.addBlock', line)
            names = re.findall(r'"([A-Za-z0-9_]+)"', line)
            if not assignment or not names:
                raise ValueError('Unparsed block registration: ' + line)
            blocks.append((assignment[1], names[0].lower(), names[0]))
        elif 'gas_lantern_on = new GasLantern' in line:
            blocks.append(('gas_lantern_on', 'gaslanternon', 'GasLanternOn'))
    items = []
    for line in items_source.splitlines():
        if 'SonarRegister.addItem' not in line:
            continue
        assignment = re.search(r'(\w+)\s*=\s*SonarRegister\.addItem', line)
        names = re.findall(r'"([A-Za-z0-9_]+)"', line)
        if not assignment or not names:
            raise ValueError('Unparsed item registration: ' + line)
        items.append((assignment[1], names[0].lower(), names[0]))
    block_ids = [row[1] for row in blocks] + [f'material_{i}' for i in range(1, 10)]
    item_ids = [row[1] for row in items]
    for base in ('circuitboard', 'circuitdamaged', 'circuitdirty'):
        item_ids += [f'{base}_{i}' for i in range(1, 14)]
    assert len(set(block_ids)) == len(block_ids)
    assert len(set(item_ids)) == len(item_ids)
    data = {'blocks': blocks, 'items': items, 'block_ids': block_ids, 'item_ids': item_ids}
    target = ROOT / 'notes' / 'registry-map.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    print(f'Blocks: {len(blocks)} legacy / {len(block_ids)} flattened; items: {len(items)} legacy / {len(item_ids)} flattened')
    return data


def config():
    text = (UPSTREAM / 'CalculatorConfig.java').read_text(encoding='utf-8')
    constants = re.findall(r'public static final int (\w+) = (\d+);', text)
    settings = re.findall(r'(\w+)\s*=\s*INT\(config,\s*"([^"]+)",\s*"([^"]+)",\s*([^,]+),\s*([^,]+),\s*([^\)]+)\);', text)
    assert len(settings) >= 100, len(settings)
    declarations = []
    definitions = []
    updates = []
    for name, category, title, default, minimum, maximum in settings:
        key = name.lower()
        declarations += [f'    public static int {name} = {default};']
        definitions += [f'        INTS.put("{name}", builder.comment("{category}: {title}").defineInRange("{key}", {default}, {minimum}, {maximum}));']
        updates += [f'        {name} = INTS.get("{name}").get();']
    code = '''package sonar.calculator.mod;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CalculatorConfig {
'''
    code += '\n'.join(f'    public static final int {name} = {value};' for name, value in constants) + '\n'
    code += '\n'.join(declarations) + '\n'
    code += '''    public static float CALCULATOR_LOCATOR_MULTIPLIER = 2.0F;
    public static boolean CALCULATOR_LOCATOR_CAN_CHANGE_TIME = true;
    public static boolean beamEffect = false;
    public static boolean enableWaila = true;
    public static boolean enableGrenades = true;
    public static boolean enableToolModels = true;
    public static final ForgeConfigSpec SPEC;
    private static final Map<String, ForgeConfigSpec.IntValue> INTS = new LinkedHashMap<>();
    private static final ForgeConfigSpec.DoubleValue LOCATOR_MULTIPLIER;
    private static final ForgeConfigSpec.BooleanValue LOCATOR_TIME;
    private static final ForgeConfigSpec.BooleanValue GRENADES;
    private static final ForgeConfigSpec.BooleanValue TOOL_MODELS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ATOMIC_BLACKLIST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
'''
    code += '\n'.join(definitions) + '\n'
    code += '''        LOCATOR_MULTIPLIER = builder.defineInRange("calculator_locator_multiplier", 2.0, 0.1, 64.0);
        LOCATOR_TIME = builder.define("calculator_locator_can_change_time", true);
        GRENADES = builder.define("enable_grenades", true);
        TOOL_MODELS = builder.define("enable_tool_models", true);
        ATOMIC_BLACKLIST = builder.defineListAllowEmpty("atomic_multiplier_blacklist", List.of(
                "minecraft:nether_star", "calculator:atomicmultiplier", "calculator:endblock",
                "calculator:electricblock", "calculator:flawlessfireblock", "calculator:flawlessblock",
                "calculator:electricdiamond", "calculator:flawlessfirediamond", "calculator:flawlessdiamond"
        ), value -> value instanceof String);
        SPEC = builder.build();
    }

    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
'''
    code += '\n'.join(updates) + '\n'
    code += '''        CALCULATOR_LOCATOR_MULTIPLIER = LOCATOR_MULTIPLIER.get().floatValue();
        CALCULATOR_LOCATOR_CAN_CHANGE_TIME = LOCATOR_TIME.get();
        enableGrenades = GRENADES.get();
        enableToolModels = TOOL_MODELS.get();
    }

    public static boolean isEnabled(ItemStack stack) {
        return true;
    }

    private CalculatorConfig() {}
}
'''
    DEST.mkdir(parents=True, exist_ok=True)
    (DEST / 'CalculatorConfig.java').write_text(code, encoding='utf-8')
    print(f'Converted {len(settings)} integer settings directly from upstream defaults and bounds')


if __name__ == '__main__':
    registry()
    config()
