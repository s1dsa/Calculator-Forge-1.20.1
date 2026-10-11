# Upstream provenance

The source baseline is the author's `1.12.2` branches:

- Calculator 5.0.12: https://github.com/SonarSonic/Calculator — commit `c796f1dc24e937f95cb5d8573f485b66e87164b4`.
- Sonar Core 5.0.19: https://github.com/SonarSonic/Sonar-Core — commit `f5b64e033e2c07af55c2d5f474052fb83fba5ca1`.

Both repositories carry the MIT License, Copyright (c) 2018 Ollie Lansdell. The license is retained at the project root and included in both distribution JARs. Legacy third-party API JARs are not redistributed as part of this port.

Published binary references, for differential inspection when source behavior is ambiguous:

| Project | Official file | SHA-256 |
| --- | --- | --- |
| Calculator | https://mediafilez.forgecdn.net/files/2847/174/calculator-1.12.2-5.0.12-15.jar | f3863b72d1677c304e4a793f1653055f797e1fc3b5f4c4748e3c3c1d92ae1604 |
| Sonar Core | https://mediafilez.forgecdn.net/files/2645/156/sonarcore-1.12.2-5.0.19-20.jar | d3c5c5c1e3e09c6c7b49fc6505aad57c1ca0e76a8e5396acabf6123e8c96fa58 |

The published binaries contain no Git commit provenance, so these source commits are not claimed to reproduce those binaries byte-for-byte. Source, resource and behavioral correspondence must be assessed separately.

The 1.20.1 port is an unofficial derivative. No endorsement by the original author is implied. Existing 1.12.2 worlds are not validated for direct migration.
