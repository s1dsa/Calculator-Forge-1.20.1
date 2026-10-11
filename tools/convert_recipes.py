"""Convert the immutable Calculator 1.12 registrations to 1.20 data. No guessed recipes.
Run from the project root: python tools/convert_recipes.py.
Every generated recipe records its source; disabled research is deliberately excluded.
"""
from pathlib import Path
import json
import re
from collections import Counter

ROOT = Path(__file__).resolve().parents[1]
UP = ROOT.parent / 'upstream/Calculator/src/main/java/sonar/calculator/mod'
DATA = ROOT / 'calculator/src/main/resources/data'
REG = json.loads((ROOT / 'notes/registry-map.json').read_text())
FIELDS = {row[0]: 'calculator:' + row[1] for row in REG['blocks'] + REG['items']}
MATERIALS = ['largeamethyst', 'largetanzanite', 'enrichedgoldingot', 'reinforcedironingot', 'weakeneddiamond', 'flawlessdiamond', 'firediamond', 'electricdiamond', 'enddiamond', 'redstoneingot']
VARIANTS = dict(zip(['AMETHYST','TANZANITE','ENRICHED_GOLD','REINFORCED_IRON','WEAKENED_DIAMOND','FLAWLESS_DIAMOND','FIRE_DIAMOND','ELECTRIC_DIAMOND','END_DIAMOND','REDSTONE_INGOT'], range(10)))
WOODS = ['oak','spruce','birch','jungle','acacia','dark_oak']
TAGS = {
 'logWood':'minecraft:logs', 'plankWood':'minecraft:planks', 'stickWood':'forge:rods/wooden',
 'treeSapling':'minecraft:saplings', 'treeLeaves':'minecraft:leaves', 'stairWood':'minecraft:wooden_stairs',
 'cobblestone':'forge:cobblestone', 'stone':'forge:stone', 'sand':'forge:sand', 'sandstone':'forge:sandstone',
 'blockGlass':'forge:glass/colorless', 'gemLapis':'forge:gems/lapis', 'blockLapis':'forge:storage_blocks/lapis',
 'gemDiamond':'forge:gems/diamond', 'gemEmerald':'forge:gems/emerald', 'ingotIron':'forge:ingots/iron',
 'ingotGold':'forge:ingots/gold', 'dustRedstone':'forge:dusts/redstone', 'oreIron':'forge:ores/iron',
 'oreGold':'forge:ores/gold', 'oreRedstone':'forge:ores/redstone', 'blockRedstone':'forge:storage_blocks/redstone',
 'dustGlowstone':'forge:dusts/glowstone', 'glowstone':'forge:storage_blocks/glowstone',
 'ingotGlowstone':'forge:ingots/glowstone', 'ingotRedstone':'forge:ingots/redstone',
 'cropPotato':'forge:crops/potato', 'cropCarrot':'forge:crops/carrot', 'cropWheat':'forge:crops/wheat',
 'slimeball':'forge:slimeballs', 'sonarStableStone':'sonarcore:stable_stone',
 'calculatorLeaves':'calculator:leaves', 'calculatorCircuit':'calculator:circuits',
 'calculatorReinforcedBlock':'calculator:reinforced_blocks', 'calculatorReinforcedStone':'calculator:reinforced_stone',
 'calculatorReinforcedDirt':'calculator:reinforced_dirt',
 'reinforcedStone':'forge:reinforced_stone', 'strongStone':'forge:strong_stone', 'hardStone':'forge:hard_stone',
 'reinforcedDirt':'forge:reinforced_dirt', 'strongDirt':'forge:strong_dirt', 'hardDirt':'forge:hard_dirt',
 'ingotEnrichedGold':'forge:ingots/enriched_gold', 'dustEnrichedGold':'forge:dusts/enriched_gold',
 'gemDiamondFake':'forge:gems/diamond_fake', 'ingotFoolsGold':'forge:ingots/fools_gold',
 'dustFoolsGold':'forge:dusts/fools_gold', 'gemAmethyst':'forge:gems/amethyst', 'gemTanzanite':'forge:gems/tanzanite',
 'dustStone':'forge:dusts/stone', 'dustDirt':'forge:dusts/dirt', 'cropBroccoli':'forge:crops/broccoli', 'cropPear':'forge:crops/pear',
}
STATS = Counter()

def write(path, value):
    target = DATA / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

