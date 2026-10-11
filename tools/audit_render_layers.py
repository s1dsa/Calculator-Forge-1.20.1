#!/usr/bin/env python3
"""Deterministic evidence for block RenderType wiring (#1/#6/#10/#13/#15).

For every block id registered in CalculatorContent.BLOCK_IDS it resolves the world model chain
(blockstates/<id>.json -> models/block/**.json -> textures) and measures the alpha channel of every
referenced calculator texture.  A block whose world model samples a texture with transparent pixels
MUST be registered with a cutout render layer; a block whose textures are fully opaque must NOT be,
otherwise it is drawn with a needless alpha-test pass.

It then parses CalculatorClient.java and compares the two sets.  Exit code is non-zero when the
registration and the texture evidence disagree.
"""
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pngmap import load_png

ROOT = r'F:\ai\calculator-port'
CALC = os.path.join(ROOT, 'calculator')
ASSETS = os.path.join(CALC, 'src/main/resources/assets/calculator')
BLOCKSTATES = os.path.join(ASSETS, 'blockstates')
MODELS = os.path.join(ASSETS, 'models')
TEXTURES = os.path.join(ASSETS, 'textures')
CLIENT = os.path.join(CALC, 'src/main/java/sonar/calculator/mod/client/CalculatorClient.java')
CONTENT = os.path.join(CALC, 'src/main/java/sonar/calculator/mod/CalculatorContent.java')

# Render layers that mean "alpha test": the texture is allowed to contain holes.
CUTOUT_LAYERS = ('cutout', 'cutoutMipped')


def load_json(path):
    with open(path, 'r', encoding='utf-8') as handle:
        return json.load(handle)


def registry_ids():
    src = open(CONTENT, encoding='utf-8').read()
    match = re.search(r'BLOCK_IDS\s*=\s*List\.of\((.*?)\);', src, re.S)
    return re.findall(r'"([^"]+)"', match.group(1)) if match else []


def model_files(model_ref):
    """Resolve a 'calculator:block/x' model reference to files, following the parent chain."""
    ref = model_ref.split(':')[-1]
    out = []
    seen = set()
    while ref and ref not in seen:
        seen.add(ref)
        path = os.path.join(MODELS, ref + '.json')
        if not os.path.isfile(path):
            break
        data = load_json(path)
        out.append(data)
        ref = data.get('parent', '').split(':')[-1] if data.get('parent') else ''
    return out


def is_ber_only(chain):
    """A model whose own chain declares an empty element list (crank handle, calculator screen) is
    drawn entirely by a block entity renderer, so no block render layer applies."""
    return any('elements' in model and len(model['elements']) == 0 for model in chain)


def texture_pngs(name):
    """Resolve a texture reference to candidate calculator PNG paths."""
    ref = name.split(':')[-1]
    if name.startswith('minecraft:'):
        return []
    # Block models use "blocks/...", item/BER models use "model/...' — both live under textures/.
    candidates = [os.path.join(TEXTURES, ref + '.png')]
    return [c for c in candidates if os.path.isfile(c)]


def alpha_stats(path):
    """(transparent_px, partial_px, total) for an RGBA texture, or None when it has no alpha."""
    w, h, ch, px = load_png(path)
    if ch < 4:
        return None
    total = w * h
    transparent = partial = 0
    for i in range(3, len(px), 4):
        a = px[i]
        if a == 0:
            transparent += 1
        elif a < 250:
            partial += 1
    return transparent, partial, total


