import re, os
BASE = r'F:\ai\upstream\Calculator\src\main\java\sonar\calculator\mod\client\gui'
files = ['generators/GuiExtractor.java','generators/GuiConductorMast.java','machines/GuiAtomicMultiplier.java',
         'generators/GuiCalculatorPlug.java','misc/GuiReinforcedChest.java','machines/GuiAnalysingChamber.java',
         'misc/GuiMagneticFlux.java','misc/GuiCO2Generator.java','misc/GuiStoneAssimilator.java',
         'calculators/GuiAtomicCalculator.java','calculators/GuiDynamicCalculator.java',
         'misc/GuiFabricationChamber.java','misc/GuiWeatherController.java','misc/GuiModuleWorkstation.java',
         'machines/GuiStorageChamber.java','generators/GuiCrankedGenerator.java']
pat = re.compile(r'textCentre|text\(|drawTexturedModalRect|xSize|ySize|Button|guiLeft \+|guiTop \+|storage|formatStorage|getName\(\)|width|height')
for f in files:
    p = os.path.join(BASE, f.replace('/', os.sep))
    print('==== ' + f)
    try:
        lines = open(p, encoding='utf8').read().splitlines()
    except FileNotFoundError:
        print('   MISSING'); continue
    for i, l in enumerate(lines):
        if pat.search(l):
            print('  %4d %s' % (i+1, l.strip()))
