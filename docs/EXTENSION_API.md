# Task and reward extensions

[简体中文](EXTENSION_API_zh.md) | [Home](../README.md) | [Java API](API.md)

The contracts below remain experimental at `0.1.0-experimental.28`. Types own server behavior, configuration and optional client presentation. Native schema 1 passes configuration leaves as JSON strings to the type's Codec: parse numeric/boolean values from `Codec.STRING` with validation. Simple externally advanced objectives and event rewards can use [KubeJS](KUBEJS_API.md).

## Registration and ownership

Register `BrnQuestPlugin` through `BrnQuestPlugins.register` during construction/common setup. The callback stages task, reward, owner-provider and field-source declarations through `BrnQuestExtensionRegistrar`; all declarations are preflighted before committing the batch. IDs must use the plugin's namespace. Duplicate IDs, foreign namespaces and late registration fail without partial registration.

The plugin facade and common registries freeze before the first server resource-reload listener is established. Low-level registry methods remain experimental compatibility entry points; prefer the plugin facade. Register client presentation separately through `ClientTaskPresentationRegistry` / `ClientRewardPresentationRegistry` during client construction; they freeze at client setup. Keep client classes out of common code and optional integration classes isolated until the dependency is known to be loaded.

Complete IDs are independent: `example:item` inherits no behavior from `brnquest:item`. New types should integrate through these contracts without type-ID branches in core screens, networking or progress code. Built-in implementation classes are internal.

`TaskType.hiddenFromCreation()` and `RewardType.hiddenFromCreation()` default to false. True hides a type from main creation and reward-table leaf creation, while retaining existing definitions, editing, execution, import and validation. Built-in `item_choice` and `xp_levels` use this for compatibility. Lists currently sort by type ID; no plugin ordering contract has been introduced.

## Task execution and selection

`TaskType<TConfig>` defines the Codec, authoritative checks, manual submission, optional quest-completion intent, inventory reevaluation and consumption. `TaskContext` supplies the online player, book/quest IDs, immutable task view and current numeric progress. The core owns progress storage. Registered instances are reused across players and reloads; do not store per-objective progress in instance fields.

`submit(context, config, selection)` runs under the server owner lock. `TaskSubmissionSelection` contains at most 36 ordered, unique main-inventory indices (0–35); empty means automatic selection. Recheck live inventory, never trust client item copies. The default overload delegates to the older two-argument method. On refusal or runtime exception the core restores its main-inventory snapshot; this does not roll back other containers, XP, world changes or external IO. Successful submission is recorded before further duplicate requests can execute the objective.

Client `submissionInteraction(context)` may return a `TaskSubmissionInteraction` factory. Create its page on the client thread; use the supplied callback at most once, after confirmation, and return to the parent when closing. Do not submit while constructing the page. The core validates current page, world/player, revision and pending state. Returning empty keeps direct submission. `candidateScreen(parent, context)` is read-only and has no submission callback; its default uses `resolvedOptions`.

Use `BrnQuestApi.submitTaskResult` for objective submission results. The older `completeTaskResult` reports whole-quest completion and may return unsatisfied after recording a partial objective.

### Passive and event progress

- `pollingIntervalTicks()` defaults to 0 (disabled). Positive intervals call `sampledProgress(context, config)` on the server thread under the owner lock. Return total progress, not a delta. Keep it cheap/read-only; do not load chunks, run locate or call write APIs.
- `craftedProgress(context, config, crafted)` receives a copy of real server crafting output. Return cumulative progress without side effects; the default returns existing progress.
- The core filters locked/out-of-sequence objectives, stores only increases, determines completion and synchronizes. Inventory reevaluation uses dirty events and a 20-tick schedule; arbitrary direct inventory mutations by another mod are not automatically detected.
- `resetTransientState(context)` clears temporary state after explicit resets for online owner members. It must not write the ledger. Types manage logout/reload caches themselves. New repeat cycles follow normal core reset rules.

## Editor configuration

`configFields()` returns `ConfigFieldDescriptor` entries: stable key, type, defaults, required/range/enum rules, registry hints, help and optional validator. An empty list opts into raw configuration. Descriptions guide forms; the server Codec and full book validation remain authoritative. Unknown map entries survive editing.

Use `withLabel(key)` and `withValueLabels(Map.of(rawValue, key))` with your own language resources. `helpText` accepts a translation key or literal fallback. Translation changes only presentation; raw keys/values remain stable. `INTEGER_VECTOR3` edits three integers with per-axis bounds. Built-in matcher widgets are internal implementation; custom matching still needs its own Codec and server execution checks.

`normalizeConfig(ConfigNormalizationContext, config)` runs for author insert/update/copy, including quest/chapter/clipboard copies and server reward-leaf preflight. The default calls the legacy map-only method once. Return only owned fields: the caller merges them into the original map so opaque keys survive. Keep normalization pure and repeatable; invalid input may throw `IllegalArgumentException`. Failure commits no partial candidate or undo entry. Undo/redo restore snapshots without normalization.

`context.registries()` is an optional current read-only lookup. Server author mutations supply it; offline helpers may not. Preserve unresolved resource values when no lookup exists, and never cache a lookup across reloads. Client preflight is advisory. Core copying remaps object IDs/dependencies; private config references remain unchanged, with no public config-remap SPI.

`TaskType.creationConfig(config, defaults)` is a pure creation-only hint hook, also used for client prefilling. Current hints include `consume_items`; consume only hints your type understands and preserve explicit fields. Updates/copies do not reapply creation defaults.

### Server-backed fields and child editors

Register sources with `registrar.fieldSource(id, source)` and point fields to `withServerSource(id)`. `Source.query(player, filter, selected)` returns entries and counts in `Result`; `current` can supply a current value and `detail` describes errors. Nonempty error codes map to `screen.brnquest.field.error.<code>`.

