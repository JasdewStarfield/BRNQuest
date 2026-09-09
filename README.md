# BRNQuest for Minecraft 1.21.1 (NeoForge)

This repository contains the active BRNQuest implementation for:

- Minecraft 1.21.1
- NeoForge 21.1
- Java 21
- branch `mc/1.21.1-neoforge`

BRNQuest includes a server-authoritative quest runtime and in-game visual editor. JEI, KubeJS, and Open Parties and Claims integration are optional at runtime. OPAC 0.30.3 is the validated compatibility baseline; compatible nearby versions are detected at runtime. OPAC parties share quest progress with individual reward receipts and HUD focus; BRNTalk owns its optional BRNQuest adapter.

## Build

Before running Gradle, check for stale Gradle, Java, or Minecraft development processes that may retain workspace locks. Then run:

```powershell
.\gradlew.bat build --no-configuration-cache --no-daemon --console=plain
```

The development run configurations are `runClient`, `runServer`, `runGameTestServer`, `runKubeJsSmokeServer`, `runKubeJsReloadSmokeServer`, and `runData`. The two KubeJS smoke servers opt into their optional runtime automatically and validate both a clean script load and a deliberately failed reload rollback.

## Documentation

Project design, implementation plans, provenance records, and acceptance evidence are maintained in the separate [BRNQuest-Docs repository](https://github.com/JasdewStarfield/BRNQuest-Docs). User-facing and extension-author documentation for this version remains alongside the source:

- 整合包作者的任务工作区、草稿、部署、更新与恢复流程：[`docs/WORKSPACE_zh.md`](docs/WORKSPACE_zh.md)
- 探索目标、坐标区域与维度分组：[`docs/EXPLORATION_TASKS_zh.md`](docs/EXPLORATION_TASKS_zh.md)
- 命令奖励、执行权限与异常处理：[`docs/COMMAND_REWARDS_zh.md`](docs/COMMAND_REWARDS_zh.md)
- 作者文本、任务书翻译表与语言回退：[`docs/AUTHOR_TEXT_LOCALIZATION_zh.md`](docs/AUTHOR_TEXT_LOCALIZATION_zh.md)
- Java 公共 API、稳定性等级和线程/权限边界：[`docs/PUBLIC_API_zh.md`](docs/PUBLIC_API_zh.md)
- 作者 API 与高级草稿恢复工作流：[`docs/AUTHOR_API_zh.md`](docs/AUTHOR_API_zh.md)
- 自定义 task/reward、客户端展示与编辑字段：[`docs/EXTENSION_API_zh.md`](docs/EXTENSION_API_zh.md)
- 客户端 Screen、Model/Frame/Intent 与组合组件边界：[`docs/CLIENT_UI_ARCHITECTURE_zh.md`](docs/CLIENT_UI_ARCHITECTURE_zh.md)
- 作者网络的协议兼容面、请求/用例/响应职责：[`docs/AUTHORING_NETWORK_ARCHITECTURE_zh.md`](docs/AUTHORING_NETWORK_ARCHITECTURE_zh.md)
- KubeJS 服务端脚本全局 API：[`docs/KUBEJS_API_zh.md`](docs/KUBEJS_API_zh.md)
- OPAC 队伍进度、奖励资格、存档升级与降级行为：[`docs/OPAC_INTEGRATION_zh.md`](docs/OPAC_INTEGRATION_zh.md)
- API 版本及兼容策略：[`docs/API_VERSIONING_zh.md`](docs/API_VERSIONING_zh.md)
- 独立示例附属模组：[`docs/EXAMPLE_ADDON_zh.md`](docs/EXAMPLE_ADDON_zh.md)

## Source layout

Production code uses the base package `yourscraft.jasdewstarfield.brnquest`. Stable API, data/import, progress, task, reward, owner, network, client, integration, command, and diagnostics concerns remain separated by package.

Internal implementation plans, validation procedures, and historical acceptance reports are maintained in BRNQuest-Docs and are not part of the user documentation set shipped with the mod.

- [原版进度目标与奖励 / Advancement tasks and rewards](docs/ADVANCEMENT_TYPES_zh.md)
