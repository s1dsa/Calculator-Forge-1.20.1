"""Strict, fail-closed port audit. Exit 0=accepted evidence, 1=incomplete, 2=invalid input.

Static names are candidates, never behavioral equivalence. Runtime snapshots and
hash-bound source/test reviews may resolve candidates. This validates evidence
structure and references; it is not a substitute for reviewing executed tests.
"""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import re
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
from audit_baseline import calls, closing, dump, sha, uncomment

PROJECT = Path(__file__).resolve().parents[1]


def target_files(roots):
    files = []
    for mod, root in roots.items():
        for path in sorted(root.rglob('*')) if root.exists() else []:
            if not path.is_file():
                continue
            rel = path.relative_to(root)
            if any(x in ('build', '.gradle', '.git', 'run', 'logs', 'out', '__pycache__') for x in rel.parts):
                continue
            if not (rel.parts[0] == 'src' or path.name in ('build.gradle', 'build.gradle.kts', 'gradle.properties')):
                continue
            raw = path.read_bytes()
            files.append({'mod': mod, 'path': rel.as_posix(), 'sha256': sha(raw), 'bytes': len(raw), 'absolute': path})
    return files


def tree_hash(files):
    return sha('\n'.join(f'{r["mod"]}/{r["path"]} {r["sha256"]}' for r in files).encode())


def validate_ref(ref, roots):
    if not isinstance(ref, dict) or not isinstance(ref.get('path'), str) or ref.get('mod') not in roots:
        return False, 'reference requires mod and relative path', None
    root = roots[ref['mod']].resolve()
    path = (root / ref['path']).resolve()
    if root not in path.parents or Path(ref['path']).is_absolute():
        return False, 'reference escapes target module', None
    if not path.is_file() or not path.stat().st_size:
        return False, 'referenced file missing or empty', None
    if not re.fullmatch('[0-9a-f]{64}', ref.get('sha256', '')):
        return False, 'reference requires sha256', None
    if sha(path.read_bytes()) != ref['sha256']:
        return False, 'reference hash mismatch', None
    return True, None, path


def assertions_ok(value):
    return isinstance(value, list) and bool(value) and all(isinstance(v, dict) and v.get('name')
        and v.get('passed') is True and 'expected' in v and 'actual' in v and v['expected'] == v['actual'] for v in value)


def execution_ok(value, roots):
    if not isinstance(value, dict) or value.get('status') != 'passed' or not value.get('command') or value.get('exit_code') != 0:
        return False, 'requires passed execution, command and exit_code=0'
    if not assertions_ok(value.get('assertions')):
        return False, 'requires nonempty named assertions with expected==actual and passed=true'
    ok, issue, _ = validate_ref(value.get('artifact'), roots)
    return ok, issue


def review_ok(evidence, obligation, roots):
    value = evidence.get('reviews', {}).get(obligation['key'])
    if not isinstance(value, dict) or value.get('verdict') != 'equivalent':
        return False, 'no reviewed equivalence evidence'
    if value.get('upstream_sha256') != obligation['upstream_sha256'] or not value.get('explanation', '').strip():
        return False, 'review requires matching upstream_sha256 and explanation'
    implementation = value.get('implementation')
    tests = value.get('tests')
    if not isinstance(implementation, list) or not implementation or not isinstance(tests, list) or not tests:
        return False, 'review requires implementation references and executed tests'
    for ref in implementation:
        ok, issue, path = validate_ref(ref, roots)
        if not ok:
            return False, issue
        if path.suffix not in ('.java', '.json', '.toml', '.gradle', '.kts') or '/src/' not in path.as_posix():
            return False, 'implementation must reference actual target source/resource files'
    for test in tests:
        ok, issue = execution_ok(test, roots)
        if not ok:
            return False, issue
    return True, None


def literal(expr):
    return json.loads(expr) if re.fullmatch(r'"(?:\\.|[^"\\])*"', expr.strip()) else None


def source(row, line):
    return {k: row[k] for k in ('mod', 'path')} | {'line': line}


