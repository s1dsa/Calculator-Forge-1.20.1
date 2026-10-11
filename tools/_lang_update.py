import io, json, os, sys
sys.stdout.reconfigure(encoding='utf-8')
BASE = r'F:\ai\calculator-port\calculator\src\main\resources\assets\calculator\lang'

BLOCK_KEYS_ZH = {
    'block.calculator.calculatorscreenblock': '计算器显示屏',
    'block.calculator.material': '紫水晶块',
    'block.calculator.material_1': '蓝水晶块',
    'block.calculator.material_2': '富集金块',
    'block.calculator.material_3': '强化铁块',
    'block.calculator.material_4': '弱化钻石块',
    'block.calculator.material_5': '无暇钻石块',
    'block.calculator.material_6': '火钻块',
    'block.calculator.material_7': '充能钻石块',
    'block.calculator.material_8': '末地钻石块',
    'block.calculator.material_9': '强化红石块',
}
BLOCK_KEYS_EN = {
    'block.calculator.calculatorscreenblock': 'Calculator Screen',
    'block.calculator.material': 'Amethyst Block',
    'block.calculator.material_1': 'Tanzanite Block',
    'block.calculator.material_2': 'Enriched Gold Block',
    'block.calculator.material_3': 'Reinforced Iron Block',
    'block.calculator.material_4': 'Weakened Diamond Block',
    'block.calculator.material_5': 'Flawless Diamond Block',
    'block.calculator.material_6': 'Fire Diamond Block',
    'block.calculator.material_7': 'Electric Diamond Block',
    'block.calculator.material_8': 'End Diamond Block',
    'block.calculator.material_9': 'Redstone Ingot Block',
}