def strip_comments(text):
    return re.sub(r'//[^\n]*|/\*.*?\*/', '', text, flags=re.S)

def split(text):
    result, start, depth, quote = [], 0, 0, None
    for i, c in enumerate(text):
        if quote:
            if c == quote and (i == 0 or text[i-1] != '\\'): quote = None
        elif c in '\"\'': quote = c
        elif c in '([{': depth += 1
        elif c in ')]}': depth -= 1
        elif c == ',' and depth == 0:
            result.append(text[start:i].strip()); start = i + 1
    result.append(text[start:].strip())
    return result

def statements(source, name):
    for match in re.finditer(r'\b' + re.escape(name) + r'\(', source):
        start = match.end(); depth = 1; quote = None
        for end in range(start, len(source)):
            c = source[end]
            if quote:
                if c == quote and source[end-1] != '\\': quote = None
            elif c in '\"\'': quote = c
            elif c == '(': depth += 1
            elif c == ')':
                depth -= 1
                if depth == 0:
                    yield split(source[start:end]), source[:match.start()].count('\n') + 1
                    break

def vanilla(field, meta):
    if field in ('LOG','LOG2','LEAVES','LEAVES2','PLANKS','SAPLING'):
        index = meta + (4 if field in ('LOG2','LEAVES2') else 0)
        suffix = {'LOG':'log','LOG2':'log','LEAVES':'leaves','LEAVES2':'leaves','PLANKS':'planks','SAPLING':'sapling'}[field]
        return WOODS[index] + '_' + suffix
    if field == 'DYE': return ['ink_sac','red_dye','green_dye','cocoa_beans','lapis_lazuli','purple_dye','cyan_dye','light_gray_dye','gray_dye','pink_dye','lime_dye','yellow_dye','light_blue_dye','magenta_dye','orange_dye','bone_meal'][meta]
    if field == 'DIRT': return ['dirt','coarse_dirt','podzol'][meta]
    if field == 'WOOL': return 'white_wool' if meta == 0 else (_ for _ in ()).throw(ValueError('Unmapped wool meta'))
    aliases = {'NETHER_BRICK':'nether_bricks','STONEBRICK':'stone_bricks','REEDS':'sugar_cane','MELON':'melon_slice','GRASS':'grass_block','NOTEBLOCK':'note_block','GOLDEN_APPLE':'golden_apple'}
    if meta != 0: raise ValueError(f'Unmapped vanilla metadata {field}:{meta}')
    return aliases.get(field, field.lower())

def obj(expr):
    expr = expr.strip()
    if expr.startswith('new ExtractionChamberOutput(') or expr.startswith('new PrecisionChamberOutput('):
        extraction = 'Extraction' in expr
        raw = expr[expr.index('(')+1:-1].replace('OreDictionary.WILDCARD_VALUE', '0')
        result = obj(raw); base = result['item']
        result['choices'] = [{'item': base + (f'_{i}' if i else ''), 'count': 1} for i in range(14)]
        if extraction: result.update(chance_bound=9, chance_equals=8)
        return result
    for wrapper in ('Ingredient.fromStacks(', 'SonarCrafting.fromBlock('):
        if expr.startswith(wrapper): return obj(expr[len(wrapper):-1])
    if expr.startswith('c('):
        meta, count, stable = split(expr[2:-1])
        return {'item':'calculator:circuitboard' + (f'_{meta}' if meta != '0' else ''), 'count':int(count), 'nbt':'{Stable:' + ('1' if stable == 'true' else '0') + ',Analysed:1b}'}
    if expr.startswith('new ItemStack('):
        args = split(expr[len('new ItemStack('):-1])
        count = int(args[1]) if len(args) > 1 else 1
        meta = args[2] if len(args) > 2 else '0'
        match = re.fullmatch(r'Variants\.(\w+)\.getMeta\(\)', meta)
        meta = VARIANTS[match[1]] if match else int(meta)
        return dict(item=resolve(args[0], meta), count=count)
    if expr.startswith('"'): return {'tag': TAGS[json.loads(expr)]}
    if expr.isdigit(): return int(expr)
    return {'item':resolve(expr), 'count':1}

