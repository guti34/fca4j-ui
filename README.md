# FCA4J UI

Companion JavaFX desktop application for [FCA4J](https://github.com/guti34/fca4j-project) — a Java toolkit for Formal Concept Analysis (FCA) and Relational Concept Analysis (RCA).

[![Build & Release](https://github.com/guti34/fca4j-ui/actions/workflows/build.yml/badge.svg)](https://github.com/guti34/fca4j-ui/actions/workflows/build.yml)
[![License: BSD-3-Clause](https://img.shields.io/badge/license-BSD--3--Clause-blue.svg)](LICENCE.txt)

FCA4J UI wraps the FCA4J command-line library in a graphical workflow: edit formal contexts and RCA families, run FCA/RCA commands, and explore the resulting lattices, AOC-posets, and implication bases visually — without touching the command line.

## Features

- **Context & Family editors** — create and edit formal contexts, RCA relational families, and models directly in the UI.
- **Full command coverage** — binarize, clarify/reduce, inspect, irreducible elements, lattice/AOC-poset construction, the Duquenne-Guigues and D-Basis implication bases, and RCA family processing.
- **Interactive visualization** — concept lattices and AOC-posets rendered via GraphViz, with pan/zoom and export to SVG, PNG, or PDF.
- **Rule basis viewer** — browse and filter computed implication rules.
- **Multilingual** — English, French, and Spanish.
- **Cross-platform installers** — native packages for Windows, Linux, and macOS with a bundled JRE (no separate Java install needed).

## Requirements

- Java 21+ if running from a jar (the platform installers bundle their own JRE, so this isn't needed for those).
- [GraphViz](https://graphviz.org/download/) — optional, only required for lattice/AOC-poset visualization and export. Configurable path in **File > Preferences**.
- [FCA4J](https://github.com/guti34/fca4j-project) — the underlying `fca4j.jar`. The installers bundle a compatible version; the path is configurable in **File > Preferences** if you want to point to a different one.

## Installation

Download the latest installer from the [Releases page](https://github.com/guti34/fca4j-ui/releases):

| Platform | Package |
|---|---|
| Windows | `.msi` |
| Linux (Debian/Ubuntu) | `.deb` |
| macOS | `.dmg` |

On first launch, open **File > Preferences** to check the paths to `fca4j.jar` and, if you want lattice/AOC-poset visualization, to the GraphViz `dot` executable.

## Building from source

```bash
cd fca4j-project && mvn install   # build & install the FCA4J core library locally
cd fca4j-ui
mvn package -DskipTests           # build the standalone jar
mvn jpackage:jpackage             # build the native installer for your platform
```

See [README-deploy.md](README-deploy.md) for platform-specific prerequisites (WiX on Windows, `fakeroot` on Linux, Xcode CLI tools on macOS) and troubleshooting.

## Related projects

- [fca4j-project](https://github.com/guti34/fca4j-project) — the FCA4J core library and command-line tool.
- [Documentation site](https://www.lirmm.fr/fca4j) — full command reference, getting started guide, and tips & tricks.

## Support & Community

Questions about FCA4J UI or the underlying library, ideas, or feedback — all welcome on the [FCA4J Forum](https://github.com/guti34/fca4j-project/discussions) (GitHub Discussions on `fca4j-project`, shared across the library and this UI). Found a confirmed bug specific to the UI? Open an [issue](https://github.com/guti34/fca4j-ui/issues) here instead.

## License

BSD 3-Clause License — see [LICENCE.txt](LICENCE.txt). Copyright © 2026 LIRMM.

## Team

Developed by the [Marel team](https://www.lirmm.fr/equipes/marel/), LIRMM (CNRS / Université de Montpellier):

- Marianne Huchard — LIRMM
- Pierre Martin — CIRAD
- Alain Gutierrez — LIRMM (technical maintainer)
