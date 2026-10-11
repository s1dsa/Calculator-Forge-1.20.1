import sys, os
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pngmap import load_png
G = r'F:\ai\calculator-port\calculator\src\main\resources\assets\calculator\textures\gui'

def bounds(name):
    w, h, ch, px = load_png(os.path.join(G, name))
    maxx = maxy = 0
    # rows/cols with any opaque pixel
    cols = [0]*w
    for y in range(h):
        base = y*w*ch
        for x in range(w):
            if px[base+x*ch+3] > 16:
                cols[x] += 1
    # find last x whose column has > 40 opaque pixels (i.e., a tall solid feature)
    solid = [x for x in range(w) if cols[x] > 40]
    print('%s: opaque-col range with >40 rows: %d..%d' % (name, min(solid), max(solid)))
    # scan for the panel border: look at row y=20 and y=120 opaqueness run
    for y in (12, 20, 120, 300, 330, 340):
        run = 0; last = -1
        for x in range(w):
            if px[(y*w+x)*ch+3] > 16: last = x
        print('   row %3d last-opaque-x=%d' % (y, last))

for n in ['guiadvancedcube.png', 'stoneseperator.png', 'guicalculatorlocator.png']:
    bounds(n)
