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

While editing, right-click the canvas and choose Book properties to edit the book title, fallback language and new quest defaults. Chapter properties expose chapter defaults. Templates cover shape, size, icon scale, minimum width, dependency visibility/completion hiding, detail/text hiding, sequential objectives and repeatability. Blank numbers and Default toggles inherit; explicit Off and zero remain overrides. New quests resolve core → book → chapter → explicit inputs once. Existing, copied and moved quests retain their effective values. Confirm the outer properties form after editing a template; canceling it discards pending changes.

New quest defaults also include hiding until completion, completed objectives required to reveal the quest, lock-icon visibility, dependency requirement, repeat cooldown in seconds and ignoring reward blocking. Thresholds and cooldowns accept nonnegative integers: zero overrides the parent while blank inherits. Dependency modes support all/any dependencies completed or started.

### Entry defaults and book settings

Book properties expose default item consumption, team sharing and claim policies for new entries, plus automatic-claim suppression and single-player quest UI pause. Chapters can override item consumption or inherit the book default. Existing entries and copies retain their values; interactive rewards default to manual claims.

Suppression affects existing rewards, exposes hidden automatic rewards for manual claiming, and preserves stored policies. Re-enabling automatic claims does not grant claimed rewards again. The pause setting applies to both browsing and editing in single-player; multiplayer remains unpaused.

Chapter properties offer a searchable **Auto focus** quest picker, including **None**. Entering the chapter or reopening the quest UI centers its target in the visible canvas without changing zoom or opening details; returning from a child screen does not refocus. Quest renames update the reference; removal or relocation to another chapter clears it, with undo support. Native chapter JSON uses optional `autofocus_id` (namespaced quest ID). FTB hexadecimal same-chapter quest references are converted on import; unsupported or invalid references are preserved with a warning.

In edit mode, right-click a quest node and choose **Set as chapter auto focus**. The shortcut supports undo; selecting the existing target creates no change. An unset target has a dedicated tooltip.

In edit mode, use **Copy configuration** in a task/reward entry menu, then **Paste** in the matching list of another quest in the same book. List focus supports Ctrl+C/V; text fields keep native text clipboard behavior. Copies freeze configuration and remain usable after source edits/deletion. Each paste receives a new ID and one undo step. Private references remain unchanged. This first slice is process-local and restricted to the same world/server, book and entry kind, with a 64 KiB snapshot limit.

Quest copies mark each existing translated title with a copy suffix (Chinese “（副本）”, otherwise “(Copy)”) and use one increasing ordinal across locales when names collide. Missing/blank translations retain fallback behavior; subtitles, descriptions and extension text remain unchanged. Copying a copy does not stack suffixes.

With multiple quests selected, right-click a selected node or empty canvas for **Copy selection**, **Delete selection**, or **Clear selection**. Copies shift together by one grid unit on each axis, remap internal dependencies, and retain external dependencies and private config references. All quest/task/reward IDs are fresh, and translated titles receive copy suffixes. Deletion asks for confirmation and removes incoming dependencies. Each batch is one undo step. Right-clicking an unselected node uses its single-node menu.

In edit mode, choose **Copy chapter** from a chapter context menu. The copy appears immediately after the source in the same group. Quests, tasks and rewards receive fresh IDs while layout and chapter settings are preserved. Internal dependencies and autofocus point to copied quests; cross-chapter dependencies and opaque configuration references stay unchanged. Existing chapter and quest title translations receive copy numbering; missing translations retain fallback behavior. Empty chapters and atomic undo/redo are supported. Player progress is not copied.

Choose **Copy to clipboard** on one quest or a selection, then switch to another chapter in the same book and choose **Paste quests** at the destination canvas position. The selection’s minimum X/Y corner anchors there, preserving relative layout. Definitions and translations are frozen at copy time, even if sources are later edited or deleted. Internal dependencies point to copies; external dependencies stay linked, and missing external targets reject the entire paste. Explicit fields are preserved without applying destination creation defaults or changing autofocus. Repeated paste and atomic undo/redo are supported. The clipboard is scoped to the world/server and book, with a 64 KiB snapshot limit; an oversized copy preserves the previous clipboard.

Two borderless 16×16 double-chevron PNG icons at the right of the status row open compact prerequisite/follow-up dialogs. Empty relations use a muted icon but remain clickable. Long lists scroll, empty lists show an empty state, and each row displays the quest title, chapter and status. Clicking opens and focuses the quest, including across chapters. Hidden quests/text retain visibility rules; drafts show preview status.

