# Reward tables

`brnquest:reward_table` supports nested `all`, `random` and `choice` tables: items, XP, XP levels, commands, advancements, native loot tables, built-in custom acknowledgement, and explicitly opted-in Java rewards. Unadapted scripts remain unavailable for composition.

Add a reward table and optionally set its **Display title** in the outer properties; leave it blank for the default name. Open **Edit configuration**, click **Add entry**, and select a type from the list. Both entries and property forms scroll with the mouse wheel. Click an entry to edit it, or right-click / use **More actions** to change type, reorder, duplicate or delete. Copies receive fresh stable IDs; reordering preserves IDs. Changing type starts with the new type's defaults; cancelling keeps the original entry. Child confirmation changes only the parent table draft; cancelling the table discards changes. After confirming, submit the outer reward properties and book draft. Ordinary edits preserve unknown fields; unsupported tree versions remain read-only.

Read-only previews and status queries never create claims. Claiming preflights every leaf before granting anything. Execution handles at most eight synchronous leaves per tick and pauses behind pending commands. Shared roots bind the first claimant; other members cannot take over. Reconnect and click again to resume an existing attempt. Server eligibility and normal root receipts remain authoritative.

Select **Random draws** at the top of the entry editor, then open **Draw settings** for 1–64 draws, replacement and empty-result weight. Use an entry's **More actions → Weight / guaranteed** to set a positive weight or grant it once. Guaranteed entries run first in list order and stay outside the random pool. Repeated picks create separate deliveries. Without replacement, selected entries leave the pool, but the empty bucket remains; if both run out, drawing stops. Switching back to **Grant all** retains but ignores random settings.

Every candidate passes preflight before selection. Decisions, including empty rolls, are stored before effects. Reconnects, repeated clicks and author edits cannot reroll existing attempts. Guaranteed-only and empty-only tables are valid; an entirely empty result consumes the claim and displays a completed-empty message. Previewing rules never draws rewards.

Select **Choose one** in the entry editor and use the **manual** root claim policy. Claiming opens a server-frozen option list. Click one option, then **Claim selected reward** to grant only that entry. Item counts are displayed; **Load more options** makes every candidate reachable. Closing or disconnecting suspends the attempt without granting a default option. Reopening restores the same candidates even after author edits. Once persisted, a confirmation cannot be changed; identical retries never replay the reward.

Choice ignores but retains random settings. Publishing an automatic policy for an interactive reward is rejected; claim-all skips it and explains that it needs an individual claim. FTB choice imports without random loot_size or positive-total-weight requirements. Nested tables remain unsupported.

Weights use exact decimal ratios converted to integer tickets, with at most 34 significant digits and a normalized decimal scale of -308..324. Values and their sum must remain finite doubles; entry weights must be positive and empty weight nonnegative. Limits are 64 KiB UTF-8, 256 total nodes including the root, and 4096 delivered items per item leaf. Empty all/choice tables are rejected; random tables without entries need positive empty weight. Attempt records are limited to 2 MiB. At 1024 unfinished attempts or 256 MiB of directory storage, new attempts are refused without deleting old evidence.

Forced records live in the world's `data/brnquest-reward-tables` directory. Commands also retain their command journal. A crash between an effect and its receipt can leave an uncertain outcome: this is not a general exactly-once transaction. Uncertain leaves stop and never replay automatically. Do not delete receipts to repair claims.

Permission level 2 inspects the player's current cycle:

```text
/brnquest reward_table status <player> "<root reward ID>"
```

After checking actual effects, permission level 4 can consume one uncertain leaf without repeating it and continue unstarted leaves:

```text
/brnquest reward_table acknowledge <player> "<root reward ID>" <attempt> "<occurrence>"
```

Only `FAILED_NO_EFFECT` permits explicit retry after fresh preparation and resource/adapter checks:

```text
/brnquest reward_table retry <player> "<root reward ID>" <attempt> "<occurrence>"
```

Changed resources, corrupt records and stale cycles remain blocked. Commands inspect the current cycle; older evidence remains on disk. Changed gameplay resources, including functions, block an old execution list; book edits do not replace its frozen entries.

FTB v13 `all_table`, `random`, `loot` and `choice` rewards import from external `reward_tables` or inline `table_data` as independent snapshots. Random ignores source empty weight; loot preserves it. Both map valid loot_size to draws with replacement and zero-weight entries to guaranteed rewards. Missing/invalid loot_size, non-positive or overflowing total weight, missing references, cycles and incompatible child policies produce diagnostics while preserving source fields. FTB loot is distinct from native Minecraft loot tables, which are not implemented yet.

Update both client and server to protocol 18. See [EXTENSION_API_zh.md](EXTENSION_API_zh.md) for the experimental API.

## Nesting and recovery

The entry type picker includes reward tables. Give each child its own title and open its entry editor; the hierarchy is shown above the list. Returning preserves the parent draft; only confirming the root submits changes. All, random and choice modes can be combined. Any configured choice makes the entire root manual-only.

Repeated draws of a subtable create independent occurrences. Confirm each pending choice path in sequence: no effects run until every required choice is resolved. Closing/reconnecting never selects a default or rerolls. New attempts use journal version 2; existing version 1 attempts retain their recovery behavior. Do not delete old receipts.

Limits are 8 table levels including the root, 256 configuration nodes, 1024 worst-case expanded leaves, 16384 expanded table/leaf nodes and the existing 2 MiB receipt budget. Exceeding a limit rejects the attempt without truncation. Repeated FTB references become independent snapshots; true cycles are rejected. Administrator status shows pending choice paths and leaf occurrences; acknowledgement/retry still targets one stopped leaf without replaying the package.

See [native loot table rewards](LOOT_TABLE_REWARDS.md) for leaf configuration and context limits.
