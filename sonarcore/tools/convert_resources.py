"""Convert the immutable MIT Sonar-Core 1.12 artwork/recipes to 1.20 resources.
Run with Python 3: python sonarcore/tools/convert_resources.py [upstream Sonar-Core path].
Generated files are checked in; this is not needed to launch the mod.
"""
from pathlib import Path
import json
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parents[1] / 'upstream' / 'Sonar-Core'
OLD = UPSTREAM / 'src/main/resources/assets/sonarcore'
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/sonarcore'
COLORS = 'normal black blue brown cyan green lightblue lightgrey lime magenta orange pink plain purple red yellow'.split()
DYES = 'gray black blue brown cyan green light_blue light_gray lime magenta orange pink white purple red yellow'.split()
STYLES = ['stablestone', 'stablestonerimmed', 'stablestoneblackrimmed']
IDS = []


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def model(name, value):
    write(ASSETS / f'models/block/{name}.json', value)


def state(name, value):
    write(ASSETS / f'blockstates/{name}.json', value)


def item(name, parent=None):
    IDS.append(name)
    write(ASSETS / f'models/item/{name}.json', {'parent': f'sonarcore:block/{parent or name}'})


shutil.copytree(OLD / 'textures', ASSETS / 'textures', dirs_exist_ok=True)
# Modern vanilla atlases scan block/, whereas original assets deliberately retain blocks/.
write(RES / 'assets/minecraft/atlases/blocks.json', {
    'sources': [{'type': 'minecraft:directory', 'source': 'blocks', 'prefix': 'blocks/'}]
})

# Forge 1.12's multi-texture cube was named cube_all. The corresponding vanilla parent is cube.
for name in [f'{style}_{color}' for style in STYLES for color in COLORS] + ['stableglass', 'clearstableglass']:
    legacy = json.loads((OLD / f'blockstates/{name}.json').read_text(encoding='utf-8-sig'))
    variants = {}
    for key, value in legacy['variants'].items():
        if key == 'inventory':
            continue
        textures = dict(legacy['defaults']['textures'])
        textures.update(value.get('textures', {}))
        suffix = ''.join('1' if part.endswith('=true') else '0' for part in key.split(','))
        name_with_state = name if suffix == '000000' else f'{name}_{suffix}'
        data = {'parent': 'minecraft:block/cube', 'textures': textures}
        if name.endswith('glass'):
            data['render_type'] = 'minecraft:translucent'
        model(name_with_state, data)
        variants[key] = {'model': f'sonarcore:block/{name_with_state}'}
    assert len(variants) == 64, (name, len(variants))
    state(name, {'variants': variants})
    item(name)

