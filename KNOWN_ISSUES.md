# Known Issues and Limitations

Port version: `1.0.3-round4` — an **in-development, unofficial** port.

This document states the current limitations honestly. It is **not** a claim of
byte-for-byte or behavior-for-behavior parity with the original 1.12.2 mods, and it
does **not** claim that every machine, item, module or integration has been verified
in-game.

## Parity and auditing

- **No strict parity claim.** The strict upstream-obligation audit is intentionally
  fail-closed: it requires source-bound evidence for every upstream obligation, and
  that bar is **not** currently met. Treat this as a test candidate, not a
  "no-differences" release.
- The 1.12.2 upstream sources and the published 1.12.2 binaries carry no shared commit
  provenance, so the source commits referenced in `UPSTREAM.md` are **not** claimed to
  reproduce those binaries byte-for-byte.

## Not ported / materially different

- **Legacy third-party integrations are not implemented:** AE2 support, IC2/EU energy
  support, Waila providers, CraftTweaker recipe scripting, and Multipart block/tile
  integration are **absent**. The modern Forge Energy (FE) storage is **not** a
  substitute for the IC2 EU or other legacy energy-network protocols.
- **No legacy API compatibility layer:** the broad 1.12.2 Sonar-Core API trees
  (cabling, wrappers, registries, flexible GUI/container infrastructure, ASM
  facilities) are not reproduced as an API-compatible library. Downstream mods that
  depended on the old SonarAPI or its integration hooks require separate adaptation.
- **JEI** is provided as an **optional** integration only (see `README.md`); the JEI
  mod is never bundled.

## Client-side / visual

- **GUI pixel layouts** for some machines differ from upstream.
- **Animation interpolation** differences remain; some client partial-tick animation
  fidelity is not equivalent to the original.
- **Vertical orientation of lamps** differs in implementation from upstream.
- GUI/model acceptance was performed as a load/visual smoke check, **not** as a full
  pixel-level GUI and renderer acceptance pass over every machine and tool.

## World, saves and migration

- **Existing 1.12.2 worlds are not validated for direct migration.** Old NBT schemas
  and legacy on-disk formats are not guaranteed to load. Back up worlds before testing.
- **Disk-save restart, chunk unload/reload** behavior has not been exhaustively
  validated for every machine.

## Multiplayer / dedicated server

- **Multiplayer/dedicated-server interaction is only partially covered.** Network
  packet behavior and all downstream menu action/value validation are not exercised
  by the current automated suites. Recipe preflight assumes a compliant handler and
  immediate server-thread execution; it is **not** transactional rollback across
  arbitrary handlers whose state changes between simulate and execute.

## Performance

- Performance sampling is **limited** to the stone separator machine (empty, 64/256
  idle, and working scenarios; 100 warmup + 300 samples per group). It is **not** a
  full cross-machine performance comparison and did **not** measure FPS.

## Historical test evidence (not re-verified for this upload)

The following are historical results from the development cycle. They are recorded as
evidence of prior runs, **not** as a re-verification performed for this repository
upload:

- A final dual-mod GameTest run reported **32 required integration GameTests passing**
  (a combined total across the Sonar Core, Calculator base, workstation, and behavior
  suites — these overlap and must not be summed again).
- Earlier suites reported **21** required GameTests (Calculator integration), **12**
  (Sonar Core boundary), and **29** (workstation/module security and transaction).
- Unit-level tests reported **75 passing**.
- The resource conversion check verified structural/byte fidelity of converted
  resources against the upstream assets; that is structural evidence and **not** proof
  of runtime rendering or optional integration behavior.

## Research and disabled upstream content

- Some upstream research features were disabled/commented in the upstream registration
  and remain so here; they are not functional.

If any of the above is resolved later, update this file rather than dropping the
limitation.
