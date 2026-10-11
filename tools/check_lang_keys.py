import json, sys, io, os, re
base = r'F:\ai\calculator-port\calculator\src\main\resources\assets\calculator\lang'
paths = {'zh_cn': os.path.join(base,'zh_cn.json'), 'en_us': os.path.join(base,'en_us.json')}
keys = ['docking.noCalculator','buttons.pause','buttons.circuits','buttons.resume',
        'locator.active','locator.multiblock','locator.owner','locator.none','locator.true','locator.false','locator.unknown',
        'circuit.stability','block.calculator.reinforcedchest','block.calculator.crankhandle',
        'block.calculator.dockingstation','block.calculator.calculatorlocator','block.calculator.handcrankedgenerator']
for name,p in paths.items():
    if not os.path.exists(p):
        print('MISSING FILE', p); continue
    data = json.load(open(p, encoding='utf-8'))
    print('==', name, 'total keys', len(data))
    for k in keys:
        print('   ', k, '->', repr(data.get(k)))
