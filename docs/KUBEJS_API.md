# KubeJS server scripting API

[简体中文](KUBEJS_API_zh.md) | [Home](../README.md) | [Java API](API.md)

With compatible KubeJS and Rhino installed, BRNQuest exposes `BRNQuest` in `server_scripts` only. Progress writes run on Minecraft's server thread through BRNQuest transactions. Development baselines: Minecraft 1.21.1, NeoForge 21.1.216, KubeJS 2101.7.2-build.374 and Rhino 2101.2.8-build.91. The optional KubeJS loader range is `[2101.7.0-build.126,2102)`, independent of the development baseline; the lower-bound API passes compilation. Use a Rhino version accepted by your KubeJS release. This scripting API remains experimental.

## Observation events

`BRNQuestEvents` events are non-cancellable server observations after authoritative state is committed. Script exceptions are reported by KubeJS and do not roll back completed transactions.

| Event | Optional target | Main fields |
| --- | --- | --- |
| `questCompleted` | Quest ID | `player`, `playerId`, `playerName`, `bookId`, `questId`, `quest`, `progress` |
| `taskProgressChanged` | Task ID | Player/book/quest fields, `taskId`, `task`, `previousValue`, `currentValue`, `delta` |
| `rewardClaimed` | Reward ID | Player/book/quest fields, `rewardId`, `reward`, `progress` |

```js
// Observe committed completions across the book.
BRNQuestEvents.questCompleted(event => {
  console.info(`${event.playerName} completed ${event.questId}`)
})

// A targeted observer uses the complete namespaced ID.
BRNQuestEvents.taskProgressChanged('example:radio_signal', event => {
  console.info(`${event.previousValue} -> ${event.currentValue}`)
})
```

Views are read-only snapshots. Events are not forwarded for an offline player.

## Script task and reward types

Declare types at top level during `server_scripts` loading:

```js
// Declare IDs during script loading, not from a later gameplay callback.
BRNQuest.registerTaskType('example:radio_signal')
BRNQuest.registerRewardType('example:play_dialogue')
```

Use full IDs in your own namespace; `minecraft` and `brnquest` are reserved. Duplicate declarations and overriding Java/built-in types are rejected. Each load stages a candidate batch; activation requires successful script and book validation. `getScriptExtensions()` returns active `taskTypeIds`, `rewardTypeIds` and diagnostic `registrationOpen`.

Script objectives accept `required_progress` (positive integer string, default 1) and optional `title` (otherwise full type ID). Advance them through `BRNQuest.addTaskProgress(player, taskId, amount)` on the server.

Script rewards accept arbitrary string configuration, conventionally `title`. Effects use a required type-targeted `customReward` listener:

```js
BRNQuestEvents.customReward('example:play_dialogue', event => {
  // Use this key in your persistent deduplication store before external effects.
  const once = event.idempotencyKey
  console.info(`reward ${event.rewardId}: ${once}`)
})
```

The event also exposes `player`, `bookId`, `questId`, `rewardId`, `typeId` and read-only `config`. `idempotencyKey` derives from stable owner, reward ID and persisted completion time; ordinary shared rewards also include claimant UUID. Use the supplied key rather than just player/reward ID, so later repeat cycles remain distinct. A missing matching listener makes reward execution fail explicitly.

## Reload and failure recovery

1. KubeJS evaluates server scripts while BRNQuest collects candidate types.
2. Zero script errors seal the candidate without replacing active types.
3. Candidate book decoding uses candidate types on the validation thread, with Codec and whole-book checks.
4. Success switches script types and the book pointer together, then emits reload events, reconciles progress and synchronizes players.

Script errors retain KubeJS file/line diagnostics and produce fatal BRNQuest diagnostic `BQV-006`. Script or book failure discards the candidate and retains the old book revision/resource key and active type registry. Reload interrupted before BRNQuest application also retains active state; the next attempt replaces unfinished candidates.

KubeJS unloads its own JavaScript listeners during reload. After a failed reload, old books/types remain queryable, but custom rewards without a current listener reject execution. Store stable IDs across ticks/reloads; do not cache views or event objects as current state.

## Queries

| Method | Result |
| --- | --- |
| `getActiveBook()` | Book object, or null when unavailable |
| `getQuest(id)` | Quest object, or null for an invalid/missing ID |
| `getQuest(id, locale)` | Localized title/subtitle/description using book fallback |
| `getProgress(player, questId)` | Current-owner progress, or null when unavailable |
| `isQuestCompleted(player, questId)` | True for completed or claimed quests |

IDs/enums are strings. Nested values contain primitives and read-only maps/lists. Each call reads the current active book.

## Writes

| Method | Meaning |
| --- | --- |
| `completeQuest(player, questId)` | Integration-level force completion, bypassing normal objectives |
| `submitQuest(player, questId[, checkmarkIntent])` | Attempt ordinary completion |
| `completeTask(player, questId, taskId)` | Submit an objective with deterministic automatic selection |
| `addTaskProgress(player, taskId, amount)` | Add positive progress to an external objective |
| `claimReward(player, rewardId)` | Idempotent individual claim |
| `claimAllRewards(player, questId)` | Individual claim transactions for eligible rewards |
| `toggleTracked(player, questId)` | Toggle this player's tracking |
| `openQuest(player[, questId])` | Synchronize authoritative state and request the client screen |

Writes use integration source `brnquest:kubejs`; scripts cannot manufacture administrator/system authority or invoke reset APIs. Missing/offline players, wrong threads, unavailable books and invalid IDs return structured outcomes:

```js
{
  status: 'SUCCESS',
  code: 'OK',
  message: '...',
  success: true,
  changed: true
}
```

NO_CHANGE keeps `success=true` and `changed=false`. Use status/code rather than parsing messages. `completeTask` retains the legacy whole-quest outcome: another unfinished objective can yield an unsatisfied result after this objective was recorded. Claim-all does not roll back earlier successful claims after a later failure.

```js
PlayerEvents.loggedIn(event => {
  // Ordinary submission still checks the quest's real requirements.
  const quest = BRNQuest.getQuest('example:repair_radio')
  if (quest === null) return
  const result = BRNQuest.submitQuest(event.player, quest.id)
  if (!result.success) console.warn(`BRNQuest submit failed: ${result.code}`)
})
```

```js
// Force completion only when your integration intentionally bypasses objectives.
const result = BRNQuest.completeQuest(player, 'example:heard_broadcast')
if (result.changed) console.info('Quest completed by integration')
```
