# Content reference

[简体中文](CONTENT_REFERENCE_zh.md) | [Home](../README.md) | [Author workflow](AUTHOR_GUIDE.md)

## Data conventions

Use full `namespace:path` IDs. Native book schema 1 stores type configuration as a string map, including numbers and booleans. Prefer the editor for nested item matchers and reward trees; their structured values are serialized inside configuration strings. Preserve unknown fields when editing external content.

Quest `extensions` can set `"brnquest:quest_name_position": "off|above|below"`; chapter `extensions` can set `"brnquest:chapter_progress": "show|hide"`. The pipes denote alternative values. An absent key or `inherit` uses client settings. Explicit properties override client preferences. Choosing inheritance in the editor removes the owned key and preserves all other extension data.

Quest notification fields in `extensions` are `brnquest:notify_task_complete`, `brnquest:notify_quest_complete`, and `brnquest:notify_reward_claimed`, with string values `"true"` or `"false"`. Their respective defaults are true, true, and false. They control actual server objective-completion, quest-completion and successful reward-claim events, without changing progress or claim eligibility. Ordinary progress increments and historical synchronization do not generate notices.

The following sections cover configuration with important behavioral constraints. In-game field help supplies defaults and validation feedback. Task dependencies, sequential objectives, completion visibility and repeat rules are configured on the quest. Team progress and deployment are covered in the author guide.

Book and chapter `quest_defaults` support `notify_task_complete`, `notify_quest_complete`, `notify_reward_claimed` (JSON booleans), `quest_name_position` (`inherit|off|above|below`, a JSON string), and `minimum_required_dependencies` (a nonnegative JSON integer), alongside existing appearance and behavior defaults. Missing book fields use system defaults; missing chapter fields follow the book's creation presets. Resolved values are applied once when creating a quest; later preset edits leave existing quests unchanged. Notification and name values are written to the quest's reserved extensions, retaining explicit instance overrides and unrelated addon data. The name-position value `inherit` stores a live client-following display mode.

## Items and experience

- `brnquest:item` supports observation and consumption. The matcher editor keeps a count per item/tag entry; `required_entries` controls how many entries must be satisfied. Item entries accept the same item type even when stack components differ; submission lets players choose actual slots. Legacy `brnquest:item_choice` remains readable/editable but is hidden from creation.
- `only_from_crafting=true` counts new server-observed crafting output, requires exactly one matcher entry, and rejects manual inventory submission. Existing inventory is not crafting evidence.
- `brnquest:xp` objectives use `value` and `points`: true means raw XP points, false means whole levels.
- New XP rewards use `brnquest:xp`, with `xp` as amount and `points=true` for points (the legacy default) or false for levels. Legacy `brnquest:xp_levels` rewards still use `xp_levels`, grant levels and remain editable/claimable; they are hidden from creation. FTB import preserves the source reward type.

## Exploration, observation and kills

| Type | Fields | Behavior |
| --- | --- | --- |
| `brnquest:dimension` | `dimension` | Dimension ID or BRNQuest `#group` |
| `brnquest:biome` | `biome` | Biome ID or native `#tag` at the player's block position |
| `brnquest:structure` | `structure` | Structure ID or native `#tag`; player must be inside an actual structure piece |
| `brnquest:location` | `dimension`, `ignore_dimension`, `position`, `size` | Axis-aligned block region |
| `brnquest:observe` | `kind`, `target`, `distance`, `duration` | Continuously look at one block/entity |
| `brnquest:kill_entity` | `target`, `count` | New eligible player-attributed kills |

All accept an optional `title`. Exploration checks eligible non-spectators every 10 ticks, reads loaded chunks only and never runs locate or generates chunks. Missing/empty selectors do not match. Recorded completion stays complete after leaving; a reset/repeat cycle can immediately complete again if the player remains at the location.

Location configuration example:

```json
{
  "dimension": "minecraft:overworld",
  "ignore_dimension": "false",
  "position": "100,64,-20",
  "size": "4,3,5"
}
```

`position` is the minimum block coordinate; all three sizes must be positive. This covers x=100…103, y=64…66, z=-20…-16. The upper endpoint is excluded. Ignoring dimension keeps the coordinate constraint.

Observation uses `kind=block|entity`, an ID or `#tag` target, distance >0 and ≤64 (default 8), and duration 0–1200 ticks (default 0). Blocks/entities obstruct sight; changing target, looking away, exceeding distance or encountering unloaded chunks interrupts observation. Fluids are not targets. Continuous time belongs to the individual player; disconnect, dimension/config changes, reload and reset clear transient timing. Only completed observation enters progress storage.

