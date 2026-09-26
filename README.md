# Archaeo

> Hidden ruins, careful excavation, and discoveries with a history.

Archaeo brings archaeological fieldwork to TF-Minecraft. Players follow the
sound of a tracker, survey the ground, and establish a camp beside a buried
ruin. Finds occupy shapes in the earth: careless digging can damage or destroy
them before they ever reach the surface.

## Features

- **Discovery by sound** — tracker pulses guide explorers toward unclaimed
  ruins, while prospecting confirms a site's location.
- **Excavation camps** — direct a dig, manage its workers, and follow progress
  through camp boards and a site dossier.
- **Careful digging** — different tools, ground layers, and timed sound cues
  turn each cut into a choice between speed and precision.
- **Fragile finds** — expose and brush out buried objects whose condition
  reflects both their age in the ground and damage during excavation.
- **Study and preservation** — clean recovered pieces, draw field sketches,
  and register finds at the camp cabinet.
- **Museum stories** — read the history of displayed finds and keep a field
  book of a site's discoveries after closing its camp.

## The expedition

**Track → Survey → Establish camp → Excavate → Recover → Study and display**

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Archaeo/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests

With Java 21 and Maven installed, run `mvn clean verify`. Tests exercise domain
rules, YAML persistence, and plugin workflows using JUnit, Mockito, and MockBukkit.
JaCoCo measures all production classes and writes its HTML report to
`target/site/jacoco/index.html` and machine-readable results to
`target/site/jacoco/jacoco.xml`. The build workflow uploads the coverage report.

Tests should assert gameplay behavior, data preservation, or failure handling.
Do not add tests solely to execute a line or manufacture unreachable states to
increase coverage. MockBukkit tests do not replace testing on a real Paper server
with the optional ItemsAdder and MMOItems integrations installed.

Adapter contract tests can also run against locally supplied plugin jars:

```sh
mvn -Ppack-api-tests clean verify \
  -Ditemsadder.jar=/path/to/ItemsAdder.jar \
  -Dfastnbt.jar=/path/to/FastNbt-jar.jar \
  -Dmmoitems.jar=/path/to/MMOItems.jar \
  -Dmythiclib.jar=/path/to/MythicLib.jar
```

These tests bind the actual API classes and mock their external operations; they
do not start those plugins. The jars remain outside this repository and are used
only on the test runtime classpath. Use the FastNbt version declared by your
ItemsAdder jar. The ordinary test suite needs none of these files.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
