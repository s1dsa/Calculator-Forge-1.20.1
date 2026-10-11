# Third-Party Notices

This project is an unofficial derivative of two MIT-licensed upstream projects.

## Upstream projects (MIT)

- **Calculator** — original mod by SonarSonic / Ollie Lansdell.
  - GitHub: https://github.com/SonarSonic/Calculator
  - CurseForge: https://www.curseforge.com/minecraft/mc-mods/calculator
- **Sonar Core** — original shared library by SonarSonic / Ollie Lansdell.
  - GitHub: https://github.com/SonarSonic/Sonar-Core
  - CurseForge: https://www.curseforge.com/minecraft/mc-mods/sonar-core

Both upstream works are licensed under the **MIT License**,
**Copyright (c) 2018 Ollie Lansdell**. The full, unmodified MIT text is retained at the
repository root in [`LICENSE`](LICENSE) and is included in both built mod jars. The
upstream copyright notice is preserved here and in `UPSTREAM.md`. Retaining the
license does **not** imply endorsement of this unofficial port by the original author.

Source, resources and assets in `calculator/` and `sonarcore/` are derived from these
upstream MIT works, with their attribution and license retained.

## Bundled binaries

This project does **not** redistribute any third-party mod jar. In particular:

- **JEI** (`mezz.jei`) is an **optional**, user-installed dependency. It is referenced
  only at compile time (and optionally in dev runtimes); no JEI class is packaged into
  the distributed jars.
- The legacy third-party API jars used by the 1.12.2 sources are **not** redistributed
  as part of this port.
- The only binary artifact committed to this repository is the Gradle Wrapper jar
  (`gradle/wrapper/gradle-wrapper.jar`), which is part of the standard Gradle Wrapper
  distribution and is required to build the project reproducibly. It is distributed
  under the Apache License 2.0 as part of Gradle.

## Platform dependencies (not redistributed)

Minecraft, Minecraft Forge and their respective dependencies are **not** included in
this repository; they are provided by the user's own Minecraft/Forge installation and
are governed by their own respective licenses and terms.
