# Changelog

## 1.0.4-beta.1 — 2026-10-17

Beta pre-release. Both `calculator` and `sonarcore` are built and published with the
same version. Requires Minecraft 1.20.1, Forge 47.4.10+, Java 17. JEI 15.x is optional.

### Conductor Mast (特斯拉电磁塔)

- Restored the original wait/status line between the slot row and the energy bar
  (upstream `GuiConductorMast`, centred at y=46, dark grey, no shadow). The line shows
  `Wait Time: <message>` during the first 500 ticks of a delay of at least 600 ticks,
  using the original nine messages (`conductor.msg` … `conductor.msg8`, including
  `You have Failed` / `你失败了`), and the remaining whole seconds otherwise.
- The message index is rolled together with each new delay on the server, exactly like
  upstream; the decision is no longer missing on the client. No recipe, success,
  failure-probability or energy-consumption gameplay was changed.
- Delay, timer and message index are synchronised as full 32-bit values through the
  machine state and the menu's split-unsigned-short container data (no 16-bit wrap).

### Machine GUIs

- All machine screens no longer draw the vanilla "Inventory" caption. The text is not
  drawn at all (not blanked via language files and not moved off-screen); titles,
  status lines, energy read-outs and every slot keep their positions.

### JEI (optional)

- Every one of the 19 recipe categories now uses a crop of its real machine or portable
  calculator GUI as the category background: the real slot row, legacy operator glyphs
  and the animated progress arrow are reused. Crops exclude the machine title, player
  inventory and energy text, so JEI's own category title is the only heading and no
  fake energy value is displayed.
- The flawless calculator page again shows four inputs with `+ ÷ + =` and one output in
  a single row; calculator/scientific use `+`/`÷`, atomic uses `+ + =`, separators use
  their double-output layout, and the fabrication chamber uses its own circuit grid.
- Input/output counts, exact NBT, probability/variant notes, numeric value notes,
  analysing circuit position/value and non-empty catalysts are preserved.
- Layouts are shared and bounds-checked at load; each slot and progress arrow must stay
  inside its artwork crop. Category titles and tooltip strings exist in en_us, zh_cn,
  ru_ru and uk_ua.
- JEI stays optional: `compileOnly` API and no JEI classes in the runtime jars. The
  pinned 1.20.1 JEI 15.62.0.219 dependency is unchanged.

### Verification

- 78/78 required GameTests pass, including three new Conductor Mast tests.
- Extended `tools/check_jei_display.py` audit passes (19 families / 286 recipes,
  artwork presence and bounds, catalyst non-emptiness, language-key parity, count/NBT/
  probability retention, no Inventory caption, status branch and 32-bit sync).
- Client-side visual confirmation of the new status line and JEI pages was not possible
  in this environment; see `KNOWN_ISSUES.md` for the remaining checks.
- The version is a new beta iteration with functional source changes. A CurseForge
  cross-project duplicate-file warning, if shown, is a false positive: these jars are
  not copies of the previous `1.0.3-round4` files and should be reviewed, not
  re-versioned to bypass the check.

## 1.0.3-round4

Previous development build; see the repository history and `KNOWN_ISSUES.md` for its
scope. This changelog starts with the beta release above.

---

## 1.0.4-beta.1 — 2026-10-17（中文）

Beta 预发布。`calculator` 与 `sonarcore` 以同一版本构建和发布。需要 Minecraft 1.20.1、
Forge 47.4.10+、Java 17；JEI 15.x 为可选依赖。

### 特斯拉电磁塔

- 恢复槽位行与能量条之间的原版等待/状态文字（对应上游 `GuiConductorMast`，居中 y=46、
  深灰色、无阴影）。当延迟不少于 600 tick 且计时不足 500 tick 时显示
  `等待时间: <消息>`，使用原版九条消息（`conductor.msg` … `conductor.msg8`，含
  `你失败了`）；其余时间显示剩余整秒数。
- 每次生成新延迟时由服务端同时随机消息索引，与原版一致，客户端不再缺失判断。未修改
  配方、成功/失败概率或耗能玩法。
- 延迟、计时与消息索引通过机器状态及菜单的“无符号短整型拆分”容器数据以完整 32 位同步，
  不会发生 16 位回绕。

### 机器界面

- 所有机器界面不再绘制原版“物品栏”标签。完全没有调用绘制（不是把翻译置空，也不是把
  文字移到屏幕外）；标题、状态文字、能量数值与所有槽位位置保持不变。

### JEI（可选）

- 全部 19 个配方类别改用对应机器/便携计算器真实 GUI 的裁切作为背景：复用真实槽位行、
  原版运算符与动画进度箭头。裁切避开机器标题、玩家物品栏与能量文字，因此页面顶部只有
  JEI 自带标题，也不显示虚假能量值。
- 完美计算器页面重新为四输入 `+ ÷ + =` 与一个输出横排；计算器/科学计算器使用 `+`/`÷`，
  原子计算器使用 `+ + =`，分离器使用双输出布局，制造室使用自身电路网格。
- 保留输入/输出数量、精确 NBT、概率/变体说明、数值说明、分析室电路位置/数值以及非空
  catalyst。
- 布局共享并在加载时校验边界：每个槽位与进度箭头都必须位于贴图裁切内。类别标题与提示
  文本在 en_us、zh_cn、ru_ru、uk_ua 中齐备。
- JEI 仍为可选：仅 `compileOnly` API，运行 jar 不含 JEI 类；固定依赖保持 1.20.1 JEI
  15.62.0.219。

### 验证

- 78/78 必需 GameTest 通过，其中新增 3 个电磁塔测试。
- 扩展后的 `tools/check_jei_display.py` 审计通过（19 类 / 286 配方、贴图存在与边界、
  catalyst 非空、语言键一致、数量/NBT/概率保留、无“物品栏”标签、状态分支与 32 位同步）。
- 当前环境无法进行客户端视觉确认；剩余实机检查详见 `KNOWN_ISSUES.md`。
- 这是包含实际功能改动的新 Beta 版本。如 CurseForge 提示跨项目重复文件，属于误报：
  这些 jar 并非 `1.0.3-round4` 的复制品，应人工审核而不是改版本号绕过检测。