Chapter properties expose `default_hide_dependency_lines` (false by default). Quest properties expose `hide_dependency_lines` as inherit/hide/show; an explicit value overrides the chapter setting for incoming lines. This never changes prerequisite evaluation. Focused relationships remain visible in blue/orange. Copies preserve explicit values; inherited values use the destination chapter default.

Client settings also expose automatic navigation collapse, wheel zoom step, grid visibility (always/editor only/never), node hold time, reduced motion, editing/viewport memory, and custom navigation/details widths. Width 0 keeps automatic layout; custom widths are constrained to preserve canvas space. Settings apply live; hold time is captured on the next press and memory preferences apply on reopening. Memory remains local to the current game process.

### Canvas artwork and backgrounds

Authors can right-click the canvas and choose **Add decoration**, then select a texture to create it at that right-click position. Cancelling the picker creates nothing. Right-click the decoration itself and choose **Decoration properties**. The left column previews its current width/height ratio; with aspect lock enabled, editing either dimension immediately updates the other. Disable aspect lock to edit them independently. the right column edits its resource ID, position, dimensions, aspect locking, layer and locking; scroll this column in small windows. **Apply** commits the working copy as one undoable edit. Cancel discards this form's changes.

**Browse loaded textures** lists PNG resources from enabled resource packs with name filtering and previews. Click the current quest/chapter texture preview to open the same browser. Empty values show an add button; cancelling preserves the original value. Resource IDs use `namespace:textures/path.png`. Missing resources show a purple placeholder while retaining their IDs; restoring the pack and reloading resources restores the image. Books store resource IDs only, so other players need the matching resource pack.

New decorations start at the right-click position, respecting grid snapping. Positions and dimensions use quest grid units. After applying, drag a decoration on the main canvas to move it; drag its gold lower-right handle to resize. Snapping affects both preview and final position, and aspect locking preserves the current ratio. Locking prevents canvas dragging, resizing and Delete; the property form can unlock or manage the object. Layers draw in ascending order, then by stable ID; all decorations stay below quest nodes. Browsing mode gives artwork no input ownership.

Select artwork on the canvas and use Ctrl+C to copy and Ctrl+V to paste at the visible canvas center. Repeated paste creates fresh IDs. Overlapping decorations select the topmost object; move it aside to reach objects below. The clipboard is scoped to the current process, world/server and book. Focused text fields keep ordinary text shortcuts. Whole-chapter copying also allocates fresh artwork IDs. Progress, objectives and reward receipts are never copied as decoration data.

Canvas and screen backgrounds separately support tile, contain, cover and opacity (0 transparent, 1 opaque). Canvas backgrounds pan and zoom with the graph; contain/cover use a fixed 1024×1024 graph-pixel square centered on the origin. Screen backgrounds stay fixed and adapt to the current window. The background page includes a separate original-ratio thumbnail and a 0.25–8 scale multiplier (legacy settings default to 1). This changes tile size or scales the centered contain/cover result. Tiling defaults to 128×128 pixel cells, scaled by zoom on the canvas. Panels and modals remain above backgrounds.

Open **Backgrounds** in chapter/book properties, then switch between canvas and screen targets inside the shared page. Both targets share Apply/Cancel. A fixed bottom-left order control cycles inherit/default, canvas above, and screen above. Chapter order inherits the book setting; the legacy default is canvas above. Decorations and quest nodes stay above both backgrounds. Button tooltips and background property pages explain their different movement and zoom behavior. During preview, chapter/details sidebars and top/bottom toolbars are hidden, and the canvas fills the window. Leaving preview preserves the previous sidebar state. Translucent panels show a live preview behind the background editor without changing the draft. Book properties preview book defaults independently of current chapter overrides. Invalid partial input retains the last valid preview. Switching to Custom retains the input and does not open the picker; click Browse loaded textures explicitly. Applying a background child page updates only the outer form; confirm the outer properties to save everything together, or cancel to discard the background changes. Chapters override each background independently: **Inherit / reset** restores the book setting; **Disabled** explicitly suppresses it. Resetting the book restores no custom background. Each chapter allows up to 128 decorations, dimensions of 0.05–1024 grid units, coordinates within ±1,000,000 and layers within ±10,000. Scene configuration is limited to 65,536 characters. Local image uploads and remote URLs are unsupported.
