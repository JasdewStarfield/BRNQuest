# Java API

[English](API.md) | [首页](../README_zh.md) | [类型扩展](EXTENSION_API_zh.md) | [KubeJS](KUBEJS_API_zh.md)

## 版本与公共边界

模组发布版本为 **1.0.0**，Java API 基线保持 **0.1.0-experimental.28**，网络协议为 **21**。任务书 schema 1 与进度 schema 2 是独立格式，客户端和服务端需一起更新。

`api.ApiStatus` 与本文公共清单共同定义兼容边界。`STABLE` 在对应 API major 内保持源码和二进制兼容，破坏性修改需要新 major 与迁移说明。`EXPERIMENTAL` 可随基线、文档和签名检查一起调整。`INTERNAL` 标记供内部使用的实现细节。

基础包名：`yourscraft.jasdewstarfield.brnquest`。

| 实验性公共面 | 用途 |
| --- | --- |
| `api.BrnQuestApi`、不可变 `*View` | 查询与权威操作 |
| `api.OperationContext`、`OperationResult`、`OperationStatus` | 权限、稳定结果与审计来源 |
| `api.AuthorApi` 及签名明确暴露的类型 | 草稿会话、编辑、发布与恢复 |
| `extension.BrnQuestPlugin`、`BrnQuestPlugins`、registrar | common 原子注册 |
| `task.TaskType`、注册表、上下文与提交选择 | 服务端目标 |
| `reward.RewardType`、注册表、上下文、领取与组合契约 | 服务端奖励 |
| `owner.ProgressOwner*` | 稳定身份、成员快照与 provider 注册 |
| `event.BrnQuestEvents`、事件与订阅 | 提交后的只读观察 |
| `editor.Config*`、`ServerFieldSources` | 字段描述、规范化与服务端查询 |
| `client.ui` 中的 presentation、提交交互、配置编辑契约；`component.EditorIcon` | 仅客户端的类型界面 |
| `runtime.ExtensionRegistrationLifecycle.RegistrationState` | 只读生命周期诊断 |

公共 API 由上表列出的契约组成。`data`、`progress`、`network`、`workspace`、`compat`、`command`、`platform`、内置实现与运行协调器属于内部代码。作者类型仅在 `AuthorApi` 明确暴露时为公共面。不要持有或修改 `PlayerProgress`、`QuestProgressData`、`ProgressEngine` 或 `QuestBookManager`。准确签名见 [API 源码](../src/main/java/yourscraft/jasdewstarfield/brnquest/api)，编译后变化由[契约哈希](../src/test/resources/contracts/public-api-signatures.txt)检查。

## 查询与视图

`BrnQuestApi` 提供 `getActiveBook`，章节组、章节、任务的集合与单 ID 查询，以及 `getTask`、`getReward`、`getProgressOwner` 和 `getProgress`。视图及嵌套集合、Map 均为不可变快照，保留作者顺序、完整类型 ID、未知配置与旧别名。非法或未知 ID 返回空结果。

任务书、组、章节、任务的 locale 重载按请求语言 → 任务书回退语言 → 原文选择；无 locale 查询保留原文。`getTranslations()` 与 `resolveText(locale, key, fallback)` 支持附加键。`QuestView.description()` 和 `descriptionFormat()` 来自同一语言，未知格式须按纯文本显示。语言选择不改变 revision。

`QuestView.behavior()` 以稳定字符串暴露依赖模式（`all_completed`、`one_completed`、`all_started`、`one_started`），并包含 `requireAllTeamMembers()`。定义描述行为，操作资格由服务端状态决定。`ChapterView.autofocusQuestId()` 可为空。书与章节创建模板在创建任务时提供默认值。

owner 和进度查询要求玩家所在服务端线程，无法安全解析玩家、服务器或 owner 时返回空。`ProgressOwnerId` 由 provider ID 和稳定 UUID 组成。个人与 OPAC 账本独立，第三方 provider 注册后不会自动被选择。`ProgressView` 标明 owner，并投影查询玩家自己的收据和追踪。全员任务显示该玩家的目标，团队未完成时可返回 `WAITING_FOR_TEAM`。

`getRewardClaimState(player, rewardId)` 返回只读上下文（owner、周期、领取代号）、revision 和建议性的 `eligible`。eligible 不等于收据未领取。条目缺失、owner 未激活或错误线程返回空。操作前重新查询并通过正常领取 API，由服务端根据当前状态检查领取权限。

## 写入、权限与结果

在在线玩家所属服务端线程调用；错误线程会拒绝，不延迟调度。显式使用 `OperationContext`：`self(player)`、检查权限的 `administrator(source)`、`integration(resourceId)` 或声明来源的系统上下文。self 只能操作相同 UUID。旧包装使用已审计的 `brnquest:legacy_java_api`，新集成应明确来源。

| 结果 | 含义 |
| --- | --- |
| `SUCCESS` | 状态发生变化 |
| `NO_CHANGE` | 合法幂等请求，状态已经满足 |
| `REJECTED` | 当前状态、资源、依赖或类型拒绝 |
| `INVALID_REQUEST` | 参数或线程不合法 |
| `NOT_READY` | 玩家未连接或没有生效书 |
| `FORBIDDEN` | 权限不足 |
| `STALE_REVISION` | 定义版本过期 |

