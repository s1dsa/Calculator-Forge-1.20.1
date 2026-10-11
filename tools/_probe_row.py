import sys
sys.path.insert(0, r'F:\ai\calculator-port\tools')
from _probe_slots import read_png

path = r'F:\ai\calculator-port\calculator\src\main\resources\assets\calculator\textures\gui\guipowercube.png'
w, h, img = read_png(path)
st = w * 4

def g(x, y):
    o = y*st + x*4
    return img[o]

print('row y=68 (menu y=34), tex x=148..208 step2:')
print(' '.join('%3d' % g(x, 68) for x in range(148, 209, 2)))
print('row y=72 (menu y=36):')
print(' '.join('%3d' % g(x, 72) for x in range(148, 209, 2)))
print('col x=176 (menu x=88), tex y=56..112 step2:')
print(' '.join('%3d' % g(176, y) for y in range(56, 113, 2)))
print('col x=160 (menu x=80), tex y=60..104 step2:')
print(' '.join('%3d' % g(160, y) for y in range(60, 105, 2)))