Kills default to `minecraft:zombie`, count 1 (positive long). Direct lethal player damage and player-owned projectile kills count once; pets, environment, cancelled deaths and historical kills do not. Shared quests add once to their ledger. Dependencies and sequential objectives still apply.

## Vanilla statistics

`brnquest:statistics` supports nine statistic categories with personal or team progress. Configure `stat_type`, `stat`, and `count` (1–2147483647). Optional `title` supports native formatting; `format=native` (default) uses the native units, while `format=raw` shows the integer count.

| Category | Object registry |
| --- | --- |
| `minecraft:custom` | Registered custom statistics, such as `minecraft:jump` |
| `minecraft:mined` | Blocks |
| `minecraft:crafted`, `minecraft:used`, `minecraft:broken`, `minecraft:picked_up`, `minecraft:dropped` | Items |
| `minecraft:killed`, `minecraft:killed_by` | Entity types |

Choose the category first, then use its server-backed object selector. Missing objects and category mismatches fail author validation. Registered mod objects in these categories are supported. Counts use vanilla raw units: distance is centimeters (100 = 1 meter), time is ticks (1200 = 1 minute), and damage is tenths (10 = 1 damage point).

| `mode` | Behavior |
| --- | --- |
| `total` (default) | Historical totals; shared teams retain the highest member value |
| `since_available` | New progress after the objective becomes executable; shared teams add individual member increments |

Dependencies and sequential objectives apply. Incremental baselines are established at unlock or at the objective's turn, independently of opening the book or tracking it. Sampling runs every 3 ticks, normally within 0.15 seconds at 20 TPS. Progress and baselines survive reconnection, reload and save/restart. A vanilla counter decrease preserves accrued incremental progress and starts future increases from the lower value. Switching teams or leaving and returning preserves existing contributions and excludes actions while away. All-member quests keep each member's counter separate and use the current roster, including offline members.

Reset starts a new incremental baseline; historical mode rereads the existing history and can satisfy again immediately. Repeat cycles also get fresh baselines. Changing the statistic category, target or mode resets an unfinished objective; changing title, format or threshold retains its counter. Completed quest history retains its receipt until reset. Missing runtime objects pause sampling while retaining state. BRNQuest never resets Minecraft statistics.

```json
{
  "stat_type": "minecraft:custom",
  "stat": "minecraft:jump",
  "count": "20",
  "mode": "since_available",
  "format": "native"
}
```

FTB Quests v13 `stat` and `ftbquests:stat` definitions map to `minecraft:custom`, `mode=total` and `format=raw`. Their SNBT `stat` and `value` become native `stat` and `count`. Original fields are retained under `ftb.*`; conflicting native field names remain separate from the imported meaning. A legacy custom-stat namespace fallback is enabled only on imported goals. Invalid IDs or thresholds produce `BQF-111`; authors can correct the native fields while retaining the original SNBT for review. Import reads quest definitions and does not migrate FTB player/team progress.

## Advancements and target groups

`brnquest:advancement` is both an objective and a reward:

| Field | Objective | Reward |
| --- | --- | --- |
| `advancement` | Vanilla ID or BRNQuest `#group` | Same |
| `criterion` | Blank for complete advancement, otherwise one criterion | Blank grants all criteria, otherwise one criterion |
| `mode` | `any` (default) or `all` | Groups grant every member |
| `title` | Optional | Optional |

Criterion selection uses a single advancement ID. Missing references fail validation/execution. Existing vanilla progress can satisfy an objective once eligible. Later revocation does not undo recorded BRNQuest completion. BRNQuest reset does not revoke vanilla progress, so another cycle can complete again.

Rewards validate the entire selection before granting to the claimant, including team-once rewards. Vanilla XP, recipes, functions and notifications may run; parents are not automatically granted. Already-awarded criteria count as no change and do not retrigger completion rewards.

Dimensions and advancements use BRNQuest groups, not dimension-type tags or generic advancement tags:

```text
data/<namespace>/brnquest/target_groups/dimension/<path>.json
data/<namespace>/brnquest/target_groups/advancement/<path>.json
```

```json
{
  "replace": false,
  "values": ["minecraft:overworld", "minecraft:the_nether"]
}
```

This dimension example can be referenced as `#example:explorable` if saved under the matching namespace/path. Advancement groups use advancement IDs instead. Nested `#group` references are supported; packs append in priority order unless `replace=true`. Missing/duplicate members, empty groups, cycles or depth over 32 are rejected. `/reload` refreshes groups. Read-only candidate lists allow inspecting resolved members without granting anything.

## Command rewards

`brnquest:command` executes one server command; use a vanilla function for multiple steps. The claimant is the source entity and `@s`, at their current position/dimension. No permanent OP is granted.

