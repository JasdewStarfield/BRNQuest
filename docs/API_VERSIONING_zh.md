# BRNQuest API 版本与兼容策略

## experimental.24 → experimental.25

`TaskSubmissionSelection` 及 `TaskType.submit(context, config, selection)` 成为公共实验接口。选择只包含主背包 0–35 的有序、不重复槽位，最多 36 个；空选择表示自动提交。服务端类型必须重新检查实时库存。旧的两参数 `submit` 继续由默认重载调用。

客户端 `submissionInteraction(context)` 可返回 `TaskSubmissionInteraction` 页面工厂，默认空值保持直接提交。工厂只使用核心提供的回调发送一次选择意图；核心检查页面、玩家、世界、revision 和待处理状态。`candidateScreen(parent, context)` 提供只读候选页，默认复用旧 `resolvedOptions`。这些默认方法无需旧 presentation 重新实现。

`TaskType.craftedProgress(context, config, crafted)` 接收真实服务端合成产出的副本，返回累计进度；默认返回原进度。该方法必须无副作用，核心负责当前目标筛选、owner 锁、单调进度与完成判定。重置、重复周期和 reload 继续使用现有账本生命周期。

新增 `BrnQuestApi.submitTaskResult` 明确返回目标提交事实：提交并记账后返回 `SUCCESS/TASK_SUBMITTED`，即使同任务其它目标尚未完成；重复提交返回无变化。旧 `completeTaskResult` 和 KubeJS 对应入口保留整任务完成结果，仍可能在目标已经记账后返回 `UNSATISFIED`。任务书 schema、网络字段和模组安装方式不变。

## experimental.23 → experimental.24

`TaskType` 与 `RewardType` 新增 `normalizeConfig(ConfigNormalizationContext, Map<String, String>)` 默认重载，默认调用旧 `normalizeConfig(Map)` 一次。旧实现无需修改。上下文只公开可选的 `HolderLookup.Provider`；服务器作者写入和奖励表叶子预检使用当前注册表，离线纯编辑入口没有注册表，类型应保留无法解析的资源值，不能用空注册表代替服务器状态。

新增、更新、单项复制、整任务复制、剪贴板粘贴和章节复制统一调用该入口；类型只规范化自己拥有的字段，核心将返回值合并到原始配置以保留不透明键。规范化必须无副作用、可重复，不保留查找器跨 reload；非法配置可抛出 `IllegalArgumentException`，作者事务不会提交部分候选或写入撤销历史。撤销/重做恢复已保存快照，不重复执行规范化。

内置 `item` / `item_choice` 通过此入口解析旧配置、组件和 tag，网络入口不再专门识别这两种类型。没有注册表的旧纯编辑入口保留物品原配置。复制仍只由核心重映射对象身份和依赖；配置中的未知字符串和私有引用保持原值。尚未引入配置引用 remap SPI。任务书 schema、网络协议和安装方式保持不变。

## experimental.22 → experimental.23

[English migration note](API_MIGRATION.md)

`ClientRewardPresentation.contentSummary(RewardView)` 是客户端纯配置内容摘要的可选默认方法，默认返回空并保留既有物品/标题后备行为。摘要不包含配置标题，不查询领取状态，不发送请求；通用布局负责组合标题，图标、物品查询及服务端领取语义不变。内置 XP/等级和示例 XP 使用同一入口。旧 presentation 无需实现新方法；兼容测试把针对 experimental.22 接口编译的消费者加载到当前接口上。任务书 schema、网络协议与模组安装方式不变。

## experimental.21 → experimental.22

`QuestView.descriptionFormat()` 新增任务说明格式名，当前已知值为 `plain` 与 `markdown_v1`。本地化查询中的正文和格式始终来自同一个语言来源；未知格式名仍原样投影，调用方必须按纯文本安全降级。原有构造器继续存在并默认 `plain`。`DraftEditService.updateQuestTranslation(...)` 新增带字符串格式名的重载；省略格式的旧重载保留当前 locale 格式。依赖 record 组件反射、模式解构或生成的 `equals`／`toString` 的附属需要检查新增组件。

## experimental.20 → experimental.21

`ChapterView` 新增 `defaultHideDependencyLines` 只读字段。保留此前构造器，旧构造器默认 false；依赖隐藏仅影响渲染，不改变任务依赖判定。


## experimental.19 → experimental.20

`ChapterView.autofocusQuestId()` 返回可空的同章节任务 ID；null 表示未设置自动聚焦。新增完整构造器并保留全部旧构造器。依赖 record 组件反射、equals/toString 的附属应考虑新增组件。

## experimental.18 → experimental.19

