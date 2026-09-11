# Archaeo — gameplay guide

Players do not use commands. Staff place hidden ruins on the map; you find them
by walking, then you plant a camp and dig the ground itself.

Default item names below are the vanilla stacks in the plugin jar. If the server
uses the custom pack (`pack/`), the same roles are custom MMOItems / ItemsAdder
stacks — see `pack/README.md`.

```
Tracker → prospect samples → establishment kit → camp board
    → Hand Pick in the prism → brush exposed finds
    → clean and register at the cabinet → plaque on a museum support
```

## 1. Find a ruin

Hold the **tracker** (recovery compass, or `ARCHAEO_TRACKER`). Pulses get faster
as you near an **unclaimed** ruin. Established and exhausted sites stay silent;
you return to those by the camp, not the radar.

There is no ruin list and no compass needle to the chunk. If the beeps fade,
you walked the wrong way.

When you are close enough, chat tells you to prospect the ground.

## 2. Confirm it (prospect)

Use the **prospecting kit** (stone hoe, or `ARCHAEO_PROSPECT`) on shovel-dug
earth: dirt, grass, sand, gravel, clay, mud. Furniture and stone do not count.

You need several samples a few blocks apart (default: 4, about 2 seconds each).
Moving too far cancels the sample. Weak traces are not enough; keep sampling
until the site is **confirmed**. That still does not claim it.

## 3. Plant the camp

Use the **establishment kit** (stick, or `ARCHAEO_ESTABLISH`) only after
confirmation. Aim at a **neighbour chunk**, not the ruin chunk. A preview of
the camp follows your look; rotate until it sits on valid ground, then confirm.

You become **director**. By default you may direct one open excavation at a
time. The radar ignores this ruin from now on.

The camp chunk is locked while the dig is active. The dig prism is the ruin
chunk (and its depth bands), not the tents. Building the camp does not spend
the work day.

Open the **board** at camp for the dossier, roster, finds list, and **Show
limits** (glowing bars of the prism, for you only, a few seconds).

The director adds workers by name. Only people on the roster may Hand Pick.
This is not a land claim plugin.

## 4. Dig (hear, then release)

Inside the prism, hold **left-click** with a listed excavation tool. Vanilla
cracks stay frozen. Listen:

| Cue | Meaning |
| --- | --- |
| Soft clings, then a louder ready chime | Empty fill. **Release on the ready chime** to lift cubes. |
| Release too soon | Nothing leaves. The next hold starts clean. |
| Release too late | More cubes leave (worse control). |
| Find cling (different timbre), often with *Stop* | That cube is not dirt. **Do not** treat it as fill. The piece stays in the ground. |

The HUD shows the **stratum** and **Hand Pick actions left today** (default 8
per Minecraft day). No force bar, no `3/6` on the block.

Default tool profiles (pack names in parentheses):

| Profile | Ready lift | Late lift | Shape |
| --- | --- | --- | --- |
| Empty hand | 1 | 1 | Straight down |
| Light (hand pick / pointing trowel) | 1 | 2 | Straight down |
| Heavy (mattock / grafting spade) | 2 | 4 | Around the aim (3×3×2, face-connected) |
| Super-heavy (breaker pick / spoil shovel) | 4 | 8 | Same volume, extra cubes picked at random among faces |

Faster vanilla mining (better tool vs that block) only changes **when** the
cues fire, not how many cubes leave.

When an empty cube comes out, chat and the HUD can show **neighbour traces**
(faces only): how many find cubes of each material touch the hole. Use that to
slow down. Diagonals do not count.

If you ignore a find cling and punch the piece out, conservation drops. A
destroyed cell is gone for good.

## 5. Brush the find out

Finds are **shapes** of connected cubes, not loot blocks. When every remaining
cube of a shape has air on a face, they drip. **Right-click hold** the brush on
a dripping cube (default 2 seconds). Look away to pause that cube’s bar; look
back to resume.

After enough distinct cubes (or the whole small shape), one item drops with
conservation and provenance. The pick never drops the piece. Brushing does not
spend the work day. Conservation 0 means nothing is recovered.

Grades on the item (defaults): Intact / Sound / Worn / Fragmentary / Crumbling.
Buried luck is rolled when the ruin is generated; careful digging cannot make a
rotten piece pristine. Organic materials survive worse than stone.

## 6. Clean, sketch, register

Take the recovered piece to the **cabinet** (cartography table, or the
ItemsAdder cabinet). Right-click with the piece in hand (sneak for vanilla):

- dirty → lab wipe (match each stain to water, brush, or air)
- cleaned, no drawing → register
- already filed → open a reading

**Field sheet** (paper) + **pencil** (feather, or `ARCHAEO_PENCIL`): click the
sheet onto the pencil in the inventory, or hold the sheet and use the pencil in
the other hand. Sign the map, then register at the cabinet with the find in
hand. The sheet is consumed; the pencil wears.

Empty-handed clicks do not open the cabinet window.

## 7. Museum

Build any display you like. **Shift + right-click** a recovered Archaeo find on
a listed support (item frame, glow frame, armor stand, lectern, shelf, and any
pack furniture in `museum.displays`) to open its plaque. Click without shift is
vanilla (hang, rotate, take). Empty slots and ordinary items do nothing extra.

## 8. Exhausted sites

When every find is recovered or destroyed, the excavation **exhausts**. Picks
and brushes stop. The camp and board remain as the site’s memory. The radar
already ignored it from the moment you established.

Closing the camp (director, from the board) frees your excavation slot.

## Staff (not players)

Players never `/archaeo`. Staff (`permissions.staff`, default `archaeo.admin`)
register ruin chunks, give role items, reload, and test finds. Typical flow:
stand in the map chunk → create a ruin at an interest level → let players
discover it. Catalogs (`artifacts.yml`, `strata.yml`, `hints.yml`, …) shape
what is buried; `config.yml` shapes the verbs above.

If vanilla mining is allowed in an unestablished prism (or with
`establish.protect-dig-site: false`), breaking a find cube destroys it. With
protection on, only the Hand Pick works the prism fill.
