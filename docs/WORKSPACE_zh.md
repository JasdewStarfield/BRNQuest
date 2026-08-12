# BRNQuest 作者工作区

BRNQuest 使用“全局作者工作区 + 世界数据包部署”的双层流程。运行时始终只以世界数据包为权威，`config` 中的文件不会与世界任务书同时加载。

## 目录结构

```text
config/brnquest/
├─ imports/<source>/
├─ workspace/
│  ├─ pack.mcmeta
│  └─ data/<namespace>/brnquest/
│     ├─ books/<book>.json
│     ├─ chapter_groups/*.json
│     └─ chapters/*.json
└─ reports/
```

整合包作者应将整个 `config/brnquest/workspace/` 随实例发布。

## 新世界行为

世界首次启动且不存在 `datapacks/brnquest-workspace/` 时，BRNQuest 会：

1. 校验 workspace 包含 `pack.mcmeta` 和至少一个原生任务书；
2. 拒绝 workspace 中的符号链接；
3. 通过临时目录完整复制到世界；
4. 将 `file/brnquest-workspace` 加入数据包选择并触发一次资源重载。

如果目标已存在，自动部署会直接跳过，绝不因整合包更新而静默覆盖世界任务。给旧存档首次安装 BRNQuest 时也遵守同一条规则。

## 管理命令

所有 workspace 管理命令要求权限等级 2。

```text
/brnquest workspace import_ftb <source> <namespace> [book_id] [--dry-run]
/brnquest workspace deploy
/brnquest workspace deploy --replace
/brnquest workspace reload
```

- `import_ftb` 从 `config/brnquest/imports/<source>/` 生成 workspace。已有 workspace 时拒绝覆盖。
- `deploy` 只在当前世界尚无部署时复制并重载。
- `deploy --replace` 是显式更新操作。旧部署先移动到世界 `brnquest-backups/brnquest-workspace.backup-<UTC时间>/`，再部署新版本；备份不会被 Minecraft 当作额外数据包发现。
- `reload` 只重新发现并重载当前世界已经部署的 workspace，不会从 `config` 复制或覆盖文件。

原有 `/brnquest import_ftb` 保留为直接写入当前世界的兼容命令；新整合包工作流应优先使用 `workspace import_ftb`。

## 更新原则

- 编辑 `config/brnquest/workspace` 不会立即改变已游玩的世界。
- 开发阶段使用 `deploy --replace` 明确更新世界，并检查自动生成的备份。
- 发布后的整合包升级不会替玩家改写既有世界任务。
- 玩家进度仍保存在世界 `data/brnquest_progress.dat`，任务定义 revision 改变后由现有对账逻辑处理。
