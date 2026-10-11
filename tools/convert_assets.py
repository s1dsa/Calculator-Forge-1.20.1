"""Deterministic 1.12 asset conversion. Upstream is read-only; originals remain byte exact.
Run with the repository's Python, without Gradle. No data/ or common Java is written.
"""
from pathlib import Path
import json, re, shutil, itertools, hashlib, collections
ROOT=Path(__file__).resolve().parents[1]
UP=ROOT.parent/'upstream/Calculator/src/main'
SRC=UP/'resources/assets/calculator'
DST=ROOT/'calculator/src/main/resources/assets/calculator'
REG=json.loads((ROOT/'notes/registry-map.json').read_text(encoding='utf8'))
WARN=[]
BER={'conductormast','transmitter','scarecrow','weatherstation','dockingstation','crankhandle','calculatorscreenblock'}
CHAMBERS={'extractionchamber','precisionchamber','reassemblychamber','restorationchamber','processingchamber'}

def load(p):
    s=p.read_text(encoding='utf-8-sig')
    s=re.sub(r'//[^\n]*|/\*.*?\*/', '', s, flags=re.S)
    s=re.sub(r'([{,]\s*)(\d+)\s*:',r'\1"\2":',s)
    return json.loads(re.sub(r',\s*([}\]])',r'\1',s))

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def merge(a,b):
    c=dict(a)
    for k,v in b.items(): c[k]=merge(c.get(k,{}),v) if isinstance(v,dict) else v
    return c

def resource(s):
    s=s.lower()
    # Unqualified legacy texture locations also belong to the vanilla namespace.
    if s.startswith(('blocks/','items/')): s='minecraft:'+s
    aliases={'minecraft:blocks/planks_oak':'minecraft:block/oak_planks',
             'minecraft:block/planks_oak':'minecraft:block/oak_planks',
             'minecraft:blocks/pumpkin_face_off':'minecraft:block/carved_pumpkin',
             'minecraft:block/pumpkin_face_off':'minecraft:block/carved_pumpkin'}
    s=aliases.get(s,s)
    if s.startswith('minecraft:blocks/'): s=s.replace('minecraft:blocks/','minecraft:block/',1)
    if s.startswith('minecraft:items/'): s=s.replace('minecraft:items/','minecraft:item/',1)
    return s.replace('redstone pick animate','redstone_pick_animate')

def parent(s):
    s=resource(s)
    if s in ('builtin/generated','minecraft:builtin/generated'): return 'minecraft:item/generated'
    if ':' not in s: s='minecraft:'+s
    ns,p=s.split(':',1)
    if not p.startswith(('block/','item/','builtin/')): p='block/'+p
    return ns+':'+p

def modern(d):
    if isinstance(d,list): return [modern(v) for v in d]
    if not isinstance(d,dict): return d
    out={k:modern(v) for k,v in d.items()}
    if 'parent' in out: out['parent']=parent(out['parent'])
    if 'textures' in out: out['textures']={k:resource(v) for k,v in out['textures'].items()}
    if 'display' in out:
        for old in ('firstperson','thirdperson'):
            if old in out['display']:
                val=out['display'].pop(old)
                out['display'][old+'_righthand']=val
                out['display'][old+'_lefthand']=val
    return out

