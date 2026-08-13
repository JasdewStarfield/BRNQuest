# 阶段 4 验收记录

## 4.1 生效快照与作者草稿分离

- 草稿固定存放于目标服务器的 `config/brnquest/drafts/<namespace>/<book-path>/`，不进入资源包 `data/` 搜索路径。
- `book.json` 使用 `NativeBookJson` 的确定性 UTF-8 表示；`draft.json` 只记录格式版本、任务书 ID、基础 revision 和草稿 revision。
- 可从当前生效任务书、服务器 workspace 或空任务书创建草稿。创建接口要求操作者是目标服务器当前在线且具有权限等级 2 的玩家。
- 草稿使用不可变 `QuestBookDefinition` 快照，不写 `QuestBookManager.active()`；只有后续显式 deploy + reload 才能切换生效快照。
- 目录写入先进入 UUID staging 目录再移动；重复创建返回 `DRAFT_EXISTS`，外部修改内容但未更新 manifest 返回 `DRAFT_REVISION_MISMATCH`。

## 4.2 服务端编辑会话

- `EditSessionService` 按 `MinecraftServer` 实例隔离，同一服务器、同一任务书只允许一个写者，不同服务器互不占锁。
- 开启、续租、关闭和查看均从目标服务器玩家列表确认连接身份，并重新检查当前权限等级 2；客户端本地 OP 信息不参与授权。
- 会话绑定随机 token、任务书 ID、操作者 UUID/名称、基础 revision、草稿 revision 和到期 tick；默认空闲超时 5 分钟。
- 续租和关闭必须提交预期草稿 revision，旧客户端返回 `STALE_DRAFT_REVISION`；其他管理员只能查看不含 token 的占用状态。
- 玩家断线、权限撤销后被清理、服务器停止或租约超时都会释放内存会话，已落盘草稿不受影响。

## 远程服务器与同步边界

- 管理员连接远程服务器时，草稿文件、会话表、权限判断和未来 CRUD 全部位于该远程服务器；客户端不会读取或覆盖自己的本地 `config/brnquest`。
- 4.1–4.2 尚未增加编辑器网络协议或界面入口；阶段 5 客户端只能通过后续受鉴权协议提交结构化操作，不能上传整本任务书覆盖服务器状态。
- 草稿和租约状态只面向获授权管理员，不向普通玩家广播。正式 `publish` 仍只修改 workspace；只有显式 deploy 并成功 reload 后，现有 `reconcileOnlinePlayers` 才向所有在线玩家同步新生效任务书和进度，之后加入的玩家取得同一 active revision。

## 自动验收

- `DraftRepositoryTest`：草稿目录隔离、UTF-8/LF manifest、确定性加载、重复创建冲突、外部修改冲突和 workspace 来源。
- `EditSessionServiceTest`：同服单写者、跨服隔离、重复打开幂等、旧 revision、过期、断线释放和重开。
- `remoteAdministratorsUseTargetServerPermissionsAndLeases` GameTest：目标服务器 2 级管理员与普通玩家边界、服务器草稿创建、第二管理员占用冲突和断线接管。
- `runGameTestServer`：15/15 required GameTest 通过。

## 4.3 草稿 CRUD 服务

- `DraftBookEditor` 只接收不可变任务书并返回新候选，不持有或修改 `QuestBookManager.active()`。
- `DraftEditService` 为任务书标题、章节组、章节、任务节点、依赖、task 和 reward 提供稳定 ID 驱动的创建、复制、修改、排序/移动和删除入口。
- 普通 update 不能改对象 ID，也不能借更新父容器覆盖其子对象；移动任务会同步更新 `chapterId`，保留任务内容和稳定 ID。
- 删除被依赖任务、非空章节或非空章节组默认返回结构化冲突；名称明确的 `remove*WithContents` / `removeQuestAndReferences` 才执行级联，并返回完整受影响对象集合。
- 每次成功操作先构造并校验完整候选，最后只替换一次会话内 `DraftSnapshot`；无效操作、旧 revision 或错误任务书不会产生部分修改。

## 4.4 增量与完整校验

- 每次 CRUD 对不可变候选检查所有权、重复 ID、容器与依赖引用、循环、不可达任务、legacy alias 目标、未知 task/reward type 和 type Codec。
- 对受影响对象检查必填标题、文本上限、有限坐标，以及第三方 `ConfigFieldDescriptor` 的类型、范围、枚举和自定义 validator。
- 显式完整校验扫描整本任务书，并附加章节组、章节和任务数量上限；诊断保留稳定代码、对象 ID 与字段路径。
- 错误或 fatal 诊断返回 `DRAFT_VALIDATION_FAILED`，结果附带诊断但仍指向原合法 snapshot；无效候选不计算 revision、不进入会话，也不广播给玩家。
- 专服 GameTest 额外验证远程管理员成功编辑、延迟旧 revision 被拒绝、无效编辑被拒绝且服务器会话 revision 保持不变。
- 完整 JUnit：85 项通过；`runGameTestServer`：15/15 required GameTest 通过；完整 `build` 通过。

## 4.5 确定性序列化

- 草稿内容继续复用 `NativeBookJson` 的稳定字段顺序、UTF-8 与 LF 输出；manifest 升级为格式 2，并显式记录草稿来源 `ACTIVE`、`WORKSPACE`、`EMPTY`、`IMPORT` 或 `UNKNOWN`。
- 格式 1 manifest 仍可读取，缺少来源时按 `UNKNOWN` 保守处理，不会因为升级而丢弃旧服务器草稿。
- 保存前比较规范化后的 `book.json` 与 `draft.json` 字节；内容完全相同时返回 `NO_CHANGE`，不改 revision、不创建备份，也不引入时间戳或绝对路径。

## 4.6 原子保存与备份

