# Changelog

## Unreleased

- Created the Minecraft 1.21.1 NeoForge and Java 21 development scaffold.
- Added client, dedicated-server, GameTest, and data-generation run configurations.
- Established loader metadata, UTF-8 resources, licensing, and version-specific worktree conventions.
- Stabilized stage-2 task and reward extension boundaries with full type IDs, typed config decoding, and frozen registration windows.
- Added client task/reward presentation registries so new types no longer require built-in UI type branches.
- Added deliberate unknown task and reward types to the native acceptance workspace for placeholder and diagnostic regression testing.
- Reconciled and fully synchronized online player progress after BRNQuest and workspace reloads.
- Preserved partially uninserted item rewards by dropping the remaining stack when inventory space is exhausted.
- Made consuming task submissions idempotent, guarded duplicate UI clicks, and immediately synchronized inventory counts after submission.
