# Public API migration

## experimental.24 to experimental.25

`TaskSubmissionSelection` and the three-argument `TaskType.submit` are now experimental public contracts. Selections contain at most 36 unique, ordered main-inventory indices (0–35); an empty selection means automatic submission. Types must validate live server inventory. The default overload still delegates to the existing two-argument method.

`ClientTaskPresentation.submissionInteraction` optionally supplies a `TaskSubmissionInteraction` screen factory. The default remains direct submission. Use the supplied callback once on the client thread, and return to the parent on close. The core checks the active screen, player, world, revision and pending request. `candidateScreen` supplies a read-only page and defaults to the existing resolved-options page. Existing implementations inherit both defaults.

`TaskType.craftedProgress` receives a defensive copy of actual server crafting output and returns cumulative progress; its default returns the previous value. Implementations must be side-effect free. The core filters current objectives, locks the owner, records monotonic progress and evaluates completion. Existing reset, repeat and reload lifecycle rules apply.

Use `BrnQuestApi.submitTaskResult` when the caller needs a row submission receipt: a committed row returns `SUCCESS/TASK_SUBMITTED` even while sibling objectives remain incomplete; retransmission returns no change. Existing `completeTaskResult` and KubeJS wrappers retain whole-quest results, including `UNSATISFIED` after a row was committed. Book schema, network fields and installation remain unchanged.

## experimental.23 to experimental.24

`TaskType` and `RewardType` add a compatible default overload:

```java
default Map<String, String> normalizeConfig(ConfigNormalizationContext context,
                                          Map<String, String> config)
```

The default invokes the existing `normalizeConfig(Map)` once. Existing implementations need no changes. `context.registries()` is an optional read-only `HolderLookup.Provider`. Authoritative draft writes and reward-table leaf preflight receive the current server lookup. Pure/offline helpers have no lookup; preserve resource values you cannot resolve there. Client leaf validation is advisory and is repeated by the server.

Normalization covers insertion, update, individual copy, whole-quest copy, clipboard paste and chapter copy. Return changes to fields your type owns; the caller merges them over the original map, retaining opaque fields. Keep normalization pure and repeatable, do not retain a lookup across reloads, and throw `IllegalArgumentException` for invalid input. Failed author operations do not commit a partial candidate or consume undo history. Undo/redo restore snapshots without invoking normalization again.

Built-in `item` and `item_choice` now use this same SPI for legacy fields, item components and tags. The transport no longer branches on those type IDs. Copy still remaps core object identities and dependencies; private config strings remain unchanged. No config-reference remapping SPI is introduced in this version. Native book schema, network protocol and installation layout are unchanged.

## experimental.22 to experimental.23

`ClientRewardPresentation` adds an optional default method:

```java
default Optional<Component> contentSummary(RewardView reward)
```

Return a description of the configured contents, such as an experience unit and amount. The shared reward layout adds the configured `title`; omit that title from this method. The method is used by live reward rows, author previews and frozen reward choices. It must not query claim state or send requests.

The default returns `Optional.empty()`, preserving the existing item-count and title fallback. Existing implementations do not need to add a method. A compatibility fixture compiles a consumer against the experimental.22 interface and loads that consumer with the current interface. This check covers this default-method addition, not every historical add-on.

Content summaries do not change icons, item lookup, permissions or server reward delivery. The native book schema, network protocol and installation layout are unchanged.

The example add-on's `brnquest_example:experience` implements the new summary using its `amount` setting. Its new `brnquest_example:checkmark` objective demonstrates both individual submission and whole-quest completion intent using the existing public task interfaces. The `signal` example retains individual-only submission.

See the [Chinese migration policy](API_VERSIONING_zh.md) and [example add-on guide](EXAMPLE_ADDON_zh.md) for related contracts.