SUCCESS 与 NO_CHANGE 的 `success()` 均为 true，仅 SUCCESS 的 `changed()` 为 true。按 status 和稳定 `code` 分支，不解析显示消息。审计记录 actor、source、action、target、object 与结果。

结构化入口：`completeQuestResult`、`completeTaskResult`、`submitTaskResult`、`addTaskProgressResult`、`claimRewardResult`、`claimAllRewardsResult`、`toggleTrackedResult`、`openQuestScreenResult`。

- `submitTaskResult` 报告单目标提交（`SUCCESS/TASK_SUBMITTED`），同任务其他目标可尚未完成；重复提交为 NO_CHANGE。
- 旧 `completeTaskResult` 报告整任务完成，因此可能在记录所选目标后返回 `UNSATISFIED`。
- `claimAllRewardsResult` 逐项执行领取事务，后续失败不撤回之前的奖励，以 `PARTIAL_FAILURE` 报告。
- 槽位选择为有界意图，服务端类型必须检查当前库存。客户端预览不能证明进度或资格。

## 作者 API 与恢复

`AuthorApi` 每次检查在线操作者、服务端线程及权限等级 2。会话操作携带 session ID、book ID 和预期 draft revision，下一次请求采用返回的新 revision。

典型流程：

1. `createEmpty`、`createFromActive`、`createFromWorkspace` 或 `importFtbDraft` 创建草稿，`catalog` 列出服务端草稿。
2. `open` 取得会话，`editor()` 返回基于稳定 ID 的 `DraftEditService`。
3. `validate`、`previewPublish`、`diff(..., WORKSPACE)` 只检查候选，不发布。
4. `save` 写草稿，`publish` 只更新工作区。
5. `deploy(player, false)` 首次部署，替换需显式 true。
6. 等待 `reload(player)` 成功后再视为新任务书生效。
7. `close` 释放会话，命令驱动期间可用 `renew` 续租。

失败即停止后续步骤。导入 dry-run 只报告，实际导入创建 `IMPORT` 草稿并拒绝同 ID 覆盖，不部署或激活。命令与 GUI 使用同一权限边界，见[作者指引](AUTHOR_GUIDE_zh.md)。

恢复流程为 `backups` → `previewRestore` → `restore(..., expectedCurrentRevision)`，只接受服务器生成的相对 backup ID。目标变化返回 `RESTORE_TARGET_CHANGED`，被覆盖目标先备份；恢复前关闭相关会话。恢复只修改一个磁盘层，不隐式 reload。

常见代码：`STALE_DRAFT_REVISION`、`REVISION_CONFLICT`、`WORKSPACE_CHANGED`、`UNSAVED_DRAFT`、`RESTORE_TARGET_CHANGED`、`DRAFT_EXISTS`、`RELOAD_VALIDATION_FAILED`。`AuthorOperationResult.message` 用于解释，不作为程序契约。审计写入失败会记录错误，不撤销已提交的内容事务。

## 事件与重载生命周期

通过 `BrnQuestEvents.subscribe` 订阅 `QuestCompletedEvent`、`TaskProgressChangedEvent`、`RewardClaimedEvent`、`QuestBookReloadedEvent` 或 `ProgressOwnerChangedEvent`。事件不可变、不可取消，只在提交后发布。监听器错误独立记录，不回滚事务或阻止后续监听器。无需订阅时关闭 `EventSubscription`。

owner 变化事件在服务端对账后发出。首次登录只建立身份；party UUID 不变的成员变化不伪造 owner 切换。授权判断始终重新查询。

Java 注册在首次服务端资源重载监听器建立前冻结，客户端 presentation 在 client setup 冻结。KubeJS 在服务端脚本加载时构建候选，仅脚本与任务书都校验成功后一起替换活动类型及任务书。失败保留旧 revision，成功后发布事件、对账在线进度并同步。跨 reload 保存 ID，不把旧内部定义或视图当当前状态。

## 附属兼容提示

模组 1.0.0 相对 experimental.28 没有修改 Java 签名。近期 API 变化：

| 基线 | 需核对的变化 |
| --- | --- |
| experimental.23 | 可选奖励 `contentSummary`，通用布局负责标题 |
| experimental.24 | 带上下文的 `normalizeConfig`，保留未知键与无注册表情形 |
| experimental.25 | 槽位提交、候选页、合成进度及 `submitTaskResult` |
| experimental.26 | 类型标题与领取 UI 钩子、配置补丁与创建页、只读领取状态查询 |
| experimental.27 | 全员完成投影与队伍共享设置 |
| experimental.28 | 目标与奖励默认 `hiddenFromCreation()` |

默认方法及保留构造器兼容所记录的旧调用路径。依赖 record 组件反射、生成相等判断或解构的消费者需核对新增组件。升级时以当前契约及示例附属编译结果为准。
