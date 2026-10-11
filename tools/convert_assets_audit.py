"""Resource closure, archive integrity, metadata coverage and ModelPart migration audit (no Gradle)."""
from pathlib import Path
import sys,json,re,hashlib,collections
sys.path.insert(0,str(Path(__file__).resolve().parent))
from convert_assets import SRC,DST,ROOT,REG

def run():
    errors=[]; external=set()
    originals=list(p for p in SRC.rglob('*') if p.is_file())
    for p in originals:
        archive=DST/'legacy_1_12'/p.relative_to(SRC)
        if not archive.exists() or archive.read_bytes()!=p.read_bytes(): errors.append('Archive mismatch '+str(p))
    for id in REG['block_ids']:
        if not (DST/'blockstates'/f'{id}.json').exists(): errors.append('Missing state '+id)
    for id in REG['block_ids']+REG['item_ids']:
        if not (DST/'models/item'/f'{id}.json').exists(): errors.append('Missing item '+id)
    models={p.relative_to(DST/'models').with_suffix('').as_posix():json.loads(p.read_text(encoding='utf8')) for p in (DST/'models').rglob('*.json')}
    def exists(value,kind,owner):
        if value.startswith('#'): return
        if value!=value.lower(): errors.append(f'Uppercase {owner}: {value}')
        ns,path=value.split(':',1) if ':' in value else ('minecraft',value)
        if ns=='minecraft': return
        if ns!='calculator': external.add(value); return
        target=DST/(('models/'+path+'.json') if kind=='model' else ('textures/'+path+'.png'))
        if not target.exists(): errors.append(f'Missing {kind} {value} in {owner}')
    for name,d in models.items():
        if 'parent' in d: exists(d['parent'],'model',name)
        for v in d.get('textures',{}).values(): exists(v,'texture',name)
        for child in d.get('children',{}).values():
            if 'parent' in child: exists(child['parent'],'model',name)
    def walk(d,owner):
        if isinstance(d,dict):
            if 'forge_marker' in d: errors.append('Legacy marker '+owner)
            if 'model' in d: exists(d['model'],'model',owner)
            for v in d.values(): walk(v,owner)
        elif isinstance(d,list):
            for v in d: walk(v,owner)
    for p in (DST/'blockstates').glob('*.json'): walk(json.loads(p.read_text()),p.name)
    for p in (DST/'textures').rglob('*.png.mcmeta'):
        try: json.loads(p.read_text(encoding='utf-8-sig'))
        except ValueError: errors.append('Invalid animation metadata '+str(p))
    source_models=ROOT.parent/'upstream/Calculator/src/main/java/sonar/calculator/mod/client/models'
    cube_count=0
    for p in source_models.glob('*.java'):
        source_count=len(re.findall(r'\.addBox\(',p.read_text()))
        target=ROOT/'calculator/src/main/java/sonar/calculator/mod/client/model'/p.name
        modern_count=len(re.findall(r'\.addBox\(',target.read_text())) if target.exists() else 0
        if modern_count!=source_count: errors.append(f'Geometry count {p.name}: {source_count} -> {modern_count}')
        cube_count+=modern_count
    report={'archive_files':len(originals),'archive_counts':dict(collections.Counter(p.suffix for p in originals)),'blockstates':len(list((DST/'blockstates').glob('*.json'))),'models':len(models),'model_cubes':cube_count,'external_sonar_references':sorted(external),'errors':sorted(set(errors))}
    print(json.dumps(report,ensure_ascii=False,indent=2))
    return errors
if __name__=='__main__': sys.exit(bool(run()))
