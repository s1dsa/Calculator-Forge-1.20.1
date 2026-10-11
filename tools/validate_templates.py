"""Generate current-version GameTest fixtures using only the Python standard library.

Outputs are dedicated source fixtures. They require no extra Gradle dependency.
"""
from __future__ import annotations
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import struct

PROJECT = Path(__file__).resolve().parents[1]


def named(kind: int, name: str, payload: bytes) -> bytes:
    encoded = name.encode('utf-8')
    return bytes([kind]) + struct.pack('>H', len(encoded)) + encoded + payload


def empty_structure(size: tuple[int, int, int]) -> bytes:
    # Vanilla StructureTemplate NBT, data version for Minecraft 1.20.1.
    payload = named(3, 'DataVersion', struct.pack('>i', 3465))
    payload += named(9, 'size', b'\x03' + struct.pack('>iiii', 3, *size))
    palette = named(8, 'Name', struct.pack('>H', 13) + b'minecraft:air') + b'\x00'
    payload += named(9, 'palette', b'\x0a' + struct.pack('>i', 1) + palette)
    block = named(9, 'pos', b'\x03' + struct.pack('>iiii', 3, 0, 0, 0))
    block += named(3, 'state', struct.pack('>i', 0)) + b'\x00'
    payload += named(9, 'blocks', b'\x0a' + struct.pack('>i', 1) + block)
    payload += named(9, 'entities', b'\x0a' + struct.pack('>i', 0))
    return named(10, '', payload + b'\x00')


def generate(output: Path, baseline: Path) -> dict:
    raw = baseline.read_bytes()
    source = json.loads(raw)
    rows = source['registries']
    expected = {kind: sorted({r['id'] for r in rows
                             if r['kind'] == kind and r['id'].startswith('calculator:')})
                for kind in ('block', 'item')}
    # Pinned upstream expansion: 82 base blocks + 9 material variants;
    # 109 base standalone items + 39 circuit variants + 91 BlockItems.
    if len(expected['block']) != 91 or len(expected['item']) != 239:
        raise ValueError('Baseline must contain exactly 91 Calculator blocks and 239 items; review changed baseline')
    expected['baseline_sha256'] = hashlib.sha256(raw).hexdigest()
    expected['scope'] = 'Registry presence only; not behavior parity'
    generated = []
    for name, size in [('test_empty', (5, 5, 5)), ('test_perf', (18, 5, 18))]:
        path = output / 'data/calculator/structures' / (name + '.nbt')
        path.parent.mkdir(parents=True, exist_ok=True)
        nbt = empty_structure(size)
        compressed = gzip.compress(nbt, mtime=0)
        assert gzip.decompress(compressed) == nbt
        path.write_bytes(compressed)
        generated.append(str(path))
    literals = lambda values: ',\n            '.join(json.dumps(value) for value in values)
    path = PROJECT / 'calculator/src/main/java/sonar/calculator/mod/gametest/RegistryExpectations.java'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('package sonar.calculator.mod.gametest;\n\nimport java.util.Set;\n\n'
                    '/** Frozen upstream expanded registry baseline; presence is not parity. */\n'
                    'final class RegistryExpectations {\n'
                    f'    static final String BASELINE_SHA256 = "{expected["baseline_sha256"]}";\n'
                    f'    static final Set<String> BLOCKS = Set.of(\n            {literals(expected["block"])});\n'
                    f'    static final Set<String> ITEMS = Set.of(\n            {literals(expected["item"])});\n'
                    '    private RegistryExpectations() {}\n}\n', encoding='utf-8')
    generated.append(str(path))
    return {'generated': generated, 'counts': {'blocks': 91, 'items': 239},
            'baseline_sha256': expected['baseline_sha256']}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=PROJECT / 'calculator/src/main/resources')
    parser.add_argument('--baseline', type=Path, default=PROJECT / 'audit/baseline.json')
    args = parser.parse_args()
    print(json.dumps(generate(args.output, args.baseline), indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