def scan_registrations(files):
    found, dynamic = [], []
    java = [r for r in files if '/src/main/java/' in '/' + r['path'] and r['absolute'].suffix == '.java']
    type_map = {'Block': 'block', 'Item': 'item', 'BlockEntityType': 'block_entity', 'EntityType': 'entity_type',
                'MenuType': 'menu', 'RecipeType': 'recipe_type', 'RecipeSerializer': 'recipe_serializer', 'CreativeModeTab': 'creative_tab'}
    for row in java:
        text = uncomment(row['absolute'].read_text(encoding='utf-8-sig'))
        registry_fields = {}
        for m in re.finditer(r'DeferredRegister\s*<\s*(\w+)[^;=]*?>\s+(\w+)\s*=', text):
            if m[1] in type_map:
                registry_fields[m[2]] = type_map[m[1]]
        for name, kind in {'BLOCKS': 'block', 'ITEMS': 'item', 'BLOCK_ENTITIES': 'block_entity',
                           'BLOCK_ENTITY_TYPES': 'block_entity', 'ENTITY_TYPES': 'entity_type', 'ENTITIES': 'entity_type'}.items():
            registry_fields.setdefault(name, kind)
        helpers = {}
        for m in re.finditer(r'\b(?:private|public|protected)\s+(?:static\s+)?[\w<>?,\s]+\s+(\w+)\s*\(\s*String\s+(\w+)[^)]*\)\s*\{', text):
            end = closing(text, text.index('{', m.start()))
            body = text[m.end():end]
            kinds = set()
            for c in calls(body, r'\w+\.register'):
                field = c['callee'].split('.')[0]
                if field in registry_fields and len(c['args']) >= 2 and c['args'][0] == m[2]:
                    kinds.add(registry_fields[field])
            if kinds:
                helpers[m[1]] = {'kinds': kinds, 'body': body}
        for c in calls(text, r'\w+\.register'):
            field = c['callee'].split('.')[0]
            if field not in registry_fields or len(c['args']) < 2:
                continue
            name = literal(c['args'][0])
            kind = registry_fields[field]
            if name is None:
                dynamic.append({'kind': kind, 'expression': c['expression'], 'source': source(row, c['line'])})
            else:
                found.append({'kind': kind, 'id': name if ':' in name else row['mod'] + ':' + name,
                              'source': source(row, c['line']), 'expression': c['expression'], 'certainty': 'literal-call-candidate'})
        for name, helper in helpers.items():
            for c in calls(text, re.escape(name)):
                if not c['args'] or literal(c['args'][0]) is None:
                    continue
                ident = literal(c['args'][0])
                for kind in helper['kinds']:
                    found.append({'kind': kind, 'id': row['mod'] + ':' + ident, 'source': source(row, c['line']),
                                  'expression': c['expression'], 'certainty': 'literal-helper-candidate'})
        # Recognize the exact transparent current SonarContent expansion; still no behavior pass.
        if row['path'].endswith('/SonarContent.java') and 'add' in helpers:
            body_match = re.search(r'void\s+buildingSet\s*\([^)]*\)\s*\{', text)
            body = text[body_match.end():closing(text, text.index('{', body_match.start()))] if body_match else ''
            suffixes = re.findall(r'add\(prefix\s*\+\s*"([^"]+)"', body)
            if 'add(fullBlock,' in body and set(suffixes) == {'stairs', 'fence', 'gate'}:
                for c in calls(text, r'buildingSet'):
                    if len(c['args']) == 3 and literal(c['args'][0]) and literal(c['args'][1]):
                        names = [literal(c['args'][1])] + [literal(c['args'][0]) + s for s in suffixes]
                        for ident in names:
                            for kind in helpers['add']['kinds']:
                                found.append({'kind': kind, 'id': row['mod'] + ':' + ident,
                                    'source': source(row, c['line']), 'certainty': 'buildingSet-expansion-candidate'})
            arrays = {}
            for name in ('COLORS', 'styles'):
                m = re.search(r'\b' + name + r'\s*=\s*\{([^}]+)\}', text)
                arrays[name] = re.findall(r'"([^"]+)"', m[1]) if m else []
            loop = re.search(r'add\(styles\[style\]\s*\+\s*"_"\s*\+\s*COLORS\[color\]', text)
            if loop and arrays['COLORS'] and arrays['styles']:
                for style in arrays['styles']:
                    for color in arrays['COLORS']:
                        for kind in helpers['add']['kinds']:
                            found.append({'kind': kind, 'id': row['mod'] + ':' + style + '_' + color,
                                'source': source(row, text.count('\n', 0, loop.start()) + 1), 'certainty': 'array-loop-expansion-candidate'})
    unique = {(r['kind'], r['id']): r for r in found}
    return unique, dynamic, java


