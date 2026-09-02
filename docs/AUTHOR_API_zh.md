# BRNQuest 作者 API 与恢复工作流

BRNQuest 的高级草稿能力只有一套服务端权威实现。管理员命令、游戏内高级草稿编辑器和附属模组都应通过 `AuthorApi` / `DraftEditService` 的事务边界工作，不得直接改写任务书 JSON、`QuestBookManager.active()` 或世界数据包。

## 权限与线程边界

- 所有入口接收目标服务器当前在线的 `ServerPlayer`，每次操作重新检查权限等级 2。
- 会话 token 只返回给持有者；后续读取、修改、保存和发布都携带 `sessionId + bookId + expectedDraftRevision`。
- 调用应发生在服务器线程。客户端只能经受鉴权网络协议提交结构化意图。
- `AuthorOperationResult` 的稳定 `status` 和 `code` 用于程序分支，`message` 只用于人类阅读。

## 核心入口

`yourscraft.jasdewstarfield.brnquest.api.AuthorApi` 提供：

- `catalog(player)`：以目标服务器权限等级 2 为边界，返回按完整任务书 ID 稳定排序的可打开草稿目录；客户端本地文件不参与目录生成。

- `createEmpty`、`createFromActive`、`createFromWorkspace`；
- `open`、`renew`、`close`；
- `editor()`：取得稳定 ID 驱动的 `DraftEditService`，编辑章节组、章节、任务、依赖、task 和 reward；
- `validate`、`diff`、`save`、`publish`；
- `deploy`、`reload`；
- `backups`、`previewRestore`、`restore`；
- `importFtbDraft`。

不可缓存跨 reload 的内部定义。每次成功修改后都从返回值取得新 `draftRevision`，下一次请求使用该 revision。

## 从空任务书到首次生效

下面是服务调用顺序；命令行使用等价的 `/brnquest author ...` 命令。

1. `AuthorApi.createEmpty(player, bookId, title)` 创建服务器草稿。
2. `AuthorApi.open(player, bookId)` 取得 session UUID 和初始 revision。
3. 通过 `AuthorApi.editor()` 依次创建章节组、章节、任务、task/reward 和依赖；每次传入上一次返回的 revision。
4. `AuthorApi.validate(...)` 执行完整校验。
5. `AuthorApi.previewPublish(...)` 以真实发布门禁预检当前 revision；`AuthorApi.diff(..., WORKSPACE)` 提供对应的语义差异。两者都只读，不写 workspace 或备份。
6. `AuthorApi.save(...)` 将会话内容原子保存到草稿目录。
7. `AuthorApi.publish(...)` 只更新作者 workspace。
8. `AuthorApi.deploy(player, false)` 首次复制到世界；后续显式使用 `replace=true`。
9. `AuthorApi.reload(player)` 成功后才切换 active 快照、对账进度并同步在线玩家。
10. `AuthorApi.close(...)` 释放编辑租约。

任一步失败都停止后续步骤。尤其不能在 publish 失败后继续 deploy，也不能把“reload 已请求”当作“reload 已成功”。

无图形界面时，`/brnquest author add_group`、`add_chapter`、`add_quest`、`add_dependency`、`add_task` 和 `add_reward` 对应上述核心创建操作。命令接受标准资源位置 ID，并在每次成功修改后回显下一步所需 revision；task/reward 配置使用不含嵌套结构的 JSON string-map。详细参数见 [`WORKSPACE_zh.md`](WORKSPACE_zh.md)。

## FTB 导入

`AuthorApi.importFtbDraft` 和所有旧名称的 `import_ftb` 命令现在都只有两种结果：

- dry-run：解析、校验并生成机器报告，不写任务内容；
- 正式导入：在 `config/brnquest/drafts/` 创建 `IMPORT` 来源草稿。

导入不会写 workspace、世界数据包或 active 快照。导入草稿必须重新打开会话，经过 validate、diff、save、publish、deploy 和 reload。已有同 ID 草稿时返回 `DRAFT_EXISTS`，不会覆盖源文件或现有作者内容。

当前 FTB v13 转换还会解析章节默认值与任务覆盖值中的可见性、详情/文本隐藏、依赖判定、最少前置数、目标顺序及重复/冷却配置，并识别经验目标、经验值奖励和经验等级奖励。`only_from_crafting` 仅在目标恰好包含一个物品匹配条目时可发布；这样可以把服务端真实合成产出无歧义地映射到单一进度计数。未支持字段继续留在导入报告中，不会伪装成已兼容。

## 备份与恢复

`BackupKind` 分为：

- `DRAFT`：`config/brnquest/backups/drafts/`；
- `WORKSPACE`：`config/brnquest/backups/workspace/`；
- `DEPLOYED`：世界目录外的 `brnquest-backups/`。

恢复固定为三步：

1. `backups` 获取服务端生成的相对 backup ID；不接受任意绝对路径。
2. `previewRestore` 校验备份并取得 `currentRevision`、备份 revision、文件数和是否会覆盖。
3. 用户确认后调用 `restore(..., expectedCurrentRevision)`。目标已变化时返回 `RESTORE_TARGET_CHANGED`；成功替换前会为当前目标再生成一份 `restore-overwritten` 安全备份。

恢复只修改所选磁盘层，不隐式 reload。恢复 workspace 或 deployed 后仍需显式检查、部署或 reload。存在相关编辑会话时草稿恢复返回 `ACTIVE_EDIT_SESSION`；关闭会话、恢复完成后必须重新打开，以免继续使用旧内存快照。

## 审计与故障排查

保存、发布和恢复写入目标服务器 `config/brnquest/reports/author-audit.jsonl`。每行是独立 UTF-8 JSON，包含 UTC 时间、操作者 UUID/名称、操作、对象、前后 revision、状态、稳定代码和消息。审计时间不进入内容 revision，审计写入失败会记录服务器错误但不会回滚已经成功的内容事务。

常见稳定代码：

- `STALE_DRAFT_REVISION`：客户端或脚本仍在使用旧会话 revision；
- `REVISION_CONFLICT` / `WORKSPACE_CHANGED`：磁盘或来源基线被外部修改；
- `UNSAVED_DRAFT`：发布前仍有会话内修改；
- `RESTORE_TARGET_CHANGED`：预览后目标又发生变化；
- `DRAFT_EXISTS`：导入或创建试图覆盖已有草稿；
- `RELOAD_VALIDATION_FAILED`：候选未通过 reload，旧 active 快照仍然有效。

## 兼容性

`AuthorApi` 当前标注为 `EXPERIMENTAL`。公开签名受 `PublicApiSnapshotTest` 保护；版本升级时仍应检查稳定错误代码和语义，不应依赖日志文本、磁盘绝对路径或内部 staging 名称。
