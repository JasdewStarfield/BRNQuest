# BRNQuest API 版本与兼容策略

## 当前版本线

- 当前公共 API 基线为 `0.1.0-experimental.9`，由仓库内的编译后签名快照持续保护。
- 首个承诺稳定的 API 版本为 `1.0.0`。在到达该版本前，代码中标为 `EXPERIMENTAL` 的类型仍可调整，但每次变更必须同时更新文档、迁移说明和签名门禁。
- `1.0.0` 起，标为 `STABLE` 的公开签名在同一 major 版本内保持源码与二进制兼容；删除、改名、缩窄可见性或改变参数/返回类型都需要下一个 major 版本。
- `INTERNAL` 类型和未列入公共清单的包不进入兼容承诺，即使 Java 可见性是 `public` 也不能被外部集成依赖。

API 版本独立于模组发布版本：补丁发布可以在不改变 API 基线的情况下修复实现；如果实验性接口发生变化，则递增实验性序号并给出迁移说明。

## 自动门禁

`PublicApiSnapshotTest` 从编译后的类中读取运行时 `ApiStatus`，按确定顺序记录公开类型、构造器、字段和方法签名，并把完整结果写入：

`build/reports/public-api-signatures.actual.txt`

仓库保存该文本的 SHA-256 基线。普通 `test`/`build` 若发现签名变化会失败；维护者必须先审阅完整报告，确认稳定性等级、文档、示例附属模组和迁移影响，再有意更新基线。只更新哈希而不审阅报告不属于合法兼容变更。

## 版本升级规则

- 只新增 `EXPERIMENTAL` 公共面：递增实验性序号并更新示例与文档。
- 兼容地新增 `STABLE` 方法或类型：允许在 `1.x` minor 版本加入，但需要新契约测试。
- 修正文档、实现缺陷且签名不变：只递增模组 patch 版本。
- 删除或改变 `STABLE` 签名/语义：进入下一个 API major，并提供迁移说明和至少一个发布周期的弃用路径（安全或漏洞修复除外）。
- 将 `EXPERIMENTAL` 提升为 `STABLE`：必须已有外部消费者、专服/客户端隔离验证、失败回退和可重复的兼容测试。

## 调用约束

公共查询返回不可变投影；服务端写操作只能在目标玩家所属服务器线程执行，并通过 `OperationContext` 与 `OperationResult` 表达权限和结果。客户端 presentation 不是进度权威。事件只读、不可取消，监听器异常不会回滚已提交事务。更完整的包边界和生命周期规则见 [`PUBLIC_API_zh.md`](PUBLIC_API_zh.md)。

## experimental.1 → experimental.2

新增作者文本查询的六个 locale 重载：`getActiveBook(locale)`、`getChapterGroups(locale)`、`getChapterGroup(id, locale)`、`getChapters(locale)`、`getChapter(id, locale)`、`getQuests(locale)`。原有 `getQuest(id, locale)` 继续可用。已有签名和不带 locale 的原文查询行为不变，无需迁移；需要展示译文的调用方显式传入语言。数据 schema 和网络协议没有变化。

## experimental.2 → experimental.3

新增 `TaskType.normalizeConfig`、`RewardType.normalizeConfig`、`RewardType.claimHandler` 默认方法，以及 `RewardClaimContext` / `RewardClaimHandler` / `RewardClaimResult`。现有类型无需实现新增方法；普通奖励继续沿用 execute 路径。

`ConfigFieldDescriptor` 增加 `labelKey` 和不可变 `valueLabelKeys` 元数据及构建方法。保留原十参数构造器，已有构造调用无需改动；record 组件形状及生成的 equals/toString 随之扩展，依赖反射组件列表的消费者需调整。数据 schema 与网络协议不变。新扩展应通过字段描述提供翻译，并通过 normalizeConfig/claimHandler 定义类型行为，不修改内部 Screen、作者协调器或进度引擎。

## experimental.3 → experimental.4

`RewardClaimContext` 新增 `claimGeneration`：完整任务重置时创建并持久化的领取代号。高级奖励的尝试键应包含 owner、奖励身份、完成周期、claimGeneration 和相应领取者身份。保留旧三参数构造器，代号默认为空；旧存档同样使用空代号，以继续识别历史记录。只有明确重置任务才生成新代号。依赖 record 组件反射的扩展需适配新增组件。

## experimental.4 → experimental.5

新增 `TaskType.pollingIntervalTicks()` / `sampledProgress(context, config)` 默认方法。旧类型默认不采样，已有实现无需改动。服务端只采样可开始且顺序上当前可执行的目标，将单调增加的结果通过原 owner 账本保存。

`ConfigFieldDescriptor` 新增 `serverSource` 及 `withServerSource(id)`，保留旧十参数和十二参数构造器。反射 record 组件的消费者需要适配。`ServerFieldSources` 及其不可变 `Entry` / `Result` / `Source` 为实验性公共面；插件用 `BrnQuestExtensionRegistrar.fieldSource` 原子注册来源。客户端通用控件查询服务端并回填原始字符串，保存仍走原作者事务。

新增只读字段查询载荷，网络协议升至 10，客户端和服务端须一起更新。任务书 schema 仍为 1。

## experimental.5 → experimental.6

`ConfigValueType` 新增 `INTEGER_VECTOR3`：同一属性行内编辑 X/Y/Z 三个整数，配置仍保存一个 `x,y,z` 字符串。`withRange` 对每个轴生效。已有类型、构造器、任务书 schema 及网络协议 10 不变；对 ConfigValueType 使用穷尽 switch 的扩展需处理新值或提供默认分支。

vector 字段可配合现有 `serverSource` 元数据：行尾按钮直接查询并填入服务端 current 值，不打开候选列表。普通 source 字段继续使用通用列表。新控件不依赖任何地点类型 ID 或特定字段名。

## experimental.6 → experimental.7

ServerFieldSources 新增 PAGE_SIZE=64、带 offset 的 query 重载，以及 Source.queryPage 默认方法。旧 query 签名与函数式 Source 保留；默认分页对旧来源返回的完整候选集合切片。原来自行截断结果的来源需移除截断，或覆盖 queryPage 以按 offset 返回最多 64 项及准确 total。内置注册表来源仅解析请求页中的成员数量。

字段查询请求新增 offset，网络协议升至 11；旧四参数 Query 构造调用默认第一页。客户端按总数显示滚动范围，惰性读取可见页。任务书数据与目标检测规则不变。


## experimental.7 → experimental.8

ClientTaskPresentation 与 ClientRewardPresentation 新增默认 `icon(view)`，返回 Optional<EditorIcon>，默认空值保留原有行为。EditorIcon 的绘制契约与 item/glyph 工厂作为实验 API 开放；其中 item 只复制物品模型用于绘制，不注册物品 tooltip、数量叠字或配方查询。旧 presentation 无需修改；纯图标类型应保持 itemSnbt 为空。网络协议仍为 11。

## experimental.8 → experimental.9

`ServerFieldSources.Source` 增加携带只读表单上下文的 `queryPage` 默认重载；旧来源无需修改。`Result` 增加 `previewSource`，声明注册的只读成员预览来源，保留旧构造器。上下文不是服务端事实，来源必须自行解析/验证，不能用于权限或写操作。编辑器仅携带最多 64 个键长不超过 128、值长不超过 256 的字段；过长字段不发送。网络协议升级为 13，客户端与服务端需同步更新，任务书 schema 不变。
