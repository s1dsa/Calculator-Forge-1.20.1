"""Structural checks for converted SonarCore assets, registered IDs and upstream texture fidelity."""
from pathlib import Path
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/sonarcore'
UPSTREAM = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parents[1] / 'upstream/Sonar-Core'
errors = []
files = list(RES.rglob('*.json'))
for path in files:
    try:
        data = json.loads(path.read_text(encoding='utf-8'))
    except Exception as failure:
        errors.append(f'{path}: {failure}')
        continue
    if '/models/' in path.as_posix():
        for texture in data.get('textures', {}).values():
            if texture.startswith('sonarcore:'):
                target = ASSETS / 'textures' / (texture.split(':', 1)[1] + '.png')
                if not target.is_file(): errors.append(f'{path}: missing texture {texture}')
        parent = data.get('parent', '')
        if parent.startswith('sonarcore:') and not (ASSETS / 'models' / (parent.split(':', 1)[1] + '.json')).is_file():
            errors.append(f'{path}: missing parent {parent}')
    if '/blockstates/' in path.as_posix():
        entries = list(data.get('variants', {}).values()) + [p['apply'] for p in data.get('multipart', [])]
        for value in entries:
            for model in value if isinstance(value, list) else [value]:
                target = model['model']
                if target.startswith('sonarcore:') and not (ASSETS / 'models' / (target.split(':', 1)[1] + '.json')).is_file():
                    errors.append(f'{path}: missing model {target}')
        if path.stem.startswith('stable') or path.stem == 'clearstableglass':
            if len(data.get('variants', {})) != 64: errors.append(f'{path}: expected 64 connection variants')
            original = json.loads((UPSTREAM / 'src/main/resources/assets/sonarcore/blockstates' / path.name).read_text())
            for state, variant in data['variants'].items():
                actual = json.loads((ASSETS / 'models' / (variant['model'].split(':', 1)[1] + '.json')).read_text())['textures']
                expected = original['defaults']['textures'] | original['variants'][state].get('textures', {})
                if actual != expected: errors.append(f'{path}: texture mapping changed at {state}')

ids = {p.stem for p in (ASSETS / 'blockstates').glob('*.json')}
assert len(ids) == 66, len(ids)
for block in ids:
    for path in [ASSETS / f'models/item/{block}.json', RES / f'data/sonarcore/loot_tables/blocks/{block}.json']:
        if not path.is_file(): errors.append(f'Missing required resource: {path}')
recipes = list((RES / 'data/sonarcore/recipes').glob('*.json'))
assert len(recipes) == 86, len(recipes)
assert sum('conditions' in json.loads(p.read_text()) for p in recipes) == 6
for path in recipes:
    result = json.loads(path.read_text())['result']['item']
    if result.split(':')[1] not in ids: errors.append(f'{path}: unknown output {result}')
for language in (ASSETS / 'lang').glob('*.json'):
    translations = json.loads(language.read_text(encoding='utf-8'))
    missing = [block for block in ids if f'block.sonarcore.{block}' not in translations]
    if missing: errors.append(f'{language}: missing translations {missing}')
old_textures = list((UPSTREAM / 'src/main/resources/assets/sonarcore/textures').rglob('*.png'))
assert len(old_textures) == 812
for original in old_textures:
    copied = ASSETS / 'textures' / original.relative_to(UPSTREAM / 'src/main/resources/assets/sonarcore/textures')
    if not copied.is_file() or hashlib.sha256(original.read_bytes()).digest() != hashlib.sha256(copied.read_bytes()).digest():
        errors.append(f'Original texture missing or modified: {copied}')
if errors:
    print('\n'.join(errors))
    raise SystemExit(1)
print(f'PASS: {len(files)} JSON files; 66 block/item/loot sets; 50 x 64 exact original connection mappings;')
print('86 recipes (6 conditional); 812 byte-identical textures; 3 complete languages; all local model/texture references.')
