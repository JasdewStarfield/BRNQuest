# BRNQuest 公共 API 边界

作者草稿、编辑会话、发布、部署、恢复及 FTB 草稿导入见 [`AUTHOR_API_zh.md`](AUTHOR_API_zh.md)。作者 API 与管理员命令共用服务器权威事务，不能直接修改 active 快照。

> 当前 API 基线：`0.1.0-experimental.1`；首个承诺稳定版本：`1.0.0`。标记为 `EXPERIMENTAL` 的签名可在稳定前调整，但必须更新本文、迁移说明和契约测试。未在本文列出的包默认为 `INTERNAL`，外部模组不得依赖。详细规则见 [`API_VERSIONING_zh.md`](API_VERSIONING_zh.md)。

## 稳定性分级

| 等级 | 兼容承诺 |
|---|---|
| `STABLE` | 在已记录的 API major 版本内保持源码和行为向后兼容；破坏性变更需要版本递增和迁移说明。 |
| `EXPERIMENTAL` | 可用于集成试验；稳定前允许调整，但每次调整必须有文档、测试和 changelog。 |
| `INTERNAL` | 实现细节，不提供兼容承诺，外部调用属于不受支持行为。 |

`yourscraft.jasdewstarfield.brnquest.api.ApiStatus` 是代码内的分级标记。类型未标记时仍以本清单为准，不能因为 Java 可见性为 `public` 就推断为公共 API。

## 当前公共面清单

| 公共面 | 当前等级 | 用途 |
|---|---|---|
| `api.BrnQuestApi` | `EXPERIMENTAL` | 服务端查询和权威写操作入口。 |
| `api.AuthorApi` 及其 `author` 返回类型 | `EXPERIMENTAL` | 权限、会话和 revision 受控的作者草稿、发布与恢复入口；详见作者 API 文档。 |
| `api.*View` | `EXPERIMENTAL` | 不可变任务书、章节、任务、task、reward 和进度投影。 |
| `api.OperationResult` / `OperationStatus` | `EXPERIMENTAL` | 结构化区分成功、幂等无变化、拒绝、非法请求、未就绪、无权限和 revision 过期。 |
| `task.TaskType` / `TaskTypeRegistry` | `EXPERIMENTAL` | 服务端任务类型及构造期注册。 |
| `reward.RewardType` / `RewardTypeRegistry` | `EXPERIMENTAL` | 服务端幂等奖励类型及构造期注册。 |
| `client.ui.ClientTaskPresentation*` | `EXPERIMENTAL` | 可选客户端 task 展示。 |
| `client.ui.ClientRewardPresentation*` | `EXPERIMENTAL` | 可选客户端 reward 展示。 |
| `api.OperationContext` | `EXPERIMENTAL` | 显式描述玩家自助、管理员、集成或系统调用的 actor、authority 和审计来源。 |
| `event.BrnQuestEvents` / `BrnQuestEvent` | `EXPERIMENTAL` | 逐监听器隔离的只读服务端观察事件。 |
| `owner.ProgressOwner*` | `EXPERIMENTAL` | 稳定 owner 身份、成员、生命周期、归档投影及构造期 provider 注册。 |
| `editor.Config*` | `EXPERIMENTAL` | task/reward 字段描述、字段诊断和无描述类型的原始配置后备投影。 |
| `runtime.ExtensionRegistrationLifecycle.RegistrationState` | `EXPERIMENTAL` | common/client/script 注册窗口的只读诊断状态；关闭窗口的方法为内部 loader 操作。 |

除 `AuthorApi` 签名明确返回或接收的实验性作者契约外，`data`、`author`、`progress`、`network`、`workspace`、`compat`、`command` 和 `platform` 包，以及 `runtime` 中除上表只读生命周期状态外的类型，当前全部是 `INTERNAL`。特别是 `PlayerProgress`、`QuestProgressData`、`ProgressEngine` 和 `QuestBookManager` 不得被集成代码持有或修改。