- `QuestBookView.settings()` 提供不可变书设置映射；`ChapterView.consumeItems()` 为可空布尔值，null 表示新物品目标继承书默认。
- 保留此前全部 view 构造器；新增的完整构造器可传入设置。旧书的默认行为和编码保持不变。
- `TaskType.creationConfig(config, defaults)` 为默认实现直接返回 config 的纯函数扩展点。类型可选择使用其拥有的创建提示；当前提示为 `consume_items`。保留显式输入、未知键，不做 IO/进度变更；更新和复制不调用此钩子。
- 创建默认值在新增项时固化；`suppress_auto_claim` 是影响现有奖励的书级运行策略，`pause_game` 为单人任务界面暂停策略。


## experimental.17 → experimental.18

`QuestBookView` 与 `ChapterView` 增加只读 `questDefaults()`，以不可变键值映射公开稀疏的新任务创建模板；旧构造器保留并使用空模板。依赖 record 组件反射、equals 或 toString 的附属应重新检查其假设。模板不代表既有任务的运行时继承值，任务视图仍返回显式有效配置。

## experimental.16 → experimental.17

`ChapterGroupView` 新增 `icon`、`description` 和不可变 `extensions`，原五参数构造器保留并使用空元数据。既有 accessor 和构造调用无需修改；依赖 record 组件反射、模式解构或生成的 equals/toString 的消费者需考虑新增组件。`ChapterGroupDefinition` 同样保留原四参数构造入口；它属于内部数据模型，不新增公共稳定承诺。API 查询中的组名称继续按既有 locale 解析，新增说明首版为原生普通文本。

任务书 JSON 使用可选组字段，空值不写入以保持旧内容 revision；网络继续使用现有 UPDATE_GROUP 配置映射，省略字段保留服务端值，显式空字符串清除图标/说明。扩展数据仅通过授权的数据编辑 API 更新，普通元数据表单保留未知值。组不拥有任务创建默认值或运行时继承规则。

## experimental.15 → experimental.16

- 新增客户端 presentation 默认 typeIcon()、注册图标重载及注册表 typeIcon(id) 查询；新增 EditorIcon.sprite。已有方法保持签名，旧实现默认返回空图标并走兼容回退。
- 类型选择器按任务/奖励注册表分别解析，不需要伪造实例。内置资源映射仅作为内置注册元数据，不限制附属命名空间。

## experimental.14 → experimental.15

`ComposableReward.freeze(context)` 是新增默认方法，默认调用 `prepare`，已有适配器保持源码/二进制兼容。`prepare` 继续用于重复的无副作用预检；`freeze` 只为首次到达的实际执行 occurrence 固定随机数据，禁止交付副作用。协调器在执行前强制保存返回值，恢复及重复确认不得再次调用 `freeze`。新方法支持原生战利品表，网络字段未变，协议保持 18。

## 当前版本线

- 当前公共 API 基线为 `0.1.0-experimental.27`，由仓库内的编译后签名快照持续保护。
- 首个承诺稳定的 API 版本为 `1.0.0`。在到达该版本前，代码中标为 `EXPERIMENTAL` 的类型仍可调整，但每次变更必须同时更新文档、迁移说明和签名门禁。
- `1.0.0` 起，标为 `STABLE` 的公开签名在同一 major 版本内保持源码与二进制兼容；删除、改名、缩窄可见性或改变参数/返回类型都需要下一个 major 版本。
- `INTERNAL` 类型和未列入公共清单的包不进入兼容承诺，即使 Java 可见性是 `public` 也不能被外部集成依赖。

API 版本独立于模组发布版本：补丁发布可以在不改变 API 基线的情况下修复实现；如果实验性接口发生变化，则递增实验性序号并给出迁移说明。

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

## experimental.9 → experimental.10

`TaskType.resetTransientState(TaskContext)` 新增默认空实现。显式重置整个任务或单个目标时，引擎为同一进度所有者的在线成员调用此方法，即使持久化进度本来为零；旧实现无需修改。实现只清理该玩家、任务书、目标对应的临时状态，不得写入持久化进度。离线状态的清理仍由类型模块负责。观察目标用此入口清空未完成的连续观察计时，核心引擎不识别具体类型。

观察计时新增只读客户端展示 payload，网络协议升级为 14；客户端与服务端须同时更新。任务书 schema 1、进度 schema 2 保持不变。

## experimental.10 → experimental.11

`ClientTaskPresentation.confirmed(TaskView, long)` 增加默认实现（单次提交类型仍以账本值 ≥ 1 为准）。累计类型应覆盖此方法，按配置数量解释服务端账本；它不能用本地背包就绪状态或整个任务的历史完成状态代替目标完成。目标行和追踪 HUD 统一调用注册类型的实现，已有类型无需修改。网络协议保持 14。

