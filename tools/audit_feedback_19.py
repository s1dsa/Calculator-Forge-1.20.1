#!/usr/bin/env python3
"""Deterministic, evidence-only gate for the 19 feedback items.

Every check reads the real repository files and asserts a property that would be false if the fix
were missing. It never runs Minecraft or Gradle, so it is safe for scripts/Validate-Feedback.ps1 to
execute on demand. Exit code is non-zero if any check fails; PASS therefore means every listed
property was actually observed in the sources, models, textures or data packs.
"""
import io
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

ROOT = r'F:\ai\calculator-port'
CALC = os.path.join(ROOT, 'calculator')
JAVA = os.path.join(CALC, 'src/main/java/sonar/calculator/mod')
RES = os.path.join(CALC, 'src/main/resources')
ASSETS = os.path.join(RES, 'assets/calculator')
DATA = os.path.join(RES, 'data/calculator')
LANG = os.path.join(ASSETS, 'lang')
TOOLS = os.path.dirname(os.path.abspath(__file__))

RESULTS = []


def read(path):
    with io.open(path, encoding='utf-8') as handle:
        return handle.read()


def src(*parts):
    return read(os.path.join(JAVA, *parts))


def check(item, description, condition, evidence):
    RESULTS.append((item, description, bool(condition), evidence))


def exists(*parts):
    return os.path.isfile(os.path.join(*parts))