| Field | Meaning |
| --- | --- |
| `command` | Required single line, ≤32767 characters; leading slash accepted |
| `source_mode` | `explicit` (default) or `player` |
| `permission_level` | 0–4 in explicit mode, default 2 |
| `silent` | Suppress vanilla output, default false |
| `feedback` | Optional author-written claim message |
| `title` | Optional display title |

Examples: `give @s minecraft:paper 1`, `function example:rewards/intro`. Placeholders `{p}`, `{x}`, `{y}`, `{z}` resolve the player name and current block coordinates; JSON/SNBT braces retain their meaning. Unsupported FTB team/chapter/quest placeholders require author correction.

`serverconfig/brnquest-server.toml` sets `commandRewardPermissionLimit` (default 2). Explicit levels above the limit fail preflight; player mode uses the lower of current player permission and the server limit. Preflight checks syntax and permissions; execution results depend on current targets and the invoked command.

Attempts live under world `data/brnquest-command-rewards/`; execution intent is forced to disk before execution. Unknown or failed attempts stop automatic replay. Administrators inspect with permission 2, and acknowledge checked effects with permission 4:

```text
/brnquest command_reward status <player> "namespace:reward_id"
/brnquest command_reward acknowledge <player> "namespace:reward_id" <attempt UUID>
```

Acknowledgement consumes the attempt without executing the command again. There is no automatic retry. Full quest reset creates a new claim generation; objective-only reset preserves receipts. Keep journals with world backups. Arbitrary commands and world saves cannot be made one atomic transaction, so uncertain outcomes require review.

## Reward tables

`brnquest:reward_table` supports nested **all**, **random** and **choice** modes. Eligible leaves include items, XP, commands, advancements, native loot tables, custom acknowledgement and explicitly opted-in Java types. Script rewards without composition support cannot be leaves.

Use **Edit configuration → Add entry**. Entry actions reorder, duplicate, change type or delete. IDs survive reorder; copies receive new IDs. Child confirmation changes the parent draft, and only outer confirmation saves it. Cancelling the root discards all local edits. Unsupported tree versions remain read-only.

- **All:** grant entries in order.
- **Random:** 1–64 draws; optional replacement and empty weight. Positive entry weights are relative. Guaranteed entries run once first, outside the pool. Without replacement, picked entries leave the pool; the empty bucket remains. An entirely empty result consumes the claim.
- **Choice:** opens a server-frozen option list. Select and confirm explicitly. Closing/reconnecting keeps the same candidates, and confirming twice does not deliver twice. Any nested choice makes the entire root manual-only; claim-all and automatic claims skip it.

All candidates pass preflight. Decisions are saved before side effects; previews never draw. Repeated subtable draws create independent occurrences. Every required nested choice is resolved before delivery. Author edits cannot reroll saved attempts. Shared attempts bind their first claimant. Execution handles at most eight synchronous leaves per tick and pauses for pending work.

Limits: 64 KiB UTF-8 configuration, 256 nodes, 8 table levels, 1024 worst-case expanded leaves, 16384 expanded nodes, 4096 items per item leaf and 2 MiB per attempt. Weights allow 34 significant digits and normalized scale -308…324, with finite double values/sums. Empty all/choice tables fail; an entry-free random table needs positive empty weight. At 1024 unfinished attempts or 256 MiB journal storage, new attempts are refused without deleting evidence.

Journals live in world `data/brnquest-reward-tables/`. Interrupted or uncertain leaves never automatically replay. Resource changes can block unfinished attempts while retaining their saved data. Permission 2 inspects; permission 4 acknowledges or retries:

```text
/brnquest reward_table status <player> "<root reward ID>"
/brnquest reward_table acknowledge <player> "<root reward ID>" <attempt> "<occurrence>"
/brnquest reward_table retry <player> "<root reward ID>" <attempt> "<occurrence>"
```

Acknowledgement accepts a checked uncertain effect without repeating it, then permits unstarted leaves to continue. Only `FAILED_NO_EFFECT` allows explicit retry after fresh resource/adapter checks. Corrupt records and stale cycles remain blocked. New attempts use journal version 2; version-1 evidence retains its recovery semantics. Do not delete receipts to unblock rewards.

### Native loot tables

`brnquest:loot_table` uses `loot_table` (one resource ID) and optional `title`. Native files are `data/<namespace>/loot_table/<path>.json`. Generation uses the claimant's level, position, entity and luck, through NeoForge's native path including global loot modifiers. No victim, tool, damage source or block state is invented; missing required context rejects the table.