GUI_ZH = {
    'gui.calculator.fabricate': '制造',
    'gui.calculator.whitelist': '白名单',
    'gui.calculator.blacklist': '黑名单',
    'gui.calculator.exact': '精准匹配',
    'gui.calculator.fuzzy': '模糊匹配',
    'gui.calculator.toggle': '开 / 关',
    'gui.calculator.required': '所需材料',
    'gui.calculator.noRecipe': '无配方',
    'gui.calculator.invalid': '无效',
    'gui.calculator.points': '点数',
    'gui.calculator.search': '搜索',
    'gui.calculator.unavailable': '不可用',
    'gui.calculator.machineStats': '机器状态',
    'gui.calculator.usage': '能耗',
    'gui.calculator.speed': '速度',
    'gui.calculator.stored': '储量',
    'gui.calculator.ticks': '刻',
    'gui.calculator.buffer': '缓冲',
    'gui.calculator.side.down': '下',
    'gui.calculator.side.up': '上',
    'gui.calculator.side.north': '北',
    'gui.calculator.side.south': '南',
    'gui.calculator.side.west': '西',
    'gui.calculator.side.east': '东',
    'gui.calculator.side.input': '输入',
    'gui.calculator.side.output': '输出',
    'gui.calculator.side.disabled': '禁用',
    'info.nutrition': '营养',
    'info.tools': '工具',
    'info.EnergyModule.name': '可储存至多 %s RF 或 %s EU',
    'info.StoneAssimilator.name': '收获运算工艺树木-+必须放置在树旁-+用于坦桑石和紫水晶-+不需要能量',
    'info.AlgorithmAssimilator.name': '收获运算工艺树木-+必须放置在树旁-+用于梨木和钻石-+不需要能量',
    'info.AtomicMultiplier.name': '可以将几乎所有物品四倍化--需要七个电路板-以及 %s RF--或 %s EU',
    'info.Sickle.name': '可用于从树上采集浆果/钻石',
    'info.ObsidianKey.name': '见纯净黑曜石',
    'info.EndForgedSword.name': '剑中之神',
    'info.EndForgedHoe.name': '你唯一的渴望',
    'info.Soil.name': '投掷时可使实体失明--可将泥土变为沙砾',
    'info.SmallStone.name': '投掷时可伤害实体--可将泥土变为耕地',
    'info.EndDiamond.name': '如果你想要末影珍珠-只需右键--可制作坚不可摧的工具',
    'info.ControlledFuel.name': '可控制自身的燃烧时间--仅在-二氧化碳发生器中有效',
    'info.CalculatorScreenBlock.name': '放置在机器侧面时-显示其中储存的 RF',
    'info.PurifiedObsidian.name': '坚不可摧/安全方块--只能用黑曜石钥匙移除',
    'info.FlawlessGlass.name': '极其昂贵的玻璃--用于完美温室',
    'info.WeatherStationMachine.name': '拥有控制天气的能力',
}
GUI_EN = {
    'gui.calculator.fabricate': 'Fabricate',
    'gui.calculator.whitelist': 'Whitelist',
    'gui.calculator.blacklist': 'Blacklist',
    'gui.calculator.exact': 'Exact match',
    'gui.calculator.fuzzy': 'Fuzzy match',
    'gui.calculator.toggle': 'On / Off',
    'gui.calculator.required': 'Required',
    'gui.calculator.noRecipe': 'NO RECIPE',
    'gui.calculator.invalid': 'INVALID',
    'gui.calculator.points': 'Points',
    'gui.calculator.search': 'Search',
    'gui.calculator.unavailable': 'Unavailable',
    'gui.calculator.machineStats': 'Machine Stats',
    'gui.calculator.usage': 'Usage',
    'gui.calculator.speed': 'Speed',
    'gui.calculator.stored': 'Stored',
    'gui.calculator.ticks': 'ticks',
    'gui.calculator.buffer': 'Buffer',
    'gui.calculator.side.down': 'Down',
    'gui.calculator.side.up': 'Up',
    'gui.calculator.side.north': 'North',
    'gui.calculator.side.south': 'South',
    'gui.calculator.side.west': 'West',
    'gui.calculator.side.east': 'East',
    'gui.calculator.side.input': 'INPUT',
    'gui.calculator.side.output': 'OUTPUT',
    'gui.calculator.side.disabled': 'DISABLED',
    'info.nutrition': 'Nutrition',
    'info.tools': 'Tools',
    'info.EnergyModule.name': 'Can store up to %s RF or %s EU',
    'info.StoneAssimilator.name': 'Harvests Calculator Trees-+Must be placed next to the Tree-+For Tanzanite and Amethyst-+Doesn\'t Require Energy',
    'info.AlgorithmAssimilator.name': 'Harvests Calculator Trees-+Must be placed next to the Tree-+For Pear and Diamond-+Doesn\'t Require Energy',
    'info.AtomicMultiplier.name': 'Can quadruple almost any item--Requires seven circuits-and %s RF--or %s EU',
    'info.Sickle.name': 'Can remove berries/diamonds- from their trees',
    'info.ObsidianKey.name': 'See Purified Obsidian',
    'info.EndForgedSword.name': 'The GOD of Swords',
    'info.EndForgedHoe.name': 'Your only desire',
    'info.Soil.name': 'Can blind entities when thrown--Can change Dirt to Gravel',
    'info.SmallStone.name': 'Can damage entities when thrown--Can change Dirt to Farmland',
    'info.EndDiamond.name': 'If you want Ender Pearls-Look no further than Right Click--Makes unbreakable tools',
    'info.ControlledFuel.name': 'Controls its own burning--Only works in-Carbon Dioxide Generator',
    'info.CalculatorScreenBlock.name': 'Displays the RF stored-in machines when placed-on the side',
    'info.PurifiedObsidian.name': 'Indestructable/Security block--Only removable with Obsidian Key',
    'info.FlawlessGlass.name': 'V. Expensive Glass--Used in Flawless Greenhouses',
    'info.WeatherStationMachine.name': 'Has the ability to-control the weather',
}

# 台阶 -> 楼梯 (#14)
STAIRS = {
    'tile.AmethystStairs.name': '紫晶楼梯',
    'tile.TanzaniteStairs.name': '坦桑楼梯',
    'tile.PearStairs.name': '梨木楼梯',
    'tile.DiamondStairs.name': '钻石木楼梯',
    'block.calculator.amethyststairs': '紫晶楼梯',
    'block.calculator.tanzanitestairs': '坦桑楼梯',
    'block.calculator.pearstairs': '梨木楼梯',
    'block.calculator.diamondstairs': '钻石木楼梯',
    'greenhouse.stairs': '楼梯',
}

def update(name, added, stairs=None, report=None):
    p = os.path.join(BASE, name)
    data = json.loads(io.open(p, encoding='utf-8').read())
    before = len(data)
    newcount = 0
    for k, v in added.items():
        if k not in data:
            data[k] = v
            newcount += 1
    if stairs:
        for k, v in stairs.items():
            if k in data:
                data[k] = v
    io.open(p, 'w', encoding='utf-8', newline='\n').write(
        json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    print('%-14s keys %d -> %d (added %d)' % (name, before, len(data), newcount))

update('zh_cn.json', {**BLOCK_KEYS_ZH, **GUI_ZH}, STAIRS)
update('en_us.json', {**BLOCK_KEYS_EN, **GUI_EN})
update('ru_ru.json', {**BLOCK_KEYS_EN, **GUI_EN})
update('uk_ua.json', {**BLOCK_KEYS_EN, **GUI_EN})
print('done')
