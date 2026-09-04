# BRNQuest for Minecraft 1.21.1 (NeoForge)

This worktree contains the active BRNQuest implementation for:

- Minecraft 1.21.1
- NeoForge 21.1
- Java 21
- branch `mc/1.21.1-neoforge`

BRNQuest includes a server-authoritative quest runtime and in-game visual editor. JEI and KubeJS integration are optional at runtime; BRNTalk and shared-progress providers remain future optional integrations rather than required dependencies.

## Build

Before running Gradle, check for stale Gradle, Java, or Minecraft development processes that may retain workspace locks. Then run:

```powershell
.\gradlew.bat build --no-configuration-cache --no-daemon --console=plain
```

The development run configurations are `runClient`, `runServer`, `runGameTestServer`, `runKubeJsSmokeServer`, `runKubeJsReloadSmokeServer`, and `runData`. The two KubeJS smoke servers opt into their optional runtime automatically and validate both a clean script load and a deliberately failed reload rollback.

## Documentation

- 整合包作者的任务工作区、草稿、部署、更新与恢复流程：[`docs/WORKSPACE_zh.md`](docs/WORKSPACE_zh.md)
- Java 公共 API、稳定性等级和线程/权限边界：[`docs/PUBLIC_API_zh.md`](docs/PUBLIC_API_zh.md)
- 作者 API 与高级草稿恢复工作流：[`docs/AUTHOR_API_zh.md`](docs/AUTHOR_API_zh.md)
- 自定义 task/reward、客户端展示与编辑字段：[`docs/EXTENSION_API_zh.md`](docs/EXTENSION_API_zh.md)
- KubeJS 服务端脚本全局 API：[`docs/KUBEJS_API_zh.md`](docs/KUBEJS_API_zh.md)
- API 版本及兼容策略：[`docs/API_VERSIONING_zh.md`](docs/API_VERSIONING_zh.md)
- 独立示例附属模组：[`docs/EXAMPLE_ADDON_zh.md`](docs/EXAMPLE_ADDON_zh.md)

## Source layout

Production code uses the base package `yourscraft.jasdewstarfield.brnquest`. Stable API, data/import, progress, task, reward, owner, network, client, integration, command, and diagnostics concerns remain separated by package.

Internal implementation plans, validation fixtures, and historical acceptance reports are maintained outside this version worktree and are not part of the user documentation set.
