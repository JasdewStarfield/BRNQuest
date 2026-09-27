# Author guide

[简体中文](AUTHOR_GUIDE_zh.md) | [Home](../README.md)

## Playing and editing

Press **J** to open the book. Select a chapter and quest, inspect objectives and rewards, and track a quest on the HUD. Item submission lets players choose inventory slots; the server checks actual inventory again. Pending requests suppress duplicate clicks. Reward choices require explicit confirmation; reopening resumes the same saved attempt.

Authors need permission level 2. Chapter and canvas context menus provide creation and property actions. Book properties contain the title, fallback language and creation defaults; chapter properties add grouping, ordering and autofocus. The gear opens local preferences for scrolling, snapping, zoom, motion and panel widths. These preferences do not change book data.

| Action | Effect |
| --- | --- |
| Apply a child picker/editor | Updates its parent form; cancelling the parent still discards it |
| Confirm normal book editing | Saves and activates the server-accepted edit in the current world |
| Save an advanced draft | Saves a separate draft; gameplay continues using the active book |
| Publish and apply a draft in-game | Reviews, publishes, deploys and reloads; activation follows successful completion |

The editor may show pending changes while waiting for the server. Check the status bar for confirmation. Rejected edits restore previous data and show an error. If publication reports a revision conflict, obtain a fresh review.

### Input and copying

- Tab/Shift+Tab move focus, arrows browse pickers, Enter activates, and Esc returns or cancels. Item submission uses F6 to switch focus groups and Space to select slots.
- Hold and drag nodes to move them. Ctrl-click selects multiple quests/decorations; group moves preserve relative positions. Locked decorations are skipped during movement and deletion.
- Ctrl+C/Ctrl+V copy and paste canvas selections; text fields keep ordinary text shortcuts. Context-menu paste uses the clicked location; keyboard paste uses the visible canvas center.
- Chapter copying creates fresh quest/task/reward IDs and remaps internal dependencies and autofocus. External dependencies and opaque configuration references remain unchanged. Player progress is not copied.
- Configuration and canvas clipboards are scoped to the current process, world/server and book, with a 64 KiB snapshot limit. Missing external dependency targets reject the entire paste. Batch edits use one undo step.
- Creation templates resolve core → book → chapter → explicit values once. Existing, moved and copied entries keep their effective values. Blank/default values inherit; explicit zero/off overrides.

Book settings also control automatic-claim suppression and single-player UI pause. Suppression retains reward policies and exposes hidden automatic rewards for manual claiming. Multiplayer never pauses. Dependency-line visibility and chapter autofocus affect navigation/display only.

## Artwork and translated text

Use **Browse loaded textures** for icons, decorations and backgrounds. Books store `namespace:textures/path.png` resource IDs. Other clients need the same resource pack. Canvas backgrounds follow pan/zoom; screen backgrounds follow the window. Both support tile/contain/cover, opacity and scale. Confirm the child background page and then the outer properties form.

**Import local PNG** and drag/drop support static PNGs up to 8 MiB and 4096 pixels per dimension. Images receive `brnquest_local:textures/imported/<hash>.png` IDs and are stored under `brnquest/local-assets/assets/brnquest_local/textures/imported/` in the game directory. Back up this local library separately and include the textures in a resource pack when distributing the book.

The language button edits a locale without changing game language. Missing translations appear as fallback placeholders; untouched fields create no translation. Pending inputs survive locale switches. The description editor offers Plain and Markdown v1 with preview; see the [content reference](CONTENT_REFERENCE.md) for supported syntax.

## Workspace and distribution

```text
config/brnquest/
├─ imports/<source>/
├─ drafts/<namespace>/<book-path>/
├─ backups/
├─ reports/
└─ workspace/
   ├─ pack.mcmeta
   └─ data/
      ├─ brnquest/brnquest/active_book.json
      └─ <namespace>/brnquest/
         ├─ books/<book>.json
         ├─ chapter_groups/*.json
         └─ chapters/*.json
```

Distribute the complete `config/brnquest/workspace/` with the modpack. Runtime definitions come from world datapacks. On world startup, if `datapacks/brnquest-workspace/` does not exist, BRNQuest validates and copies the workspace, enables it and requests a reload. An empty workspace is allowed; symlinks are rejected. Existing world deployments are never silently overwritten by a pack update.

`active_book.json` selects the last published book. Older packs without it fall back to resource-ID ordering; an invalid selected ID produces a diagnostic and falls back. Player progress lives in the world's `data/brnquest_progress.dat`.

### Create, import and publish

Use the in-game draft catalog for creating from the active book, saving versions and restoring draft history. Headless authors can start with:

```text
/brnquest author create empty <book> <title>
/brnquest author open <book>
/brnquest author status <book>
```

`open` returns a session UUID and revision. Subsequent content commands use `<session> <book> <revision>` followed by their fields:

| Command | Remaining arguments |
| --- | --- |
| `set_title` | `<title>` |
| `add_group` | `<group> <order> <title>` |
| `add_chapter` | `<chapter> <group> <order> <icon> <title>` |
| `add_quest` | `<quest> <chapter> <x> <y> <icon> <title>` |
| `add_dependency` | `<quest> <dependency>` |
| `add_task` | `<quest> <task> <type> <optional> <config-json>` |
| `add_reward` | `<quest> <reward> <type> <claim-policy> <team> <config-json>` |

Use full `namespace:path` IDs and the new revision returned by every edit. Config JSON is a flat object of primitive values; nested objects/arrays are rejected. Types ultimately receive string values.

To convert this modpack's saved FTB v13 book directly, run with operator permission level 2:

