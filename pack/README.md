# Archaeo purchase pack

Optional drop-ins to ship with the plugin. The jar still starts on vanilla items.
Copy what you need onto the server and replace models, recipes, or ids.

Player loop: [`GAMEPLAY.md`](GAMEPLAY.md).

MMOItems tools in this pack use **vanilla materials**. There is no custom MMOItems
resource pack yet; looks stay compass / hoe / stick / pickaxe until that is added.

```
pack/
  README.md
  GAMEPLAY.md
  plugins/
    Archaeo/config.yml
    MMOItems/
      item/tools.yml                         type TOOLS templates (merge into this file)
      crafting-stations/archeology-station.yml
    ItemsAdder/archeo/
      contents/
        base.yml                             furniture + craft for the table
        categories.yml                       /ia menu category
      resourcepack/assets/archeo/
        models/furniture/                    Blockbench JSON
        textures/furniture/                  PNGs used by those models
```

## Copy onto the server

Paths are relative to the server root.

| From this pack | Onto the server |
| --- | --- |
| `plugins/Archaeo/config.yml` | `plugins/Archaeo/config.yml` after Archaeo has created its data folder. Leave `artifacts.yml`, `strata.yml`, and the other catalogs as generated. |
| `plugins/MMOItems/item/tools.yml` | merge into `plugins/MMOItems/item/tools.yml` (MMOItems type **TOOLS**, not `TOOL`) |
| `plugins/MMOItems/crafting-stations/archeology-station.yml` | `plugins/MMOItems/crafting-stations/archeology-station.yml` |
| `plugins/ItemsAdder/archeo/` | `plugins/ItemsAdder/contents/archeo/` |

Then: `/iareload` + `/iazip`, `/mi reload`, `/archaeo reload` (or a full restart).

## Two stations

They are not the same GUI.

| World furniture | Pack id | Opens |
| --- | --- | --- |
| Archeology Table | `itemsadder:archeo:archeology_station` | MMOItems crafting (`archeology-station`) |
| Archeology Cabinet | `itemsadder:archeo:archeology_cabinet` | Archaeo lab / register (`sketch.cabinet`) |

MMOItems does not bind a block in the station YAML. Open the workshop with console:

```
/mi stations open archeology-station <player>
```

Typical bind: ItemsAdder `events.placed_furniture.interact.execute_commands` on `archeology_station` (`as_console: true`, `{player}`), or an NPC / command block. Do **not** put that command on the cabinet, or both GUIs fight.

The table is also crafted in vanilla via the shaped recipe in `base.yml` (planks + bone). The cabinet is crafted at the MMOItems station (recipe `archeology-cabinet`).

`pack/plugins/Archaeo/config.yml` currently sets `sketch.cabinet` to `itemsadder:tfmc:archeology_cabinet`. The furniture in this pack is `itemsadder:archeo:archeology_cabinet`. Align that line with the namespace you actually load.

## What to replace

- ItemsAdder models/textures: `plugins/ItemsAdder/archeo/resourcepack/assets/archeo/`.
- Station recipes / GUI: `plugins/MMOItems/crafting-stations/archeology-station.yml`.
- Tool stats and names: `plugins/MMOItems/item/tools.yml`.
- Which stacks Archaeo accepts: item lines in `plugins/Archaeo/config.yml`.

If you rename an ItemsAdder key or an MMOItems id, change the matching Archaeo line. If you add MMOItems custom models later, point `item-model` / `custom-model-data` on those templates at a separate resource pack; this folder does not include one.

## Id map (`pack/plugins/Archaeo/config.yml`)

| Role | Id |
| --- | --- |
| Tracker | `mmoitems:TOOLS:ARCHAEO_TRACKER` |
| Prospect kit | `mmoitems:TOOLS:ARCHAEO_PROSPECT` |
| Establish kit | `mmoitems:TOOLS:ARCHAEO_ESTABLISH` |
| Light cut | `mmoitems:TOOLS:HAND_PICK`, `mmoitems:TOOLS:POINTING_TROWEL` |
| Heavy cut | `mmoitems:TOOLS:MATTOCK`, `mmoitems:TOOLS:GRAFTING_SPADE` |
| Super-heavy cut | `mmoitems:TOOLS:BREAKER_PICK`, `mmoitems:TOOLS:SPOIL_SHOVEL` |
| Pencil | `mmoitems:TOOLS:ARCHAEO_PENCIL` |
| Brush | `BRUSH` (vanilla; station can craft it) |
| Sketch sheet | `PAPER` (vanilla) |
| Cabinet | `itemsadder:tfmc:archeology_cabinet` in config (see namespace note above) |

Museum displays stay vanilla frames / lectern / shelf. Lab wipe tools in the cabinet window stay vanilla GUI copies.
