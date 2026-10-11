import sys, os
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pngmap import load_png
G = r'F:\ai\calculator-port\calculator\src\main\resources\assets\calculator\textures\gui'

def coarse(name, step=8):
    w, h, ch, px = load_png(os.path.join(G, name))
    print('== %s %dx%d ch=%d' % (name, w, h, ch))
    print('    ' + ''.join(str((x//10) % 10) for x in range(0, w, step)))
    for y in range(0, h, step):
        row = ''
        for x in range(0, w, step):
            o = (y*w+x)*ch
            a = px[o+3] if ch == 4 else 255
            r, g, b = px[o], px[o+1], px[o+2]
            if a < 16:
                row += ' '
            else:
                lum = (r*30+g*59+b*11)//100
                row += '.' if lum > 200 else ('+' if lum > 150 else ('o' if lum > 90 else '#'))
        print('%3d %s' % (y, row))

coarse('guiadvancedcube.png', 8)
print()
coarse('guicalculatorlocator.png', 8)
