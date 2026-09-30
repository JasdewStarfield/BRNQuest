# Changelog

## [Unreleased]

## [1.0.2] - 2026-09-30

### Fixed

- Read escaped newlines in FTB SNBT text correctly, including Chinese descriptions, while preserving literal backslashes and rejecting invalid escapes.
- Create the missing default group for ungrouped FTB chapters, including books that also declare chapter groups.
- Validate imported legacy aliases for chapter groups, chapters, tasks, and rewards as well as quests.
- Preserve unsupported FTB task and reward types with their configuration as unknown entries, and report them as compatibility warnings.
- Preserve visible text from click-to-copy components and report the omitted click behavior as a compatibility warning.
- Distinguish fatal import failures from recoverable conversion errors in command summaries and editor diagnostics; failed imports no longer claim to have created a draft.

### Changed

- Clarify warnings for omitted link styling and document import diagnostic levels in the author guide.

### Added

- Automated GitHub Release publishing with bilingual release notes; optional Modrinth and CurseForge uploads can be enabled after configuring their project IDs and tokens.