def scan_resources(files):
    resources, recipes, invalid = {}, [], []
    for row in files:
        if '/resources/' not in row['path']:
            continue
        res = row['path'].split('/resources/', 1)[1]
        resources[(row['mod'], res)] = row
        if row['absolute'].suffix == '.json':
            try:
                obj = json.loads(row['absolute'].read_text(encoding='utf-8-sig'))
                if '/recipes/' in res:
                    ns = res.split('/')[1]
                    rid = ns + ':' + res.split('/recipes/', 1)[1][:-5]
                    recipes.append({'id': rid, 'mod': row['mod'], 'path': row['path'], 'sha256': row['sha256'],
                                    'type': obj.get('type') if isinstance(obj, dict) else None,
                                    'json': obj, 'semantic_status': 'requires source recipe mapping'})
            except (ValueError, UnicodeError) as e:
                invalid.append({'mod': row['mod'], 'path': row['path'], 'error': str(e)})
    return resources, recipes, invalid


def compare(baseline, baseline_path, roots, evidence=None):
    evidence = evidence or {}
    files = target_files(roots)
    target_hash = tree_hash(files)
    baseline_hash = sha(baseline_path.read_bytes())
    actual, dynamic, java = scan_registrations(files)
    resources, recipes, invalid_json = scan_resources(files)
    expected = {(r['kind'], r['id']): r for r in baseline['registries']}
    report = {'schema_version': 1, 'status': 'FAIL', 'baseline_sha256': baseline_hash, 'target_tree_sha256': target_hash,
              'target_java_files': len(java), 'missing': [], 'extra': [], 'semantic_review': [],
              'name_matches': [], 'resource_byte_matches': [], 'accepted_evidence': [], 'runtime_pending': [],
              'evidence_errors': [], 'unresolved_dynamic_registration': dynamic,
              'target_recipe_files': recipes, 'invalid_target_json': invalid_json,
              'policy': 'Names/bytes only show presence. Missing or unreviewed behavior blocks acceptance; strict full parity needs runtime evidence.',
              'limits': 'Balanced lexical scanner, not Java execution. Dynamic names and merged classes may need mappings. Evidence structure/hash validation does not certify test honesty.'}
    if not java:
        report['missing'].append({'key': 'implementation', 'reason': 'no target main Java implementation'})
    if evidence:
        for key, value in [('schema_version', 1), ('baseline_sha256', baseline_hash), ('target_tree_sha256', target_hash)]:
            if evidence.get(key) != value:
                report['evidence_errors'].append({'key': key, 'reason': 'evidence binding mismatch'})
    binding_ok = bool(evidence) and not report['evidence_errors']
    usable = evidence if binding_ok else {}
    runtime_passed = set()
    for requirement in baseline.get('runtime_requirements', []):
        key = requirement['key']
        ok, issue = execution_ok(usable.get('runtime', {}).get(key), roots)
        if ok:
            runtime_passed.add(key)
        else:
            report['runtime_pending'].append({'key': key, 'reason': issue})
    snapshot = None
    if 'runtime:registries-and-expanded-recipes' in runtime_passed:
        ok, issue, path = validate_ref(usable.get('snapshot_file'), roots)
        if ok:
            try:
                snapshot = json.loads(path.read_text(encoding='utf-8'))
                if snapshot.get('target_tree_sha256') != target_hash or not isinstance(snapshot.get('registries'), list):
                    raise ValueError('snapshot must match target tree and contain registries array')
                for r in snapshot['registries']:
                    if not isinstance(r, dict) or not isinstance(r.get('kind'), str) or not isinstance(r.get('id'), str):
                        raise ValueError('malformed runtime registry row')
                actual = {(r['kind'], r['id']): {**r, 'source': usable['snapshot_file'], 'certainty': 'runtime-snapshot'} for r in snapshot['registries']}
            except (ValueError, TypeError) as e:
                report['evidence_errors'].append({'key': 'snapshot_file', 'reason': str(e)})
                snapshot = None
        else:
            report['evidence_errors'].append({'key': 'snapshot_file', 'reason': issue})
    obligations = {r['key']: r for r in baseline['review_obligations']}
    covered_target = set()
    for (kind, ident), row in expected.items():
        key = row['key']
        mapped = usable.get('registry_mappings', {}).get(key, [ident])
        if not isinstance(mapped, list) or not mapped or not all(isinstance(x, str) for x in mapped):
            mapped = [ident]
        targets = [(kind, x) for x in mapped]
        if not all(k in actual for k in targets):
            report['missing'].append({'key': key, 'kind': kind, 'id': ident, 'source': row['source'],
                'reason': 'no static candidate/runtime registration; dynamic or merged registrations need reviewed mapping'})
            continue
        covered_target.update(targets)
        report['name_matches'].append({'key': key, 'targets': [actual[k] for k in targets]})
        ok, issue = review_ok(usable, obligations[key], roots)
        if ok and snapshot is not None:
            report['accepted_evidence'].append(key)
        else:
            report['semantic_review'].append({'key': key, 'reason': issue or 'runtime registry snapshot required'})
    for k, value in actual.items():
        if k not in covered_target:
            report['extra'].append({'kind': k[0], 'id': k[1], 'source': value['source'],
                                    'reason': 'additional target registration; new menus/serializers may be required by 1.20.1'})
    for key, obligation in obligations.items():
        if key.startswith('registry:'):
            continue
        ok, issue = review_ok(usable, obligation, roots)
        if ok:
            report['accepted_evidence'].append(key)
        else:
            report['semantic_review'].append({'key': key, 'source': obligation['source'], 'reason': issue})
    # Every upstream resource is accounted for, including intentionally transformed legacy formats.
    transformations = {r.get('key'): r for r in usable.get('resources', []) if isinstance(r, dict)}
    used_resources = set()
    for row in baseline['resources']:
        key = row['mod'] + ':' + row['resource_path']
        direct = resources.get((row['mod'], row['resource_path']))
        candidates = [(k, v) for k, v in resources.items() if v['sha256'] == row['sha256']]
        if direct and direct['sha256'] == row['sha256']:
            candidates = [((row['mod'], row['resource_path']), direct)]
        if candidates:
            chosen, resource = candidates[0]
            used_resources.add(chosen)
            report['resource_byte_matches'].append({'key': key, 'target_mod': chosen[0], 'target_path': resource['path'],
                'sha256': row['sha256'], 'relocated': chosen != (row['mod'], row['resource_path'])})
            continue
        tr = transformations.get(key, {})
        ok, issue, path = validate_ref(tr.get('target'), roots)
        if ok and tr.get('upstream_sha256') == row['sha256'] and tr.get('explanation') and 'runtime:client-resource-gui-render' in runtime_passed:
            report['accepted_evidence'].append('resource:' + key)
            used_resources.add((tr['target']['mod'], tr['target']['path'].split('/resources/', 1)[-1]))
        elif direct:
            used_resources.add((row['mod'], row['resource_path']))
            report['semantic_review'].append({'key': 'resource:' + key, 'reason': 'resource bytes changed; migration review and actual client test required',
                'upstream_sha256': row['sha256'], 'target_sha256': direct['sha256']})
        else:
            report['missing'].append({'key': 'resource:' + key, 'reason': 'resource absent or renamed/transformed without evidence', 'sha256': row['sha256']})
    for key, resource in resources.items():
        if key not in used_resources:
            report['extra'].append({'kind': 'resource', 'mod': key[0], 'path': key[1], 'sha256': resource['sha256']})
    # JSON files themselves never establish recipe equivalence. List uncovered recipe additions explicitly.
    covered_recipe_paths = set()
    for key in report['accepted_evidence']:
        if key.startswith('recipe:'):
            for ref in usable['reviews'][key]['implementation']:
                covered_recipe_paths.add((ref['mod'], ref['path']))
    for recipe in recipes:
        if (recipe['mod'], recipe['path']) not in covered_recipe_paths:
            report['semantic_review'].append({'key': 'target-recipe:' + recipe['id'], 'reason': 'target recipe lacks input/output/count/NBT/chance/condition mapping',
                'path': recipe['path']})
    if dynamic and snapshot is None:
        report['semantic_review'].append({'key': 'dynamic-registrations', 'reason': 'source names are dynamic; capture runtime registry snapshot', 'count': len(dynamic)})
    if invalid_json:
        report['missing'].append({'key': 'valid-resource-json', 'reason': 'target resource JSON parse failure', 'count': len(invalid_json)})
    report['summary'] = {'expected_registries': len(expected), 'static_or_runtime_candidates': len(actual),
        'name_matches': len(report['name_matches']), 'resource_byte_matches': len(report['resource_byte_matches']),
        'target_recipe_files': len(recipes), 'missing': len(report['missing']), 'extra': len(report['extra']),
        'semantic_review': len(report['semantic_review']), 'runtime_pending': len(report['runtime_pending']),
        'evidence_errors': len(report['evidence_errors']), 'accepted_evidence': len(report['accepted_evidence'])}
    # Extra modern API registrations/resources are informational; a parity claim still needs full review.
    report['status'] = 'PASS' if not (report['missing'] or report['semantic_review'] or report['runtime_pending'] or report['evidence_errors']) else 'FAIL'
    return report


