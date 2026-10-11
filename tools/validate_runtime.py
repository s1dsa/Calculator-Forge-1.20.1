"""Validate actual GameTest artifacts without manufacturing audit equivalence evidence.

Requires a current run JUnit XML plus reports. The caller supplies --not-before to
reject stale artifacts. Successful validation covers named smoke tests only.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

PROJECT = Path(__file__).resolve().parents[1]
REQUIRED = (
    'actualRegistryAndLoadedRecipeSnapshot',
    'loadedRecipesConsumeAndReturnExactOutputs',
    'extractionUsesExactLegacyRandomSequence',
    'machineNbtRoundTripAndCapabilities',
    'worldTickerProcessesOneLogAndIdlePreservesEnergy',
    'realWorld64And256MachineSampling',
)
REPORTS = ('registry-snapshot', 'recipe-behavior', 'extraction-probability',
           'machine-nbt', 'machine-world-tick', 'machine-performance')


def timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
    if parsed.tzinfo is None:
        raise ValueError('Timestamp must include timezone')
    return parsed


def validate(directory: Path, junit: Path, not_before: datetime) -> dict:
    errors, artifacts, documents = [], [], {}
    def record(path):
        if datetime.fromtimestamp(path.stat().st_mtime, timezone.utc) < not_before:
            errors.append('Stale artifact: ' + str(path))
        artifacts.append({'path': str(path.resolve()), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    try:
        record(junit)
        tree = ET.parse(junit)
        cases = tree.findall('.//testcase')
        if not cases:
            errors.append('JUnit contains no tests')
        for method in REQUIRED:
            matches = [case for case in cases if method.lower() in case.get('name', '').lower()]
            if len(matches) != 1:
                errors.append('Required test absent or duplicated in JUnit: ' + method)
            elif any(matches[0].find(tag) is not None for tag in ('failure', 'error', 'skipped')):
                errors.append('Required test failed/errored/skipped: ' + method)
        for case in cases:
            if case.find('failure') is not None or case.find('error') is not None:
                errors.append('JUnit failure: ' + case.get('name', '<unnamed>'))
        for name in REPORTS:
            path = directory / (name + '.json')
            record(path)
            document = json.loads(path.read_text(encoding='utf-8'))
            if document.get('schema_version') != 1 or document.get('minecraft') != '1.20.1':
                errors.append('Wrong report schema/target: ' + name)
            if timestamp(document['generated_at']) < not_before:
                errors.append('Stale embedded timestamp: ' + name)
            documents[name] = document
        run_ids = {d.get('run_id') for d in documents.values()}
        if len(run_ids) != 1 or not next(iter(run_ids)):
            errors.append('Reports belong to different or unknown server runs')
        registry = documents['registry-snapshot']
        if not registry.get('registry_expectations_match') or registry.get('calculator_block_count') != 91 or registry.get('calculator_item_count') != 239:
            errors.append('Live registry count/set assertions not satisfied')
        rows = registry.get('registries', [])
        for kind, expected in [('block', 91), ('item', 239)]:
            ids = [r['id'] for r in rows if r['kind'] == kind and r['id'].startswith('calculator:')]
            if len(ids) != expected or len(set(ids)) != expected:
                errors.append('Invalid registry snapshot rows: ' + kind)
        if documents['recipe-behavior'].get('passed_cases') != 65:
            errors.append('Missing deterministic recipe coverage')
        if documents['extraction-probability'].get('draws_per_recipe') != 12600:
            errors.append('Missing exact-sequence probability coverage')
        performance = documents['machine-performance']
        expected_scenarios = [(0, 'empty_test_structure'), (64, 'idle_stone_separator'), (64, 'active_stone_separator'),
                              (256, 'idle_stone_separator'), (256, 'active_stone_separator')]
        scenarios = performance.get('scenarios', [])
        if [(s.get('machine_count'), s.get('workload')) for s in scenarios] != expected_scenarios:
            errors.append('Performance scenarios incomplete or reordered')
        for scenario in scenarios:
            samples = scenario.get('server_tick_ns', [])
            if scenario.get('warmup_ticks') != 100 or scenario.get('sample_ticks') != 300 or len(samples) != 300:
                errors.append('Missing performance warmup/samples')
            elif any(type(s) is not int or s <= 0 for s in samples):
                errors.append('Invalid actual nanosecond samples')
            elif abs(scenario.get('mean_ms', -1) - sum(samples) / len(samples) / 1e6) > 1e-8:
                errors.append('Performance summary differs from raw samples')
            if scenario.get('workload') == 'active_stone_separator' and scenario.get('completed_operations_in_sample', 0) < scenario['machine_count']:
                errors.append('No actual work completed in active scenario')
    except (OSError, ValueError, KeyError, TypeError, ET.ParseError) as error:
        errors.append(str(error))
    return {'schema_version': 1, 'status': 'PASS' if not errors else 'FAIL', 'parity_proven': False,
            'scope': 'Executed named Calculator smoke tests and actual server tick samples only',
            'not_before': not_before.isoformat(), 'issues': errors, 'artifacts': artifacts,
            'remaining': ['Complete upstream behavior review required by audit_port.py',
                          'Disk world save/server restart and old-world migration',
                          'Client GUI/render/resource behavior and multiplayer security',
                          'All recipe families, tags, conditions and NBT edge cases',
                          'All machines, upgrades, automation sides, world interactions',
                          'Dedicated isolated repeat performance runs; no FPS evidence']}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reports', type=Path, default=PROJECT / 'build/reports/calculator-gametest')
    parser.add_argument('--junit', type=Path, required=True)
    parser.add_argument('--not-before', type=timestamp, required=True)
    parser.add_argument('--output', type=Path, default=PROJECT / 'build/reports/runtime-validation.json')
    args = parser.parse_args()
    report = validate(args.reports, args.junit, args.not_before)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
    return 0 if report['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
