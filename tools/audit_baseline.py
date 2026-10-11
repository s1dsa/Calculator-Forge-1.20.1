"""Reproducible, read-only inventory of Calculator/SonarCore 1.12.2.

No Java execution and no third-party parser dependency. This is a lexical inventory,
not a Java compiler: unknown expressions and dynamic control flow remain explicit.
Only the requested output directory is written.
"""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re

PROJECT = Path(__file__).resolve().parents[1]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def dump(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def uncomment(text: str) -> str:
    """Mask comments, preserve literals, offsets and physical line numbers."""
    pattern = r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(pattern, lambda m: re.sub(r'[^\n]', ' ', m[0]) if m[0].startswith(('//', '/*')) else m[0], text)


def literal_mask(text: str) -> str:
    return re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', lambda m: ' ' * len(m[0]), text)


def closing(text: str, start: int) -> int:
    pairs = {'(': ')', '{': '}', '[': ']'}
    stack = [pairs[text[start]]]
    quote = None
    i = start + 1
    while i < len(text):
        c = text[i]
        if quote:
            if c == '\\':
                i += 2
                continue
            if c == quote:
                quote = None
        elif c in '\"\'':
            quote = c
        elif c in pairs:
            stack.append(pairs[c])
        elif c in ')}]':
            if not stack or c != stack[-1]:
                raise ValueError(f"unbalanced Java delimiters at {i}")
            stack.pop()
            if not stack:
                return i
        i += 1
    raise ValueError(f"unclosed Java delimiter at {start}")


def split_args(text: str) -> list[str]:
    result, start, i = [], 0, 0
    while i < len(text):
        c = text[i]
        if c in '([{':
            i = closing(text, i) + 1
            continue
        if c in '\"\'':
            q = c
            i += 1
            while i < len(text):
                if text[i] == '\\':
                    i += 2
                    continue
                if text[i] == q:
                    break
                i += 1
        if c == ',':
            result.append(text[start:i].strip())
            start = i + 1
        i += 1
    if text[start:].strip():
        result.append(text[start:].strip())
    return result


def calls(text: str, pattern: str):
    for m in re.finditer(r'(?<![\w$])(?P<callee>' + pattern + r')\s*\(', literal_mask(text)):
        start = text.index('(', m.start())
        end = closing(text, start)
        yield {'callee': text[m.start('callee'):m.end('callee')], 'args': split_args(text[start + 1:end]),
               'offset': m.start(), 'end': end + 1, 'line': text.count('\n', 0, m.start()) + 1,
               'expression': text[m.start():end + 1]}


def controls(text: str) -> list[dict]:
    found = []
    for c in calls(text, r'(?:for|while|if|switch|catch)'):
        pos = c['end']
        while pos < len(text) and text[pos].isspace():
            pos += 1
        if pos < len(text) and text[pos] == '{':
            end = closing(text, pos)
        else:
            end = text.find(';', pos)
        found.append({'start': c['offset'], 'end': end, 'kind': c['callee'],
                      'condition': ', '.join(c['args']), 'line': c['line']})
    return found


def context(ctrl: list[dict], pos: int) -> list[dict]:
    return [{k: x[k] for k in ('kind', 'condition', 'line')} for x in ctrl if x['start'] <= pos <= x['end']]


def expression(value: str):
    value = value.strip()
    if re.fullmatch(r'"(?:\\.|[^"\\])*"', value):
        return {'kind': 'string', 'value': json.loads(value), 'java': value}
    if re.fullmatch(r"'(?:\\.|[^'\\])'", value):
        return {'kind': 'character', 'value': value[1:-1], 'java': value}
    if re.fullmatch(r'-?\d+', value):
        return {'kind': 'integer', 'value': int(value), 'java': value}
    if value in ('true', 'false'):
        return {'kind': 'boolean', 'value': value == 'true', 'java': value}
    m = re.fullmatch(r'new\s+ItemStack\s*\((.*)\)', value, re.S)
    if m:
        args = split_args(m[1])
        return {'kind': 'item_stack', 'item': expression(args[0]),
                'count': expression(args[1] if len(args) > 1 else '1'),
                'metadata': expression(args[2] if len(args) > 2 else '0'), 'java': value}
    if re.fullmatch(r'[\w$]+(?:\.[\w$]+)*(?:\[\d+\])?', value):
        return {'kind': 'symbol', 'value': value, 'java': value}
    return {'kind': 'java_expression', 'java': value, 'requires_semantic_review': True}


def git_info(root: Path) -> dict:
    gd = root / '.git'
    head = (gd / 'HEAD').read_text().strip()
    commit = head
    if head.startswith('ref: '):
        ref = head[5:]
        if (gd / ref).exists():
            commit = (gd / ref).read_text().strip()
        elif (gd / 'packed-refs').exists():
            commit = next((line.split()[0] for line in (gd / 'packed-refs').read_text().splitlines()
                           if line.endswith(' ' + ref)), 'UNRESOLVED')
    config = (gd / 'config').read_text()
    origin = re.search(r'\[remote "origin"\][\s\S]*?url\s*=\s*(\S+)', config)
    return {'head': head, 'commit': commit, 'origin': origin[1] if origin else None}


def category(mod: str, rel: str) -> str:
    if '/integration/' in rel or '/handlers/planting/' in rel or '/handlers/fluids/' in rel or '/handlers/energy/' in rel:
        return 'integrations-and-capabilities'
    if '/client/' in rel or '/renderer' in rel or '/model' in rel:
        return 'client-rendering-and-gui'
    if '/network/' in rel or '/sync/' in rel or '/container' in rel:
        return 'network-sync-and-menus'
    if '/recipe' in rel or any(x in rel for x in ('Crafting.java', 'Smelting.java', 'OreDict.java')):
        return 'recipes-and-tags'
    if '/research/' in rel or '/guide/' in rel or '/calculators/' in rel or '/modules/' in rel:
        return 'portable-calculators-modules-and-research'
    if '/tileentity/' in rel or '/multiblock/' in rel or '/block/machines/' in rel or '/block/generators/' in rel:
        return 'machines-and-multiblocks'
    if any(x in rel for x in ('/common/world/', '/common/entities/', 'Sapling', 'Crops', 'Leaves', 'Logs', 'Planks')):
        return 'plants-world-and-entities'
    if '/item/' in rel:
        return 'items-tools-and-materials'
    if '/block/' in rel:
        return 'blocks-and-connected-textures'
    return 'core-api-config-and-registration'


def enum_values(text: str, enum_name: str) -> list[dict]:
    m = re.search(r'\benum\s+' + re.escape(enum_name) + r'\b[^\{]*\{', text)
    if not m:
        return []
    body = text[m.end():text.index(';', m.end())]
    vals = []
    for i, v in enumerate(split_args(body)):
        match = re.match(r'(\w+)\s*(?:\((.*)\))?\s*$', v, re.S)
        if match:
            vals.append({'name': match[1], 'ordinal': i, 'arguments': split_args(match[2] or '')})
    return vals


def source_ref(mod: str, rel: str, line: int) -> dict:
    return {'mod': mod, 'path': rel, 'line': line}


def build(roots: dict[str, Path]) -> dict:
    data = {'schema_version': 1, 'target': {'minecraft': '1.20.1', 'forge': '47.4.10', 'java': 17},
            'methodology': {
                'source_scope': 'all files below src; hashes are raw bytes; Java LOC counts splitlines()',
                'physical_loc': 'all physical Java lines, including comments and blank lines',
                'code_loc': 'nonempty lines after comment masking; braces/imports count',
                'parser': 'balanced lexical calls; comments excluded; full expressions preserved; not a Java compiler',
                'recipe_count': 'call sites, never claimed to equal runtime-expanded recipe count',
                'registry_mapping': 'lowercase legacy name; nonzero material/circuit metadata => base_N',
                'semantic_policy': 'name or resource equality alone does not demonstrate behavioral equivalence',
                'coverage': 'every Java file is a review obligation, including utility APIs and apparently dormant classes'},
            'mods': {}, 'registries': [], 'recipe_call_sites': [], 'recipe_helpers': [], 'registration_call_sites': [],
            'ore_dictionary': [], 'network_packets': [], 'integrations': [], 'world_registration_calls': [],
            'config_declarations': [], 'resources': [], 'source_files': [], 'metadata_mappings': [],
            'review_obligations': [], 'runtime_requirements': []}
    cached = {}
    for mod, root in roots.items():
        files, resource_files, module_counts = [], [], defaultdict(lambda: Counter())
        for path in sorted((root / 'src').rglob('*')):
            if not path.is_file():
                continue
            raw = path.read_bytes()
            rel = path.relative_to(root).as_posix()
            row = {'mod': mod, 'path': rel, 'sha256': sha(raw), 'bytes': len(raw)}
            if path.suffix == '.java':
                text = raw.decode('utf-8-sig')
                clean = uncomment(text)
                row.update({'physical_loc': len(text.splitlines()),
                            'code_loc': sum(bool(x.strip()) for x in clean.splitlines()),
                            'nonblank_loc': sum(bool(x.strip()) for x in text.splitlines()),
                            'module': category(mod, rel)})
                pkg = re.search(r'\bpackage\s+([^;]+);', clean)
                row['package'] = pkg[1] if pkg else None
                row['types'] = [{'kind': m[1], 'name': m[2], 'declaration': m[0],
                                 'line': clean.count('\n', 0, m.start()) + 1}
                                for m in re.finditer(r'\b(class|interface|enum)\s+(\w+)[^{;]*\{', clean)]
                row['methods'] = [{'declaration': ' '.join(m[0].split()), 'line': clean.count('\n', 0, m.start()) + 1}
                                  for m in re.finditer(r'\b(?:public|protected|private)\s+(?:static\s+|final\s+|abstract\s+|synchronized\s+)*(?:[\w<>\[\].?,]+\s+)?\w+\s*\([^;{}]*\)\s*(?:throws\s+[^;{]+)?[;{]', clean)]
                row['integration_imports'] = sorted(set(re.findall(r'^import\s+(?!java\.|javax\.|net\.minecraft\.|net\.minecraftforge\.|sonar\.|org\.apache\.|com\.google\.|io\.netty\.|org\.lwjgl\.)([\w.]+)', clean, re.M)))
                cached[(mod, rel)] = (clean, controls(clean))
                module_counts[row['module']].update({'files': 1, 'physical_loc': row['physical_loc'], 'code_loc': row['code_loc']})
                files.append(row)
                data['source_files'].append(row)
                data['review_obligations'].append({'key': f'source:{mod}:{rel}', 'kind': 'source-behavior',
                    'upstream_sha256': row['sha256'], 'source': source_ref(mod, rel, 1), 'module': row['module']})
            elif '/resources/' in rel:
                row['resource_path'] = rel.split('/resources/', 1)[1]
                row['extension'] = path.suffix.lower()
                data['resources'].append(row)
                resource_files.append(row)
        digest = sha('\n'.join(f"{r['path']} {r['sha256']}" for r in files + resource_files).encode())
        data['mods'][mod] = {'root': str(root.resolve()), 'git': git_info(root), 'source_tree_sha256': digest,
            'java_files': len(files), 'physical_loc': sum(f['physical_loc'] for f in files),
            'code_loc': sum(f['code_loc'] for f in files), 'nonblank_loc': sum(f['nonblank_loc'] for f in files),
            'resource_files': len(resource_files), 'resource_bytes': sum(f['bytes'] for f in resource_files),
            'resource_extensions': dict(sorted(Counter(f['extension'] for f in resource_files).items())),
            'modules': dict(sorted(module_counts.items()))}

    def lookup(mod, suffix):
        matches = [(r, t[0]) for (m, r), t in cached.items() if m == mod and r.endswith(suffix)]
        if len(matches) != 1:
            raise ValueError(f'expected one {mod} {suffix}, got {len(matches)}')
        return matches[0]

    stable_rel, stable_text = lookup('sonarcore', '/StableStone.java')
    stable = enum_values(stable_text, 'Variants')
    material_rel, material_text = lookup('calculator', '/MaterialBlock.java')
    material = enum_values(material_text, 'Variants')
    circuit_rel, circuit_text = lookup('calculator', '/CircuitBoard.java')
    circuit_count = re.search(r'super\s*\(\s*(\d+)\s*\)', circuit_text)
    if not circuit_count:
        raise ValueError('CircuitBoard subtype count could not be parsed')

    def simple_value_signature():
        _, base = lookup('sonarcore', '/ValueHelperV2.java')
        body = base[base.index('class SimpleValueHelper'):]
        m = re.search(r'super\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(true|false)\s*\)', body)
        if not m:
            raise ValueError('SimpleValueHelper signature cannot be parsed')
        return int(m[1]), int(m[2]), m[3] == 'true'

    items_rel, items_text = lookup('calculator', '/CalculatorItems.java')
    circuit_counts = {}
    for c in calls(items_text, r'SonarRegister\.addItem'):
        m = re.search(r'new SonarMetaItem\s*\(\s*(\d+)\s*\)', c['expression'])
        if m:
            circuit_counts[json.loads(c['args'][-2]).lower()] = int(m[1])

    for (mod, rel), (text, ctrl) in cached.items():
        # Direct and wrapper entry points; declarations lack a receiver in these APIs.
        for c in calls(text, r'SonarRegister\.add(?:Block|Item)'):
            args = c['args']
            isblock = c['callee'].endswith('Block')
            kind = 'block' if isblock else 'item'
            tail = args[-1]
            wrapper = list(calls(tail, r'new\s+SonarRegistry(?:Meta)?block|new\s+SonarRegistryBlock|new\s+SonarRegistryItem'))
            if wrapper:
                wargs = wrapper[0]['args']
                name, constructor = wargs[1], wargs[0]
                tile = wargs[2].removesuffix('.class') if len(wargs) > 2 else None
            else:
                name, constructor, tile = args[-2], tail, None
            names = []
            expansion = None
            if re.fullmatch(r'"[^"\n]+"', name):
                names = [(json.loads(name), None)]
            elif re.fullmatch(r'"[^"\n]+"\s*\+\s*\'_\'\s*\+\s*variant\.name\(\)', name):
                prefix = re.match(r'"([^"]+)"', name)[1]
                names = [(prefix + '_' + v['name'], v['ordinal']) for v in stable]
                expansion = {'enum': 'StableStone.Variants', 'source': source_ref('sonarcore', stable_rel, 15), 'count': len(stable)}
            else:
                data['registration_call_sites'].append({'kind': kind, 'source': source_ref(mod, rel, c['line']),
                    **c, 'parse_status': 'unresolved-name', 'controls': context(ctrl, c['offset'])})
            assignment = re.search(r'([\w]+(?:\[[^]]+\])?)\s*=\s*$', text[max(0, c['offset'] - 120):c['offset']])
            for name_value, ordinal in names:
                record = {'mod': mod, 'kind': kind, 'legacy_name': name_value, 'id': mod + ':' + name_value.lower(),
                    'field': assignment[1] if assignment else None, 'constructor': constructor, 'tile_class': tile,
                    'source': source_ref(mod, rel, c['line']), 'expression': c['expression'], 'metadata': 0,
                    'parse_status': 'enum-expanded' if expansion else 'literal', 'expansion': expansion,
                    'enum_ordinal': ordinal, 'controls': context(ctrl, c['offset'])}
                data['registries'].append(record)
                if isblock:
                    data['registries'].append({**record, 'kind': 'item', 'implicit_block_item': True,
                        'wrapper_source': 'sonarcore:src/main/java/sonar/core/network/SonarCommon.java:27'})
                    if tile:
                        data['registries'].append({**record, 'kind': 'block_entity', 'legacy_id_note': 'unqualified legacy TileEntity key; see AbstractSonarRegistry'})
        # Enumerate all relevant registration calls, even if their arguments are dynamic.
        for c in calls(text, r'(?:[\w$.]+\.)?(?:register\w*|addRecipe|addRecipes|addShaped\w*|addShapeless\w*|addSmelting|addBlock|addItem|addCategory|addRecipeCategory|putObject)'):
            after = text[c['end']:].lstrip()
            prefix = text[max(0, c['offset'] - 100):c['offset']]
            if after.startswith('{') or re.search(r'\b(?:void|public|private|protected|static)\s*$', prefix):
                continue
            row = {'source': source_ref(mod, rel, c['line']), 'callee': c['callee'], 'arguments': c['args'],
                   'expression': c['expression'], 'controls': context(ctrl, c['offset']),
                   'parse_status': 'call-site-not-runtime-count'}
            data['registration_call_sites'].append(row)
            if c['callee'].endswith('registerTileEntity') and c['callee'] == 'SonarRegister.registerTileEntity':
                name = expression(c['args'][-1])
                if name['kind'] == 'string':
                    data['registries'].append({'mod': mod, 'kind': 'block_entity', 'legacy_name': name['value'],
                        'id': mod + ':' + name['value'].lower(), 'tile_class': c['args'][0].removesuffix('.class'),
                        'source': row['source'], 'expression': c['expression'], 'parse_status': 'literal'})
            if c['callee'].endswith('registerModEntity'):
                loc = list(calls(c['args'][0], r'new\s+ResourceLocation'))
                name = expression(loc[0]['args'][-1]) if loc else {}
                if name.get('kind') == 'string':
                    data['registries'].append({'mod': mod, 'kind': 'entity_type', 'legacy_name': name['value'],
                        'id': mod + ':' + name['value'].lower(), 'entity_class': c['args'][1],
                        'source': row['source'], 'arguments': c['args'], 'parse_status': 'literal'})
            if c['callee'].endswith('registerMessage'):
                data['network_packets'].append({**row, 'packet': c['args'][1], 'discriminator_expression': c['args'][2],
                    'direction': c['args'][3], 'runtime_verification': 'registration order, optional mod branches, codec, thread and authorization'})
            if c['callee'].endswith('registerOre'):
                data['ore_dictionary'].append(row)
            if re.search(r'(?:registerDimension|registerBiome|registerWorldGenerator)', c['callee']):
                data['world_registration_calls'].append(row)
        # Account for direct BLOCKS register bypassing SonarRegister (GasLanternOn).
        for c in calls(text, r'ForgeRegistries\.BLOCKS\.register'):
            field = c['args'][0]
            assignment = re.search(r'\b' + re.escape(field) + r'\s*=\s*([^;]+);', text[:c['offset']])
            if assignment:
                namecall = list(calls(assignment[1], r'setRegistryName'))
                if namecall and len(namecall[-1]['args']) == 2:
                    name = expression(namecall[-1]['args'][1])
                    if name['kind'] == 'string':
                        base = {'mod': mod, 'legacy_name': name['value'], 'id': mod + ':' + name['value'].lower(),
                            'field': field, 'constructor': assignment[1], 'source': source_ref(mod, rel, c['line']),
                            'parse_status': 'direct-register-literal', 'metadata': 0}
                        data['registries'].append({**base, 'kind': 'block'})
                        # The paired direct ItemBlock registration is present in this source.
                        direct_items = [x for x in calls(text, r'ForgeRegistries\.ITEMS\.register') if field in x['expression']]
                        if direct_items:
                            data['registries'].append({**base, 'kind': 'item', 'implicit_block_item': True,
                                'source': source_ref(mod, rel, direct_items[0]['line'])})
        # Capture mod probes/annotations and external imports, preserving dependency hints.
        refs = defaultdict(list)
        for m in re.finditer(r'(?:isModLoaded\s*\(\s*|modid\s*=\s*)"([^\"]+)"', text):
            refs[m[1].lower()].append(text.count('\n', 0, m.start()) + 1)
        for dep, lines in refs.items():
            data['integrations'].append({'mod': mod, 'dependency': dep, 'source': source_ref(mod, rel, lines[0]),
                'lines': lines, 'status': 'optional-or-conditional-reference; availability in 1.20.1 requires explicit review'})
        if rel.endswith('Config.java') or rel.endswith('Constants.java'):
            for m in re.finditer(r'\b(?:public\s+)?static\s+(?:final\s+)?(\w+)\s+(\w+)\s*=\s*([^;]+);', text):
                data['config_declarations'].append({'source': source_ref(mod, rel, text.count('\n', 0, m.start()) + 1),
                    'type': m[1], 'name': m[2], 'value_expression': m[3].strip()})
        machine = '/common/recipes/' in rel and 'void addRecipes(' in text
        craft = rel.endswith(('CalculatorCrafting.java', 'SonarCrafting.java'))
        smelt = rel.endswith('CalculatorSmelting.java')
        if not (machine or craft or smelt):
            continue
        methods = list(calls(text, r'addRecipes|registerCraftingRecipes|addSmeltingRecipes'))
        method_spans = []
        for c in methods:
            pos = c['end']
            while pos < len(text) and text[pos].isspace():
                pos += 1
            if pos < len(text) and text[pos] == '{':
                method_spans.append((pos, closing(text, pos)))
        helper = None
        if machine:
            ctor = re.search(r'\bsuper\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(true|false)\s*\)', text)
            rev = bool(re.search(r'boolean\s+reverseRecipes\s*\(\s*\)\s*\{\s*return\s+true', text))
            rid = re.search(r'String\s+getRecipeID\s*\(\s*\)\s*\{\s*return\s+"([^"]+)"', text)
            helper = {'mod': mod, 'class': Path(rel).stem, 'source': source_ref(mod, rel, 1),
                      'recipe_id': rid[1] if rid else None,
                      'inputs': int(ctor[1]) if ctor else None, 'outputs': int(ctor[2]) if ctor else None,
                      'shapeless': ctor[3] == 'true' if ctor else None, 'reverse': rev}
            if 'extends SimpleValueHelper' in text:
                ni, no, shapeless = simple_value_signature()
                helper.update({'inputs': ni, 'outputs': no, 'shapeless': shapeless,
                               'signature_source': 'sonarcore:ValueHelperV2.SimpleValueHelper'})
            helper['research_recipes_enabled_default'] = None
            enabled = re.search(r'boolean\s+enableResearchRecipes\s*\(\s*\)\s*\{\s*return\s+(true|false)', text)
            if enabled:
                helper['research_recipes_enabled_default'] = enabled[1] == 'true'
            if 'FabricationChamberRecipes' == helper['class']:
                helper['custom_signature'] = 'first argument output; remaining circuit inputs; c(meta,count,stable) adds Analysed=true and Stable=0/1 NBT'
            data['recipe_helpers'].append(helper)
        for c in calls(text, r'(?:[\w$.]+\.)?(?:addRecipe|addShapedOre|addShaped|addShapelessOre|addShapeless|addSmelting)'):
            if not any(start <= c['offset'] <= end for start, end in method_spans):
                continue
            args = c['args']
            active_controls = context(ctrl, c['offset'])
            row = {'key': f'recipe:{mod}:{rel}:{c["line"]}:{c["offset"]}',
                'mod': mod, 'source': source_ref(mod, rel, c['line']), 'callee': c['callee'],
                'expression': c['expression'], 'arguments': [expression(a) for a in args],
                'controls': active_controls, 'family': 'machine' if machine else ('smelting' if smelt else 'crafting'),
                'runtime_expansion_required': bool(active_controls), 'source_call_count': 1,
                'parse_status': 'lexically-parsed; semantic-and-runtime-review-required'}
            if machine:
                row['helper'] = helper['class']
                if helper['inputs'] is not None:
                    first = helper['outputs'] if helper['reverse'] else helper['inputs']
                    second = helper['inputs'] if helper['reverse'] else helper['outputs']
                    a, b = args[:first], args[first:first + second]
                    row['inputs'] = [expression(x) for x in (b if helper['reverse'] else a)]
                    row['outputs'] = [expression(x) for x in (a if helper['reverse'] else b)]
                    row['additionals'] = [expression(x) for x in args[first + second:]]
                local = re.findall(r'\btype\s*=\s*(ResearchRecipeType\.\w+)', text[:c['offset']])
                if local:
                    row['research_type_lexical_binding'] = local[-1]
                if helper['class'] == 'FabricationChamberRecipes':
                    row['outputs'] = [expression(args[0])]
                    row['inputs'] = []
                    for arg in args[1:]:
                        inp = expression(arg)
                        cm = re.fullmatch(r'c\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(true|false)\s*\)', arg)
                        if cm:
                            inp.update({'kind': 'circuit_nbt_requirement', 'metadata': int(cm[1]), 'count': int(cm[2]),
                                        'nbt': {'Stable': 1 if cm[3] == 'true' else 0, 'Analysed': True}})
                        row['inputs'].append(inp)
                if helper['research_recipes_enabled_default'] is False and row.get('additionals'):
                    row['default_enabled'] = False
                    row['disabled_reason'] = 'enableResearchRecipes() returns false and buildRecipe rejects type != NONE'
                else:
                    row['default_enabled'] = 'requires runtime object/tag validation'
                for cc in active_controls:
                    if cc['kind'] == 'switch':
                        sw = next(x for x in ctrl if x['line'] == cc['line'] and x['kind'] == 'switch')
                        labels = list(re.finditer(r'\b(?:case\s+[^:]+|default)\s*:', text[sw['start']:c['offset']]))
                        row.setdefault('switch_case_lexical_context', []).append(labels[-1][0] if labels else 'unresolved')
            elif craft:
                ore = c['callee'].endswith('Ore')
                outpos = 1 if ore else 2
                row['output'] = expression(args[outpos])
                inp = args[outpos + 1:]
                if 'Shaped' in c['callee']:
                    pattern, keys = [], []
                    while inp and inp[0].startswith('"'):
                        pattern.append(expression(inp.pop(0)))
                    for j in range(0, len(inp) - 1, 2):
                        keys.append({'symbol': expression(inp[j]), 'ingredient': expression(inp[j + 1])})
                    row.update({'pattern': pattern, 'keys': keys, 'unpaired_arguments': inp[len(keys) * 2:]})
                else:
                    row['inputs'] = [expression(x) for x in inp]
            elif len(args) >= 3:
                row.update({'input': expression(args[0]), 'output': expression(args[1]), 'experience': expression(args[2])})
            data['recipe_call_sites'].append(row)
            data['review_obligations'].append({'key': row['key'], 'kind': 'recipe-semantics', 'source': row['source'],
                'upstream_sha256': sha(c['expression'].encode()), 'dynamic': row['runtime_expansion_required']})

    # Flatten only item variants, not orientation/age/connected-texture block states.
    for base, count, names, evidence in [('material', len(material), [v['name'] for v in material], material_rel),
            ('circuitboard', int(circuit_count[1]), [], circuit_rel),
            ('circuitdamaged', circuit_counts['circuitdamaged'], [], items_rel),
            ('circuitdirty', circuit_counts['circuitdirty'], [], items_rel)]:
        for meta in range(count):
            target = 'calculator:' + base + (f'_{meta}' if meta else '')
            mapping = {'legacy_id': 'calculator:' + base, 'metadata': meta, 'target_id': target,
                'variant_name': names[meta] if names else None, 'source': source_ref('calculator', evidence, 1)}
            data['metadata_mappings'].append(mapping)
            if meta:
                original = [r for r in data['registries'] if r['id'] == 'calculator:' + base and r['kind'] in ('block', 'item')]
                for r in original:
                    data['registries'].append({**r, 'id': target, 'metadata': meta, 'parse_status': 'metadata-flattening-contract',
                        'variant_name': mapping['variant_name'], 'variant_source': mapping['source']})
    keys = set()
    for r in data['registries']:
        key = f'registry:{r["kind"]}:{r["id"]}'
        if key in keys:
            raise ValueError(f'duplicate registry obligation {key}')
        keys.add(key)
        r['key'] = key
        data['review_obligations'].append({'key': key, 'kind': 'registration-and-behavior', 'source': r['source'],
            'upstream_sha256': sha(json.dumps(r, sort_keys=True).encode())})
    data['world_summary'] = {'custom_dimension_registration_calls': 0, 'custom_biome_registration_calls': 0,
        'note': 'No active dimension/biome registrations found by full source scan. Trees, crops, teleportation and world interactions remain behavioral obligations.',
        'search_patterns': ['registerDimension', 'DimensionType', 'registerBiome', 'BiomeManager', 'registerWorldGenerator'],
        'matches': [{'mod': mod, 'path': rel, 'line': text.count('\n', 0, m.start()) + 1, 'token': m[0]}
                    for (mod, rel), (text, _) in cached.items()
                    for m in re.finditer(r'\b(?:registerDimension|DimensionType|registerBiome|BiomeManager|registerWorldGenerator)\b', text)]}
    for name, desc in [
        ('build-java17-forge1201', 'Both modules build on Java17/Forge47.4.10 and metadata/dependencies validate.'),
        ('dedicated-server-start-save-restart', 'Dedicated server loads, saves and restarts without client-only class loading; verify NBT persistence.'),
        ('client-resource-gui-render', 'Actual client launch, all creative content, models/textures/CTM/screens/entity renderers; no missing assets.'),
        ('registries-and-expanded-recipes', 'Runtime registry and recipe snapshots with exact variant, input/output/count/tag/research/conditional equivalence.'),
        ('machine-energy-inventory-and-randomness', 'Numerical energy, processing ticks, upgrades, automation, probability and server authority against upstream.'),
        ('portable-tools-modules-and-research', '45 tools, calculators, module usage, durability/tier/effects, research and cross-dimension warp/jump persist.'),
        ('plants-greenhouse-world-and-weather', 'Tree generation, crops, harvest, multiblock formation/breakage, environment and weather behavior.'),
        ('network-two-player-validation', 'Two-client synchronization, menu clicks, packet side/thread/permissions and reconnect tests.'),
        ('optional-integrations-profiles', 'Test SonarCore alone, both mods, and each available optional integration; unavailable successors require explicit disposition.')]:
        data['runtime_requirements'].append({'key': 'runtime:' + name, 'description': desc})
    data['summary'] = {'java_files': sum(m['java_files'] for m in data['mods'].values()),
        'physical_loc': sum(m['physical_loc'] for m in data['mods'].values()),
        'code_loc': sum(m['code_loc'] for m in data['mods'].values()), 'resources': len(data['resources']),
        'registry_targets': dict(sorted(Counter(r['mod'] + ':' + r['kind'] for r in data['registries']).items())),
        'recipe_call_sites': len(data['recipe_call_sites']), 'recipe_families': dict(Counter(r['family'] for r in data['recipe_call_sites'])),
        'packet_registration_call_sites': len(data['network_packets']), 'ore_dictionary_call_sites': len(data['ore_dictionary']),
        'review_obligations': len(data['review_obligations'])}
    return data


def checklist(data: dict) -> str:
    lines = ['# Calculator / SonarCore Forge 1.20.1 移植对照清单', '',
        '此清单由 tools/audit_baseline.py 从只读 1.12.2 源码生成。未完成勾选是验收义务，不代表已实现。', '',
        '## 固定基线与规模', '', '|库|提交|Java 文件|物理 LOC|去注释非空 LOC|资源文件|', '|---|---|---:|---:|---:|---:|']
    for mod, m in data['mods'].items():
        lines.append(f'|{mod}|`{m["git"]["commit"]}`|{m["java_files"]}|{m["physical_loc"]}|{m["code_loc"]}|{m["resource_files"]}|')
    lines += ['', '物理 LOC 包含空行和注释；去注释非空 LOC 包含 import、括号。逐文件 hash/LOC/type/method/import 见 baseline.json。', '',
        '## 验收原则', '',
        '- [ ] 原始名转 lowercase，不插入下划线；仅 metadata>0 用 base_N；Material 10 个、电路每组 14 个。',
        '- [ ] 同名仅证明候选注册存在。工具类别/属性、机器行为、配方语义、资源、网络与 GUI 必须分别举证。',
        '- [ ] 配方数为源码调用点，包含循环与条件，不能宣称等于实际配方数。运行时矿辞展开、随机输出和研究限制须实际对照。',
        '- [ ] 不把注释中的未启用注册（teleporter/researchChamber/flawlessFurnace 等）当成实际内容；残留类也要有保留或移除的说明。',
        '- [ ] 自定义维度/生物群系注册扫描结果为空；仍须验证树木、作物、传送与环境行为。',
        '- [ ] SHA256 一致仅证明资源字节保留；1.12 模型格式、blockstate、语言键与渲染仍需迁移。', '',
        '## 模块工作量与难点', '', '|库|模块|文件|物理 LOC|', '|---|---|---:|---:|']
    for mod, info in data['mods'].items():
        for module, n in info['modules'].items():
            lines.append(f'|{mod}|{module}|{n["files"]}|{n["physical_loc"]}|')
    lines += ['', '最难模块：灵活菜单/双端网络/NBT 同步；能源/流体/库存 capability 生命周期；温室和发电机多方块；元数据/矿辞配方与研究、概率输出；CTM 与自定义模型渲染；MCMultipart、IC2、Tesla/RF、Waila 等旧接口的替代与兼容验证。', '',
        '## 实际运行测试义务', '']
    for r in data['runtime_requirements']:
        lines.append(f'- [ ] `{r["key"]}` — {r["description"]}')
    lines += ['', '## 注册内容与行为逐项对照', '', '|类型|目标 ID|原始 metadata|实现构造或类|来源|', '|---|---|---:|---|---|']
    for r in data['registries']:
        src = r['source']
        ctor = r.get('tile_class') or r.get('entity_class') or r.get('constructor', '')
        lines.append(f'|{r["kind"]}|`{r["id"]}`|{r.get("metadata", 0)}|`{ctor.replace(chr(10), " ").replace("|", "&#124;")}`|{src["path"]}:{src["line"]}|')
    lines += ['', '## 配方类别与源码调用点', '', '|来源|调用点|控制流内调用点|', '|---|---:|---:|']
    groups = defaultdict(list)
    for r in data['recipe_call_sites']:
        groups[r['mod'] + '/' + r['source']['path']].append(r)
    for name, entries in sorted(groups.items()):
        lines.append(f'|{name}|{len(entries)}|{sum(bool(e["controls"]) for e in entries)}|')
    lines += ['', '每条配方完整 expression/arguments、inputs/outputs、研究绑定、pattern/key 和 controls 均位于 baseline.json；不使用猜测后的现代物品 ID 替代原始表达式。', '',
        '## 网络与兼容集成', '', '|库|包|方向|原 discriminator|条件|', '|---|---|---|---|---|']
    for r in data['network_packets']:
        lines.append(f'|{r["source"]["mod"]}|{r["packet"]}|{r["direction"]}|{r["discriminator_expression"]}|{str(r["controls"]).replace("|", "&#124;")}|')
    lines += ['', '需审查的可选模组探测/注解及外部 import 均保留在 baseline.json。探测到依赖不等于当代版本存在。', '',
        ', '.join(sorted({r['dependency'] for r in data['integrations']})), '',
        '## 静态验收执行', '', '```text',
        'python tools/audit_baseline.py',
        'python tools/audit_port.py --output audit/port-report.json',
        'python tools/audit_selftest.py', '```', '',
        'audit_port.py 默认严格验收，退出 1 表示存在 missing/semantic-review；空工程必败。extra 单独列出，不自动判定行为等价。',
        '可选 --evidence 指向 schema_version=1 的 JSON，绑定 baseline_sha256、target_tree_sha256；reviews 按 obligation key 记录 verdict=equivalent、说明、implementation 与 tests，文件引用需 path 和 sha256。runtime 按 runtime key 提供 status=passed、command、assertions、artifact；注册行为还需 snapshot_file 中的实际 registries。',
        '资源变换通过 resources 条目记录 mod:path、target_path、target_sha256 和说明；需附实际 client-resource-gui-render 运行证据。所有引用均必须在目标工程目录内、存在并 hash 匹配。证据链接检查不能取代独立审阅测试内容。',
        '完整示例结构可通过 audit_port.py --write-evidence-template <audit 路径> 生成；模板含待办状态，不能自动通过。', '',
        '## 每个源码文件的迁移义务', '',
        '完整 711 个源码文件清单及每个 review key 保存在 baseline.json.source_files / review_obligations；允许多旧类合并成新实现，但必须逐义务提供实际行为依据。', '']
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--upstream', type=Path, default=PROJECT.parent / 'upstream')
    parser.add_argument('--output-dir', type=Path, default=PROJECT / 'audit')
    args = parser.parse_args()
    data = build({'calculator': args.upstream / 'Calculator', 'sonarcore': args.upstream / 'Sonar-Core'})
    args.output_dir.mkdir(parents=True, exist_ok=True)
    dump(args.output_dir / 'baseline.json', data)
    manifest = ''.join(f'{r["sha256"]}  {r["mod"]}/{r["path"]}\n' for r in data['resources'])
    (args.output_dir / 'resources.sha256').write_text(manifest, encoding='utf-8')
    (args.output_dir / 'PORTING-CHECKLIST.md').write_text(checklist(data), encoding='utf-8')
    print(json.dumps(data['summary'], ensure_ascii=True, indent=2))


if __name__ == '__main__':
    main()