`TaskType` 和 `RewardType` 已分别改用 `TaskContext`/`TaskView` 与 `RewardContext`/`RewardView`，客户端 presentation 也只接收不可变 `TaskView`/`RewardView` 和客户端展示上下文；这些 SPI 均不再暴露 `PlayerProgress`、`TaskDefinition` 或 `RewardDefinition`。`data` 包仍是内部实现，不能因视图转换而被视为公共 API。

## 不可变查询

`BrnQuestApi` 提供以下查询层级：

- `getActiveBook`；
- `getChapterGroups` / `getChapterGroup`；
- `getChapters` / `getChapter`；
- `getQuests` / `getQuest`；
- `getTask`；
- `getReward`；
- `getProgressOwner`；
- `getProgress`。

集合、配置 Map 和嵌套视图均为不可变副本。查询保留作者顺序、完整命名空间类型 ID、未知类型配置和 legacy alias，不返回 Codec DTO、运行时索引、SavedData 或可变集合。无效或未知 ID 返回空结果，不抛出 ID 解析异常。

任务书定义快照可以安全读取；owner 和进度存储只允许在服务端线程读取。因此 `getProgressOwner` / `getProgress` 在玩家无服务器、调用线程错误或无法安全解析 owner 时返回空结果。`ProgressView` 显式携带本次投影对应的 `ProgressOwnerId`。

## ProgressOwner

- `ProgressOwnerId` 由完整 provider `ResourceLocation` 和稳定 UUID 组成；不得使用显示名、可变队名或猜测的队长 UUID 代替稳定身份。
- `ProgressOwnerProvider` 负责在线玩家解析、成员快照、`ACTIVE` / `ARCHIVED` / `UNAVAILABLE` 生命周期和归档证据查询。
- provider 在模组构造或 common setup 注册，并在首次任务书 reload 前与服务端 task/reward 注册表一起冻结；完整命名空间同路径不继承内置语义。
- 当前唯一激活的 provider 是 `brnquest:personal`：owner UUID 等于玩家 UUID，成员仅包含本人，始终为 `ACTIVE`，不会生成伪归档。
- 其他 provider 目前可以编译、注册和接受契约测试，但不会被选择为生效 owner。启用共享 provider 必须先明确迁移、退出、换队、解散、奖励和 orphan 语义。
- schema 1 继续在 NBT 的 `players` 字段按个人 owner UUID 读写，不修改现有世界存档格式；所有事务锁和存储访问已先经过 owner 解析。

## 写操作结果

所有正式写入口提供 `OperationResult` 版本。旧布尔包装只表达 `result.success()`，用于当前调用方迁移；新集成应检查 `status` 和稳定 `code`。

- `SUCCESS`：本次调用改变了状态；
- `NO_CHANGE`：请求合法但状态已经满足，例如重复完成或重复领取；
- `REJECTED`：对象存在但当前状态、依赖、资源或类型不允许操作；
- `INVALID_REQUEST`：参数或调用线程不合法；
- `NOT_READY`：玩家未连接或没有生效任务书；
- `FORBIDDEN`：调用上下文没有所需权限；
- `STALE_REVISION`：调用者基于过期定义提交操作。

`SUCCESS` 和 `NO_CHANGE` 的 `success()` 都为 `true`，使网络重传、登录对账和脚本重试保持幂等；只有 `SUCCESS` 的 `changed()` 为 `true`。

当前结构化写入口包括：

- `completeQuestResult`；
- `completeTaskResult`；
- `addTaskProgressResult`；
- `claimRewardResult`；
- `claimAllRewardsResult`；
- `toggleTrackedResult`；
- `openQuestScreenResult`。

`claimAllRewardsResult` 逐项复用单奖励幂等事务。若中途失败，已经成功领取的奖励不会回滚，结果使用 `PARTIAL_FAILURE` 明确报告事务边界。

## 编辑器字段描述