MODEL_CACHE={}
def bake(d,stem):
    """Forge per-variant textures become dedicated model JSON, never lost on the state."""
    model={'parent':parent(d.get('model','cube_all'))}
    if 'textures' in d: model['textures']={k:resource(v) for k,v in d['textures'].items()}
    if model['parent']=='minecraft:item/generated':
        model.setdefault('textures',{}).setdefault('layer0',model.get('textures',{}).get('particle','calculator:items/calculator'))
    if d.get('transform')=='forge:default-block':
        model['display']={'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[0.625]*3},'ground':{'translation':[0,3,0],'scale':[0.25]*3},'fixed':{'scale':[0.5]*3},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[0.375]*3},'firstperson_righthand':{'rotation':[0,45,0],'scale':[0.4]*3}}
    key=json.dumps(model,sort_keys=True)
    if key not in MODEL_CACHE:
        name=stem+'_'+hashlib.sha256(key.encode()).hexdigest()[:12]
        MODEL_CACHE[key]='calculator:block/converted/'+name
        write(DST/'models/block/converted'/f'{name}.json',model)
    result={'model':MODEL_CACHE[key]}
    for k in ('x','y','uvlock','weight'):
        if k in d: result[k]=d[k]
    return result

def inventory(d,name):
    # Forge submodels are real simultaneous geometry, not vanilla weighted alternatives.
    ref=bake(d,name+'_inventory')
    if d.get('submodel'):
        children={'base':{'parent':ref['model']}}
        for n,sub in d['submodel'].items():
            sm=merge({k:v for k,v in d.items() if k not in ('submodel','x','y')},sub)
            children[n]={'parent':bake(sm,name+'_inventory_'+n)['model']}
            if sub.get('x') or sub.get('y'):
                # Inventory pipe only: bake the 180-degree opposite connection in elements below.
                children[n]['transform']={'rotation':[sub.get('x',0),sub.get('y',0),0]}
        write(DST/'models/item'/f'{name}.json',{'loader':'forge:composite','children':children,'item_render_order':list(children)})
    else: write(DST/'models/item'/f'{name}.json',{'parent':ref['model']})

def state(p):
    name=p.stem.lower(); old=load(p); defaults=old.get('defaults',{}); variants=old.get('variants',{})
    if name=='circuitboard':
        for base in ('circuitboard','circuitdamaged','circuitdirty'):
            for i in range(14):
                n=base+(f'_{i}' if i else '')
                inventory(merge(defaults,variants['variant'][f'meta{i}']),n)
        return
    if name=='material':
        for i,(key,v) in enumerate(variants['variant'].items()):
            n='material'+(f'_{i}' if i else '')
            d=merge(defaults,v); inventory(d,n)
            write(DST/'blockstates'/f'{n}.json',{'variants':{'':bake(d,n)}})
        return
    inv=variants.get('inventory',[{}]); inv=inv[0] if isinstance(inv,list) else inv
    inventory(merge(defaults,inv),name)
    if name in BER or name.endswith('mastblock') or name in ('transmitterblock','weatherstationblock','scarecrowblock'):
        # BER-only blocks must not also draw the legacy builtin/generated particle icon.
        particle=resource(defaults.get('textures',{}).get('particle',defaults.get('textures',{}).get('layer0','calculator:blocks/amethyst_block')))
        write(DST/'models/block'/f'{name}_ber.json',{'textures':{'particle':particle},'elements':[]})
        write(DST/'blockstates'/f'{name}.json',{'variants':{'':{'model':f'calculator:block/{name}_ber'}}})
        return
    axes={k:v for k,v in variants.items() if k not in ('inventory','normal') and '=' not in k}
    if name.endswith('log'): axes.get('axis',{}).pop('none',None)
    # Machine properties are owned by MachineBlock; absent legacy side enums cannot appear in JSON.
    if is_machine(name):
        for key in list(axes):
            if key in ('north','east','south','west','up','down'):
                WARN.append(f'{name}: legacy {key} I/O texture variants require a synchronized side property')
                del axes[key]
        if 'active' in axes: axes['lit']=axes.pop('active')
        if 'dir' in axes:
            directions=axes.pop('dir')
            axes['facing']={k:v for k,v in directions.items() if k in ('north','south','east','west')}
            WARN.append(f'{name}: floor/ceiling attachment unavailable in horizontal MachineBlock')
    if any('submodel' in v for axis in axes.values() for v in axis.values()):
        parts=[{'apply':bake(defaults,name)}]
        for axis,values in axes.items():
            for value,d in values.items():
                for subname,sub in d.get('submodel',{}).items():
                    subd=merge({k:v for k,v in defaults.items() if k not in ('x','y')},sub)
                    parts.append({'when':{axis:value},'apply':bake(subd,name+'_'+subname)})
        write(DST/'blockstates'/f'{name}.json',{'multipart':parts}); return
    result={}
    if axes:
        for combo in itertools.product(*(v.items() for v in axes.values())):
            d=defaults; props={}
            for axis,(value,extra) in zip(axes,combo): props[axis]=value; d=merge(d,extra)
            # Explicit full combinations override the expanded Forge property defaults.
            for key,extra in variants.items():
                if '=' in key and dict(s.split('=') for s in key.split(','))==props: d=merge(d,extra)
            result[','.join(f'{k}={v}' for k,v in props.items())]=bake(d,name)
    else:
        for key,extra in variants.items():
            if '=' in key: result[key]=bake(merge(defaults,extra[0] if isinstance(extra,list) else extra),name)
        if not result:
            v=variants.get('normal',{}); v=v[0] if isinstance(v,list) else v
            result['']=bake(merge(defaults,v),name)
    write(DST/'blockstates'/f'{name}.json',{'variants':result})
    repair_machine_state(DST/'blockstates'/f'{name}.json')


def is_machine(name):
    # WorldContent handles natural blocks; all remaining registrations use MachineBlock.
    return (name in REG['block_ids']
            and not any(name.endswith(x) for x in ('fence','piping','leaves','log','sapling','stairs','planks'))
            and name not in ('flawlessglass','purifiedobsidian')
            and not name.startswith(('crop','material')))


def repair_machine_state(path):
    if not is_machine(path.stem): return
    data=load(path)
    if 'variants' not in data: return
    variants={}
    for key,value in data['variants'].items():
        props=dict(part.split('=',1) for part in key.split(',') if part)
        if 'active' in props: props['lit']=props.pop('active')
        if 'dir' in props: props['facing']=props.pop('dir')
        if props.get('facing') in ('up','down'): continue
        unsupported=set(props)-{'facing','lit'}
        if unsupported: raise ValueError(f'{path}: unsupported MachineBlock properties {unsupported}')
        variants[','.join(f'{k}={v}' for k,v in props.items())]=value
    if variants!=data['variants']:
        data['variants']=variants
        write(path,data)


def repair_assets():
    """Repair existing assets without regenerating other agents' world mappings."""
    for path in (DST/'blockstates').glob('*.json'): repair_machine_state(path)
    for path in (DST/'models').rglob('*.json'):
        data=load(path); fixed=modern(data)
        if path==DST/'models/block/scarecrow.json':
            # Full original height 41 fits vanilla's 48-unit coordinate span.
            # Move all nine boxes by -12.5 to center at y=8; preserve sizes and UVs.
            if max(e['to'][1] for e in fixed['elements'])>32:
                for element in fixed['elements']:
                    for key in ('from','to'): element[key][1]-=12.5
                    if 'rotation' in element: element['rotation']['origin'][1]-=12.5
        if fixed!=data: write(path,fixed)
    # World rendering stays with ModelScarecrow BER; inventory uses the full static shape.
    display={
        'gui':{'rotation':[0,0,0],'scale':[0.35]*3},
        'ground':{'translation':[0,3.075,0],'scale':[0.15]*3},
        'fixed':{'scale':[0.35]*3},
        'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[0.25]*3},
        'thirdperson_lefthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[0.25]*3},
        'firstperson_righthand':{'rotation':[0,45,0],'scale':[0.25]*3},
        'firstperson_lefthand':{'rotation':[0,225,0],'scale':[0.25]*3}}
    for name in ('scarecrow','scarecrowblock'):
        write(DST/'models/item'/f'{name}.json',{'parent':'calculator:block/scarecrow','display':display})
    illegal=DST/'textures/items/tools/redstone pick animate.png'
    if illegal.exists():
        # Legal rename preserves image bytes and never touches the upstream source.
        illegal.replace(illegal.with_name('redstone_pick_animate.png'))


def language():
    for p in (SRC/'lang').glob('*.lang'):
        data={}
        for line in p.read_text(encoding='utf-8-sig').splitlines():
            if line.strip().startswith(('#','//')) or '=' not in line: continue
            k,v=line.split('=',1); data[k.strip()]=v.strip()
        lower={k.lower():v for k,v in data.items()}
        for kind,prefix in [('blocks','block'),('items','item')]:
            for _,id,legacy in REG[kind]:
                key=('tile.' if kind=='blocks' else 'item.')+legacy+'.name'
                if key.lower() in lower: data[f'{prefix}.calculator.{id}']=lower[key.lower()]
        for i,key in enumerate(('amethyst','tanzanite','enriched_gold','reinforced_iron','weakened_diamond','flawless_diamond','fire_diamond','electric_diamond','end_diamond','redstone_ingot')):
            for legacy in (f'tile.Material.{i}.name',f'tile.Material.{key}.name',f'tile.Material{i}.name',f'tile.{key}.name'):
                if legacy.lower() in lower: data['block.calculator.material'+(f'_{i}' if i else '')]=lower[legacy.lower()]; break
        for base in ('circuitboard','circuitdamaged','circuitdirty'):
            for i in range(14):
                for key in (f'item.{base}.{i}.name',f'item.{base}{i}.name',f'item.{base}.name'):
                    if key.lower() in lower:
                        data['item.calculator.'+base+(f'_{i}' if i else '')]=lower[key.lower()]; break
        write(DST/'lang'/(p.stem.lower()+'.json'),data)

def run():
    for p in SRC.rglob('*'):
        if not p.is_file(): continue
        rel=p.relative_to(SRC)
        if p.suffix in ('.png','.mcmeta'):
            dest=DST/str(rel).lower().replace('redstone pick animate','redstone_pick_animate'); dest.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(p,dest)
        elif 'models' in rel.parts: write(DST/str(rel).lower(),modern(load(p)))
    for p in (SRC/'blockstates').glob('*.json'): state(p)
    # The wall display had no blockstate; the portable item provides its inventory appearance.
    inventory({'model':'builtin/generated','textures':{'layer0':'calculator:items/calculator_screen'}},'calculatorscreenblock')
    write(DST/'models/block/calculatorscreenblock_ber.json',{'textures':{'particle':'calculator:items/calculator_screen'},'elements':[]})
    write(DST/'blockstates/calculatorscreenblock.json',{'variants':{'':{'model':'calculator:block/calculatorscreenblock_ber'}}})
    language()
    repair_assets()
    print(json.dumps({'original_counts':dict(collections.Counter(p.suffix for p in SRC.rglob('*') if p.is_file())),'converted_models':len(MODEL_CACHE),'pending_side_properties':len(WARN)},indent=2))

if __name__=='__main__': run()
