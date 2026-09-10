# Native loot table rewards

Choose **Native loot table** (`brnquest:loot_table`) in the reward picker. Enter one `loot_table` ID, such as `minecraft:chests/simple_dungeon`, or select a loaded table from the server-backed picker. `title` is optional. Native datapacks use `data/<namespace>/loot_table/<path>.json`. FTB random/loot rewards remain imported as `brnquest:reward_table`; they are not native loot table references.

Each claim generates once using the claimant's level, position, entity and luck. No victim, damage source, tool or block state is invented. Tables or references requiring unavailable context are rejected. Compatible chest/custom tables and valid empty tables are supported, not every loot context. Generation uses the native NeoForge path, including global loot modifiers.

There is no separate details button. Without a custom title, the entry summary shows the table ID; rules remain in the editor field help. No preview roll is performed. Generated stacks and components are persisted before inventory delivery, with the same overflow drops as item rewards. Empty results consume the claim. Each generation is limited to 1024 nonempty stacks, 4096 items and 512 KiB of serialized data; the combined attempt remains limited to 2 MiB. Oversized results are not delivered.

Loot rewards also work inside all/random/choice tables. Repeated selections generate independent results; unconfirmed choice branches do not generate. All required choices finish before delivery. Editing the quest book, closing the screen or reconnecting cannot replace saved results. Resource changes block unfinished attempts while retaining their original results, instead of rerolling.

An interrupted delivery without durable success evidence requires review and is never automatically repeated. Use `/brnquest reward_table status <player> <reward>` and the existing permission-level-4 acknowledgement/retry commands. Retry is restricted to failures known to have had no effects; acknowledgement accepts existing effects and does not redeliver uncertain items. See [reward-table recovery](REWARD_TABLES.md).
