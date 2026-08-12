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

## Source layout

Production code uses the base package `yourscraft.jasdewstarfield.brnquest`. As features are introduced, keep stable API, data/import, progress, task, reward, team, network, client, integration, command, and diagnostics concerns in separate packages as specified by the shared design document.