Stacks and components are saved before delivery; inventory overflow drops at the claimant. Empty results consume the claim. Limits are 1024 nonempty stacks, 4096 items and 512 KiB generated data, within the overall 2 MiB attempt budget. Saved outcomes survive reconnects and book edits. Repeated selected occurrences generate independently; unconfirmed choices do not generate. Recovery uses the same reward-table commands and uncertainty rules.

## Localization and descriptions

Add a table to native book JSON:

```json
{
  "title": "My Book",
  "localization": {
    "fallback_locale": "en_us",
    "translations": {
      "zh_cn": {
        "title": "我的任务书",
        "quest.example:first.title": "第一个任务",
        "quest.example:first.quest_subtitle": "准备出发",
        "quest.example:first.quest_desc": "# 起步\n\n第一行",
        "quest.example:first.quest_desc_format": "markdown_v1"
      }
    }
  }
}
```

Keys include `title`, `chapter_group.<ID>.title`, `chapter.<ID>.title`, and the quest keys above. Lookup tries requested locale → `fallback_locale` → native source. Missing/blank text falls back. Locale names normalize to lowercase/underscores. Description text and format always come from the same locale; native descriptions use `description_format`. Copies, saves, undo and publishing preserve both together.

Quest titles and subtitles always support native colors (`§0`–`§9`/`§a`–`§f`), bold (`§l`), italic (`§o`), underline (`§n`), strikethrough (`§m`), obfuscation (`§k`) and reset (`§r`), independently of the body format. Codes are case-insensitive; color codes clear previous decorations, and unknown or incomplete codes remain literal. For example, `§6§lStart§r` renders a gold bold title. Single-line fields allow 256 characters and bodies allow 32768, including formatting codes. Existing fields store the raw strings; quest details, node names, quest selectors, tracking HUD and notifications display the styles.

`plain` shows Markdown punctuation literally but honors vanilla `§` formatting. `markdown_v1` is a controlled subset: paragraphs, explicit breaks, headings 1–3, one-level `-`/`*` lists, bold, emphasis, inline code and absolute HTTP/HTTPS links. Ordered/nested lists, blockquotes, fenced code, HTML, remote images, commands and other URL schemes are unsupported. Unknown formats are preserved and rendered as plain text; author writes cannot create unknown formats.

Additional syntax (shown literally):

```text
[Quest](brnquest:quest/example:first)
[Underlined](brnquest:style/underline)
[Styled](brnquest:style/color/red+underline+strikethrough)
[RGB](brnquest:style/color/12abef)
[Obfuscated](brnquest:style/obfuscated)
![Texture](texture:example:textures/guide.png)
![Item](item:minecraft:stone)
```

Task links resolve full IDs in the current book and respect hidden-content rules. External links use Minecraft's confirmation screen. Colors accept vanilla names or six-digit RGB. Textures must already be loaded and items registered; no remote downloads or item SNBT/components are supported. Standalone images form blocks; inline images fit the text line. Missing resources retain placeholders. Item images support native tooltips/JEI; textures have no item interactions. Local textures are not distributed by publication.

## FTB import

FTB Quests v13 is a read-only import format. Rich-text behavior follows the pinned Quests v2101.1.34 / Library 2101.1.35 baseline. Review every diagnostic before publication:

- Visibility, dependency modes, sequential objectives, repeat/cooldown fields and supported XP types are mapped. `only_from_crafting` requires exactly one matcher entry.
- Observation maps target kind and timer, using distance 8. Kill maps `entityTypeTag` before `entity`; omitted FTB count defaults to 100. Unsupported block state/entity NBT/name filters retain source data and a blocking `ftb.encounter_error` marker. Remove it only after choosing replacement semantics.
- Location arrays become comma-separated coordinates/sizes and preserve source SNBT. Invalid shapes are rejected instead of approximated by a radius. Advancement criterion remains a criterion.
- Commands map permissions and silent mode. Unresolved `feedback_message` keys are retained as `ftb.feedback_message` with BQF-107; supply native `feedback`. Unsupported placeholders need correction.
- `all_table`, `random`, `loot`, `choice` and referenced tables become independent native reward-table snapshots. Cycles/missing references are diagnosed. Random ignores source empty weight; loot preserves it. Valid `loot_size` maps to draws with replacement and zero-weight entries become guaranteed. FTB loot is distinct from native `loot_table`.
- Each localized description is converted independently. Supported legacy colors/styles, safe JSON text/URLs, images and resolvable quest links become native Markdown. Unsupported actions are removed; visible text and non-equivalent originals remain in diagnostics/provenance (`ftb.rich_text_source.*`). Preserved source does not run at runtime.

Use a dry-run and inspect the imported draft in-game before replacing an existing deployment.