def resolve(field, meta=0):
    if field.startswith(('Blocks.', 'Items.')): return 'minecraft:' + vanilla(field.split('.')[1], meta)
    if field.startswith('SonarCore.'):
        name = field.split('.')[1]
        if name == 'stableStone[0]': return 'sonarcore:stablestone_normal'
        # Reinforced dirt has no metadata variants, including legacy research metadata 2.
        if meta and name != 'reinforcedDirtBlock': raise ValueError(field)
        return 'sonarcore:' + name.lower()
    name = field.removeprefix('Calculator.')
    result = FIELDS[name]
    if meta:
        if name not in ('material_block','circuitBoard','circuitDamaged','circuitDirty'): raise ValueError(field)
        result += f'_{meta}'
    return result

def ingredient(value):
    result = {'ingredient':{k:v for k,v in value.items() if k in ('item','tag')}, 'count':value.get('count',1)}
    if 'nbt' in value: result['nbt'] = value['nbt']
    return result

def output(value):
    if 'tag' in value:
        # The only OreDictionary output is plankWood. Vanilla oak is registered first upstream.
        if value['tag'] != 'minecraft:planks': raise ValueError(value)
        return {'item':'minecraft:oak_planks','count':1}
    return value

def machine(type_, ins, outs, source, shapeless=False, value=0, values=None):
    index = STATS[type_]; STATS[type_] += 1
    recipe = {'type':'calculator:' + type_, 'order':index, 'shapeless':shapeless,
              'ingredients':[ingredient(i) for i in ins], 'results':[output(o) for o in outs],
              'value':value, 'energy':0, 'process_time':0, '_source':source}
    if values is not None: recipe['input_values'] = values
    if type_ == 'fabrication': recipe['allow_extra_inputs'] = True
    write(f'calculator/recipes/machine/{type_}/{index:03d}.json', recipe)

def machines():
    spec = {'CalculatorRecipes':('calculator',2,True), 'ScientificRecipes':('scientific',2,True),
      'AtomicCalculatorRecipes':('atomic',3,True), 'FlawlessCalculatorRecipes':('flawless',4,True),
      'StoneSeparatorRecipes':('stone',1,False), 'AlgorithmSeparatorRecipes':('algorithm',1,False),
      'ExtractionChamberRecipes':('extraction',1,False), 'PrecisionChamberRecipes':('precision',1,False),
      'AnalysingChamberRecipes':('analysing',2,False), 'FabricationChamberRecipes':('fabrication',0,True),
      'StarchExtractorRecipes':('starch',1,True), 'RedstoneExtractorRecipes':('redstone',1,True),
      'GlowstoneExtractorRecipes':('glowstone',1,True), 'HealthProcessorRecipes':('health',1,True),
      'ConductorMastRecipes':('conductor_mast',1,True), 'TreeHarvestRecipes':('harvest',1,False)}
    for name, (type_, n, shapeless) in spec.items():
        text = strip_comments((UP / f'common/recipes/{name}.java').read_text())
        # Restrict parser to addRecipes body; Calculator research is disabled by upstream buildRecipe.
        text = text[text.index('public void addRecipes()'):]
        text = text[:text.index('ResearchRecipeType type =')] if type_ == 'calculator' else text[:text.index('\n\t}', text.index('{'))]
        for args, line in statements(text, 'addRecipe'):
            data = [obj(a) for a in args]
            src = f'common/recipes/{name}.java: ' + ', '.join(args)
            if type_ == 'calculator': machine(type_,data[1:],data[:1],src,shapeless)
            elif type_ == 'fabrication': machine(type_,data[1:],data[:1],src,shapeless)
            elif type_ == 'analysing': machine(type_,[],data[2:],src,values=data[:2])
            elif type_ in ('starch','redstone','glowstone','health'): machine(type_,data[:1],[],src,shapeless,value=data[1])
            elif type_ == 'conductor_mast': machine(type_,data[:1],data[1:2],src,shapeless,value=data[2])
            else: machine(type_,data[:n],data[n:],src,shapeless)
    for type_, bases in [('restoration',['circuitdirty']),('reassembly',['circuitdamaged']),('processing',['circuitdamaged','circuitdirty'])]:
        for i in range(14):
            suffix = f'_{i}' if i else ''
            for base in bases:
                machine(type_,[{'item':'calculator:'+base+suffix,'count':1}],[{'item':'calculator:circuitboard'+suffix,'count':1}],f'{type_}ChamberRecipes.addCircuit meta={i}')

def craft_write(recipe, source):
    index = STATS['crafting']; STATS['crafting'] += 1
    recipe['_source'] = source
    write(f'calculator/recipes/crafting/{index:03d}_{recipe["result"]["item"].split(":")[1]}.json', recipe)