stairs_ids, fence_ids, gate_ids, dirt_ids, stone_ids = [], [], [], [], []
for prefix, base, texture, dirt in [
    ('reinforcedstone', 'reinforcedstoneblock', 'reinforcedstone', False),
    ('reinforcedstonebrick', 'reinforcedstonebrick', 'reinforcedstonebrick', False),
    ('reinforceddirt', 'reinforceddirtblock', 'reinforceddirt', True),
    ('reinforceddirtbrick', 'reinforceddirtbrick', 'reinforceddirtbrick', True),
]:
    tex = f'sonarcore:blocks/{texture}'
    model(base, {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex}})
    state(base, {'variants': {'': {'model': f'sonarcore:block/{base}'}}})
    item(base)
    for suffix in ['', 'stairs', 'fence', 'gate']:
        (dirt_ids if dirt else stone_ids).append(base if not suffix else prefix + suffix)

    stairs = prefix + 'stairs'
    stairs_ids.append(stairs)
    for suffix, parent in [('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')]:
        model(stairs + suffix, {'parent': f'minecraft:block/{parent}', 'textures': {'bottom': tex, 'top': tex, 'side': tex}})
    variants = {}
    for facing, angle in [('east', 0), ('south', 90), ('west', 180), ('north', 270)]:
        for half in ['bottom', 'top']:
            for shape in ['straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right']:
                rotation = angle + (270 if shape.endswith('left') else 0)
                if half == 'top' and shape != 'straight':
                    rotation += 90
                suffix = '' if shape == 'straight' else '_' + shape.split('_')[0]
                variants[f'facing={facing},half={half},shape={shape}'] = {
                    'model': f'sonarcore:block/{stairs}{suffix}', 'x': 180 if half == 'top' else 0,
                    'y': rotation % 360, 'uvlock': True}
    state(stairs, {'variants': variants})
    item(stairs)

    fence = prefix + 'fence'
    fence_ids.append(fence)
    for suffix, parent in [('_post', 'fence_post'), ('_side', 'fence_side'), ('', 'fence_inventory')]:
        model(fence + suffix, {'parent': f'minecraft:block/{parent}', 'textures': {'texture': tex}})
    parts = [{'apply': {'model': f'sonarcore:block/{fence}_post'}}]
    for side, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
        parts.append({'when': {side: 'true'}, 'apply': {'model': f'sonarcore:block/{fence}_side', 'y': angle, 'uvlock': True}})
    state(fence, {'multipart': parts})
    item(fence)

    gate = prefix + 'gate'
    gate_ids.append(gate)
    for suffix in ['', '_open', '_wall', '_wall_open']:
        model(gate + suffix, {'parent': f'minecraft:block/template_fence_gate{suffix}', 'textures': {'texture': tex}})
    variants = {}
    for facing, angle in [('south', 0), ('west', 90), ('north', 180), ('east', 270)]:
        for in_wall in [False, True]:
            for opened in [False, True]:
                suffix = ('_wall' if in_wall else '') + ('_open' if opened else '')
                variants[f'facing={facing},in_wall={str(in_wall).lower()},open={str(opened).lower()}'] = {
                    'model': f'sonarcore:block/{gate}{suffix}', 'y': angle, 'uvlock': True}
    state(gate, {'variants': variants})
    item(gate)

