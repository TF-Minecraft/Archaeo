# TFMC artifact migration: vanilla to MMOItems

This reference describes the TFMC preset change on 10 October 2026 (ServerAssets commits `9442023` and `1a61407`). It is not a built-in replacement map: each running server uses its own `plugins/Archaeo/artifacts.yml`. The shipped Archaeo default catalog is different.

Use `/archaeo find info` while holding an old piece, or `/archaeo find list <#serial>` followed by `/archaeo find info <#serial> <number|uuid>`. These read the archive without rebuilding or changing the item. They show the artifact ID, stored item token, UUID, and all current pool entries. A missing stored token is reported as unknown; an absent artifact ID needs a manual replacement choice.

## TFMC equivalences

Every target below is under `mmoitems:ARCHAEO:`. Artifact IDs stayed the same during this preset change. Match by artifact ID, not just vanilla material: five different archaeological finds used `BONE`.

| Artifact ID | Old item(s) | Current MMOItems ID(s) |
| --- | --- | --- |
| `imperial_denar` | `GOLD_NUGGET` | `IMPERIAL_DENAR`, `IMPERIAL_DENAR_LAUREL`, `IMPERIAL_DENAR_PROFILE`, `IMPERIAL_DENAR_WORN` |
| `pottery_sherd` | All 23 vanilla pottery sherds | `POTTERY_SHERD_PLAIN`, `POTTERY_SHERD_BANDS`, `POTTERY_SHERD_FIGURE`, `POTTERY_SHERD_RIM` |
| `bronze_bit` | `COPPER_HOE` | `BRONZE_BIT` |
| `imperial_blade` | `IRON_SWORD` | `IMPERIAL_BLADE` |
| `offering_bowl` | `DECORATED_POT` | `OFFERING_BOWL` |
| `hearth_ash` | `CHARCOAL` | `HEARTH_ASH` |
| `faith_charm` | `GOLD_INGOT` | `FAITH_CHARM` |
| `runestone_chip` | `AMETHYST_SHARD` | `RUNESTONE_CHIP` |
| `war_kit` | `IRON_NUGGET` | `WAR_KIT` |
| `ash_glass` | `GLASS` | `ASH_GLASS` |
| `dryad_ward` | `MOSSY_COBBLESTONE` | `DRYAD_WARD` |
| `abyssalite_fitting` | `IRON_INGOT` | `ABYSSALITE_FITTING` |
| `human_inhumation` | `BONE` | `HUMAN_INHUMATION` |
| `dryad_bone` | `BONE` | `DRYAD_BONE` |
| `ruin_guard` | `BONE` | `RUIN_GUARD` |
| `pastoral_dump` | `BONE` | `PASTORAL_DUMP` |
| `wyvern_bone` | `BONE` | `WYVERN_BONE` |

Imperial denars have four current variants; pottery sherds have four current variants. There is no recorded one-to-one motif correspondence. The intermediate `POTTERY_SHERD_ARCHER`, `_ARMS_UP`, `_BREWER`, `_HEART`, `_HOWL`, `_MINER`, `_SHELTER`, and `_SKULL` variants were replaced by the current four-piece pool. Do not infer a particular new motif from an old one.

[`artifact-equivalences.csv`](artifact-equivalences.csv) lists every old vanilla item and every retired MMOItems pottery motif separately, with all valid current candidates.

## Replace a piece

1. Bring the old object into a connected player inventory or another loaded location. Read its identity with `/archaeo find info` (main hand) or `/archaeo find info #12 3`. Note the artifact ID and full UUID.
2. Preview deletion with `/archaeo find delete #12 3`. The preview supplies a command using the UUID, which identifies the exact piece even if numbers are ambiguous.
3. Run `/archaeo find delete #12 <uuid> confirm`. This removes only that recovered row, reduces the site recovered count, and removes matching physical pieces from connected player inventories, ender chests, cursors, loaded dropped items, frames, item displays, armor stands, and loaded block inventories, including items packed in shulkers. The site and all other finds remain.
4. Give the current artifact with `/archaeo give artifact <artifact-id> #12 <player>`. Example: `/archaeo give artifact bronze_bit #12 PlayerName`. This uses the current catalog pool and requires the relevant pack plugins and IDs to be available.

This is deletion followed by a new staff-given find. The new piece gets a new UUID and inventory number, a newly rolled conservation value and the recipient as recoverer. Previous cleaning, drawings, interpretations, custom naming, and recovery attribution are not transferred. If those must be retained, do not use this workflow as an in-place upgrade.

## Scope and recovery

- Staff permission is `permissions.staff` (default `archaeo.admin`). Console can use explicit serial and number/UUID; hand inspection requires a player.
- Only `RECOVERED` rows can be deleted. Buried/lost finds and dossiers held for ownership review are refused.
- A complete dossier backup is saved under `plugins/Archaeo/sites/.deleted-finds/` before the row is removed. If backup or persistence fails, no physical cleanup runs. A failed deletion write restores the row and recovered count in memory.
- Removed inventory numbers remain reserved through the persisted `last-find-number` field. Other pieces are never renumbered. Worker activity counters remain historical.
- Offline player data, unloaded chunks, container-bearing entities other than the listed displays, bundles, and previously printed books/reports/sketches are not rewritten. Unloaded copies lose their live archive link; they must be collected and discarded manually. The command does not claim a global item wipe.
- Restore a backup with the server stopped and reconcile any physical copies and later dossier changes first; replacing a live dossier can trigger the existing ownership-conflict guards.
- `find list` accepts an optional page number and shows eight recovered pieces per page. `find info` can inspect any row by UUID; deletion stays restricted to recovered pieces.
