# Calculator + Sonar Core — Unofficial Forge 1.20.1 Port

Unofficial, community-maintained port of the Minecraft mods **Calculator** and its
shared library **Sonar Core**, targeting **Minecraft Java Edition 1.20.1**, **Forge
47.4.10** and **Java 17**.

Maintainer of this port: **崇宫澪酱** (GitHub [@s1dsa](https://github.com/s1dsa)).

> This is an **unofficial derivative** work. It is **not affiliated with, sponsored
> by, or endorsed by** the original author. "Calculator" and "Sonar Core" are the
> original works of SonarSonic / Ollie Lansdell.

## Supported environment

| Component | Version |
| --- | --- |
| Minecraft Java Edition | 1.20.1 |
| Forge | 47.4.10 (`[47.4.10,48)`) |
| Java | 17 (Gradle toolchain) |
| Gradle wrapper | 8.14.3 (BIN distribution) |
| Mappings | `official` / `1.20.1` |
| Port version | `1.0.4-beta.1` |

## Modules

This repository contains two Gradle subprojects, both built together:

- **`sonarcore`** — the shared infrastructure module (Forge Energy storage, sided
  inventories, fluid tanks, menu/network helpers, converted blocks/items/recipes).
  Mod id `sonarcore`.
- **`calculator`** — the Calculator machines, items, tools, modules, world content
  and client rendering. Mod id `calculator`. It has a **mandatory dependency on the
  ported `sonarcore`** (see `dependencies.calculator` in
  `calculator/src/main/resources/META-INF/mods.toml`).

Both mods must be installed together; `calculator` will not load without the ported
`sonarcore`.

## JEI integration (optional)

JEI is an **optional** dependency and is **never bundled** into the distributed jars.

- The JEI integration plugin lives under `sonar.calculator.mod.integration.jei`. It is
  the only JEI-touching code and is loaded by JEI's own client-side `@JeiPlugin` scan.
- JEI is on the **compile classpath only** (`compileOnly`) so the integration compiles;
  the API artifacts are declared in `calculator/jei.gradle`. The full JEI mod is added
  to the **dev runtimes only** (`runtimeOnly`) unless disabled.
- To build or run the dev environment **without** the JEI mod, pass `-Pjei=false`:

  ```
  gradlew build -Pjei=false
  gradlew runClient -Pjei=false
  ```

  With `-Pjei=false` the JEI API stays on the compile classpath (sources still build)
  but the JEI mod is dropped from the dev runtimes. This is the configuration used to
  verify the "runs without JEI" case.
- To pin a different JEI version for a re-verification, pass `-Pjei_version=<version>`
  (default `15.62.0.219`).

Every recipe category reuses the artwork of its real machine or portable calculator:
the category background is cropped from the corresponding GUI texture (slot row,
legacy operator glyphs such as `+ ÷ + =`, and the animated progress arrow). The crop
starts below the machine title and stops above the energy bar / player inventory, so
JEI's own title is the only heading and no fake energy value or inventory caption is
drawn. Input/output counts, exact NBT, probability/variant notes, numeric values and
catalysts are still shown. The flawless-calculator page therefore reproduces the
original JEI layout: four inputs with `+ ÷ + =` and one output in a single row.

## No bundled third-party binaries

The distributed mod jars do **not** embed any third-party mod jar (no JEI, no legacy
integration jars). Legacy compatibility integrations from the 1.12.2 sources (AE2,
IC2/EU, Waila, CraftTweaker, Multipart) are **not** part of this port. See
`KNOWN_ISSUES.md` for the full list of limitations.

## Building from source

A JDK 17 must be available; the Gradle wrapper downloads its own Gradle distribution
(pinned by SHA-256 in `gradle/wrapper/gradle-wrapper.properties`).

```
# Windows
gradlew.bat build
gradlew.bat runClient
gradlew.bat runServer

# Linux / macOS
./gradlew build
./gradlew runClient
./gradlew runServer
```

Useful tasks and helpers:

- `gradlew build` — compile, reobfuscate and assemble both mod jars.
- `gradlew check :calculator:runGameTestServer` — run the Forge GameTest suites.
- `scripts/*.ps1` — reproducible PowerShell wrappers for build/test/run and port
  validation (`Build.ps1`, `Test.ps1`, `Run-Client.ps1`, `Run-Server.ps1`,
  `Verify-Port.ps1`, ...).
- `tools/*.py` — resource conversion/validation and audit scripts.

Output jars are written to `calculator/build/libs/` and `sonarcore/build/libs/`.

## License and attribution

This project is distributed under the **MIT License**. The original upstream license
is preserved verbatim at [`LICENSE`](LICENSE): **Copyright (c) 2018 Ollie Lansdell**.
The copyright notice and the MIT terms are retained in the repository root and are
included in the built jars.

Original projects and sources:

- Calculator — original mod by SonarSonic / Ollie Lansdell
  - CurseForge: https://www.curseforge.com/minecraft/mc-mods/calculator
  - GitHub: https://github.com/SonarSonic/Calculator
- Sonar Core — original shared library by SonarSonic / Ollie Lansdell
  - CurseForge: https://www.curseforge.com/minecraft/mc-mods/sonar-core
  - GitHub: https://github.com/SonarSonic/Sonar-Core

Both upstream repositories carry the MIT License, Copyright (c) 2018 Ollie Lansdell.
The MIT grant permits derivative works; retaining the copyright and license text does
**not** imply that the original author endorses this unofficial port. See
`UPSTREAM.md` for exact upstream commit provenance and `THIRD-PARTY-NOTICES.md` for
third-party license notes.

## Status and known limitations

This is a beta development port (`1.0.4-beta.1`), **not** a claim of byte-for-byte or
behavior-for-behavior parity with the original. Client-side rendering and GUI
layouts, some animation interpolation, and a number of legacy integrations differ
from upstream, and **existing 1.12.2 worlds are not validated for direct migration**.
Read [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) before using the jars in a real world.

## Reporting bugs

Please report issues at:
https://github.com/s1dsa/Calculator-Forge-1.20.1/issues

---

# Calculator + Sonar Core — 非官方 Forge 1.20.1 移植

这是 Minecraft 模组 **Calculator** 及其共享库 **Sonar Core** 的**非官方社区移植**，
目标环境为 **Minecraft Java 版 1.20.1**、**Forge 47.4.10**、**Java 17**。

本移植的维护者：**崇宫澪酱**（GitHub [@s1dsa](https://github.com/s1dsa)）。

> 这是一个**非官方衍生作品**，与原作者**无隶属关系，未获其赞助或背书**。
> “Calculator”与“Sonar Core”是 SonarSonic / Ollie Lansdell 的原创作品。

## 支持环境

| 组件 | 版本 |
| --- | --- |
| Minecraft Java 版 | 1.20.1 |
| Forge | 47.4.10（`[47.4.10,48)`） |
| Java | 17（Gradle 工具链） |
| Gradle wrapper | 8.14.3（BIN 发行版） |
| 映射 | `official` / `1.20.1` |
| 移植版本 | `1.0.4-beta.1` |

## 模块

仓库包含两个一起构建的 Gradle 子项目：

- **`sonarcore`** —— 共享基础设施模块（Forge Energy 存储、分面物品栏、流体罐、
  菜单/网络辅助、已转换的方块/物品/配方）。Mod id 为 `sonarcore`。
- **`calculator`** —— Calculator 的机器、物品、工具、模块、世界内容与客户端渲染。
  Mod id 为 `calculator`。它**强制依赖移植后的 `sonarcore`**（见
  `calculator/src/main/resources/META-INF/mods.toml` 中的 `dependencies.calculator`）。

两个模组必须同时安装；缺少移植版 `sonarcore` 时 `calculator` 不会加载。

## JEI 集成（可选）

JEI 是**可选**依赖，**绝不会**被打包进发布的 jar。

- JEI 集成插件位于 `sonar.calculator.mod.integration.jei`，是唯一接触 JEI 的代码，
  由 JEI 自身的客户端 `@JeiPlugin` 扫描加载。
- JEI 仅在**编译类路径**上（`compileOnly`）以便集成代码编译；API 制品声明在
  `calculator/jei.gradle`。完整的 JEI 模组只在**开发运行环境**中加入
  （`runtimeOnly`），除非显式关闭。
- 若要**不带** JEI 模组构建或运行开发环境，使用 `-Pjei=false`：

  ```
  gradlew build -Pjei=false
  gradlew runClient -Pjei=false
  ```

  传入 `-Pjei=false` 时 JEI API 仍在编译类路径上（源码照常编译），但 JEI 模组会
  从开发运行环境中移除。这正是用于验证“无 JEI 亦可运行”的配置。
- 如需固定其他 JEI 版本进行重新验证，传 `-Pjei_version=<version>`（默认
  `15.62.0.219`）。

每个配方类别都直接采用对应机器/便携计算器的界面美术：类别背景从实际 GUI 贴图裁切，
包含槽位行、原版运算符（如 `+ ÷ + =`）和动画进度箭头；裁切区域从机器标题下方开始、
在能量条/玩家物品栏上方结束，因此页面顶部只保留 JEI 自带标题，不重复绘制机器标题、
虚假能量值或“物品栏”文字。输入/输出数量、精确 NBT、概率/变体说明、数值型说明与
catalyst 均保留。完美计算器页面因此复现原版 JEI 布局：四个输入槽与 `+ ÷ + =`
运算符及一个输出槽横排显示。

## 不打包任何第三方二进制

发布的模组 jar **不嵌入**任何第三方模组 jar（不含 JEI，也不含旧版集成 jar）。
1.12.2 源码中的旧版兼容集成（AE2、IC2/EU、Waila、CraftTweaker、Multipart）**不**属于
本移植。完整限制列表见 `KNOWN_ISSUES.md`。

## 从源码构建

需要 JDK 17；Gradle wrapper 会自行下载其 Gradle 发行版（在
`gradle/wrapper/gradle-wrapper.properties` 中以 SHA-256 固定）。

```
# Windows
gradlew.bat build
gradlew.bat runClient
gradlew.bat runServer

# Linux / macOS
./gradlew build
./gradlew runClient
./gradlew runServer
```

常用任务与辅助脚本：

- `gradlew build` —— 编译、重混淆并组装两个模组 jar。
- `gradlew check :calculator:runGameTestServer` —— 运行 Forge GameTest 套件。
- `scripts/*.ps1` —— 可复现的 PowerShell 封装，用于构建/测试/运行及移植校验
  （`Build.ps1`、`Test.ps1`、`Run-Client.ps1`、`Run-Server.ps1`、`Verify-Port.ps1` 等）。
- `tools/*.py` —— 资源转换/校验与审计脚本。

产物 jar 输出到 `calculator/build/libs/` 与 `sonarcore/build/libs/`。

## 许可证与署名

本项目以 **MIT 许可证**分发。原始上游许可证逐字保留于 [`LICENSE`](LICENSE)：
**Copyright (c) 2018 Ollie Lansdell**。版权声明与 MIT 条款保留在仓库根目录，并已包含
在构建产出的 jar 中。

原始项目与源码：

- Calculator —— 原作者 SonarSonic / Ollie Lansdell
  - CurseForge：https://www.curseforge.com/minecraft/mc-mods/calculator
  - GitHub：https://github.com/SonarSonic/Calculator
- Sonar Core —— 原作者 SonarSonic / Ollie Lansdell
  - CurseForge：https://www.curseforge.com/minecraft/mc-mods/sonar-core
  - GitHub：https://github.com/SonarSonic/Sonar-Core

两个上游仓库均采用 MIT 许可证，Copyright (c) 2018 Ollie Lansdell。MIT 授权允许衍生
作品；保留版权与许可证文本**不代表**原作者对本非官方移植的背书。上游提交来源详见
`UPSTREAM.md`，第三方许可证说明详见 `THIRD-PARTY-NOTICES.md`。

## 状态与已知限制

这是一个 Beta 开发版本（`1.0.4-beta.1`），**并非**与原版逐字节或逐行为一致的声明。客户端
渲染与 GUI 布局、部分动画插值以及若干旧版集成均与上游存在差异，且**现有 1.12.2 世界
未经验证，不支持直接迁移**。在真实世界中启用前请阅读 [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md)。

## 问题反馈

请在以下地址提交问题：
https://github.com/s1dsa/Calculator-Forge-1.20.1/issues
