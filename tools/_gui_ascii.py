import sys, os
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pngmap import ascii_map
G = r'F:\ai\calculator-port\calculator\src\main\resources\assets\calculator\textures\gui'
ascii_map(os.path.join(G, 'guiadvancedcube.png'), 0, 0, 176, 100, 2)
print()
ascii_map(os.path.join(G, 'stoneseperator.png'), 0, 0, 176, 100, 2)
