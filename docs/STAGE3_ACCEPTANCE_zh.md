# BRNQuest 阶段 3 验收记录

## 2026-08-13 第一批：公共查询与操作结果

> 对应计划项：3.1、3.2、3.3。实现、自动测试、文档、构建和相关 GameTest 已通过，版本提交为 `263cf9a`。

### 实现范围

- 增加 `ApiStatus` 和 `ApiStability`，将当前公共 API、任务/奖励 SPI、客户端 presentation 和已有事件统一标记为 `EXPERIMENTAL`。
- 建立公共面清单，明确未列出包默认为 `INTERNAL`，并记录阶段 2 SPI 对内部定义/进度类型的过渡性泄漏。
- 增加任务书、章节组、章节、任务、task 和 reward 的不可变视图及完整查询入口。
- 将玩家进度投影限制为当前任务所属的 task/reward，使用资源位置键并补充完成时间。
- 无效查询 ID 改为空结果，避免把 `ResourceLocation` 解析异常暴露给集成调用方。
- 将写操作结果扩展为 `SUCCESS`、`NO_CHANGE`、`REJECTED`、`INVALID_REQUEST`、`NOT_READY`、`FORBIDDEN` 和 `STALE_REVISION`。
- 为完成 task、推进 task、领取单项/全部奖励、追踪任务和先同步后开屏提供结构化入口，同时保留现有布尔便利包装。
- 重复完成、重复提交和重复领取明确返回成功的 `NO_CHANGE`，不再伪装为本次发生了状态修改。

### 自动验证

- 构建前进程检查发现一个从 08:47 运行的未知 Java 进程；沙箱无法读取其命令行。`gradlew --status` 确认没有 Gradle daemon，后续单次 daemon 均正常退出，未发现工作区锁。
- `gradlew.bat compileJava compileTestJava --no-configuration-cache --no-daemon --console=plain`：通过。
- `gradlew.bat test --no-configuration-cache --no-daemon --console=plain`：通过。
- JUnit 汇总：18 个 suite、45 项测试、0 failure、0 error、0 skipped。
- `gradlew.bat build --no-configuration-cache --no-daemon --console=plain`：通过。
- JUnit 汇总：21 个 suite、51 项测试、0 failure、0 error、0 skipped。
- `gradlew.bat runGameTestServer --no-configuration-cache --no-daemon --console=plain`：9/9 required GameTest 通过并正常保存、关闭。
- 两个工作树相关文件的尾随空白检查通过；版本工作树 `git diff --check` 通过。

### 本批未包含

- 3.4 的显式 actor、跨玩家权限和审计上下文；当前 Java API 仍视为受信任服务端集成入口。
- 完整只读事件集与观察者异常隔离。
- 用公共不可变上下文替换 `TaskType`、`RewardType` 和 presentation 对内部定义/进度类型的引用。
- ProgressOwner、编辑器字段描述、示例附属模组和公共签名兼容门禁。
- 客户端 UI 没有变化，因此本批未要求新增人工 UI 回归。

## 2026-08-13 第二批：调用上下文、事件与服务端 SPI

> 对应计划项：3.4、3.5、3.6。实现与验收提交为 `0e0c233`。

### 实现范围

- 增加不可变 `OperationContext`，区分玩家自助、管理员、集成和系统 authority；玩家自助操作只能修改同一 UUID。
- 权限等级 2 的管理员命令显式建立管理员上下文；serverbound payload 使用玩家自助上下文；库存观察器使用具名系统上下文。
- 上下文写操作统一记录必要的 actor、source、action、target、object 和结构化结果，不在审计日志写入任务文本或物品 NBT。
- 建立 `BrnQuestEvents` 隔离事件总线和可关闭订阅；完成任务、task 进度变化、奖励领取和成功 reload 均在状态提交后发布不可变事件。
- 建立 `ProgressOwnerChangedEvent` 的稳定载荷；个人 owner 阶段没有实际切换，事件将在 3.8 provider 生命周期启用。
- 将 `TaskType` 改为 `TaskContext` + `TaskView`，将 `RewardType` 改为 `RewardContext` + `RewardView`。
- 解码、内部 definition 转换和可变进度访问收回 `INTERNAL` executor，使用反射契约测试阻止 `data`/`progress` 类型重新进入公共 SPI 签名。
- 保留完整命名空间注册、Codec 诊断、冻结后拒绝注册、任务资源消费和奖励幂等账本语义。

