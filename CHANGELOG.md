# Changelog

## Unreleased

### Added

- Added an opt-in client text layout diagnostic mode. Hover BRNQuest interface text to inspect available space, original and rendered size, scaling, truncation, clipping, and the current frame's issue count. Text without a declared control slot reports its measurement boundary; wrapped text is measured per rendered line.

### Changed

- Stacked the navigation creation buttons and arranged editor tabs in two rows on narrow panels. Descriptor property labels reserve their full wrapped height; fixed actions use measured footer widths, full-width property rows, or multiple text lines. Long identifier buttons preserve both ends and expose the full value in their tooltip.
- Shortened the selected-item label and moved its clearing instruction into the tooltip.
- Wrapped normal tooltips, including space-free IDs, while preserving text styles. Text diagnostics now report normal tooltip dimensions and offer a Shift preview.

### Fixed

- Aligned the dependency editor footer actions in two full-width rows, with matching input targets.
- Kept property scroll ranges and typed field hitboxes aligned after long labels expand their rows. Fixed diagnostic labels reporting truncated remainder text as fitting.

- Type-owned configuration actions reserve their icon width and move below full-width labels when needed; multiline buttons now require vertical padding. Compact editor tabs pair Tasks with Rewards and use the explicit Dependency label.
