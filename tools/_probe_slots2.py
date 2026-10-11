import io, os, sys
sys.stdout.reconfigure(encoding='utf-8')
base = r'F:\ai\upstream\Calculator\src\main\java\sonar\calculator\mod\common\containers'
raw = os.environ.get('PROBE_BASE', base)
files = os.environ.get('PROBE_FILES', '').split(',')
for f in files:
    if not f:
        continue
    print('##### ' + f)
    p = os.path.join(raw, f)
    try:
        lines = io.open(p, encoding='utf-8').read().splitlines()
    except FileNotFoundError:
        print('   MISSING'); continue
    for i, l in enumerate(lines, 1):
        s = l.strip()
        if 'addSlotToContainer' in s or 'new Slot' in s or 'xDisplayPosition' in s or 'yDisplayPosition' in s:
            print('%5d %s' % (i, s))
