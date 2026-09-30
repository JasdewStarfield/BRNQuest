# Changelog

## Unreleased

### Added

- Added text layout diagnostics in client settings to help find text that does not fit. Hold Shift to preview normal tooltips while diagnostics are enabled.

### Changed

- Improved button and property-label layouts to display long text more clearly, especially in English.
- Stacked the Add Group and Add Chapter buttons. Narrow detail panels now show Properties and Dependency above Tasks and Rewards; renamed Links to Dependency.
- Long IDs preserve their beginning and end, with the full value available in tooltips.
- Shortened the selected-item label and moved its clearing instruction into the tooltip.
- Added automatic wrapping for long tooltips, including long IDs, while preserving text styles.

### Fixed

- Fixed misaligned buttons in the dependency editor and text extending beyond multiline buttons.
- Fixed scrolling and clicking problems in property forms with long labels.
- Fixed diagnostics incorrectly reporting shortened property labels as fully displayed.