## experimental.11 → experimental.12

任务和奖励 presentation 新增 `resolvedOptions(view)` 默认入口，返回 `Optional<List<Component>>`。未提供表示维持原有行为；提供空列表表示有候选浏览入口但没有解析成员。共享只读窗口显示完整列表，类型负责解析成员和名称，不由 QuestScreen 判断具体类型。任务的默认 `hasCandidateMenu` 使用此入口；既有物品候选功能继续沿用原实现。

地点分组/原生标签解析成员在登录和数据包同步时分包发送给普通玩家，网络协议升级为 15；没有放宽 OP 作者字段查询权限。客户端与服务端需同时更新。

## experimental.12 → experimental.13

`RewardType.composition()` 新增默认空 Optional；旧扩展仍可顶层领取，但不会自动作为奖励表叶子执行。`ComposableReward` 提供纯 `validateConfig`、无副作用 `prepare`、带 occurrence 身份的 `execute`、保守 `recover` 和适配器版本。`RewardLeafContext` 保留真实根上下文，将逻辑路径与 occurrence 独立传递，不伪造顶层 ID。

客户端新增 `ClientConfigEditors.register(typeId, fieldKey, factory)`，共享界面只路由子配置编辑器；工厂拥有自己的配置解释和返回值。现有字段表单无需调整。示例 addon 通过公开接口 opt-in。

奖励表只读状态查询使用新 payload，网络协议升级为 16；客户端与服务端须同步更新，任务书 schema 1 保持不变。
## experimental.13 → experimental.14

新增 `RewardType.requiresManualClaim(Map<String,String>)` 默认方法，默认为 false，已有扩展无需修改。交互式奖励返回 true 后，自动触发与一键领取会跳过该类型，自动领取策略在整书配置校验时报错；显式单项领取仍走原有资格与账本边界。方法必须只读取配置，不产生副作用。

网络协议 17 新增自选奖励请求及展示分页包，客户端和服务端须一起更新。选择携带冻结尝试身份、节点 occurrence、状态版本和条目 ID；服务器重新校验执行者及当前根资格，不接受客户端提供的奖励效果。

网络协议 18 为嵌套自选响应增加当前 occurrence 与版本，路径字段允许最长 1024 字节；客户端按服务端待选择节点继续下一步。API 仍为 experimental.14，本阶段没有新增公开组合接口。

## experimental.25 → experimental.26

全部内置类型使用公开注册入口，并将专属页面、协议和生命周期接线归入内部实现包。新增 `ClientTaskPresentation` 标题装饰与数量默认方法、`ClientRewardPresentation` 状态刷新与领取准备默认方法、`RewardType.clientClaimResponse` 默认方法，以及 `ClientConfigEditors` 上下文重载和创建工厂。已有方法保持签名，旧编译消费者继承兼容默认行为。配置、协议 `18`、恢复日志及单 JAR 安装方式不变。调用边界见 [`EXTENSION_API_zh.md`](EXTENSION_API_zh.md)，英文迁移见 [`API_MIGRATION.md`](API_MIGRATION.md)。

`BrnQuestApi.getRewardClaimState(player, rewardId)` 在服务器线程返回只读 `RewardClaimState`，包含既有不可变领取上下文（owner、周期、重置 generation）、revision 与建议性的 `eligible` 标记。任务已完成且玩家属于完成时成员时为 eligible；已领收据仍由领取事务判定，因此 eligible 不代表可再次发奖。玩家/服务器缺失、非服务器线程、奖励条目不存在或 owner 未激活时返回空。查询不执行奖励、不推进类型日志；恢复操作须重新查询身份并进入普通公开领取 API，不能把缓存快照作为授权。内置观测通过既有 `resetTransientState`、reload/logout 和只读 owner 身份清理本地计时，插件不接触可变进度账本。

## experimental.27：队伍进度策略

`QuestBehaviorView.requireAllTeamMembers()` 暴露单任务的全员完成规则；原构造器保留，新字段默认 false。`QuestBookView.settings()` 增加默认 true 的 `share_team_progress`。全员任务的进度查询返回请求玩家自己的目标计数，任务状态、依赖解锁和奖励仍等待全员完成。`WAITING_FOR_TEAM` 表示个人完成已保存，队伍仍在等待；首次记录返回 SUCCESS，重复提交返回 NO_CHANGE。网络协议升级为 19，客户端与服务端需同步更新。成员变化、历史切换及重置规则见 [队伍进度](TEAM_PROGRESS_zh.md)。
