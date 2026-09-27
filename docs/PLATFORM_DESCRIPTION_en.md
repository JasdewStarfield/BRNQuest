# BRNQuest

**A Minecraft quest system with an in-game visual editor for modpacks, adventure maps, and multiplayer servers.**

Connect exploration and progression through chapters, introduce new mechanics through quests, and reward players as they advance. BRNQuest brings quest planning, content editing, and progress tracking together in a single quest book.

## What can you use it for?

- Create modpack tutorials, technology paths, and progression milestones.
- Build exploration, collection, combat, and story quests for adventure maps.
- Set up shared goals, challenges for every team member, and repeatable server activities.
- Connect dialogue reading, story completion, and follow-up conversations to quests with BRNTalk.

## Highlights

### Build quests in-game

Use the visual canvas to create chapters, arrange quests, and connect dependencies. The editor supports multiple selection, dragging, copy and paste, chapter duplication, undo, and redo. Book and chapter creation defaults help reduce repetitive configuration.

Regular edits take effect when saved. Advanced drafts provide separate editing, saved versions, change review, and publication tools for ongoing modpack development.

### Shape progression with varied objectives

Ask players to hold or submit items, craft specific items, earn experience, enter dimensions or biomes, discover structures, reach designated areas, observe blocks or entities, defeat targets, and complete vanilla advancements. Manual checkmarks suit tutorials and reading prompts.

Combine dependencies, sequential objectives, optional objectives, visibility conditions, repeat cycles, and cooldowns to build everything from introductory guides to long-term challenges.

### Configure rewards and reward tables

Offer items, experience, commands, vanilla advancements, and native loot tables as rewards. Reward tables support grouped rewards, random draws, player choices, and nested table references.

### Match your modpack's presentation

Quest descriptions support Markdown v1, including headings, lists, emphasis, links, and images. Use resource-pack textures for quest icons, canvas decorations, and backgrounds. Built-in localization editing lets authors provide multiple languages in the same quest book.

Players can track quests on the HUD, select inventory items for submission, and confirm their choices through reward selection screens.

### Design progression for teams

With Open Parties and Claims, share quest progress or require each team member to complete objectives individually. Rewards can be claimed per member or once for the whole team. Each quest book can use personal or team progress.

### Import and extend

Import supported content from FTB Quests v13. Imports become drafts with conversion reports, ready to review and adjust before publication.

KubeJS server scripts and the Java API provide progress queries, events, quest operations, and custom objective and reward types.

## Optional integrations

| Mod | Features |
| --- | --- |
| **JEI** | Recipe and usage lookup, item-selector integration, and space reserved for the inventory quest button |
| **Open Parties and Claims** | Team identities, shared progress, and member rewards |
| **KubeJS + Rhino** | Server scripts, quest events, and custom types |
| **BRNTalk** | Message-seen and conversation-completion objectives, plus rewards that start or resume conversations or open the dialogue screen |

## Quick start

1. Use **Minecraft 1.21.1, NeoForge 21.1, and Java 21**. Install the same BRNQuest version on the server and every client. For single-player, install it on the client.
2. Enter a world and press **J**, or click the quest icon in the upper-left corner of the inventory screen.
3. With **permission level 2**, enter editing mode and create chapters, quests, objectives, and rewards.
4. Modpack authors can distribute the quest workspace with their pack and supply textures through a resource pack. See the [author guide](https://github.com/JasdewStarfield/BRNQuest/blob/mc%2F1.21.1-neoforge/docs/AUTHOR_GUIDE.md) for editing, deployment, and recovery instructions.

## Quickly convert your current FTB quests

With **BRNQuest 1.0.1 or later**, save your quest book in FTB Quests, then run this command with **permission level 2**:

```text
/brnquest import_ftb_local
```

The command reads `config/ftbquests/quests/` in the current instance and converts supported FTB Quests v13 content into the BRNQuest draft `ftbquests:main`, including chapters, languages, and reward tables. No manual file copying is needed. Multiplayer imports read the server's files.

Press **J**, open the editor's **advanced draft catalog**, and select the imported book. Review the chat diagnostics and the report in `config/brnquest/reports/`, make any adjustments, then **publish and apply it to the current world**.

To preview the conversion first, run `/brnquest import_ftb_local --dry-run`; this only generates a diagnostic report. Existing drafts are never overwritten. For another import, choose a new name, such as `/brnquest import_ftb_local mypack retry`.

Conversion preserves the FTB source files and imports quest definitions only; player and team progress are not migrated. Keep the resource packs that provide any custom images. See the [import reference](https://github.com/JasdewStarfield/BRNQuest/blob/mc%2F1.21.1-neoforge/docs/CONTENT_REFERENCE.md#ftb-import) for supported content.

## AI and developer responsibilities

BRNQuest is designed and maintained by its developer, with AI tools participating in development.

- **Developer**: Leads feature design, architecture decisions, artwork, in-game testing and acceptance, releases, and ongoing maintenance; decides on implementation approaches and the content included in the project.
- **AI**: Generates and modifies code based on the developer's requirements and feedback, and assists with refactoring, problem analysis, automated validation, and documentation.

## Project information

- **Author**: Jasdew Starfield
- **License**: MIT
- **Source and documentation**: [GitHub](https://github.com/JasdewStarfield/BRNQuest)
- **Bug reports and suggestions**: [GitHub Issues](https://github.com/JasdewStarfield/BRNQuest/issues)
