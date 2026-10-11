import json, os, sys
sys.stdout.reconfigure(encoding='utf-8')
LANGS = r'F:\ai\calculator-port\calculator\src\main\resources\assets\calculator\lang'
for f in ['zh_cn', 'en_us', 'ru_ru', 'uk_ua']:
    d = json.load(open(os.path.join(LANGS, f + '.json'), encoding='utf8'))
    print('==', f, 'count', len(d))
    for i in range(10):
        k = 'tile.Material.%d.name' % i
        print('   ', k, '=', d.get(k, '<MISSING>'))
    for k in ('tile.CalculatorScreen.name', 'item.CalculatorScreen.name', 'info.CalculatorScreen.name',
              'block.calculator.calculatorscreenblock', 'block.calculator.material', 'block.calculator.material_3'):
        print('   ', k, '=', d.get(k, '<MISSING>'))
