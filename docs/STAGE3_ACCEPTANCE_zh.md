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

## 2026-08-13 第四批：ProgressOwner SPI

> 对应计划项：3.8。实现保持 schema 1 和个人进度体验不变；本批没有客户端展示变化，无需新增人工 UI 回归。

### 实现范围

- 增加由完整 provider ID 与稳定 UUID 组成的 `ProgressOwnerId`，以及不可变 owner、生命周期和归档投影。
- 增加构造期 `ProgressOwnerProviderRegistry`；provider 描述玩家解析、成员关系、生命周期和归档查询，并在首次任务书 reload 前冻结。
- 唯一激活的 `brnquest:personal` provider 使用玩家 UUID 作为 owner UUID，只返回玩家本人，始终为 `ACTIVE`。
- 进度存取和事务锁先解析 owner；`ProgressView` 显式携带 owner ID。schema 1 仍使用原有 `players` NBT 字段和 UUID 键，不发生存档迁移。
- 外部 provider 当前只允许注册和契约验证，不提供激活入口；不根据队长 UUID、显示名或可变队名猜测共享 owner。
- `ProgressOwnerChangedEvent` 改为携带前后两个 `ProgressOwnerId`，但个人阶段没有合法切换，因此不会发布伪事件。

### 验证重点

- 公共 SPI 不泄漏 `PlayerProgress`、`QuestProgressData` 或其他内部存档类型，集合投影防御性复制。
- 完整 provider ID 隔离、重复注册拒绝、reload 前冻结和冻结后拒绝注册。
- GameTest 验证个人 owner 的 provider、UUID、成员和生命周期，并回归既有进度、奖励、权限和事件事务。

### 验证结果

- 构建前 `Get-Process` 与 `gradlew --status` 确认无残留 Java、Minecraft 或 Gradle daemon。
- owner 定向契约测试通过；完整 `build` 通过，JUnit 汇总为 23 个 suite、58 项测试、0 failure、0 error、0 skipped。
- `runGameTestServer` 在纯 Minecraft + NeoForge + BRNQuest 专服环境完成，12/12 required GameTest 通过并正常保存、关闭。
- 全局搜索确认 `ProgressEngine` 不再按 `player.getUUID()` 直接获取进度或建立事务锁；`git diff --check` 通过。

## 2026-08-13 第五批：编辑器字段描述 SPI

> 对应计划项：3.9。本批只建立未来编辑器消费的公共描述与校验投影，不增加客户端编辑界面。

### 实现范围

- `TaskType` / `RewardType` 增加默认空列表的 `configFields()`，不破坏未声明编辑元数据的已有扩展类型。
- `ConfigFieldDescriptor` 支持字段键、基础值类型、默认值、必填、范围、枚举、资源注册表约束提示、帮助文本和隔离的自定义校验器。
- `ConfigEditorSchemas` 通过注册类型生成 task/reward 编辑投影，不硬编码类型 ID；内置 item/checkmark 提供基础字段描述。
- `ConfigEditorSchema` 无论是否有描述都保留完整不可变 raw Map；未知类型、custom 开放配置或元数据异常使用安全 raw fallback。
- 字段预览诊断不替代 `configCodec()` 和任务书发布校验，不能用编辑元数据绕过服务端权威验证。

### 验证重点

- 覆盖必填、默认值、数值范围、枚举、资源位置语法、自定义校验器和异常隔离。
- 验证未知同路径类型不继承内置描述，raw Map 防御性复制且字段不丢失。
- 反射检查公共编辑 SPI 不暴露内部 definition、进度或存档类型。

### 验证结果

- 构建前 `Get-Process` 与 `gradlew --status` 确认无残留 Java、Minecraft 或 Gradle daemon。
- 字段描述定向测试通过；完整 `build` 通过，JUnit 汇总为 24 个 suite、62 项测试、0 failure、0 error、0 skipped。
- `runGameTestServer` 在纯 Minecraft + NeoForge + BRNQuest 专服环境完成，12/12 required GameTest 通过，原有 task/reward Codec 与事务行为未改变。
- 本批没有客户端编辑界面或运行时展示变化，无需人工 UI 回归；`git diff --check` 通过。

## 2026-08-13 第六批：注册与 reload 生命周期

> 对应计划项：3.10。本批整理既有冻结点和原子快照路径，不改变 schema、协议或客户端 UI。

### 实现范围

- `ExtensionRegistrationLifecycle` 统一 common 与预留 script 窗口，首次服务端资源监听器建立前一次冻结 task/reward/owner 注册表；只读状态可用于诊断。
- client setup 在冻结 task/reward presentation 后标记客户端窗口关闭；common 生命周期不引用客户端类，专服保持隔离。
- `QuestBookManager.install` 串行完成候选校验，并只用一次原子引用写入发布成功快照；null 候选、fatal 诊断或扩展验证异常保留上一 revision。
- `lastReport()` 和已提交报告防御性复制，调用方后续修改不能篡改 reload 结果。
- reload 成功日志只在实际安装成功后输出；验证失败明确记录保留旧快照。

