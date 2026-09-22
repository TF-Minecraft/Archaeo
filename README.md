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

## Repository layout

- `src/main/java/` — plugin source.
- `src/main/resources/` — bundled defaults, catalogs, and `plugin.yml` metadata.
- `pack/plugins/` — optional MMOItems and ItemsAdder integrations, including an
  Archaeo configuration preset. See the [pack installation guide](https://github.com/TF-Minecraft/Docs/blob/main/projects/Archaeo/docs/pack-installation.md).
- TFMC-specific configuration and lore catalogs live in private
  [ServerAssets](https://github.com/TF-Minecraft/ServerAssets/tree/main/configs/Archaeo).

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
