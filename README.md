# BRNQuest for Minecraft 1.21.1 (NeoForge)

This worktree contains the first BRNQuest implementation target:

- Minecraft 1.21.1
- NeoForge 21.1
- Java 21
- branch `mc/1.21.1-neoforge`

The initial scaffold is intentionally dependency-free beyond NeoForge. KubeJS, BRNTalk, team-provider, and UI integrations will be added behind optional boundaries as their milestones are implemented.

## Build

Before running Gradle, check for stale Gradle, Java, or Minecraft development processes that may retain workspace locks. Then run:

```powershell
.\gradlew.bat build --no-configuration-cache --no-daemon --console=plain
```

The development run configurations are `runClient`, `runServer`, `runGameTestServer`, and `runData`.

The current manual client acceptance procedure is documented in
[`docs/CLIENT_ACCEPTANCE_zh.md`](docs/CLIENT_ACCEPTANCE_zh.md).

日常回归优先使用零可选依赖的
[`docs/TEST_PACK_zh.md`](docs/TEST_PACK_zh.md) 原生验收任务包；EOW fixture 保留用于导入兼容测试。

整合包作者的全局任务工作区、新世界自动部署和显式更新流程见
[`docs/WORKSPACE_zh.md`](docs/WORKSPACE_zh.md)。

## Source layout

Production code uses the base package `yourscraft.jasdewstarfield.brnquest`. As features are introduced, keep stable API, data/import, progress, task, reward, team, network, client, integration, command, and diagnostics concerns in separate packages as specified by the shared design document.
