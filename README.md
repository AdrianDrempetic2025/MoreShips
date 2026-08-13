# MoreShips (Project Beacon)

Custom multi-occupant, modular, combat-capable water vehicles for Minecraft Java 26.2 (Paper).

Players craft a ship core, place it in water, apply a hull material, install modules, and finalize. Ships carry multiple riders, cargo, cannons, and health modules. They fight, take damage, get destroyed into wrecks, and drop recoverable cargo.

Vanilla boats are untouched.

## Status

**Scaffold** — building empty jar. No production behavior yet.

Implementation is sequenced per the Development Ontology v2 L5-04 plan (critical invariants first: cargo conservation, identity, lifecycle atomicity, then ergonomics).

## Build

```powershell
./gradlew build
```

Produces `build/libs/MoreShips-<version>.jar`. Drop into a Paper 26.2 server's `plugins/` directory.

## Layout

```
MoreShips/
├── build.gradle.kts              # Paper 26.2 API, Java 25, JUnit 5
├── settings.gradle.kts
├── gradle/                       # wrapper
├── src/main/java/com/glooshy/ships/
│   └── MoreShips.java            # main class stub
├── src/main/resources/
│   └── plugin.yml                # plugin metadata
└── src/test/java/                # (empty — first tests come in BUILD-01)
```

## Ontology

Specification and design artifacts live in `../ONTOLOGY/Desktop/Jan-MC-Plugin-02-Ships/`. The Development Ontology v2 templates live in `../ONTOLOGY/Desktop/Jan-MC-Plugin-02-Ships/zz000-DEVELOPMENT-ONTOLOGY-v2/`.

Risk class: **RC3** (concurrency, persistence, async lifecycle, multi-actor).

## License

TBD — matches the rest of the Glooshy plugins.
