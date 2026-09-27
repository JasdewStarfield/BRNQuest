# Java API

[简体中文](API_zh.md) | [Home](../README.md) | [Type extensions](EXTENSION_API.md) | [KubeJS](KUBEJS_API.md)

## Versions and supported surface

The mod release is **1.0.0**. The Java API baseline remains **0.1.0-experimental.28**; network protocol is **21**. Book schema 1 and progress schema 2 are separate formats. Update clients and server together.

`api.ApiStatus` and the documented surface define compatibility. `STABLE` contracts preserve source/binary compatibility within their API major; incompatible changes require a new major and migration guidance. `EXPERIMENTAL` contracts may change with an updated baseline, documentation and signature checks. `INTERNAL` marks implementation details for internal use.

Base package: `yourscraft.jasdewstarfield.brnquest`.

| Experimental surface | Purpose |
| --- | --- |
| `api.BrnQuestApi`, immutable `*View` types | Queries and authoritative operations |
| `api.OperationContext`, `OperationResult`, `OperationStatus` | Authority, stable outcomes and audit source |
| `api.AuthorApi` and types explicitly exposed by its signatures | Draft sessions, edits, publication and recovery |
| `extension.BrnQuestPlugin`, `BrnQuestPlugins`, registrar | Atomic common registration |
| `task.TaskType`, registry, context and submission selection | Server objectives |
| `reward.RewardType`, registry, context, claim/composition contracts | Server rewards |
| `owner.ProgressOwner*` | Stable identities, member snapshots and provider registration |
| `event.BrnQuestEvents`, events and subscriptions | Read-only post-commit observations |
| `editor.Config*`, `ServerFieldSources` | Field descriptions, normalization and server-backed queries |
| `client.ui` presentation, submission interaction, config editor contracts; `component.EditorIcon` | Client-only type UI |
| `runtime.ExtensionRegistrationLifecycle.RegistrationState` | Read-only lifecycle diagnostics |

The public API consists of the contracts listed above. `data`, `progress`, `network`, `workspace`, `compat`, `command`, `platform`, built-in implementations and runtime coordinators are internal. Author types are public only where explicitly exposed by `AuthorApi`. Do not hold or mutate `PlayerProgress`, `QuestProgressData`, `ProgressEngine` or `QuestBookManager`. Use the [API source](../src/main/java/yourscraft/jasdewstarfield/brnquest/api) for exact signatures; the [contract hash](../src/test/resources/contracts/public-api-signatures.txt) guards compiled signature changes.

## Queries and views

`BrnQuestApi` supplies `getActiveBook`, chapter-group/chapter/quest collection and single-ID queries, `getTask`, `getReward`, `getProgressOwner` and `getProgress`. Views and nested collections/maps are immutable snapshots. They retain author order, complete type IDs, unknown configuration and legacy aliases. Invalid/unknown IDs return empty results.

Locale overloads for book/group/chapter/quest queries resolve requested language → book fallback → native source. Queries without locale retain source values. `getTranslations()` and `resolveText(locale, key, fallback)` support additional keys. `QuestView.description()` and `descriptionFormat()` come from the same locale; unknown formats must render as plain text. Language selection never changes the book revision.

`QuestView.behavior()` exposes dependency modes as stable strings (`all_completed`, `one_completed`, `all_started`, `one_started`) and includes `requireAllTeamMembers()`. Definitions describe behavior, while server state decides eligibility. `ChapterView.autofocusQuestId()` is nullable. Book/chapter templates supply defaults when creating quests.

Owner/progress queries require the player's server thread and return empty when the player/server/owner cannot be safely resolved. A `ProgressOwnerId` combines provider ID and stable UUID. Personal and OPAC ledgers remain separate; third-party providers do not become selected merely by registering. `ProgressView` identifies its owner and projects the querying player's receipts/tracking. All-member quests show that player's objectives and may report `WAITING_FOR_TEAM` while shared completion remains pending.

`getRewardClaimState(player, rewardId)` returns a read-only context (owner, cycle, claim generation), revision and advisory `eligible` flag. Eligibility does not mean an unclaimed receipt. Missing rewards, inactive owners and wrong-thread calls return empty. Requery and use the normal claim API before acting. Claims are authorized against current server state.

## Writes, authority and outcomes

Call on the online player's server thread; the API rejects wrong-thread writes instead of scheduling them. Use an explicit `OperationContext`: `self(player)`, a permission-checked `administrator(source)`, `integration(resourceId)`, or a declared system source. Self authority only targets the same UUID. Legacy wrappers use audited `brnquest:legacy_java_api`; new integrations should identify themselves explicitly.

