# Lore catalog rewrite — agent instructions

Give this file to an agent that will retune Archaeo catalogs for a server’s lore
and does **not** know how the plugin wires those YAML files together.

Rewrite player-facing text, which finds exist, tags, weights, and ids you own.
Do **not** invent cross-file tokens. Do **not** change Java. Ignore
`docs/concepto.md` for this task.

**Files you rewrite:** `src/main/resources/artifacts.yml`, `hints.yml`,
`interpretations.yml`, `strata.yml`, `materials.yml` (and the same names under
`plugins/Archaeo/` on a running server).

**Files you may only extend if a rewrite needs a new token:** `config.yml`
(`sketch.lab.stains` / `sketch.lab.tools`, and `rarity:` if you add rarity
keys). `interest.yml` level keys must stay (`low` / `medium` / `high` /
`exceptional`). Do not rename stratum **machine ids** to match lore.

After editing, run the checklist at the bottom. If a row fails, fix YAML; do
not leave dangling ids. The plugin usually does not crash on unknown tokens —
the find, hint, or phrase simply never participates.

---

## Hardcoded tokens (do not invent)

These are Java enums or string literals. A new YAML value does nothing useful.

| Token | Allowed values | If you invent another |
|---|---|---|
| `artifacts.*.profile` | `object` (or omit), `individual`, `animal` | Treated as `object` |
| Phrase `profiles:` | same three | Token ignored |
| `interpretations.yml` → `profiles.<id>` | `object`, `individual`, `animal` | Section ignored |
| `interest.yml` level keys | `low`, `medium`, `high`, `exceptional` | Plugin will not load that tier |
| Stratum **machine ids** | Keep `I`, `II`, `III`, `IV` | Optional deep layer never spawns (see below) |
| `artifacts.*.item` | Bukkit `Material` name, or a YAML list of them (`GOLD_NUGGET`, `BONE`, …). A list picks one when the find is generated and stores it on that instance | Falls back to `BRICK`. Not ItemsAdder / MMOItems ids |
| `materials.*.clean-glass` and stain `glass:` | Bukkit pane materials | Fallback pane |
| `rarity:` on an artifact | A key under `config.yml` `rarity:` (`common`, `rare`, `epic`, `legendary`, or one you added there) | Display falls back toward common |

**Stratum ids are the important trap.** `interest.yml` `stratum-iv-chance` only
toggles the band whose id is exactly `IV`. A band with `always-present: false`
that is **not** named `IV` is never present. Keep keys `I`–`IV`. Change
`display-name` and depths for lore. Do not add `V` expecting the chance key to
apply.

`I` is also the emergency fallback band in generation. Do not delete it.

---

## Shared vocabularies (closed sets you define)

You may rename these, but **every reference must exist on the producing side**.
Build the set first, then reuse it.

### 1. Material ids

**Produced by:** keys under `materials.yml` → `materials:`

**Consumed by:** `artifacts.yml` → `material:`

An unknown material still loads (survival 1.0, synthetic lab row). Every
artifact should point at a real materials row.

### 2. Stratum ids

**Produced by:** keys under `strata.yml` → `strata:` (keep `I`–`IV`)

**Consumed by:**

- `artifacts.yml` → `strata:` (which bands this template may spawn in)
- `hints.yml` → `require-strata-all`, `require-missing-stratum`

A find only spawns if **at least one** of its `strata` ids is present on that
site. If every artifact lists only `IV`, poor sites (no IV) generate **zero
finds**.

At least some artifacts must list `I` and/or `II` and/or `III`.

### 3. Artifact ids

**Produced by:** keys under `artifacts.yml` → `artifacts:`

**Consumed by:** `interpretations.yml` → `suggested-for:` (those keys, not
display names)

Unknown `suggested-for` ids are silently ignored (no crash, no guarantee).

Phrase option ids (`combat_edge`, …) must be **globally unique** across all
types (one flat map). Type ids (`function`, `species`, …) must be unique.
`profiles.*.types` must list type ids that exist under `types:`.

