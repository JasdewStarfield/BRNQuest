# BRNQuest for Minecraft 1.21.1 (NeoForge)

This repository contains the active BRNQuest implementation for:

- Minecraft 1.21.1
- NeoForge 21.1
- Java 21
- branch `mc/1.21.1-neoforge`

BRNQuest includes a server-authoritative quest runtime and in-game visual editor. JEI, KubeJS, and Open Parties and Claims integration are optional at runtime. OPAC 0.30.3 is the validated compatibility baseline; compatible nearby versions are detected at runtime. OPAC parties share quest progress by default, with optional personal progress and per-quest all-member completion; reward receipts and HUD focus remain individual; BRNTalk owns its optional BRNQuest adapter.

## Build

Before running Gradle, check for stale Gradle, Java, or Minecraft development processes that may retain workspace locks. Then run:

```powershell
.\gradlew.bat build --no-configuration-cache --no-daemon --console=plain
```

The development run configurations are `runClient`, `runServer`, `runGameTestServer`, `runKubeJsSmokeServer`, `runKubeJsReloadSmokeServer`, and `runData`. The two KubeJS smoke servers opt into their optional runtime automatically and validate both a clean script load and a deliberately failed reload rollback.

## Documentation

- 队伍共享、独立进度和全员完成 / Team sharing, personal progress and all-member completion: [中文](docs/TEAM_PROGRESS_zh.md) / [English](docs/TEAM_PROGRESS.md)

- 任务界面、键盘操作与编辑生效层次 / Quest UI, keyboard controls and editing: [中文](docs/QUEST_UI_zh.md) / [English](docs/QUEST_UI.md)

The `docs/` directory contains only public user, administrator, and extension-author documentation. Internal architecture, implementation plans, developer test workflows, and acceptance evidence are maintained separately.

- 整合包作者的任务工作区、草稿、部署、更新与恢复流程：[`docs/WORKSPACE_zh.md`](docs/WORKSPACE_zh.md)
- 观察方块/实体与击杀目标：[`docs/ENCOUNTER_TASKS_zh.md`](docs/ENCOUNTER_TASKS_zh.md)
- 探索目标、坐标区域与维度分组：[`docs/EXPLORATION_TASKS_zh.md`](docs/EXPLORATION_TASKS_zh.md)
- 命令奖励、执行权限与异常处理：[`docs/COMMAND_REWARDS_zh.md`](docs/COMMAND_REWARDS_zh.md)
- 作者文本、任务书翻译表与语言回退 / Author text, translation tables, and locale fallback: [中文](docs/AUTHOR_TEXT_LOCALIZATION_zh.md) / [English](docs/AUTHOR_TEXT_LOCALIZATION.md)
- Java 公共 API、稳定性等级和线程/权限边界：[`docs/PUBLIC_API_zh.md`](docs/PUBLIC_API_zh.md)
- 作者 API 与高级草稿恢复工作流：[`docs/AUTHOR_API_zh.md`](docs/AUTHOR_API_zh.md)
- 自定义 task/reward、客户端展示与编辑字段：[`docs/EXTENSION_API_zh.md`](docs/EXTENSION_API_zh.md)
- 奖励表配置、嵌套与领取恢复：[中文](docs/REWARD_TABLES_zh.md) / [English](docs/REWARD_TABLES.md)
- 原生战利品表、上下文与固定生成结果：[中文](docs/LOOT_TABLE_REWARDS_zh.md) / [English](docs/LOOT_TABLE_REWARDS.md)
- 文件读写故障日志与问题反馈：[`docs/FILE_IO_DIAGNOSTICS_zh.md`](docs/FILE_IO_DIAGNOSTICS_zh.md)
- KubeJS 服务端脚本全局 API：[`docs/KUBEJS_API_zh.md`](docs/KUBEJS_API_zh.md)
- OPAC 队伍进度、奖励资格、存档升级与降级行为：[`docs/OPAC_INTEGRATION_zh.md`](docs/OPAC_INTEGRATION_zh.md)
- API 版本及兼容策略：[`docs/API_VERSIONING_zh.md`](docs/API_VERSIONING_zh.md)
- 独立示例附属模组：[`docs/EXAMPLE_ADDON_zh.md`](docs/EXAMPLE_ADDON_zh.md)

## Source layout

Production code uses the base package `yourscraft.jasdewstarfield.brnquest`. Separate Java compilation units enforce the dependency direction `main` (core) ← `builtin` ← `integration` (FTB conversion and JEI); integrations also use core directly. Internal service providers assemble common and client registrations. The published mod remains one JAR with the same mod ID, type IDs, resource paths and public API.

`gameTest`, `moduleTest`, `opacTest`, and `exampleAddon` are development-only source sets. Their classes, worlds and fixtures are excluded from the production JAR. Shared language catalogs and generic UI sprites belong to core; built-in-only type sprites belong to `builtin`.

生产代码按 core、内置类型和 FTB/JEI 适配层分别编译，编译依赖单向向下。安装仍使用一个 JAR，模组 ID、类型 ID、资源路径及公开 API 保持兼容；测试与示例源码集不进入正式产物。

Internal implementation plans, validation procedures, and historical acceptance reports are maintained in BRNQuest-Docs and are not part of the user documentation set shipped with the mod.

- [原版进度目标与奖励 / Advancement tasks and rewards](docs/ADVANCEMENT_TYPES_zh.md)

- 奖励表 / Reward tables：[中文](docs/REWARD_TABLES_zh.md) · [English](docs/REWARD_TABLES.md)