The default `queryPage` slices a complete result into 64-item pages. Large sources should override paging and produce only the requested page, with truthful totals. The context-aware overload receives unsaved form fields as query hints; revalidate IDs and permissions. `previewSource` opens another registered source as a read-only member preview, never writing back to the parent. All queries require author permission 2 and remain subject to transport bounds.

`ClientConfigEditors.register(typeId, fieldKey, factory)` creates child screens. Commit only on confirmation. The original single-value factory stays compatible; the contextual overload receives immutable config and returns a field patch which the author service merges. `label(value)`/`icon(value)` describe the field button. `registerCreation(reward, type, factory)` supplies a type-specific creation page; core author permissions/revisions/validation still apply. Register these only in client code.

## Client presentation

Task/reward presentations use immutable views and copied item stacks. Missing presentation falls back to a placeholder/full type ID and no assumed interaction. These hooks never authorize progress or rewards:

| Hook | Contract |
| --- | --- |
| `interactive`, `acceptsQuestCompletionIntent`, `readyForSubmission` | Local click/readiness hints; server rechecks |
| `objectiveTitle`, `progressText`, `confirmed` | Objective/HUD meaning; confirmed interprets stored server progress |
| `displayedItem`, `acceptedItems`, `hasCandidateMenu` | Display stacks, tooltips and optional JEI/candidate access |
| `resolvedOptions` | Complete localized member list; optional SHOW_TEXT hover carries raw IDs |
| `icon(view)` | Draws the icon through `EditorIcon` |
| `contentSummary(reward)` | Reward contents only; shared layout adds configured title; no claim queries |
| `titleDecoration`, `requiredCount` | Optional task title styling and display count |

`typeIcon()` has no instance parameter. It may return an `EditorIcon`; alternatively supply a static icon at registration:

```java
// Register from client-only initialization; the sprite belongs to this add-on.
ClientTaskPresentationRegistry.register(typeId, presentation,
    EditorIcon.sprite(ResourceLocation.parse("myaddon:quest_types/example")));
```

The sprite lives at `assets/myaddon/textures/gui/sprites/quest_types/example.png` (16×16 transparent PNG recommended). Resolution is presentation icon → registration icon → generic fallback. Unknown external types never borrow built-in icons by path. Missing supplied sprites use the atlas missing sprite; do not read files per frame.

Reward `refresh(reward)` runs on client ticks for the selected completed quest and may perform rate-limited read-only queries. `prepareClaim(revision, reward)` prepares an expected type-specific response. Server `clientClaimResponse(player, reward, result)` runs after a revision-matched network claim; it may send a choice screen response, but must not execute again or mutate receipts. Direct Java API claims do not trigger this network-response hook.

## Reward claims and composition

`RewardContext` contains player, IDs and an immutable reward view. The default execute path marks the ordinary receipt and requests a save before executing; a save request is not a forced disk commit. Types must not edit core receipts themselves.

`claimHandler()` optionally supplies `RewardClaimHandler`, called after completion/member/repeat checks on the server thread under the owner lock. It receives `RewardClaimContext` with owner, completion cycle and `claimGeneration`. `SUCCESS` commits the normal receipt/event; `PENDING` leaves it unclaimed and reports success without change; `FAILURE` leaves it unclaimed and reports failure. Exceptions report `CLAIM_HANDLER_FAILED`.

Handlers own preflight, reentrancy, persistence and recovery for side effects. Non-idempotent effects need durable evidence. Include owner, reward identity, cycle, claim generation and the ordinary reward's recipient in deduplication identity. Full resets create a new generation; old saves retain the empty generation, which must not be randomized on read. Asynchronous completion must return to the server thread, recheck identity and re-enter `claimRewardResult`, reporting the saved outcome without replay.

`requiresManualClaim(config)` is a pure policy declaration. True requires individual manual claiming; automatic and claim-all skip the type and incompatible policies fail publication. The type still owns its choice UI/protocol.

Only `composition()` opt-in types can be table leaves:

1. `validateConfig` performs pure configuration checks.
2. `prepare` preflights resources/permissions/quantities and returns persistable string data, with no effects or random draws; it can run repeatedly.
3. `freeze` fixes data for the selected occurrence once; default delegates to prepare. The coordinator forces that data to disk before effects.
4. `execute` receives root context, stable logical path and unique occurrence after forced STARTED evidence. Do not call top-level claims recursively or create leaf receipts in the normal ledger.
5. PENDING pauses later leaves. `recover` needs verifiable evidence; its default is unknown. Never implement recovery by blindly replaying execute. Change adapter `version()` when persisted data/recovery semantics become incompatible.

Recovery must check both the effect and its receipt; uncertain outcomes stop for review. Built-in custom records an acknowledgement. See [content recovery commands](CONTENT_REFERENCE.md).

## Example add-on and integration checks

[`src/exampleAddon`](../src/exampleAddon) is a separately compiled real NeoForge mod, `brnquest_example`, included in development runs unless `-PexcludeExampleAddon` is set. It is a development-only example using the public API.

Examples include tag-sampled `marker`, individually submitted `signal`, quest-intent `checkmark`, independent slot-selecting/crafting `item`, XP `experience`, and pending/refused/successful `guarded_tag`. The latter demonstrates idempotent tag insertion. The `player_tags` source demonstrates server-backed fields. `brnquest_example:reward_experience` is a function granting 4 XP to its executor.

Useful focused verification: `compileExampleAddonJava`, relevant contract/unit tests, then only affected server/client scenarios. Check namespaced registration, Codec rejection, unknown-field preservation, repeat requests, failure/recovery, dedicated-server isolation and missing-client-presentation fallback. New UI still needs actual client observation.