### 4. Tags (two different jobs)

Tags are a free string vocabulary. They are **not** materials, not stratum ids,
not artifact ids.

**Job A — which hints attach** (`hints.yml` filters). Compared only to the
**union of `artifacts.*.tags` of finds already placed**. Hint `tags:` are
**not** used here.

- `require-tags-any`: at least one of these artifact tags is on the site
- `require-tags-all`: all of these artifact tags are on the site

If you write `require-tags-any: [fire]` and no artifact has `fire`, that hint
never attaches.

**Job B — which interpretation phrases are more likely.** The station weights a
phrase ×3 when `suggested-by` intersects:

- the artifact’s `tags`, **plus**
- `tags:` of hints **already attached** to the site

A hint can inject tags no artifact has (`unknown`, `erosion`, `abandonment`, …)
**only if that hint actually attached**. Putting `suggested-by: [erosion]` with
no artifact tag `erosion` and no attached hint tagged `erosion` never boosts
that phrase.

`hint.tags` is flavour for Job B, not a selection filter. `text:` is what
players see.

`suggested-by` is tags. `suggested-for` is artifact ids. Do not mix them.

Example of a broken loop: `suggested-by: [clustered]` does nothing unless some
artifact lists `clustered` **or** some attachable hint lists `clustered` under
`tags:` (the default clustered hint uses `[deposit, ceremonial]`, not
`[clustered]`).

### 5. Lab stains (materials ↔ `config.yml`)

**Produced by:** `config.yml` → `sketch.lab.stains.<id>` (`display-name`,
`glass`, `tool`)

**Consumed by:** `materials.yml` → `stains:` (list of those ids)

Each stain’s `tool` must be a key under `sketch.lab.tools` (`water`, `brush`,
`air` in the default pack). If the tool id does not exist on the rack, that
stain cannot be wiped.

If a material lists unknown stain ids, the lab skips them and falls back to the
first stain in `config.yml`. If you need a new dirt kind, add it under
`sketch.lab.stains` (and a tool if needed), **then** list it on the material.
Do not invent stain ids only in `materials.yml`.

---

## What each file is for (behaviour, not lore)

### `strata.yml`

- Key = machine id (`I`…`IV`).
- `order`: 1 = top of the cut. Also drives the buried-conservation depth
  penalty (`order - 1`).
- `display-name`: player-facing (camp boards).
- `depth-min` / `depth-max`: blocks below the chunk’s median ground Y. Do not
  overlap wildly; min ≤ max.
- `always-present`: `true` for I–III; `false` only for `IV`.

Epoch / calendar date is **not** a stratum field. Dating is an interpretation
type (`epoch`). Do not encode eras as stratum ids.

### `artifacts.yml`

Each key is a find template.

| Field | Rule |
|---|---|
| `display-name` | Player-facing name |
| `size-min` / `size-max` | Connected cells. `min ≤ max`, both ≥ 1. Invalid range can crash generation |
| `material` | Must exist in `materials.yml` |
| `profile` | `object` / omit, `individual`, or `animal`. Picks which station questions run |
| `weight` | Relative spawn chance (≥ 1). Also feeds rarity if `rarity:` is omitted |
| `rarity` | Optional override; must be a `config.yml` `rarity:` key |
| `relic` | Leave `false` unless you also raise `min-relics` in `interest.yml` and mark some templates `relic: true`. Relic quota with an empty relic pool just skips those slots |
| `strata` | Subset of `I`–`IV` this template may occupy |
| `tags` | Job A + Job B vocabulary |
| `item` | Vanilla Bukkit material, or a list of them. A list is rolled once when the find is generated |
| `study-notes` | Shown after study at camp; not on lift |

Every `profile: individual` / `animal` find still needs `material`, `strata`,
and `tags` like any other.

### `hints.yml`

Players see `text`. Filters decide if the hint can be rolled; `weight` is the
roll among those that match. Interest `hint-count` is a cap: if the matching
pool is smaller, the site gets fewer notes.

