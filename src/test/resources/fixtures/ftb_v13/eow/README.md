# Embers of Winter FTB Quests v13 fixture

This directory is a read-only compatibility snapshot of the user-owned Embers of Winter task book retrieved on 2026-08-12 from:

```text
config/ftbquests/quests
```

It contains `data.snbt`, `chapter_groups.snbt`, six chapter files, and `lang/zh_cn.snbt`. Tests and import tools must never modify these files.

The pre-import textual baseline is:

- format version: 13;
- chapter groups: 2;
- chapter files: 6;
- quest IDs at the FTB chapter quest level: 53;
- task/reward type declarations: 60 (`checkmark` 43, `item` 13, `custom` 4).

The importer-generated semantic manifest becomes authoritative if it identifies a structural distinction that the textual baseline cannot represent.