# All original blocks, including custom glass, use Block's normal self drop.
for name in IDS:
    write(RES / f'data/sonarcore/loot_tables/blocks/{name}.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1,
            'entries': [{'type': 'minecraft:item', 'name': f'sonarcore:{name}'}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def tag(namespace, kind, path, values):
    write(RES / f'data/{namespace}/tags/{kind}/{path}.json', {'replace': False, 'values': values})


stable = [f'sonarcore:{style}_{color}' for style in STYLES for color in COLORS]
for kind in ['blocks', 'items']:
    tag('sonarcore', kind, 'stable_stone', stable)
    tag('sonarcore', kind, 'stable_glass', ['sonarcore:stableglass', 'sonarcore:clearstableglass'])
    tag('forge', kind, 'sonar_stable_stone', ['#sonarcore:stable_stone'])
for path, values in [('stairs', stairs_ids), ('fences', fence_ids), ('fence_gates', gate_ids),
                     ('mineable/pickaxe', stone_ids + [x.removeprefix('sonarcore:') for x in stable]),
                     ('mineable/shovel', dirt_ids)]:
    tag('minecraft', 'blocks', path, ['sonarcore:' + x for x in values])


def ingredient(item_id):
    return {'tag': item_id[1:]} if item_id.startswith('#') else {'item': item_id if ':' in item_id else 'sonarcore:' + item_id}


def recipe(name, result, count, ingredients=None, pattern=None, key=None, conditional=False):
    data = {'type': 'minecraft:crafting_shaped' if pattern else 'minecraft:crafting_shapeless',
            'category': 'building', 'group': 'sonarcore', 'result': {'item': 'sonarcore:' + result, 'count': count}}
    if pattern:
        data.update(pattern=pattern, key={k: ingredient(v) for k, v in key.items()})
    else:
        data['ingredients'] = [ingredient(value) for value in ingredients]
    if conditional:
        data['conditions'] = [{'type': 'forge:not', 'value': {'type': 'forge:mod_loaded', 'modid': 'calculator'}}]
    write(RES / f'data/sonarcore/recipes/{name}.json', data)
    # Recipe discovery does not bypass conditions: a missing conditional recipe is harmless.
    criteria = {'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {
        'items': [({'tag': next(iter(data['key'].values()))['tag']} if 'tag' in next(iter(data['key'].values()))
                   else {'items': [next(iter(data['key'].values()))['item']]})]}}} if pattern else {
        'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {
            'items': [({'tag': data['ingredients'][0]['tag']} if 'tag' in data['ingredients'][0]
                       else {'items': [data['ingredients'][0]['item']]})]}}}
    criteria['has_the_recipe'] = {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': f'sonarcore:{name}'}}
    advancement = {'parent': 'minecraft:recipes/root', 'criteria': criteria,
                   'requirements': [['has_material', 'has_the_recipe']], 'rewards': {'recipes': [f'sonarcore:{name}']}}
    if conditional:
        advancement['conditions'] = data['conditions']
    write(RES / f'data/sonarcore/advancements/recipes/{name}.json', advancement)


recipe('reinforcedstonebrick', 'reinforcedstonebrick', 2, ['reinforcedstoneblock'] * 2)
recipe('stablestone_normal', 'stablestone_normal', 2, ['reinforcedstonebrick'] * 2)
recipe('stableglass_from_clear', 'stableglass', 1, ['clearstableglass'])
recipe('clearstableglass', 'clearstableglass', 1, ['stableglass'])
for prefix, base in [('reinforcedstone', 'reinforcedstoneblock'), ('reinforcedstonebrick', 'reinforcedstonebrick'),
                     ('reinforceddirt', 'reinforceddirtblock'), ('reinforceddirtbrick', 'reinforceddirtbrick')]:
    recipe(prefix + 'stairs', prefix + 'stairs', 4, pattern=['A  ', 'AA ', 'AAA'], key={'A': base})
    recipe(prefix + 'fence', prefix + 'fence', 6, pattern=['ASA', 'ASA'], key={'A': base, 'S': '#forge:rods/wooden'})
    recipe(prefix + 'gate', prefix + 'gate', 1, pattern=['SAS', 'SAS'], key={'A': base, 'S': '#forge:rods/wooden'})
for color, dye in zip(COLORS, DYES):
    recipe(f'stablestone_{color}_dye', f'stablestone_{color}', 8, pattern=['SSS', 'SDS', 'SSS'],
           key={'S': 'stablestone_normal', 'D': 'minecraft:' + dye + '_dye'})
    for src, dest in zip(STYLES, STYLES[1:] + STYLES[:1]):
        recipe(f'{dest}_{color}_style', f'{dest}_{color}', 1, [f'{src}_{color}'])
for material, source in [('stone', '#forge:cobblestone'), ('dirt', 'minecraft:dirt')]:
    for wood, count in [('planks', 1), ('logs', 4)]:
        recipe(f'reinforced{material}block_from_{wood}', f'reinforced{material}block', count,
               [source, '#minecraft:' + wood], conditional=True)
recipe('stableglass_standalone', 'stableglass', 2, ['#forge:glass', '#forge:glass'], conditional=True)
recipe('reinforceddirtbrick_standalone', 'reinforceddirtbrick', 2, ['reinforceddirtblock'] * 2, conditional=True)

for language in (OLD / 'lang').glob('*.lang'):
    translated = {}
    for line in language.read_text(encoding='utf-8-sig').splitlines():
        if not line.strip() or line.startswith(('//', '#')) or '=' not in line:
            continue
        key, value = line.split('=', 1)
        if key.startswith('tile.') and key.endswith('.name'):
            block = key[5:-5].lower()
            if block not in IDS:
                continue
            key = 'block.sonarcore.' + block
        elif key == 'itemGroup.SonarCore':
            key = 'itemGroup.sonarcore'
        translated[key] = value
    translated.setdefault('itemGroup.sonarcore', 'Sonar Core')
    write(ASSETS / f'lang/{language.stem.lower()}.json', translated)

assert len(IDS) == len(set(IDS)) == 66
assert len(list((RES / 'data/sonarcore/recipes').glob('*.json'))) == 86
print(f'Generated {len(IDS)} blocks/items, 3200 connected models, 86 recipes, 66 loot tables, 3 locales.')