- 保存先写入目标草稿目录同级的 UUID staging 目录，文件写入后执行 `force(true)`，再从 staging 重新读取并校验任务书、manifest 和 revision。
- 覆盖前把旧目录移动到 `config/brnquest/backups/drafts/<namespace>/<book-path>/<old-revision>-<uuid>/`；该目录不在数据包搜索路径中，不会被 Minecraft 自动加载。
- staging 验证成功后才替换目标目录；故障注入覆盖“旧目录已移入备份、但新目录尚未激活”的窗口，并验证自动恢复旧草稿及清理 staging。若恢复本身遭遇底层文件系统故障，旧内容仍保留在备份目录中供后续恢复流程使用。

## 4.7 revision 冲突控制

- 编辑会话同时记录当前草稿 revision 与最后成功保存的 revision；两者不同时 `dirty=true`，成功保存后只在会话仍指向同一快照时推进保存点。
- 保存检查 active、来源 base、会话 draft、磁盘 draft 和 workspace revision。磁盘草稿被外部修改、active/workspace 来源变化，或空白草稿创建后出现同 ID workspace 时均返回 `REVISION_CONFLICT`，不会静默覆盖。
- 冲突结果包含完整 `RevisionVector`、稳定冲突代码、期望 revision、实际 revision 和消息，供后续命令与客户端展示；来源未知的旧草稿只执行可靠的磁盘并发检查。

## 4.8 结构化差异预览

- `QuestBookDiffer` 比较解码后的不可变定义，输出任务书、章节组、章节、任务节点、task 和 reward 的新增、删除、属性变化、移动/排序、依赖、类型和配置变化。
- 任务节点在旧 ID 唯一且非空时可把 ID 变化识别为改名；无法无歧义配对时保守表示为删除和新增。
- 差异条目按对象类型、对象 ID、变化类型和字段稳定排序。输入先经 `NativeBookJson` 解码，因此空白、缩进或 JSON 字段排列等纯格式变化不会产生语义差异。
- 已授权远程管理员可预览当前会话相对于磁盘草稿、workspace 或 active 的差异；普通玩家不能读取草稿内容。

## 4.5–4.8 边界

- `save` 仅更新目标服务器草稿目录，不会 publish workspace、deploy 世界数据包、reload active 快照或向普通玩家同步；这些仍由 4.9–4.10 的显式命令和事务完成。
- 备份恢复、审计记录和导入来源的完整工作流分别留在 4.11–4.12；本批只提供保存事务产生的可恢复备份。

## 4.5–4.8 自动验收

- 完整 JUnit：93 项通过，覆盖确定性保存与 `NO_CHANGE`、格式 1 manifest 兼容、备份读取、失败恢复、外部磁盘 revision 冲突、四方来源冲突和结构化 diff。
- `remoteAdministratorsUseTargetServerPermissionsAndLeases` GameTest：远程管理员编辑后会话为 dirty，目标服务器保存成功后恢复 clean，重复保存幂等，保存后差异为空。
- `runGameTestServer`：15/15 required GameTest 通过。
- 完整 `build` 与 `git diff --check` 通过。

## 4.9 作者命令

- 新增权限等级 2 的 `/brnquest author` 命令树，覆盖从 active/workspace/空任务书创建草稿、打开/查看/续租/关闭/丢弃会话、改标题、validate、diff、save、publish、deploy 和 reload。
- 会话相关命令显式携带 session UUID、任务书 ID 和预期 draft revision；`open` 只把 secret session UUID 返回给当前操作者，`status` 不泄露 token。
- 所有内容操作委托给 4.1–4.8 的服务端服务，不建立命令专用草稿模型；命令返回稳定结果代码并写入操作者、操作、对象、状态和代码审计日志。
- `discard` 仅关闭内存会话并放弃未保存修改，保留磁盘草稿；阶段 5 UI 将复用相同服务调用。

## 4.10 发布与部署事务

- `DraftPublishService` 只接受已保存且完整校验通过的会话草稿；发布前再次检查磁盘、来源、active 和 workspace revision。非 workspace 来源不会静默覆盖已有的同 ID workspace 任务书。
- publish 在同文件系统 UUID staging 中复制现有 workspace、替换目标任务书并重新解码验证；覆盖前把整个旧 workspace 移入 `config/brnquest/backups/workspace/`，失败时恢复旧 workspace。
- publish 成功后会话转为 `WORKSPACE` 来源并以已发布 revision 作为新基线，因此远程管理员可以继续编辑、保存和再次发布；相同内容重复发布返回 `NO_CHANGE`。
- deploy 使用唯一 staging，校验复制结果，并在替换世界数据包前把旧包移到世界目录外的 `brnquest-backups/`。故障注入验证即使新包已激活，失败仍恢复旧部署并清理 staging。
- 原有和新增 deploy 命令都不再隐式 reload。只有显式 reload 成功后才切换 active 快照并对账在线玩家；fatal 校验会报告失败并保留上一 active 快照。

## 4.9–4.10 自动验收

- `WorkspacePublishRepositoryTest` 覆盖首次发布、重复发布幂等、保留 workspace 其他文件、覆盖备份、外部 revision 冲突和激活后失败恢复。
- `WorkspaceDeploymentServiceTest` 覆盖首次部署、拒绝静默覆盖、显式替换备份、无效 workspace 以及激活后失败恢复。
- 远程管理员 GameTest 覆盖 save → publish、重复 publish、继续编辑、未保存禁止 publish、再次 save/publish，以及专服作者命令树注册。
- 完整 JUnit：100 项通过；`runGameTestServer`：15/15 required GameTest 通过；完整 `build` 与 `git diff --check` 通过。首次 publish/deploy 在激活后发生故障时也会恢复为“未发布/未部署”状态。
