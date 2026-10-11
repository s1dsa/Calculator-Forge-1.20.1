"""Targeted round4 weather rail layout assertions; not a rendered-client verdict."""
from pathlib import Path
import json
import re

root = Path(__file__).resolve().parents[1]
screen = (root / 'calculator/src/main/java/sonar/calculator/mod/client/screen/MachineScreen.java').read_text(encoding='utf-8')
menu = (root / 'calculator/src/main/java/sonar/calculator/mod/machine/MachineMenu.java').read_text(encoding='utf-8')
branch = screen.split('} else if (id.equals("weathercontroller")) {', 1)[1].split('} else if', 1)[0]
controls = re.findall(r'control\(Component.translatable\("([^"]+)"\), (\d+), (-?\d+), (-?\d+), (\d+)\);', branch)
assert len(controls) == 4, controls
expected = [('weather.time', 100), ('weather.rain', 101), ('weather.thunder', 102), ('gui.calculator.toggle', 11)]
rects = []
for (key, action, x, y, w), (expected_key, expected_action) in zip(controls, expected):
    assert (key, int(action)) == (expected_key, expected_action)
    x, y, w = int(x), int(y), int(w)
    rects.append((x, y, x+w, y+18))
    assert 0 <= x < x+w <= 176 and -20 <= y < y+18 <= -2

def overlaps(a, b):
    return a[0] < b[2] and a[2] > b[0] and a[1] < b[3] and a[3] > b[1]

obstacles = {'title': (7, 6, 169, 15), 'energy': (49, 63, 127, 73),
             'battery': (27, 59, 45, 77), 'inventory': (7, 83, 169, 161)}
for i, rect in enumerate(rects):
    assert not any(overlaps(rect, other) for other in rects[:i])
    assert not any(overlaps(rect, obstacle) for obstacle in obstacles.values())
assert 'case "weathercontroller" -> new int[][]{{28,60}};' in menu
assert 'topPos = Math.max(20, (height - imageHeight - 20) / 2 + 20);' in screen
assert 'leftPos = Math.max(0, Math.min(leftPos, Math.max(0, width - imageWidth)));' in screen
sizes = [(320, 240), (427, 240), (480, 270), (640, 360), (854, 480), (1920, 1080)]
for width, height in sizes:
    left = max(0, min((width-176)//2, max(0, width-176)))
    top = max(20, (height-166-20)//2+20)
    assert top+166 <= height
    for x1,y1,x2,y2 in rects:
        assert 0 <= left+x1 < left+x2 <= width and 0 <= top+y1 < top+y2 <= height
lang = json.loads((root / 'calculator/src/main/resources/assets/calculator/lang/zh_cn.json').read_text(encoding='utf-8'))
for key, _ in expected:
    assert lang[key]
before = (root / 'audit/reinforced-chest-round4/before/calculator/src/main/java/sonar/calculator/mod/client/screen/MachineScreen.java').read_text(encoding='utf-8')
def without_weather(source):
    source = re.sub(r'        if \(id.equals\("weathercontroller"\)\) \{.*?\n        \}\n', '', source, flags=re.S)
    return re.sub(r'} else if \(id.equals\("weathercontroller"\)\) \{.*?} else if', '} else if', source, flags=re.S)
assert without_weather(before) == without_weather(screen), 'Non-weather controls changed'
result = {'status':'PASS', 'non_weather_controls_unchanged':True, 'machine':'weathercontroller', 'controls':controls,
          'relative_rectangles':rects, 'obstacles':obstacles, 'screen_sizes':sizes,
          'rendered_client_verified':False}
output = root / 'audit/reinforced-chest-round4/weather-layout.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False))