### 明确边界

- `OperationContext.integration` 仅供受信任的进程内服务端模组；它不是客户端权限提升机制，客户端不能构造或传输该对象。
- 事件是不可取消的观察通知。监听器失败被隔离，但外部监听器自己的副作用仍由监听器负责幂等和重试。
- 3.7 才会把客户端 presentation 从内部 definition 类型迁移到公开 view；本批不提前勾选 3.7。

### 验证记录

- 构建前 `Get-Process` 和 `gradlew --status` 均确认无残留 Java/Gradle 进程和 daemon。
- `gradlew.bat build --no-configuration-cache --no-daemon --console=plain`：通过。
- 首轮 11 项 GameTest 中新增权限/事件测试通过；既有奖励溢出测试因在玩家周围 8 格统计到并行用例的两颗钻石而失败，预期 1、实得 3。
- 将该测试改为记录领取前实体 UUID、只统计本次领取产生的新实体；未修改奖励实现。
- 修正后 `gradlew.bat runGameTestServer --no-configuration-cache --no-daemon --console=plain`：11/11 required GameTest 通过，专服正常保存并关闭。
- 本批没有客户端布局和交互变化，因此无需新增人工 UI 回归。

## 2026-08-13 第三批：客户端展示 SPI

> 对应计划项：3.7。候选实现为 `63fa45d`，原生物品 Tooltip 兼容修正及最终验收代码为 `2f4e567`。

### 实现范围

- `ClientTaskPresentation` 与 `ClientRewardPresentation` 从内部 definition 迁移到不可变 `TaskView`/`RewardView`。
- 增加防御性复制物品栈的任务/奖励展示上下文，统一标题、进度文本、节点样式和交互提示扩展点。
- 原生 item、checkmark、custom 展示和任务树/HUD 全部经 presentation 注册表解析；完整命名空间未知类型使用问号、完整类型 ID 且不可交互。
- 物品目标与奖励始终走原生 `ItemStack` tooltip 管线，保留复杂组件属性和其他模组追加内容；非物品展示通过单一末端 tooltip 显示操作提示。
- 新增反射签名和完整 ID 隔离测试；专服仍不引用或加载客户端 presentation 实现。

### 人工验收

- 使用重新启动的 Minecraft 1.21.1 + NeoForge 21.1.216 开发客户端，按 [`CLIENT_ACCEPTANCE_zh.md`](CLIENT_ACCEPTANCE_zh.md) 检查现有 UI 体验和本批展示变化。
- item/checkmark/custom/未知类型的标题、进度、样式、占位和点击边界验收通过，整体体验与修改前一致。
- 首轮发现可交互物品 Tooltip 被缩减为物品名和操作提示，不利于复杂组件及其他模组 Tooltip；`2f4e567` 改为所有物品无条件走原生 `ItemStack` Tooltip 管线，复测通过。

### 自动验证

- 构建前 `Get-Process` 与 `gradlew --status` 确认无残留 Java、Minecraft 或 Gradle daemon。
- 客户端展示定向测试与任务树展示回归通过；完整 `build` 通过，JUnit 汇总为 22 个 suite、54 项测试、0 failure、0 error、0 skipped。
- `runGameTestServer` 在纯 Minecraft + NeoForge + BRNQuest 专服环境完成，11/11 required GameTest 通过，证明 common/server 路径未加载客户端展示实现。
- `git diff --check` 通过。