def template(baseline, baseline_path, roots):
    return {'schema_version': 1, 'baseline_sha256': sha(baseline_path.read_bytes()), 'target_tree_sha256': tree_hash(target_files(roots)),
        'reviews': {r['key']: {'verdict': 'pending', 'upstream_sha256': r['upstream_sha256'], 'explanation': '',
                              'implementation': [], 'tests': []} for r in baseline['review_obligations']},
        'runtime': {r['key']: {'status': 'pending', 'command': '', 'exit_code': None, 'assertions': [], 'artifact': None}
                    for r in baseline['runtime_requirements']},
        'registry_mappings': {}, 'snapshot_file': None, 'resources': [],
        'format_examples_only': {'reference': {'mod': 'calculator', 'path': 'src/main/java/Example.java', 'sha256': 'ACTUAL_SHA256'},
             'assertion': {'name': 'machine energy per tick', 'expected': 40, 'actual': 40, 'passed': True},
             'resource': {'key': 'calculator:assets/calculator/lang/en_us.lang', 'upstream_sha256': 'BASELINE_SHA256',
                          'target': {'mod': 'calculator', 'path': 'src/main/resources/assets/calculator/lang/en_us.json', 'sha256': 'ACTUAL_SHA256'},
                          'explanation': 'Describe every key/value transformation'},
             'registry_mapping': {'registry:block_entity:calculator:powercube': ['calculator:machine']}}}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--baseline', type=Path, default=PROJECT / 'audit/baseline.json')
    p.add_argument('--target', type=Path, default=PROJECT)
    p.add_argument('--output', type=Path, default=PROJECT / 'audit/port-report.json')
    p.add_argument('--evidence', type=Path)
    p.add_argument('--write-evidence-template', type=Path)
    args = p.parse_args()
    try:
        baseline = json.loads(args.baseline.read_text(encoding='utf-8'))
        if baseline.get('schema_version') != 1 or not baseline.get('registries') or not baseline.get('source_files') or not baseline.get('resources'):
            raise ValueError('empty/incompatible baseline cannot be accepted')
        evidence = json.loads(args.evidence.read_text(encoding='utf-8')) if args.evidence else None
        if evidence is not None and not isinstance(evidence, dict):
            raise ValueError('evidence must be an object')
        roots = {mod: args.target / mod for mod in baseline['mods']}
        if args.write_evidence_template:
            dump(args.write_evidence_template, template(baseline, args.baseline, roots))
            print('Wrote pending evidence template; this does not perform acceptance.')
            return 0
        report = compare(baseline, args.baseline, roots, evidence)
        dump(args.output, report)
        print(json.dumps({'status': report['status'], **report['summary']}, indent=2))
        return 0 if report['status'] == 'PASS' else 1
    except (OSError, ValueError, TypeError, KeyError) as e:
        print('AUDIT ERROR: ' + str(e), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
