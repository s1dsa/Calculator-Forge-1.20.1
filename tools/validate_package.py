"""Validate built mod contents, never infer behavioral parity from ZIP filenames.
Requires Python 3.11+. Exit 0 means packaging checks only, not full port parity.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import tomllib
import zipfile

PROJECT = Path(__file__).resolve().parents[1]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def validate_jar(mod: str, path: Path, project: Path = PROJECT) -> dict:
    issues, checked = [], []
    result = {'mod': mod, 'jar': str(path.resolve()), 'parity_proven': False,
              'scope': 'Packaging, parsed metadata and packaged resource bytes only', 'issues': issues}
    if not path.is_file():
        issues.append('Built jar is missing')
        return result | {'status': 'FAIL'}
    result['sha256'] = sha(path.read_bytes())
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)):
                issues.append('Duplicate ZIP entries')
            corrupt = archive.testzip()
            if corrupt:
                issues.append('CRC failure: ' + corrupt)
            required = ['LICENSE', 'META-INF/mods.toml', 'pack.mcmeta',
                        'sonar/calculator/mod/Calculator.class' if mod == 'calculator' else 'sonar/core/SonarCore.class']
            if mod == 'calculator':
                required += ['data/calculator/structures/test_empty.nbt', 'data/calculator/structures/test_perf.nbt',
                             'sonar/calculator/mod/gametest/RegistryGameTests.class',
                             'sonar/calculator/mod/gametest/RecipeGameTests.class',
                             'sonar/calculator/mod/gametest/MachineGameTests.class',
                             'sonar/calculator/mod/gametest/MachinePerformanceGameTests.class']
            for name in required:
                if name not in names or not archive.read(name):
                    issues.append('Missing/empty required entry: ' + name)
            if 'LICENSE' in names:
                license_bytes = archive.read('LICENSE')
                if license_bytes != (project / 'LICENSE').read_bytes():
                    issues.append('Packaged LICENSE differs from project LICENSE')
                license_text = license_bytes.decode('utf-8-sig')
                for phrase in ('Copyright (c) 2018 Ollie Lansdell', 'Permission is hereby granted', 'THE SOFTWARE IS PROVIDED "AS IS"'):
                    if phrase not in license_text:
                        issues.append('LICENSE missing upstream MIT attribution/terms: ' + phrase)
            if 'META-INF/mods.toml' in names:
                text = archive.read('META-INF/mods.toml').decode('utf-8')
                if '${' in text:
                    issues.append('Unexpanded mods.toml build property')
                meta = tomllib.loads(text)
                definitions = meta.get('mods', [])
                if len(definitions) != 1 or definitions[0].get('modId') != mod:
                    issues.append('Expected exactly one matching modId')
                if meta.get('modLoader') != 'javafml' or meta.get('license') != 'MIT':
                    issues.append('Incorrect loader/license metadata')
                deps = {d.get('modId'): d for d in meta.get('dependencies', {}).get(mod, [])}
                for dependency in ('minecraft', 'forge') + (('sonarcore',) if mod == 'calculator' else ()):
                    dep = deps.get(dependency, {})
                    if dep.get('mandatory') is not True or not dep.get('versionRange'):
                        issues.append('Missing mandatory versioned dependency: ' + dependency)
                minecraft_range = deps.get('minecraft', {}).get('versionRange', '')
                if '1.20.1' not in minecraft_range:
                    issues.append('Minecraft dependency does not identify target 1.20.1')
                result['metadata'] = meta
            if 'pack.mcmeta' in names:
                pack = json.loads(archive.read('pack.mcmeta'))
                if pack.get('pack', {}).get('pack_format') != 15:
                    issues.append('Wrong 1.20.1 pack_format (expected 15)')
            for name in names:
                data = archive.read(name)
                if name.endswith('.class') and data[:4] != b'\xca\xfe\xba\xbe':
                    issues.append('Invalid class header: ' + name)
                if name.endswith('.json'):
                    try:
                        json.loads(data)
                    except (ValueError, UnicodeError):
                        issues.append('Invalid packaged JSON: ' + name)
            resource_roots = [project / mod / 'src/main/resources', project / mod / 'src/generated/resources']
            resources = {}
            for root in resource_roots:
                for resource in sorted(root.rglob('*')) if root.exists() else []:
                    if resource.is_file():
                        name = resource.relative_to(root).as_posix()
                        if name in resources and resources[name].read_bytes() != resource.read_bytes():
                            issues.append('Conflicting source resource: ' + name)
                        resources[name] = resource
            for name, source in resources.items():
                if name not in names:
                    issues.append('Source resource omitted from jar: ' + name)
                elif name not in ('META-INF/mods.toml', 'pack.mcmeta'):
                    actual = archive.read(name)
                    if actual != source.read_bytes():
                        issues.append('Stale/changed packaged resource: ' + name)
                    checked.append({'path': name, 'sha256': sha(actual)})
            if mod == 'calculator':
                recipe_names = [n for n in names if n.startswith('data/calculator/recipes/') and n.endswith('.json')]
                if not recipe_names:
                    issues.append('No machine/crafting recipes packaged')
                result['recipe_files'] = len(recipe_names)
            for prefix in (f'assets/{mod}/lang/', f'assets/{mod}/models/', f'assets/{mod}/textures/'):
                if not any(n.startswith(prefix) for n in names):
                    issues.append('Missing asset family: ' + prefix)
            result['checked_resource_hashes'] = checked
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        issues.append(str(error))
    return result | {'status': 'PASS' if not issues else 'FAIL'}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--calculator-jar', type=Path, required=True)
    parser.add_argument('--sonarcore-jar', type=Path, required=True)
    parser.add_argument('--project', type=Path, default=PROJECT)
    parser.add_argument('--output', type=Path, default=PROJECT / 'build/reports/package-validation.json')
    args = parser.parse_args()
    reports = [validate_jar(mod, path, args.project) for mod, path in
               [('calculator', args.calculator_jar), ('sonarcore', args.sonarcore_jar)]]
    result = {'schema_version': 1, 'status': 'PASS' if all(r['status'] == 'PASS' for r in reports) else 'FAIL',
              'parity_proven': False, 'jars': reports}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': result['status'], 'issues': {r['mod']: r['issues'] for r in reports}}, indent=2))
    return 0 if result['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