def crafting():
    text = strip_comments((UP/'CalculatorCrafting.java').read_text())
    for match in re.finditer(r'SonarCrafting\.(addShapedOre|addShaped|addShapeless)\(', text):
        method = match[1]
        args, _ = next(statements(text[match.start():], method))
        if any('variant.' in a for a in args): continue  # finite enum expansion below
        args = args[1:] if method == 'addShapedOre' else args[2:]
        result = obj(args.pop(0))
        if method == 'addShapeless':
            recipe = {'type':'minecraft:crafting_shapeless','ingredients':[ingredient(obj(a))['ingredient'] for a in args],'result':result}
        else:
            pattern = []
            while args and args[0].startswith('"'): pattern.append(json.loads(args.pop(0)))
            key = {args[i][1:-1]:ingredient(obj(args[i+1]))['ingredient'] for i in range(0,len(args),2)}
            # Vanilla's shaped serializer trims outer spaces; keep source pattern for exact mirroring.
            recipe = {'type':'minecraft:crafting_shaped','pattern':pattern,'key':key,'result':result}
        craft_write(recipe, 'CalculatorCrafting.java '+method)
    for meta, base in enumerate(MATERIALS):
        block = 'calculator:material' + (f'_{meta}' if meta else '')
        craft_write({'type':'minecraft:crafting_shaped','pattern':['AAA']*3,'key':{'A':{'item':'calculator:'+base}},'result':{'item':block,'count':1}},f'CalculatorCrafting.java material enum {meta}')
        craft_write({'type':'minecraft:crafting_shapeless','ingredients':[{'item':block}],'result':{'item':'calculator:'+base,'count':9}},f'CalculatorCrafting.java material enum {meta}')
    for name, input_, output_, xp in [('enriched_gold','enrichedgold','enrichedgoldingot',0.8),('broccoli','broccoli','cookedbroccoli',0.2)]:
        write('calculator/recipes/smelting/'+name+'.json',{'type':'minecraft:smelting','ingredient':{'item':'calculator:'+input_},'result':'calculator:'+output_,'experience':xp,'cookingtime':200,'_source':'CalculatorSmelting.java'})
        STATS['smelting'] += 1

TAG_VALUES = {}
def addtag(id_, values, kind='items'):
    TAG_VALUES.setdefault((id_,kind),[]).extend(values)

def tags():
    text = strip_comments((UP/'CalculatorOreDict.java').read_text())
    text = text[:text.index('public static void registerOre(')]
    for args, line in statements(text,'registerOre'):
        if len(args) != 2: continue
        tag = TAGS[json.loads(args[0])]
        if 'circuitBoard, 1, i' in args[1]: values = ['calculator:circuitboard'+(f'_{i}' if i else '') for i in range(14)]
        else: values = [obj(args[1])['item']]
        addtag(tag, values)
    # Forge's conventional common tags are supplied explicitly as vanilla defaults.
    defaults = {'forge:rods/wooden':['minecraft:stick'], 'forge:cobblestone':['minecraft:cobblestone','minecraft:mossy_cobblestone'],
      'forge:stone':['minecraft:stone'], 'forge:sand':['minecraft:sand','minecraft:red_sand'],
      'forge:sandstone':['minecraft:sandstone','minecraft:chiseled_sandstone','minecraft:cut_sandstone','minecraft:red_sandstone','minecraft:chiseled_red_sandstone','minecraft:cut_red_sandstone'],
      'forge:glass/colorless':['minecraft:glass'], 'forge:gems/lapis':['minecraft:lapis_lazuli'],
      'forge:gems/diamond':['minecraft:diamond'], 'forge:gems/emerald':['minecraft:emerald'],
      'forge:ingots/iron':['minecraft:iron_ingot'], 'forge:ingots/gold':['minecraft:gold_ingot'],
      'forge:dusts/redstone':['minecraft:redstone'], 'forge:dusts/glowstone':['minecraft:glowstone_dust'],
      'forge:storage_blocks/lapis':['minecraft:lapis_block'], 'forge:storage_blocks/redstone':['minecraft:redstone_block'],
      'forge:storage_blocks/glowstone':['minecraft:glowstone'], 'forge:ores/iron':['minecraft:iron_ore','minecraft:deepslate_iron_ore'],
      'forge:ores/gold':['minecraft:gold_ore','minecraft:deepslate_gold_ore'], 'forge:ores/redstone':['minecraft:redstone_ore','minecraft:deepslate_redstone_ore'],
      'forge:crops/potato':['minecraft:potato'], 'forge:crops/carrot':['minecraft:carrot'], 'forge:crops/wheat':['minecraft:wheat'],
      'forge:slimeballs':['minecraft:slime_ball'], 'forge:ingots/glowstone':[], 'forge:ingots/redstone':[]}
    for tag, values in defaults.items(): addtag(tag,values)
    for suffix, tag in [('log','logs'),('planks','planks'),('stairs','wooden_stairs'),('fence','wooden_fences'),('sapling','saplings')]:
        values = ['calculator:'+tree+suffix for tree in ['amethyst','tanzanite','pear','diamond']]
        addtag('minecraft:'+tag,values,'blocks')
        if suffix == 'fence': addtag('minecraft:'+tag,values)
        if suffix == 'log':
            addtag('minecraft:logs_that_burn',values); addtag('minecraft:logs_that_burn',values,'blocks')
    # Upstream calculatorLeaves is intentionally distinct from treeLeaves for starch values.
    leaves = ['calculator:'+tree+'leaves' for tree in ['amethyst','tanzanite','pear','diamond']]
    addtag('calculator:leaves',leaves,'blocks')
    for (tag,kind), values in TAG_VALUES.items():
        ns, path = tag.split(':'); write(f'{ns}/tags/{kind}/{path}.json',{'replace':False,'values':list(dict.fromkeys(values))}); STATS['tags'] += 1