def block_textures(block_id):
    """Every texture sampled by every world model variant of a block."""
    path = os.path.join(BLOCKSTATES, block_id + '.json')
    if not os.path.isfile(path):
        return None
    data = load_json(path)
    refs = []
    variants = data.get('variants')
    if isinstance(variants, dict):
        for value in variants.values():
            entries = value if isinstance(value, list) else [value]
            for entry in entries:
                if isinstance(entry, dict) and 'model' in entry:
                    refs.append(entry['model'])
    for part in data.get('multipart', []) or []:
        if isinstance(part.get('apply'), dict) and 'model' in part['apply']:
            refs.append(part['apply']['model'])
    textures = []
    ber_only = False
    for ref in refs:
        chain = model_files(ref)
        if is_ber_only(chain):
            ber_only = True
            continue
        for model in chain:
            for key, name in (model.get('textures') or {}).items():
                # 'particle' feeds the block-break particle, not a rendered face.
                if key != 'particle' and isinstance(name, str):
                    textures.extend(texture_pngs(name))
    if ber_only and not textures:
        return []
    return sorted(set(textures))


def client_registration():
    """(block id -> layer method, wiring errors) read from CalculatorClient.java."""
    src = open(CLIENT, encoding='utf-8').read()
    wiring = []
    if 'ItemBlockRenderTypes.setRenderLayer' not in src:
        wiring.append('CalculatorClient.java never calls ItemBlockRenderTypes.setRenderLayer')
    layers = {}
    for array, method in (('CUTOUT_BLOCKS', 'cutout'), ('CUTOUT_MIPPED_BLOCKS', 'cutoutMipped')):
        match = re.search(array + r'\s*=\s*\{(.*?)\};', src, re.S)
        if not match:
            wiring.append('CalculatorClient.java has no %s array' % array)
            continue
        ids = re.findall(r'"([^"]+)"', match.group(1))
        if not ids:
            wiring.append('%s is empty' % array)
        for block_id in ids:
            layers[block_id] = method
        if ('for (String id : %s)' % array) not in src:
            wiring.append('%s is never iterated into a setRenderLayer call' % array)
    return layers, wiring


def main():
    ids = registry_ids()
    print('blocks declared in CalculatorContent: %d' % len(ids))
    needs_cutout, opaque, no_model = {}, {}, []
    for block_id in ids:
        files = block_textures(block_id)
        if files is None or not files:
            no_model.append(block_id)
            continue
        transparent = []
        for png in files:
            stats = alpha_stats(png)
            if stats is None:
                continue
            holes, partial, total = stats
            if holes > 0:
                transparent.append((os.path.relpath(png, TEXTURES).replace('\\', '/'),
                                    round(100.0 * holes / total, 1)))
        if transparent:
            needs_cutout[block_id] = transparent
        else:
            opaque[block_id] = sorted(os.path.relpath(p, TEXTURES).replace('\\', '/') for p in files)

    print('\nblocks whose world model contains transparent pixels (=> cutout required): %d' % len(needs_cutout))
    for block_id, entries in sorted(needs_cutout.items()):
        short = [e for e in entries]
        print('  %-22s %s' % (block_id, ', '.join('%s %s%%' % e for e in short[:2]) + ('' if len(short) <= 2 else ' (+%d)' % (len(short) - 2))))

    layers, failures = client_registration()
    print('\nCalculatorClient.java render layers: cutout=%d cutoutMipped=%d' % (
        sum(1 for v in layers.values() if v == 'cutout'),
        sum(1 for v in layers.values() if v == 'cutoutMipped')))

    for block_id in sorted(needs_cutout):
        if layers.get(block_id) not in CUTOUT_LAYERS:
            failures.append('block %s has transparent world texture but is not registered cutout/cutoutMipped' % block_id)
    for block_id, method in sorted(layers.items()):
        if block_id not in ids:
            failures.append('registered block %s is not a registered calculator block' % block_id)
        elif block_id in opaque and method in CUTOUT_LAYERS:
            failures.append('block %s is registered cutout but every world texture is fully opaque' % block_id)

    print('\nblocks without a world model (item-only or BER-only): %d' % len(no_model))
    print('blocks with fully opaque world textures: %d' % len(opaque))
    print('\n' + ('FAILURES:\n  ' + '\n  '.join(failures) if failures else 'ALL RENDER LAYER CHECKS PASSED'))
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
