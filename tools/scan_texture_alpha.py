import os, sys
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pngmap import load_png
root = r'F:\ai\calculator-port\calculator\src\main\resources\assets\calculator\textures'
hits = []
for d, _, fs in os.walk(root):
    if 'gui' in d or 'model' in d:
        continue
    for f in sorted(fs):
        if not f.endswith('.png'):
            continue
        p = os.path.join(d, f)
        try:
            w, h, ch, px = load_png(p)
        except Exception as e:
            print('ERR', f, e); continue
        if ch < 4:
            continue
        total = w * h
        clear = 0
        for i in range(3, len(px), 4):
            if px[i] < 250:
                clear += 1
        if clear > total * 0.02:
            hits.append((os.path.relpath(p, root), round(100.0 * clear / total, 1)))
print('textures with >2%% non-opaque pixels: %d' % len(hits))
for name, pct in hits:
    print('  %-55s %5s%%' % (name, pct))
