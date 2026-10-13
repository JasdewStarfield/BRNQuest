# Changelog

## Unreleased

### Added

- Editor text fields provide local undo/redo, literal formatting-code editing and clearer selections. Localized source drafts keep their history across view changes and picker returns; the raw configuration editor keeps unsaved text when resized.
- Added optional IMBlocker integration for owned editor inputs, including focus detection and composition-window positioning.
- Quest titles and subtitles support native colors and text styles through typed/pasted codes or formatting tools.
- The text editor offers manual Source/Preview/Split views and a draggable divider. Its compact toolbar targets the title, subtitle or body, and view changes and picker returns preserve editing positions.
- Added top-center floating notices above gameplay and menus, with quest/objective icons and objective requirements. New notices fold older ones behind them, showing up to three layers and a folded count. The tracking HUD moves aside when needed.
- Quest properties offer separate switches for objective completion, quest completion and reward claims. Clients can adjust notice width and duration, disable all notices or enable sound; nearby events of the same kind within one quest merge.
- Book and chapter creation presets include notification switches, quest name position and minimum required dependencies.
- Added client settings and quest properties to always show quest names above or below their nodes.
- Added client settings and chapter properties to show completed / visible quest counts and percentages. Quest and chapter properties can override or inherit client preferences.
- Added text layout diagnostics in client settings to help find text that does not fit. Hold Shift to preview normal tooltips while diagnostics are enabled.

### Changed

- Improved button and property-label layouts to display long text more clearly, especially in English.
- Received notices enter immediately while older cards fold independently. Only the latest three groups retain content, with older groups represented by the folded count; all layers withdraw together after the latest notice's display time.
- Quest appearance, behavior and notification forms share their fields and validation with book/chapter creation presets. Appearance fields are grouped under Configure appearance.
- Simplified child editor confirmation labels and instructions. Creation presets are separated from live display rules; empty numbers identify their system/book source, existing quest numbers remain concrete, and tooltips explain how values resolve.
- Stacked the Add Group and Add Chapter buttons. Narrow detail panels now show Properties and Dependency above Tasks and Rewards; renamed Links to Dependency.
- Long IDs preserve their beginning and end, with the full value available in tooltips.
- Shortened the selected-item label and moved its clearing instruction into the tooltip.
- Added automatic wrapping for long tooltips, including long IDs, while preserving text styles.

### Fixed

- Editor captions mark shortened text and reveal the full hint or error on hover. Background and decoration controls clip smoothly at viewport edges; background and texture-browser buttons use the shared editor style.
- Fixed wheel scrolling in objective/reward property forms, inventory overlap with accepted-item editor buttons, background-editor scrollbars and clipped reward-type choices.
- Quests without configured objectives stay incomplete, preventing premature completion notices and leaving progress ready when objectives are added.
- Fixed switching editing mode redirecting chapter browsing to a task from a closed detail panel.

- Fixed misaligned buttons in the dependency editor and text extending beyond multiline buttons.
- Fixed scrolling and clicking problems in property forms with long labels.
- Fixed diagnostics incorrectly reporting shortened property labels as fully displayed.