| Result | Meaning |
| --- | --- |
| `SUCCESS` | State changed |
| `NO_CHANGE` | Valid idempotent request; already satisfied |
| `REJECTED` | Current state, resources, dependencies or type reject the action |
| `INVALID_REQUEST` | Invalid arguments or thread |
| `NOT_READY` | No connected player or active book |
| `FORBIDDEN` | Insufficient authority |
| `STALE_REVISION` | Outdated definition revision |

`success()` is true for SUCCESS and NO_CHANGE; `changed()` only for SUCCESS. Branch on status and stable `code`, not display messages. Audits record actor, source, action, target, object and outcome.

Structured entry points: `completeQuestResult`, `completeTaskResult`, `submitTaskResult`, `addTaskProgressResult`, `claimRewardResult`, `claimAllRewardsResult`, `toggleTrackedResult`, `openQuestScreenResult`.

- `submitTaskResult` reports individual objective submission (`SUCCESS/TASK_SUBMITTED`) even if other objectives remain unfinished. Duplicate submissions return NO_CHANGE.
- Legacy `completeTaskResult` reports whole-quest completion and may return `UNSATISFIED` after recording the selected objective.
- `claimAllRewardsResult` runs individual claim transactions. Earlier successful rewards remain delivered if a later one fails; `PARTIAL_FAILURE` reports that boundary.
- Slot selections describe the player's requested inventory slots; the server type checks current inventory, progress and eligibility.

## Author API and recovery

`AuthorApi` uses the online actor, server thread and permission level 2 for every operation. Session operations carry session ID, book ID and expected draft revision. Use each returned revision for the next request.

Typical flow:

1. `createEmpty`, `createFromActive`, `createFromWorkspace` or `importFtbDraft` creates a draft; `catalog` lists server drafts.
2. `open` acquires the session. `editor()` returns `DraftEditService` for stable-ID-based edits.
3. `validate`, `previewPublish` and `diff(..., WORKSPACE)` check the candidate without publishing.
4. `save` writes the draft; `publish` updates only the workspace.
5. `deploy(player, false)` performs first deployment; use true explicitly to replace it.
6. Await successful `reload(player)` before treating the new active book as committed.
7. `close` releases the session; `renew` keeps it alive during command-driven work.

Stop on failure. Import dry-run only reports; actual import creates an `IMPORT` draft and rejects an existing draft ID. It does not deploy or activate content. Commands and the GUI use the same authority boundary; see the [author guide](AUTHOR_GUIDE.md).

Recovery is `backups` → `previewRestore` → `restore(..., expectedCurrentRevision)`. Only server-issued relative backup IDs are accepted. Changed targets fail with `RESTORE_TARGET_CHANGED`; the overwritten target is backed up. Close relevant edit sessions first. Restoration changes one disk layer and never implicitly reloads.

Useful author codes: `STALE_DRAFT_REVISION`, `REVISION_CONFLICT`, `WORKSPACE_CHANGED`, `UNSAVED_DRAFT`, `RESTORE_TARGET_CHANGED`, `DRAFT_EXISTS`, `RELOAD_VALIDATION_FAILED`. `AuthorOperationResult.message` provides display text; program logic uses status and result codes. Audit write failure is logged without undoing a committed content transaction.

## Events and reload lifecycle

Subscribe with `BrnQuestEvents.subscribe` to `QuestCompletedEvent`, `TaskProgressChangedEvent`, `RewardClaimedEvent`, `QuestBookReloadedEvent` or `ProgressOwnerChangedEvent`. Events are immutable, non-cancellable and post-commit. Listener failures are logged independently; they neither undo the committed transaction nor prevent later listeners. Close `EventSubscription` when no longer needed.

Owner-change events follow server reconciliation. Initial login establishes identity without a change event; roster changes with the same party UUID retain the current owner. Always requery for authorization.

Java registration freezes before the first server resource-reload listener is established. Client presentation freezes at client setup. KubeJS builds a temporary candidate during server-script evaluation; only successful script and book validation replace the active script types and book together. Failed candidates retain the previous revision. Successful reload publishes events, reconciles online progress and synchronizes players. Cache IDs, not internal definitions or views as current state across reloads.

## Compatibility notes for add-ons

Mod 1.0.0 changes no Java signatures relative to experimental.28. Recent API transitions:

| Baseline | Changes to review |
| --- | --- |
| experimental.23 | Optional reward `contentSummary`; shared layout owns the title |
| experimental.24 | Context-aware `normalizeConfig`; preserve unknown fields and absent registry context |
| experimental.25 | Slot-selection submission, candidate screens, crafting progress and `submitTaskResult` |
| experimental.26 | Type-owned title/claim UI hooks, config patches/creation screens, read-only claim-state query |
| experimental.27 | All-member completion projection and team-sharing setting |
| experimental.28 | Default `hiddenFromCreation()` on task/reward types |

Default methods and retained constructors preserve the documented older call paths. Consumers relying on record-component reflection, generated equality or deconstruction must review added components. Use the current contracts and compile the example add-on when upgrading.
