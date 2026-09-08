# BRNQuest 作者工作区

BRNQuest 使用“全局作者工作区 + 世界数据包部署”的双层流程。运行时始终只以世界数据包为权威，`config` 中的文件不会与世界任务书同时加载。

## 目录结构

```text
config/brnquest/
├─ imports/<source>/
├─ drafts/<namespace>/<book-path>/
├─ backups/
├─ workspace/
│  ├─ pack.mcmeta
│  └─ data/<namespace>/brnquest/
│     ├─ books/<book>.json
│     ├─ chapter_groups/*.json
│     └─ chapters/*.json
└─ reports/
```

整合包作者应将整个 `config/brnquest/workspace/` 随实例发布。

服务端启动时会自动创建 imports、drafts、backups、workspace 和 reports 目录。空 workspace 表示尚未发布任务，不会被当作损坏数据包。

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

/brnquest author create active
/brnquest author create empty <book> <title>
/brnquest author create workspace <book>
/brnquest author open <book>
/brnquest author status <book>
/brnquest author renew <session> <revision>
/brnquest author close <session> <revision>
/brnquest author discard <session> <revision>
/brnquest author set_title <session> <book> <revision> <title>
/brnquest author add_group <session> <book> <revision> <group> <order> <title>
/brnquest author add_chapter <session> <book> <revision> <chapter> <group> <order> <icon> <title>
/brnquest author add_quest <session> <book> <revision> <quest> <chapter> <x> <y> <icon> <title>
/brnquest author add_dependency <session> <book> <revision> <quest> <dependency>
/brnquest author add_task <session> <book> <revision> <quest> <task> <type> <optional> <config-json>
/brnquest author add_reward <session> <book> <revision> <quest> <reward> <type> <claim-policy> <team> <config-json>
/brnquest author validate <session> <book> <revision>
/brnquest author diff <session> <book> <revision> <saved_draft|workspace|active>
/brnquest author save <session> <book> <revision>
/brnquest author publish <session> <book> <revision>
/brnquest author deploy [--replace]
/brnquest author backups <draft|workspace|deployed>
/brnquest author restore_preview <kind> <backup-id>
/brnquest author restore <kind> <backup-id> <current-revision|->
/brnquest author reload
```

- `import_ftb` 从 `config/brnquest/imports/<source>/` 创建独立 `IMPORT` 草稿；不会直接修改 workspace。
- `deploy` 只在当前世界尚无部署时复制；不会隐式 reload。
- `deploy --replace` 是显式更新操作。旧部署先移动到世界 `brnquest-backups/brnquest-workspace.backup-<UTC时间>/`，再部署新版本；备份不会被 Minecraft 当作额外数据包发现。
- `reload` 只重新发现并重载当前世界已经部署的 workspace，不会从 `config` 复制或覆盖文件。
- `author publish` 要求会话草稿已经保存且通过完整校验，只原子更新 workspace；重复发布相同内容返回 `NO_CHANGE`。
- `author deploy` 与 `author reload` 严格分离。只有 reload 成功后 active 快照和在线玩家同步才会变化。

每条 `author` 命令都重新使用目标服务器的在线身份与权限；命令回显稳定结果代码，日志记录操作者、操作、对象、状态和代码。`open` 返回后续命令必须携带的 session UUID 与当前 revision。`discard` 只丢弃会话内未保存状态，不删除最后一份磁盘草稿。

高级草稿会话默认在最后一次成功读取、修改、保存、发布或显式续租后保持 30 分钟；断线、服务器停止或权限撤销会立即释放。游戏内编辑器会自动续租，命令作者长时间停留时仍可执行 `renew <session> <revision>`。

内容命令使用原生 `namespace:path` 资源位置参数；每次成功修改都会回显新的 `revision=`，下一条命令必须使用该值。task/reward 的 `config-json` 必须是只含原始值的 JSON 对象，例如 `{"title":"确认任务"}` 或 `{"item":"{count:1,id:\"minecraft:stone\"}","count":"4"}`；嵌套对象和数组会以 `INVALID_CONFIG_JSON` 拒绝，避免有损展开。命令覆盖无 GUI 的核心建书流程；完整复制、移动、更新和级联删除能力由同一 `DraftEditService` 提供，并由游戏内高级草稿编辑器复用。

恢复必须先执行 `restore_preview`，再把回显的 `current_revision` 原样传给 `restore`；当前目标不存在时使用单个 `-`。恢复不会隐式 deploy 或 reload。完整 API 与恢复说明见 [`AUTHOR_API_zh.md`](AUTHOR_API_zh.md)。

原有 `/brnquest import_ftb` 与 `/brnquest workspace import_ftb` 名称继续兼容，但两者都只从统一 inbox 创建 `IMPORT` 来源草稿，不再直接写 workspace 或世界数据包。

## 更新原则

- 编辑 `config/brnquest/workspace` 不会立即改变已游玩的世界。
- 开发任务书时依次使用 `publish`、`deploy --replace`、`reload`，每步确认成功后再继续，并检查自动生成的备份。
- 发布后的整合包升级不会替玩家改写既有世界任务。
- 玩家进度仍保存在世界 `data/brnquest_progress.dat`，任务定义 revision 改变后由现有对账逻辑处理。

## 服务端只读健康检查

管理员可运行 `/brnquest health` 查看当前活动任务书、任务数量、UTF-8 正文大小、分块预算，以及最近一次已完成的任务书加载结果。`/brnquest health details` 额外显示完整任务书 ID、revision、来源资源、失败候选及最多 5 条技术诊断。两个入口均要求权限等级 2，查询成功返回 1，不以加载成功与否作为命令执行结果。

加载被拒绝时，输出区分失败候选与当时保留的有效 revision；实时编辑不会覆盖这条加载记录。未知候选的任务数量在技术详情中以 `-1` 表示。技术诊断最多保留 32 条、优先严重项，输出每条最多 1024 个字符。现有 `/brnquest diagnose` 继续输出当前完整校验报告，其内容可能因后续实时编辑而变化。

这里的字节数和分块数是当前活动任务书的逻辑正文预算，不包含包头、压缩或协议开销，也不表示客户端已收到或应用。查询不触发 reload、同步、保存或租约续期；结果保存在内存中，不是跨服务器重启的历史日志。
