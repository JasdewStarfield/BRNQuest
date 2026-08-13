# BRNQuest 公共 API 边界

> 当前 API 版本：阶段 3 实验性。标记为 `EXPERIMENTAL` 的签名可在首个稳定 API 版本前调整，但必须更新本文和契约测试。未在本文列出的包默认为 `INTERNAL`，外部模组不得依赖。

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
| `api.*View` | `EXPERIMENTAL` | 不可变任务书、章节、任务、task、reward 和进度投影。 |
| `api.OperationResult` / `OperationStatus` | `EXPERIMENTAL` | 结构化区分成功、幂等无变化、拒绝、非法请求、未就绪、无权限和 revision 过期。 |
| `task.TaskType` / `TaskTypeRegistry` | `EXPERIMENTAL` | 服务端任务类型及构造期注册。 |
| `reward.RewardType` / `RewardTypeRegistry` | `EXPERIMENTAL` | 服务端幂等奖励类型及构造期注册。 |
| `client.ui.ClientTaskPresentation*` | `EXPERIMENTAL` | 可选客户端 task 展示。 |
| `client.ui.ClientRewardPresentation*` | `EXPERIMENTAL` | 可选客户端 reward 展示。 |
| `api.OperationContext` | `EXPERIMENTAL` | 显式描述玩家自助、管理员、集成或系统调用的 actor、authority 和审计来源。 |
| `event.BrnQuestEvents` / `BrnQuestEvent` | `EXPERIMENTAL` | 逐监听器隔离的只读服务端观察事件。 |

`data`、`progress`、`runtime`、`network`、`workspace`、`compat`、`command` 和 `platform` 包当前全部是 `INTERNAL`。特别是 `PlayerProgress`、`QuestProgressData`、`ProgressEngine` 和 `QuestBookManager` 不得被集成代码持有或修改。

`TaskType` 和 `RewardType` 已分别改用 `TaskContext`/`TaskView` 与 `RewardContext`/`RewardView`，不再暴露 `PlayerProgress`、`TaskDefinition` 或 `RewardDefinition`。客户端 presentation 的阶段 2 签名仍直接引用内部 definition；这项剩余泄漏属于 3.7，不能据此把整个 `data` 包视为公共 API。

## 不可变查询

`BrnQuestApi` 提供以下查询层级：

- `getActiveBook`；
- `getChapterGroups` / `getChapterGroup`；
- `getChapters` / `getChapter`；
- `getQuests` / `getQuest`；
- `getTask`；
- `getReward`；
- `getProgress`。

集合、配置 Map 和嵌套视图均为不可变副本。查询保留作者顺序、完整命名空间类型 ID、未知类型配置和 legacy alias，不返回 Codec DTO、运行时索引、SavedData 或可变集合。无效或未知 ID 返回空结果，不抛出 ID 解析异常。

任务书定义快照可以安全读取；玩家进度存储只允许在服务端线程读取。因此 `getProgress` 在玩家无服务器或调用线程错误时返回空结果。

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

## 线程、权限与生命周期

- 写操作只接受在线 `ServerPlayer`，并且必须在该玩家所在服务端线程调用。
- API 不会把错误线程调用静默调度到未来 tick，因为这会让返回结果与实际提交时机不一致。
- 正式写入口接受 `OperationContext`；玩家自助 authority 只能修改同一 UUID，管理员上下文只能由权限等级 2 的命令源建立，集成和系统上下文必须声明稳定来源 ID。
- 兼容期无上下文包装使用明确的 `brnquest:legacy_java_api` 集成来源并写入审计日志；新集成不得继续依赖这一包装。
- 网络 payload 使用玩家自助上下文并继续由服务端校验 revision、对象和资源；管理员命令使用管理员上下文。
- 每次上下文写操作记录 actor、source、action、target、object、status、code 和 changed；审计日志不记录任务说明、物品 NBT 或其他非必要玩家数据。
- 返回的视图不能跨 reload 代表“当前状态”；集成应按 ID 重新查询新 revision，不能缓存内部定义对象。

## 后续冻结门槛

以下内容完成前不把本页接口提升为 `STABLE`：

- ProgressOwner SPI；
- task/reward 的编辑器字段描述 SPI；
- 注册与 reload 生命周期契约；
- 仅依赖公共 API 的示例附属模组；
- 公共签名兼容门禁和最低兼容版本文档。

## 只读事件

通过 `BrnQuestEvents.subscribe` 订阅：

- `QuestCompletedEvent`；
- `TaskProgressChangedEvent`；
- `RewardClaimedEvent`；
- `QuestBookReloadedEvent`；
- `ProgressOwnerChangedEvent`。

事件不可取消，只在对应状态提交后发布，并携带稳定 ID 和不可变 view。监听器按注册顺序独立调用；单个监听器抛出的运行时异常或链接错误会被记录，但不会阻止后续监听器，也不会回滚合法任务事务。关闭 `EventSubscription` 后不再接收事件。

当前只启用个人进度 owner，因此尚无自然的 owner 切换；`ProgressOwnerChangedEvent` 的载荷契约已建立，阶段 3.8 接入 provider 生命周期时才会产生实际事件。