### 验证重点

- fatal/null 候选保留上一快照，成功候选只发布一次完整 revision。
- 专服在首次 reload 前关闭 common/script 窗口，且 client 窗口未被执行；所有 common 注册表状态一致。
- 原有 reload 在线玩家对账、task/reward Codec、事件和专服启动回归继续通过。

### 验证结果

- 构建前 `Get-Process` 与 `gradlew --status` 确认无残留 Java、Minecraft 或 Gradle daemon。
- reload 原子保留与诊断副本定向测试通过；完整 `build` 通过，JUnit 汇总为 24 个 suite、64 项测试、0 failure、0 error、0 skipped。
- `runGameTestServer` 在纯 Minecraft + NeoForge + BRNQuest 专服环境完成，13/13 required GameTest 通过；新增门禁确认 common/script 已冻结且 client 生命周期未在专服执行。
- 本批没有客户端 UI 变化；此前 3.7 客户端启动已覆盖 client setup 冻结路径。`git diff --check` 通过。

## 2026-08-13 第七批：示例附属模组、API 门禁与阶段收口

> 对应计划项：3.11、3.12、3.13。本批不修改生产任务书、存档 schema、网络协议或现有客户端布局。

### 实现范围

- 增加独立 `exampleAddon` source set 和真实 NeoForge mod `brnquest_example`；默认开发运行加载，`-PexcludeExampleAddon` 可验证消费者完全缺失的纯核心组合。
- 示例只使用公共包，覆盖被动 marker task、手动 signal task、字符串配置 Codec、经验奖励、客户端 task/reward presentation、字段描述和 `QuestCompletedEvent` 订阅。
- 新增源码边界测试，禁止示例导入 `data`、`progress`、`runtime`、`network`、`workspace`、`command`、`platform` 或通过反射绕过 API。
- 新增端到端 GameTest：通过公共注册表和操作 API 完成两种任务，观察只读事件，并验证奖励首次执行、重复领取幂等无变化。
- API 基线记录为 `0.1.0-experimental.1`，首个承诺稳定版本记录为 `1.0.0`；`ApiStatus` 改为运行时保留，编译后签名快照及 SHA-256 门禁进入普通 `test`/`build`。
- 增加 API 版本策略和示例附属模组文档；完整签名变化报告固定写入 `build/reports/public-api-signatures.actual.txt` 供人工审阅。

### 3.13 验收矩阵

- 核心 + 示例附属模组：模组列表确认同时加载 `brnquest` 与 `brnquest_example`，14/14 required GameTest 通过。
- 纯核心：`-PexcludeExampleAddon` 模组列表不含示例附属模组，14/14 required GameTest 通过；示例运行契约安全跳过，其余专服测试完整执行。
- 公共 API 与示例边界：完整 JUnit 26 个 suite、66 项测试、0 failure、0 error、0 skipped；签名基线、internal 泄漏和反射绕过检查均通过。
- 客户端 presentation 缺失：既有完整命名空间隔离测试继续验证问号占位、完整类型 ID 和不可交互后备；示例客户端 presentation 在独立 source set 编译通过。
- reload 失败：fatal/null 候选保留旧 revision 与快照、诊断防御性复制的契约测试继续通过。
- 专服类加载：两种 GameTest 配置均在 `forgeserverdev` 运行；client 注册窗口未执行，示例的客户端引用由 dist guard 隔离。
- 构建产物：默认 `build` 和 `build -PexcludeExampleAddon` 均通过；核心 JAR 搜索不到 `brnquest_example` 类或资源，示例没有混入生产发布物。

### 夹具修正记录

- 首轮被动条件使用模拟玩家 XP 等级，但 headless mock 生命周期会重算字段；改用确定性的服务端 scoreboard tag，仍保持“观察外部服务端状态、不调用 BRNQuest 进度写入口”的被动语义。
- 首轮经验奖励直接使用 `Codec.INT`，与 schema 1 的字符串叶值不匹配；修正为 `Codec.STRING` 的显式整数解析，并把这一约束写入扩展文档。
- 修正后默认与纯核心 GameTest、JUnit 和两种完整构建全部通过。

### 最终检查

- 每次完整 `build` 前均检查 Java/Minecraft 进程和 `gradlew --status`，没有运行中的 Gradle daemon 或残留游戏进程。
- `git diff --check` 通过；本批没有生产 UI 行为变化，3.7 已完成人工客户端验收，因此无需重复人工布局测试。
