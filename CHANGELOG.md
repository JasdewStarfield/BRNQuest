# Changelog

## [Unreleased]

## [1.2.0] - 2026-11-06

Changes since the 1.1.0 release.

### Changed

- Publish review shows readable summaries for added groups, chapters, quests, objectives and rewards, including their parent, content counts, type and configuration. Localization changes identify the affected object, language and field. Details wrap and scroll, with technical data and explicit truncation notices available for inspection.
- Built-in publish validation errors, warnings, field issues and revision conflicts now have Chinese translations. Diagnostic codes and technical details remain available for troubleshooting.
- Split editor state, forms and book workflows into focused modules, and separated progress queries, sampling, reward execution and completion transitions. Existing completion, repeat, reset and reward failure behavior is preserved.
- Reuse stateful objective selections during polling while continuing to check current type registrations, reducing repeated list construction.

### Fixed

- Stop editor input when the server denies or invalidates the editing lease. Ignore superseded lease responses and acknowledgements for another book so they cannot replace the current editing session.
- Keep highlighted dependency lines centered with symmetric 1/3-pixel widths. Arrow endpoints leave room for node outlines and stay in place when selection or tracking changes, including short connections and oversized icons.
- Prevent repeated alpha blending where a dependency line meets its arrow or turns a corner, preserving uniform opacity within each connection.
- Restore optional JEI, KubeJS and Open Parties and Claims dependencies in Modrinth release metadata. Release upload recovery handles empty CurseForge relations and Modrinth file metadata field order correctly.

### Compatibility

- Minecraft 1.21.1, NeoForge 21.1 and Java 21. Install 1.2.0 on the server and all clients.
- Public Java API baseline `0.1.0-experimental.30`, network protocol 21, book schema 1 and progress schema 2 are unchanged. Existing quest books and progress require no format migration; this release adds no required dependencies or configuration options.

## [1.1.0] - 2026-10-14

Changes since the 1.0.2 release.

### Added

- Added statistic objectives covering all nine vanilla categories, with historical totals or new progress after unlock and native-unit or raw-integer display. Teams can share progress or require every member to finish; shared history uses the highest member value, while new progress sums member contributions. FTB Quests statistic objectives can be imported.
- Quest names can stay visible above or below nodes, and chapters can show completed / visible quest counts and percentages. Client preferences can be overridden in individual quest or chapter properties.
- Added top-center notices for objective completion, quest completion and reward claims, visible above gameplay and menus. They show icons and objective requirements, merge successive events of the same kind within a quest, fold into up to three layers with a folded count, and avoid overlap with the tracking HUD. Quests select which events trigger notices; clients can disable notices, adjust width, duration, scaling and background opacity, or enable sounds.
- The quest text editor supports native colors and styles in titles and subtitles, manual Source / Preview / Split views, a draggable divider and contextual formatting tools. View changes and picker returns preserve drafts, caret, selection and scroll positions.
- Editor inputs support undo/redo, editable formatting codes and clearer selections; raw configuration drafts survive resizing. IMBlocker integration supports input-method switching and candidate-window positioning.
- Added optional text layout diagnostics in client settings to help identify text that does not fit. Hold Shift to preview normal tooltips while diagnostics are enabled.

### Changed

- The tracking HUD defaults to 75% scale, with configurable scaling, maximum width and background opacity. Its width follows content, long objectives wrap, and shows how many objectives are not displayed. Quests remain tracked after completion and reward claims, show their completed state, and can still be tracked or untracked manually.
- Book and chapter creation presets include quest name position, notification switches and minimum required dependencies. Property forms group appearance settings, clarify value sources and distinguish presets for newly created quests from live display preferences; confirmation labels and instructions are simpler.
- Editor layouts adapt better to narrow panels and large GUI scales: group/chapter creation buttons stack vertically, detail actions use two rows, and objective/reward lists place Add and Paste above a full-width Done button. Background and texture-browser controls use the shared editor style.

### Fixed

- Quests without configured objectives stay incomplete. Adding objectives allows normal progress without first resetting a prematurely completed quest.
- Switching editing mode preserves the browsed chapter instead of following an old selection from a closed detail panel.
- Long property labels, buttons, hints and tooltips display more clearly through wrapping or explicit omission with full hover text. Long IDs preserve their beginning and end; multiline button text stays within its border.
- Fixed scrolling and click alignment in objective/reward property forms, inventory/button overlap in accepted-item selection, and clipped reward-type choices. Background and decoration controls remain partially visible as they scroll across viewport edges.
