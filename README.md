# BRNQuest

[简体中文](README_zh.md) | English

BRNQuest is a server-authoritative quest system with an in-game visual editor for Minecraft modpacks. Create quest books, connect objectives, distribute rewards, and import supported FTB Quests v13 content into native data.

## Installation

- Mod version: **1.0.0**
- Minecraft **1.21.1**, NeoForge **21.1**, Java **21**
- Install the same BRNQuest version on the server and every client. Single-player uses the same JAR.
- FTB Quests is an import source; it is not required at runtime.

| Optional integration | Purpose | Development baseline |
| --- | --- | --- |
| JEI | Item recipe/use lookup | 19.25.1.334 |
| Open Parties and Claims | Party progress and reward eligibility | 0.30.3 |
| KubeJS + Rhino | Server scripts, events and custom types | 2101.7.2-build.374 + 2101.2.8-build.91 |
| BRNTalk | Dialogue tasks/rewards through BRNTalk's adapter | Use a version compatible with BRNQuest's API |

Development baselines are independent of loader requirements. Optional dependency ranges are JEI `[19.8.4.110,20)` and KubeJS `[2101.7.0-build.126,2102)`; OPAC has no minimum-version gate. Both lower-bound APIs compile successfully with BRNQuest. Older releases still need in-game integration checks; KubeJS determines its own Rhino requirements.

## Start here

Press **J** or click the quest icon in the upper-left corner of the inventory screen to open the quest book; the key is rebindable. Authors need permission level 2. Use normal editing for immediate world changes or advanced drafts for review and publication. Distribute a task workspace with your modpack and referenced artwork through a resource pack.

| Guide | English | 简体中文 |
| --- | --- | --- |
| Author guide: editing, deployment, teams and recovery | [Read](docs/AUTHOR_GUIDE.md) | [阅读](docs/AUTHOR_GUIDE_zh.md) |
| Content reference: objectives, rewards, localization and import limits | [Read](docs/CONTENT_REFERENCE.md) | [阅读](docs/CONTENT_REFERENCE_zh.md) |
| Java API: queries, operations, author sessions and compatibility | [Read](docs/API.md) | [阅读](docs/API_zh.md) |
| Type extensions: registration, UI and reward recovery contracts | [Read](docs/EXTENSION_API.md) | [阅读](docs/EXTENSION_API_zh.md) |
| KubeJS scripting API | [Read](docs/KUBEJS_API.md) | [阅读](docs/KUBEJS_API_zh.md) |

## Build from source

Use Java 21 and the included Gradle wrapper:

```powershell
.\gradlew.bat build --no-configuration-cache --no-daemon --console=plain
```

The distributable is written to `build/libs/`. `runClient` and `runServer` start development instances. Their development-only example add-on can be excluded with `-PexcludeExampleAddon`. Common code, built-in types and integrations compile separately and ship as one JAR. See the [extension guide](docs/EXTENSION_API.md) for the public-only example add-on.

## Client text layout diagnostics

In the quest editor's client settings, enable **Debug → Text layout diagnostics**, or set `textLayoutDebug = true` under `[debug]` in `config/brnquest-client.toml`. Hover interface text to inspect available space, original and rendered size, scaling, truncation, and clipping. Hold Shift to preview normal tooltips while diagnostics are enabled. Diagnostics also report their width before reflow, final size, row count, and remaining overflow. Disable the option to restore normal tooltips.

Measurements use the current font and GUI coordinates. Shared buttons and fitted text renderers provide text slots; wrapped text is measured per rendered line. Text without a declared slot reports an unknown control area and shows the screen/scissor boundary. Diagnostics apply to BRNQuest screens. Normal tooltip text wraps within 320 GUI pixels or the screen width minus 24 pixels, whichever is smaller; long IDs break between glyphs while keeping text styles. Oversized custom images and tooltips that exceed the screen height remain visible in the diagnostic report.

## AI and developer responsibilities

BRNQuest is designed and maintained by its developer, with AI tools participating in development.

- **Developer**: Leads feature design, architecture decisions, artwork, in-game testing and acceptance, releases, and ongoing maintenance; decides on implementation approaches and the content included in the project.
- **AI**: Generates and modifies code based on the developer's requirements and feedback, and assists with refactoring, problem analysis, automated validation, and documentation.

## Support and license

Report reproducible problems through [GitHub Issues](https://github.com/JasdewStarfield/BRNQuest/issues), including mod/loader versions, steps and relevant logs. Check logs for private paths or player details before posting them.

BRNQuest is licensed under [MIT](LICENSE). Bundled dependency attribution is in [Third-party notices](THIRD_PARTY_NOTICES.md).