```text
/brnquest import_ftb_local
/brnquest import_ftb_local --dry-run
/brnquest import_ftb_local <namespace> [book_id] [--dry-run]
```

This reads `config/ftbquests/quests/` in the server instance (the game directory in singleplayer), including chapters, languages and reward tables. No file moves or FTB runtime dependency are required. Save any pending edits in FTB Quests first. The default destination is the draft `ftbquests:main`; a custom namespace defaults to book ID `main`. On multiplayer servers the files must be on the server. `/brnquest workspace import_ftb_local` accepts the same arguments. Quest definitions are converted using the existing [import limits](CONTENT_REFERENCE.md#ftb-import); player/team progress is not migrated. The source files remain unchanged, and an existing draft ID is refused; choose another ID to import again.

To import another FTB v13 book, place the source under `config/brnquest/imports/<source>/`:

```text
/brnquest workspace import_ftb <source> <namespace> [book_id] --dry-run
/brnquest workspace import_ftb <source> <namespace> [book_id]
```

Dry-run displays diagnostics and writes a conversion report under `config/brnquest/reports/` without creating a draft. Reports are also saved when conversion has fatal errors. Import creates an `IMPORT` draft when there are no fatal errors, preserves the source, and refuses an existing draft ID. The legacy `/brnquest import_ftb` name has the same draft-only behavior. Open the imported book from the editor's draft catalog, inspect diagnostics, then publish and apply it to use it in the current world. See [import limits](CONTENT_REFERENCE.md#ftb-import).

For command-driven publication, stop if any step fails:

```text
/brnquest author validate <session> <book> <revision>
/brnquest author diff <session> <book> <revision> workspace
/brnquest author save <session> <book> <revision>
/brnquest author publish <session> <book> <revision>
/brnquest author deploy
/brnquest author reload
```

Read each returned revision before the next command. `publish` requires a saved, valid draft and only updates the global workspace. `deploy` copies into a world with no deployment; updating an existing deployment requires explicit `deploy --replace`, which first backs up the old deployment. `reload` re-reads the deployed pack without copying from config. Only successful reload changes active gameplay. The in-game **Publish and apply** flow combines these phases after review.

Sessions last 30 minutes after their latest successful activity; the editor renews automatically. Commands can use `author renew <session> <revision>`. Disconnect, server stop or permission loss releases the session. `author close` and `author discard` take the same two arguments; discard removes unsaved session state and retains the saved draft.

## Teams and rewards

With compatible OPAC installed, parties use their stable party UUID. Book setting `share_team_progress` defaults to true. Turning it off selects personal history while retaining team history. Joining, leaving, switching teams or toggling this setting never copies or merges histories. Missing/incompatible OPAC safely falls back to personal history; the old team ledger remains stored.

Normal shared quests share objectives, but HUD tracking and ordinary reward receipts remain individual. Completion freezes the member list, including offline members. Each eligible member can claim ordinary rewards once; `team_reward=true` grants once to the first eligible claimant. Later joiners cannot claim an old cycle. Automatic rewards for offline members wait until they reconnect in the same party.

`require_all_team_members` makes each current member complete every required objective personally. Offline members count. Personal completion shows **Waiting for teammates** until the whole team finishes. Before completion, arrivals must complete their objectives and departures stop blocking; after completion, later joins do not relock the quest. Personal mode and players without a party use ordinary personal completion.

A full reset clears member objectives and receipts and creates a new claim generation. Resetting one objective clears it for every member while preserving receipts. New repeat cycles clear member objectives. By default, repeat cycles wait for every eligible ordinary reward and the team reward to be claimed, including offline/departed members. Authors can opt into `ignore_reward_blocking`; starting that new cycle ends previous unclaimed eligibility.

## Backup, recovery and diagnosis

Back up the whole world, workspace, drafts and local assets as appropriate. Reward recovery requires both player progress and command/reward-table journals; back them up together. Schema-2 progress upgrades preserve legacy personal records. Downgrading to a build that cannot read schema 2 requires a pre-upgrade backup.

Author backups use `draft`, `workspace` and `deployed` kinds:

```text
/brnquest author backups <kind>
/brnquest author restore_preview <kind> <backup-id>
/brnquest author restore <kind> <backup-id> <current-revision|->
```

Use the preview's exact current revision (`-` when absent). Changed targets are rejected; the overwritten target is backed up. Close active draft sessions before restoration, then reopen. Restore changes only the selected disk layer; deploy/reload remain explicit. Deployment backups are kept in the world's `brnquest-backups/`, outside the datapack search directory.

| Command/log | Purpose |
| --- | --- |
| `/brnquest health [details]` | Active book and latest load result; requires permission 2 |
| `/brnquest health player <player> [details]` | Server's recent transmission record |
| `/brnquest_client health [details]` | Local received/applied revisions and rejection history; no OP needed |
| `/brnquest diagnose` | Current full validation report |
| `config/brnquest/reports/author-audit.jsonl` | Author operations and stable result codes |
| `[BRNQuest/FILE_IO]` in `logs/latest.log` | Failed IO operation, paths, timing and full exception stack |

Server `SUBMITTED` means packets were handed to the sender, not that the client applied them. Compare server/player/client revisions. Client rejection history lasts until disconnect and may include a failure followed by successful recovery. IO logs do not identify external file-lock owners. Keep the full stack and operation time when reporting a fault. The optional `tools/diagnostics/Start-FileIoCapture.ps1` helper requires a directory-filtered ProcMon configuration and explicit filter confirmation.

Command and reward-table outcomes can be uncertain after interruption. Their status, acknowledgement and restricted retry commands are in the [content reference](CONTENT_REFERENCE.md). Never repair claims by deleting journals.
