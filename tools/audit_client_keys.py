import io, json, os, re, sys
sys.stdout.reconfigure(encoding='utf-8')
BASE = r'F:\ai\calculator-port\calculator\src\main\resources\assets\calculator\lang'
SRC = r'F:\ai\calculator-port\calculator\src\main\java'

EXTRA = {
    'gui.calculator.empty': {'zh_cn': '-空-', 'en_us': '-empty-', 'ru_ru': '-empty-', 'uk_ua': '-empty-'},
}

# 1) ensure the extra keys exist
for name, values in EXTRA.items():
    for lang, text in values.items():
        p = os.path.join(BASE, lang + '.json')
        data = json.loads(io.open(p, encoding='utf-8').read())
        if name not in data:
            data[name] = text
            io.open(p, 'w', encoding='utf-8', newline='\n').write(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
            print('added %s -> %s' % (name, lang))

# 2) collect literal translatable keys used in source
literal = set()
dynamic = set()
pat_lit = re.compile(r'Component\.translatable\(\s*"([^"]+)"')
pat_dyn = re.compile(r'Component\.translatable\(\s*([^"\s][^,\)]*)')
for d, _, fs in os.walk(SRC):
    for f in fs:
        if not f.endswith('.java'):
            continue
        t = io.open(os.path.join(d, f), encoding='utf-8', errors='ignore').read()
        for m in pat_lit.finditer(t):
            literal.add(m.group(1))
        for m in pat_dyn.finditer(t):
            dynamic.add(m.group(1).strip())

# 3) compare against every lang file
literal = {k for k in literal if not k.endswith('.')}
print('\nliteral translatable keys in source: %d' % len(literal))
for lang in ('zh_cn', 'en_us', 'ru_ru', 'uk_ua'):
    data = json.loads(io.open(os.path.join(BASE, lang + '.json'), encoding='utf-8').read())
    missing = sorted(k for k in literal if k not in data)
    print('%-6s missing=%d %s' % (lang, len(missing), missing))
# explicitly cover the string-concatenated key families used by the client code
data = json.loads(io.open(os.path.join(BASE, 'zh_cn.json'), encoding='utf-8').read())
concatenated = ['gui.calculator.side.' + s for s in ('down', 'up', 'north', 'south', 'west', 'east', 'input', 'output', 'disabled')]
concatenated += ['info.' + s for s in ('all', 'calculators', 'modules', 'circuits', 'nutrition', 'generators', 'machines', 'tools', 'items', 'blocks')]
concatenated += ['block.calculator.' + s for s in ('material', 'material_1', 'material_2', 'material_3', 'material_4', 'material_5', 'material_6', 'material_7', 'material_8', 'material_9', 'calculatorscreenblock')]
conc = sorted(k for k in concatenated if k not in data)
print('\nconcatenated key families missing in zh_cn: %d %s' % (len(conc), conc))
print('\ndynamic key expressions (need manual coverage):')
for d in sorted(dynamic):
    print('  ', d)