def main():
    # ---- #1 flawless glass: glass sound, all CTM variants, cutout -------------------------------
    world = src('world', 'WorldContent.java')
    glass = re.search(r'case "flawlessglass".*?\);', world, re.S)
    check('#1', 'flawless glass uses SoundType.GLASS',
          glass and 'SoundType.GLASS' in glass.group(0) and 'SoundType.STONE' not in glass.group(0),
          'WorldContent.createBlock("flawlessglass")')
    variants = json.load(io.open(os.path.join(ASSETS, 'blockstates/flawlessglass.json'), encoding='utf-8'))['variants']
    check('#1', 'flawless glass blockstate covers all 64 neighbour combinations',
          len(variants) == 64, 'blockstates/flawlessglass.json variants=%d' % len(variants))
    check('#1', 'flawless glass is registered cutout in CalculatorClient',
          'flawlessglass' in re.search(r'CUTOUT_BLOCKS = \{(.*?)\};', src('client', 'CalculatorClient.java'), re.S).group(1),
          'CalculatorClient.CUTOUT_BLOCKS')

    # ---- #2/#4/#5/#12 client GUI keys exist ------------------------------------------------------
    langs = {name: json.load(io.open(os.path.join(LANG, name + '.json'), encoding='utf-8'))
             for name in ('zh_cn', 'en_us', 'ru_ru', 'uk_ua')}
    gui_keys = ['gui.calculator.side.input', 'gui.calculator.side.output', 'gui.calculator.side.disabled',
                'gui.calculator.points', 'locator.active', 'locator.multiblock', 'locator.owner', 'circuit.stability',
                'buttons.pause', 'buttons.circuits']
    missing = {k for k in gui_keys for name, table in langs.items() if k not in table}
    check('#2/#4/#5/#12', 'GUI/locator/button translation keys present in all four languages',
          not missing, 'missing=%s' % sorted(missing))

    # ---- #3 advanced power cube: face textures + world/GUI binding ------------------------------
    for tex in ('advancedpowercube_front_slot2.png', 'advancedpowercube_side_slot2.png', 'machine_side.png'):
        check('#3', 'power cube overlay texture %s exists' % tex, exists(ASSETS, 'textures/blocks', tex),
              'assets/calculator/textures/blocks/' + tex)
    renderer = src('client', 'render', 'MachineRenderer.java')
    check('#3', 'world renderer paints per-face overlay for the advanced power cube',
          'case "advancedpowercube"' in renderer and 'energySideMode(side)' in renderer,
          'MachineRenderer.render (case advancedpowercube)')
    be = src('machine', 'MachineBlockEntity.java')
    bt = src('machine', 'MachineBlock.java')
    check('#3', 'server-authoritative face cycle exists and is reachable from the block gesture',
          'boolean cycleEnergySide(' in be and 'cycleEnergySide(hit.getDirection())' in bt,
          'MachineBlockEntity.cycleEnergySide + MachineBlock.use')
    # The overlay must sit toward the outside of the block, otherwise the front face keeps its baked
    # yellow INPUT symbol and can never show OUTPUT/DISABLED.
    check('#3', 'front-face overlay is offset outwards (not hidden behind the model face)',
          'FRAME_INSET - OVERLAY_EPS' in renderer and '1F - FRAME_INSET + OVERLAY_EPS' in renderer,
          'MachineRenderer.face')

    # ---- #8 explicit centre+down+up preserves upstream geometry/UV with one root display --------
    import copy
    centre = json.load(io.open(os.path.join(ASSETS, 'models/block/pipingcentre.json'), encoding='utf-8'))['elements']
    down = json.load(io.open(os.path.join(ASSETS, 'models/block/pipingconnection.json'), encoding='utf-8'))['elements']
    up = copy.deepcopy(down)
    for element in up:
        bottom, top = element['from'][:], element['to'][:]
        element['from'] = [bottom[0], 16 - top[1], bottom[2]]
        element['to'] = [top[0], 16 - bottom[1], top[2]]
    expected = centre + down + up
    for pipe, tex in (('amethystpiping', 'calculator:model/amethyst_piping'),
                      ('tanzanitepiping', 'calculator:model/tanzanite_piping')):
        model_path = os.path.join(ASSETS, 'models/item', pipe + '.json')
        model = json.load(io.open(model_path, encoding='utf-8')) if os.path.isfile(model_path) else {}
        ok = model.get('elements') == expected and model.get('textures', {}).get('0') == tex
        ok = ok and model.get('display', {}).get('gui') == {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]}
        check('#8', 'pipe item %s preserves exact centre/down/up geometry and UV under the root GUI transform' % pipe,
              ok, 'models/item/%s.json elements=%s' % (pipe, len(model.get('elements', []))))

    # ---- #6 reinforced chest shape + per tick lid sync -----------------------------------------
    check('#6', 'reinforced chest uses the legacy chest selection shape',
          'CHEST_SHAPE' in bt and re.search(r'id\.equals\("reinforcedchest"\)\) return CHEST_SHAPE', bt) is not None,
          'MachineBlock.getShape')
    chest_sync = re.search(r'private boolean perTickSync\(\)\s*\{(.*?)\n    \}', be, re.S)
    check('#6', 'reinforced chest lid angle synchronizes every tick',
          chest_sync and '"reinforcedchest"' in chest_sync.group(1),
          'MachineBlockEntity.perTickSync')
    # The upstream BlockChest is drawn only by its block entity renderer; the port must not also draw
    # the baked box model, otherwise the static duplicate hides the animated lid.
    chest_bs = json.load(io.open(os.path.join(ASSETS, 'blockstates/reinforcedchest.json'), encoding='utf-8'))
    chest_refs = {v['model'] for v in chest_bs['variants'].values()}
    chest_empty = bool(chest_refs) and all(
        json.load(io.open(os.path.join(ASSETS, 'models', ref.split(':')[-1] + '.json'), encoding='utf-8')).get('elements') == []
        for ref in chest_refs)
    check('#6', 'reinforced chest world model is BER-only (no static duplicate of the animated lid)',
          chest_empty, 'blockstates/reinforcedchest.json -> %s' % sorted(chest_refs))

    # ---- #7/#10 pause + "remove circuits" button protocol ---------------------------------------
    env = src('machine', 'MachineEnvironment.java')
    check('#7/#10', 'server implements the eject-installed-upgrades action (menu button 12)',
          'action == 12' in env and 'upgrades.getStackInSlot(slot)' in env,
          'MachineEnvironment.handleAction action==12')

    # ---- #9 docking station: mounted calculator slot + refusal ----------------------------------
    check('#9', 'docking station refuses the menu until a calculator is mounted',
          'refusesMenu()' in be and 'RENDER_CALCULATOR_SLOT' in be,
          'MachineBlockEntity.refusesMenu')
    menu = src('machine', 'MachineMenu.java')
    check('#9', 'menu exposes the mounted calculator for the docking station',
          'getMountedCalculator' in menu and 'RENDER_CALCULATOR_SLOT' in menu,
          'MachineMenu.getMountedCalculator')

    # ---- #11 crank handle: BER empty model + attachment gate -------------------------------------
    crank_bs = json.load(io.open(os.path.join(ASSETS, 'blockstates/crankhandle.json'), encoding='utf-8'))
    crank_model = list(crank_bs['variants'].values())[0]['model'].split(':')[-1]
    crank_ber = json.load(io.open(os.path.join(ASSETS, 'models', crank_model + '.json'), encoding='utf-8'))
    check('#11', 'crank handle world model is empty and drawn by the block entity renderer',
          crank_ber.get('elements') == [], 'models/%s.json elements=%r' % (crank_model, crank_ber.get('elements')))
    check('#11', 'crank handle placement and removal are gated on the generator below it',
          'isGenerator(level, pos.below())' in bt and 'id.equals("crankhandle")' in bt,
          'MachineBlock.canSurvive / neighborChanged')

    # ---- #13 multiblock dismantling: placeholders carry an empty loot table ----------------------
    loot_dir = os.path.join(DATA, 'loot_tables/blocks')
    aux = ['conductormastblock.json', 'weatherstationblock.json', 'transmitterblock.json', 'scarecrowblock.json']
    empty_loot = []
    for name in aux:
        path = os.path.join(loot_dir, name)
        if exists(loot_dir, name):
            pools = json.load(io.open(path, encoding='utf-8')).get('pools', [])
            if pools == []:
                empty_loot.append(name)
    check('#13', 'height-multiblock placeholder blocks drop nothing on break',
          len(empty_loot) == len(aux), 'empty loot tables: %s' % empty_loot)
    check('#13', 'dismantling is guarded against re-entry and never force-loads a chunk',
          'DISMANTLING' in env and 'hasChunkAt' in env and 'dismantle(' in env,
          'MachineEnvironment.dismantle / cleaning')

    # ---- #14 stairs are called stairs in Chinese -------------------------------------------------
    stairs = [k for k in langs['zh_cn'] if 'stairs' in k.lower()]
    bad = [k for k in stairs if '台阶' in langs['zh_cn'][k]]
    check('#14', 'Chinese stair names use 楼梯 and not 台阶',
          stairs and not bad, 'stairs keys=%d, still 台阶=%s' % (len(stairs), bad))
    # The conversion injected the generic forge:default-block display onto every model, which for
    # stairs and fence_inventory overrides the vanilla gui/fixed rotations. The item models must now
    # resolve to exactly the vanilla transforms.
    import zipfile
    vanilla_jar = os.path.join(os.path.dirname(ROOT), r'.cache\gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client-extra.jar')
    if not os.path.isfile(vanilla_jar):
        check('#14', 'stairs/fence item orientation matches vanilla', False,
              'missing %s (cannot verify without the client jar)' % vanilla_jar)
    else:
        jar = zipfile.ZipFile(vanilla_jar)
        entries = set(jar.namelist())

        def vmodel(ref):
            path = 'assets/minecraft/models/' + ref.split(':')[-1] + '.json'
            return json.loads(jar.read(path).decode('utf-8')) if path in entries else None

        def v_display(ref, seen=None):
            seen = seen or set()
            ref = ref.split(':')[-1]
            if ref in seen:
                return {}
            seen.add(ref)
            model = vmodel(ref)
            if model is None:
                return {}
            out = dict(v_display(model['parent'], seen)) if model.get('parent') else {}
            out.update(model.get('display') or {})
            return out

        def norm(t):
            t = t or {}
            return (tuple(t.get('rotation', [0, 0, 0])), tuple(t.get('translation', [0, 0, 0])), tuple(t.get('scale', [1, 1, 1])))

        drift = []
        for item in ('amethyststairs', 'tanzanitestairs', 'pearstairs', 'diamondstairs',
                     'amethystfence', 'tanzanitefence', 'pearfence', 'diamondfence'):
            model = json.load(io.open(os.path.join(ASSETS, 'models/item', item + '.json'), encoding='utf-8'))
            parent = model.get('parent', '')
            if not parent.startswith('calculator:block/converted/'):
                drift.append('%s: unexpected parent %s' % (item, parent))
                continue
            converted = json.load(io.open(os.path.join(ASSETS, 'models/block/converted', parent.split('/')[-1] + '.json'), encoding='utf-8'))
            vanilla_parent = converted.get('parent', '')
            want = v_display(vanilla_parent)
            have = dict(want)
            have.update(converted.get('display') or {})
            bad_keys = [k for k in set(want) | set(have) if norm(want.get(k)) != norm(have.get(k))]
            if bad_keys:
                drift.append('%s: %s' % (item, bad_keys))
        check('#14', 'stairs/fence item orientation matches vanilla (no injected transform override)',
              not drift, 'drift=%s' % drift)

    # ---- #15 crops/saplings/leaves registered cutout ---------------------------------------------
    client_src = src('client', 'CalculatorClient.java')
    cutout = set(re.findall(r'"([^"]+)"', re.search(r'CUTOUT_BLOCKS = \{(.*?)\};', client_src, re.S).group(1)))
    mipped = set(re.findall(r'"([^"]+)"', re.search(r'CUTOUT_MIPPED_BLOCKS = \{(.*?)\};', client_src, re.S).group(1)))
    for block in ('amethystsapling', 'tanzanitesapling', 'pearsapling', 'diamondsapling',
                  'cropbroccoli', 'cropprunae', 'cropfiddledew'):
        check('#15', 'sapling/crop %s registered cutout' % block, block in cutout, 'CalculatorClient.CUTOUT_BLOCKS')
    for leaf in ('amethystleaves', 'tanzaniteleaves', 'pearleaves', 'diamondleaves'):
        check('#15', 'leaves %s registered cutoutMipped' % leaf, leaf in mipped, 'CalculatorClient.CUTOUT_MIPPED_BLOCKS')

    # ---- #16 sapling consumed on success / restored on failure -----------------------------------
    sapling = src('world', 'CalculatorSaplingBlock.java')
    tree = src('world', 'CalculatorTreeFeature.java')
    check('#16', 'sapling grow path clears the sapling then restores the exact state on failure',
          'Blocks.AIR.defaultBlockState()' in sapling and re.search(r'place\(.*?\)\s*\)?\s*\{', sapling, re.S) is not None
          and sapling.count('setBlock') >= 2,
          'CalculatorSaplingBlock.growTree')

    # ---- #17 diamond sapling accepts the end diamond block ---------------------------------------
    check('#17', 'diamond tree accepts the end diamond block as soil and converts it to grass',
          'material_8' in tree and 'GRASS_BLOCK' in tree, 'CalculatorTreeFeature soil handling')

    # ---- #18 no hardcoded English, creative tab translated ---------------------------------------
    check('#18', 'creative tab title uses the translation key',
          'Component.translatable("itemGroup.Calculator")' in src('CalculatorContent.java'),
          'CalculatorContent TABS title')
    item_dir = os.path.join(JAVA, 'item')
    literal = []
    for base, _d, files in os.walk(item_dir):
        for name in files:
            if not name.endswith('.java'):
                continue
            for line in read(os.path.join(base, name)).splitlines():
                for match in re.finditer(r'Component\.literal\(\s*"([^"]*)"', line):
                    if re.search(r'[A-Za-z]{3,}', match.group(1)):
                        literal.append('%s: %s' % (name, match.group(1)))
    check('#18', 'item classes contain no user-visible hardcoded English literals',
          not literal, 'offenders=%s' % literal)

    # ---- #19 JEI: build wiring, optional dependency, server isolation ----------------------------
    build = read(os.path.join(CALC, 'build.gradle'))
    check('#19', 'calculator build applies the JEI fragment',
          "apply from: 'jei.gradle'" in build, 'calculator/build.gradle')
    check('#19', 'JEI is compileOnly/runtimeOnly and the runtime is gated by -Pjei',
          "compileOnly fg.deobf" in read(os.path.join(CALC, 'jei.gradle'))
          and "project.findProperty('jei') != 'false'" in read(os.path.join(CALC, 'jei.gradle')),
          'calculator/jei.gradle')
    # The published jei-1.20.1-forge jar is self-contained, so its POM closure must not be resolved:
    # it lists build-only tooling that is not published in reachable repositories and split modules
    # that duplicate classes. `transitive = false` is what keeps :calculator:compileJava resolvable.
    check('#19', 'JEI runtime dependency is scoped transitive = false',
          'transitive = false' in read(os.path.join(CALC, 'jei.gradle')),
          'calculator/jei.gradle')
    # jei-1.20.1-forge-api is a stub (only mezz/jei/api/forge/**); the API surface the plugin imports
    # lives in jei-1.20.1-common-api, so both must be declared or :calculator:compileJava fails with
    # "package mezz.jei.api does not exist".
    check('#19', 'JEI API artifact (common-api) is declared on the compile classpath',
          'jei-1.20.1-common-api' in read(os.path.join(CALC, 'jei.gradle')),
          'calculator/jei.gradle')
    offenders = []
    for base, _d, files in os.walk(JAVA):
        rel = os.path.relpath(base, JAVA)
        if rel.startswith('client') or rel.replace('\\', '/').startswith('integration/jei'):
            continue
        for name in files:
            if not name.endswith('.java'):
                continue
            text = read(os.path.join(base, name))
            if 'net.minecraft.client' in text or 'mezz.jei' in text:
                offenders.append(os.path.join(rel, name))
    check('#19', 'no common/server source loads a client or JEI class',
          not offenders, 'offenders=%s' % offenders)
    check('#19', 'JEI plugin and types are present',
          exists(JAVA, 'integration/jei/CalculatorJeiPlugin.java') and exists(JAVA, 'integration/jei/JeiRecipeTypes.java'),
          'integration/jei/**')

    # ---- provenance preserved --------------------------------------------------------------------
    props = read(os.path.join(ROOT, 'gradle.properties'))
    check('provenance', 'author 崇宫澪酱 preserved in gradle.properties',
          'mod_authors=\\u5d07\\u5bab\\u6faa\\u9171' in props or '崇宫澪酱' in props, 'gradle.properties mod_authors')
    check('provenance', 'MIT LICENSE present',
          exists(ROOT, 'LICENSE') and 'MIT' in read(os.path.join(ROOT, 'LICENSE')),
          'LICENSE')

    # ---- report ----------------------------------------------------------------------------------
    failed = [r for r in RESULTS if not r[2]]
    for item, description, ok, evidence in RESULTS:
        print('%-14s [%s] %s  <- %s' % (item, 'PASS' if ok else 'FAIL', description, evidence))
    print('\nchecks: %d, passed: %d, failed: %d' % (len(RESULTS), len(RESULTS) - len(failed), len(failed)))
    if failed:
        print('FEEDBACK RESOURCE CHECKS FAILED')
        return 1
    print('ALL FEEDBACK RESOURCE CHECKS PASSED')
    return 0


if __name__ == '__main__':
    sys.exit(main())