- `TaskType.configFields` / `RewardType.configFields` 返回不可变 `ConfigFieldDescriptor` 列表；默认空列表明确选择 raw fallback。
- 字段可声明稳定键、`BOOLEAN` / `INTEGER` / `DECIMAL` / `TEXT` / `ENUM` / `RESOURCE_LOCATION` / `ITEM_STACK` 类型、默认值、必填、数值范围、枚举值、目标资源注册表、帮助文本和自定义校验器。
- `ConfigEditorSchemas.forTask` / `forReward` 只按注册类型获取描述，不按内置类型 ID 分支。字段描述或可选校验器异常被隔离，退回不丢数据的原始配置。
- `ConfigEditorSchema.rawConfig` 始终保留完整不可变字符串 Map，包括描述外字段；未知类型、缺失模组或空描述使用 `rawFallback=true`，游戏内编辑器会提供不丢字段的原始配置查看与编辑入口。
- 字段诊断只用于编辑预览。发布仍必须通过服务端 type Codec 和整本任务书校验，字段描述不能绕过权威校验。

## 线程、权限与生命周期

- 写操作只接受在线 `ServerPlayer`，并且必须在该玩家所在服务端线程调用。
- API 不会把错误线程调用静默调度到未来 tick，因为这会让返回结果与实际提交时机不一致。
- 正式写入口接受 `OperationContext`；玩家自助 authority 只能修改同一 UUID，管理员上下文只能由权限等级 2 的命令源建立，集成和系统上下文必须声明稳定来源 ID。
- 兼容期无上下文包装使用明确的 `brnquest:legacy_java_api` 集成来源并写入审计日志；新集成不得继续依赖这一包装。
- 网络 payload 使用玩家自助上下文并继续由服务端校验 revision、对象和资源；管理员命令使用管理员上下文。
- 每次上下文写操作记录 actor、source、action、target、object、status、code 和 changed；审计日志不记录任务说明、物品 NBT 或其他非必要玩家数据。
- 返回的视图不能跨 reload 代表“当前状态”；集成应按 ID 重新查询新 revision，不能缓存内部定义对象。

注册与 reload 顺序固定为：模组构造/common setup 注册 common 与预留 script 扩展 → 首次服务端资源监听器建立前统一冻结 → 解码候选任务书 → 完成 type Codec 与整本校验 → 单次原子指针替换 → 发布 reload 事件 → 对账在线玩家并同步。客户端 presentation 在 client setup 冻结，专服不会执行或加载客户端生命周期。

冻结后所有注册表明确拒绝新条目；`/reload` 不会重新开放注册窗口。候选解析、扩展校验或 fatal 校验失败时，当前有效 revision 和快照保持不变。`lastReport()` 返回防御性副本，调用方不能在提交后修改已记录的诊断。

## 后续冻结门槛

以下内容完成前不把本页接口提升为 `STABLE`：

- 至少一个 API `1.0.0` 候选版的外部集成反馈与迁移演练；
- 将当前实验性清单逐项审阅并明确提升或保留实验性。

仓库提供仅依赖公共 API 的真实示例附属模组和编译后签名门禁，使用方法见 [`EXAMPLE_ADDON_zh.md`](EXAMPLE_ADDON_zh.md)。

## 只读事件

通过 `BrnQuestEvents.subscribe` 订阅：

- `QuestCompletedEvent`；
- `TaskProgressChangedEvent`；
- `RewardClaimedEvent`；
- `QuestBookReloadedEvent`；
- `ProgressOwnerChangedEvent`。

事件不可取消，只在对应状态提交后发布，并携带稳定 ID 和不可变 view。监听器按注册顺序独立调用；单个监听器抛出的运行时异常或链接错误会被记录，但不会阻止后续监听器，也不会回滚合法任务事务。关闭 `EventSubscription` 后不再接收事件。

当前只启用个人进度 owner，因此尚无自然的 owner 切换，也不会为了制造事件而伪造切换。`ProgressOwnerChangedEvent` 使用前后两个稳定 `ProgressOwnerId`；未来只有在 provider 启用、迁移与归档事务安全提交后才发布。