Other filters (not tags):

- `min-wealth` / `max-wealth` vs `interest.yml` `base-wealth` (default pack:
  low=1, medium=3, high=6, exceptional=10). Do not change wealth numbers here
  without reading `interest.yml`.
- `min-strata`: minimum **present** band count (IV absent → often 3).
- `require-disturbed: true`: needs at least one disturbed band
  (`disturbed-chance` in interest).
- `require-missing-stratum: IV`: only when IV is absent.
- `require-strata-all: [I, III]`: those ids must all be present.

Keep some loosely filtered hints so low-wealth sites still fill `hint-count`.
A pack of only `min-wealth: 10` hints leaves poor ruins silent.

### `interpretations.yml`

Classification station. Not site flavour; one find, one question at a time.

- `profiles.<path>.types`: question order for that path. Default pack: object
  → function, formation, epoch; individual/animal → species, deposit, epoch.
- Each `types.<id>` needs `display-name`, `question`, and `options` with
  **enough phrases that apply to that path**. Fewer than 3 options means the
  player sees all of them (no real choice). Aim for ≥ 6 per type, ≥ 3 that
  apply to each path that uses the type.
- Phrase `profiles: [individual]` / `[animal]` splits species/deposit pools.
  **Omit** `profiles` on a phrase to offer it on every path (epoch).
- `suggested-by`: tags, weight only (×3). Never hides a phrase, never marks it
  correct.
- `suggested-for`: artifact ids. If any phrase lists this artifact, **one** of
  those is always among the three offers. The rest of the pool still appears.

Every artifact should appear in `suggested-for` of at least one phrase **per
type on its profile**, or that find has no floor guarantee (offers are random
among the path pool).

Do not put species phrases on object finds: either set
`profiles: [individual]` / `[animal]` on those options, or they leak onto
objects if you omit `profiles`.

### `materials.yml`

| Field | Rule |
|---|---|
| `display-name` | Neighbour traces in the cut, lore |
| `survival` | 0.05–1.0. Scales buried conservation (organic rots, stone lasts). Dig skill does not |
| `clean-glass` | Clean lab pane colour |
| `stains` | Ids from `config.yml` `sketch.lab.stains` |

Lab wipe verbs (“clean” / “Cleaning”) are hardcoded in Java, not YAML. Do not
look for a YAML field for that.

---

## Do not touch (unless a new stain, tool, or rarity key is required)

Gameplay knobs in `config.yml` (tracker, tools, conservation grades,
auto-ruins, …) and `interest.yml` budgets. Lore rewrite is catalogs plus
stain/tool labels if the dirt kinds change.

---

## Checklist (mandatory)

1. Every `artifacts.material` is a `materials.yml` key.
2. Every `artifacts.strata` id and every hint `require-strata-*` /
   `require-missing-stratum` is one of `I`–`IV`.
3. At least one artifact can spawn in I/II/III (not only IV).
4. Every `suggested-for` id is an `artifacts.yml` key.
5. Every `suggested-by` tag appears on some `artifacts.tags` **or** on
   `hints.tags` of a hint that can actually attach.
6. Every `require-tags-any` / `require-tags-all` tag appears on some
   `artifacts.tags`.
7. Every `materials.stains` id exists under `config.yml` `sketch.lab.stains`.
   Every stain `tool` exists under `sketch.lab.tools`.
8. Every `artifacts.profile` and every phrase `profiles` token is
   `object`, `individual`, or `animal`.
9. Every `profiles.*.types` id exists under `types:`. Every type used by a
   path has ≥ 3 options that apply to that path.
10. Interpretation option ids are unique across the whole file.
11. `size-min ≤ size-max`. `item` is a Bukkit material or a list of them. Stratum keys are still
    `I`–`IV`. `relic: false` unless interest relic quotas are raised.
12. Some hints have no wealth/tag filters (or loose ones) so `hint-count` can
    fill on poor sites.
