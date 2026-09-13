# Quest interface and editing

The quest book uses graystone panels, type icons and status badges. Chapter navigation, the quest canvas and the details sidebar keep their existing locations. Collapse sidebars to make room for the canvas in small windows.

## Players

1. Select a chapter and quest to view requirements, progress and rewards. Scroll long lists and hover truncated names. The tracking HUD uses the same objective names and progress.
2. Item submission previews planned consumption. Select inventory slots and confirm; the server checks the items again. Cancelling does not submit. Pending actions prevent duplicate clicks; after a timeout, retry manually.
3. Select a reward choice, then confirm it. Leave and return through the reward entry to resume the current choice. Table previews do not draw or claim rewards, and random candidates are not guaranteed results.
4. Items retain native names, component tooltips and counts. With JEI installed, real items support recipe lookup; decorative type icons do not. Experience points and levels are shown separately.

## Authors

Open chapter properties from its context menu to change the name, item/texture icon or group. Chapter IDs are read-only. In edit mode, drag chapters to reorder them or move between groups; press Esc or release outside the list to cancel. Group headings collapse, with per-book collapse state remembered until the game exits.

Basic properties and objective/reward configuration remain in the sidebar. Required fields, optional fields and completion/claim rules have separate sections. Returning from a picker preserves parent form input. Reward tables support child tables and leaf editors, with a path identifying the current level.

Distinguish local forms from the two editing modes:

- Applying a child window changes its parent form only. Cancelling the parent still discards those local edits.
- Normal book editing is live: once the server accepts a completed form, it saves the change to the current world and activates it immediately. Check the status bar; local edits and pending requests do not mean the change is saved.
- Advanced drafts remain separate. A saved draft requires review and explicit publication. The in-game publication flow publishes, deploys and reloads; the active book changes only after success. Review shows names, icons and field changes; “…” opens full diagnostics. After a revision conflict, obtain a fresh review. If publication fails, follow the reported phase when recovering.

## Input

Pickers support Tab focus navigation, arrow-key browsing and Enter activation. Reward selection and confirmation are separate actions. Item choice editing uses F6 to switch between candidates and inventory; submission uses F6 for buttons/inventory and Space to select a slot. Esc returns or cancels the current level. Mouse tooltips hide during keyboard navigation and return when the pointer moves.

See also [reward tables](REWARD_TABLES.md), [workspace and recovery (Chinese)](WORKSPACE_zh.md), and [extension presentation (Chinese)](EXTENSION_API_zh.md).

Use the gear at the top right to open BRNQuest client settings, including grid snapping, scrolling, animation speeds and automatic centering. It is available while browsing and editing, without author permission. The native settings page saves changes when you return; they apply without restarting. With its default file watcher enabled, NeoForge also reloads external changes to `brnquest-client.toml`. A drag already in progress retains its initial snap setting.

In either editing mode, the grid icon at the top left toggles snapping directly. Highlighted means On; a slash means Off. Its tooltip shows only the current state. Preferences do not change book revisions or undo history.

Right-click a chapter-group heading and open **Group properties** to edit its name, choose or clear an item/texture icon, and enter a short description. Changes apply only when you confirm; cancel keeps the previous values. Group icons appear beside headings and descriptions appear on hover. Unknown extension data remains intact when editing or reordering groups.

Group names are edited in the current game language while other translations are preserved. Icons and short descriptions are shared metadata. Changing only metadata does not create a new name translation.

The language button in group/chapter names and quick quest title/subtitle editors starts with the player’s current language. Choose an existing language or enter a new locale code (such as `ja_jp`) to author a translation without changing the game language. The button tooltip briefly identifies fallback text. Switching languages retains pending input; Done saves all changes together, while Cancel discards them. A single-field quick edit leaves other text fields untouched.

When the selected language has no translation, fallback text appears only as a gray placeholder. Typing starts from an empty field, and confirming untouched input creates no translation. In the default language itself, the native default remains directly editable.