def pool(item, conditions=None, functions=None):
    entry = {'type':'minecraft:item','name':'calculator:'+item}
    if functions: entry['functions'] = functions
    result = {'rolls':1,'entries':[entry]}
    if conditions: result['conditions'] = conditions
    return result

def loot():
    no_drop = {'conductormastblock','weatherstationblock','transmitterblock','scarecrowblock'}
    for block in REG['block_ids']:
        pools = []
        if block not in no_drop:
            target = {'calculatorscreenblock':'calculatorscreen','gaslanternon':'gaslanternoff'}.get(block,block)
            if block.endswith('leaves'):
                condition = {'condition':'minecraft:any_of','terms':[{'condition':'minecraft:match_tool','predicate':{'items':['minecraft:shears']}},{'condition':'minecraft:match_tool','predicate':{'enchantments':[{'enchantment':'minecraft:silk_touch','levels':{'min':1}}]}}]}
                pools = [pool(target,[condition])]
            elif block.startswith('crop'):
                seed, product = {'cropbroccoli':('broccoliseeds','broccoli'),'cropprunae':('prunaeseeds','coaldust'),'cropfiddledew':('fiddledewfruit','fiddledewfruit')}[block]
                mature = {'condition':'minecraft:block_state_property','block':'calculator:'+block,'properties':{'age':'7'}}
                pools = [pool(product,[mature]), pool(seed,functions=[{'function':'calculator:legacy_crop_seeds'}])]
            else: pools = [pool(target,[{'condition':'minecraft:survives_explosion'}])]
        write(f'calculator/loot_tables/blocks/{block}.json',{'type':'minecraft:block','pools':pools}); STATS['loot'] += 1

def validate():
    known = {'calculator:'+i for i in REG['block_ids']+REG['item_ids']}
    for file in DATA.rglob('*.json'):
        value = json.loads(file.read_text(encoding='utf-8'))
        def walk(node):
            if isinstance(node,dict):
                for key, value in node.items():
                    if key in ('item','name') and isinstance(value,str) and value.startswith('calculator:'):
                        assert value in known, (file,value)
                    walk(value)
            elif isinstance(node,list):
                for element in node: walk(element)
        walk(value)
    assert all(STATS[t] for t in ['calculator','scientific','atomic','flawless','stone','algorithm','extraction','restoration','reassembly','precision','processing','analysing','fabrication','starch','redstone','glowstone','conductor_mast','health','harvest'])
    assert STATS['calculator'] == 21
    assert STATS['fabrication'] == 12
    assert STATS['processing'] == 28
    assert STATS['restoration'] == STATS['reassembly'] == 14
    print(json.dumps(dict(STATS),indent=2))

if __name__ == '__main__':
    machines(); crafting(); tags(); loot(); validate()
