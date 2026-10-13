# Changelog

## Unreleased

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
