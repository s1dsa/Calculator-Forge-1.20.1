import io, os, sys
sys.stdout.reconfigure(encoding='utf-8')
# dump whole files given via PROBE_FILES (comma separated) relative to PROBE_BASE
base = os.environ['PROBE_BASE']
for f in os.environ.get('PROBE_FILES', '').split(','):
    if not f:
        continue
    print('##### ' + f)
    p = os.path.join(base, f.replace('/', os.sep))
    try:
        print(io.open(p, encoding='utf-8').read())
    except FileNotFoundError:
        print('   MISSING')
