# BRNQuest 扩展入口

原生战利品组合使用 experimental.15 新增的默认 `ComposableReward.freeze(context)` 固定每个已选 occurrence 的生成数据；`prepare` 可被多次用于预检，不能抽取随机结果或发奖。默认 `freeze` 委托 `prepare`，既有非随机适配器无需修改。结果落盘后才调用 `execute`，恢复读取原结果而不再 freeze。

公共面、稳定性、查询和写操作结果的总边界见 [`PUBLIC_API_zh.md`](PUBLIC_API_zh.md)，版本承诺见 [`API_VERSIONING_zh.md`](API_VERSIONING_zh.md)。本文继续说明任务与奖励类型契约；当前 `0.1.0-experimental.15` 基线中的 SPI 仍标记为实验性。

任务和奖励扩展采用“服务端行为 + 可选客户端展示”两条独立注册链。原生任务书使用 schema 1 的字符串 `config`，注册类型的 `Codec` 会在加载时将其解码为类型自己的不可变配置，并将失败写入诊断报告。

schema 1 会把每个配置叶值作为 JSON 字符串交给 Codec；数字和布尔字段应先用 `Codec.STRING` 安全解析，而不能直接假定收到 JSON number/boolean。示例附属模组的经验奖励展示了带错误结果的字符串整数 Codec。

若只需要从整合包脚本查询、推进或观察任务，或声明简单的外部进度 task / 事件奖励，不必编写 Java 插件；请使用 [KubeJS 服务端脚本 API](KUBEJS_API_zh.md)。Java SPI 仍适合需要自定义 Codec、服务端判定或客户端 presentation 的复杂类型。

## 注册时间

- 附属模组应在自己的构造或 common setup 期间调用 `BrnQuestPlugins.register(BrnQuestPlugin)`。插件通过暂存的 `BrnQuestExtensionRegistrar` 一次声明 task、reward 和 owner provider；只有回调正常结束且全部声明通过预检后才写入实际注册表。
- 插件 ID 应使用附属模组自己的命名空间；BRNQuest 强制 task、reward 和 owner provider 与该插件 ID 使用相同命名空间。重复插件 ID、重复类型或跨插件命名空间声明会明确失败，不留下半注册结果。
- `TaskTypeRegistry.register`、`RewardTypeRegistry.register` 和 `ProgressOwnerProviderRegistry.register` 作为低层实验性入口继续兼容，但新附属模组应使用插件门面，以获得成组预检和清楚的所有权边界。
- 插件门面和三个服务端注册表会在首次服务端资源 reload 监听器建立前一起冻结；冻结后注册会明确失败。
- 客户端展示应在客户端构造期间调用 `ClientTaskPresentationRegistry.register`、`ClientRewardPresentationRegistry.register`，并在 client setup 时冻结。
- 类型必须使用完整 `ResourceLocation`；`example:item` 不会继承 `brnquest:item` 的行为或展示。

可选集成的插件实现应放在附属模组自己的隔离包中，并且只在确认 BRNQuest 已加载后触碰该类。BRNQuest 核心不反向引用附属模组类型。例如 BRNTalk 将使用 `brntalk.compat.brnquest` 注册 `brntalk:*` 扩展；没有安装 BRNQuest 时，这个兼容包不会被加载。客户端 presentation 继续放在独立客户端类中，不能通过 common 插件接口把客户端类型带入专服。

## 任务类型职责

`TaskType<TConfig>` 负责：

- 声明并解码配置 Codec；
- 判断服务端权威完成状态；
- 声明是否接受任务行手动提交；
- 可选接受任务级完成意图；
- 可选响应背包变化重算；
- 在提交型任务中执行一次性消耗。

任务运行方法接收不可变 `TaskContext`：包含在线服务端玩家、book/quest ID、`TaskView` 和当前 task 数值。扩展不能读取或保存 `PlayerProgress`；任何进度修改都必须通过 `BrnQuestApi` 返回到服务端事务协调器。

`ProgressEngine` 不识别具体任务类型 ID。常规新类型不应要求修改进度引擎或网络协议；现有 `CompleteTaskPayload` 会把任务行意图交给注册类型重新校验。

`RewardType<TConfig>` 接收不可变 `RewardContext`，其中包含玩家、book/quest ID 和 `RewardView`。默认执行路径在调用扩展前标记普通领取账本并请求存档；这不等于强制落盘。扩展不得自行修改领取状态，并应把一次执行所需的全部副作用放在同一次调用中。需要独立尝试记录的类型使用下述可选领取接口。

任务书 reload 成功后，BRNQuest 会在服务器线程重新对账所有在线玩家，并发送新定义和完整进度快照。新增的无前置任务因此应立即进入 `AVAILABLE`，而不是等待玩家重登。

## 客户端展示

客户端 presentation 只负责图标、符号、标题、进度文本、客户端预览和交互提示，不能成为进度权威来源。未注册展示的任务与奖励使用占位符，仍保留完整类型 ID 和服务端诊断。

- `ClientTaskPresentation` 的静态展示方法接收不可变 `TaskView`；标题、进度和提示方法接收 `TaskPresentationContext`，其中的物品栈已防御性复制。
- `ClientRewardPresentation` 接收不可变 `RewardView` 或 `RewardPresentationContext`，可根据仅供显示的 claimable/claimed 状态生成提示。
- `interactive` 和 `acceptsQuestCompletionIntent` 仅控制客户端是否建立点击入口；服务端仍会按注册的 `TaskType`、当前库存、权限和 revision 重新校验。
- `objectiveTitle` 可为详情目标行补充“需求/持有”等显示语义；`readyForSubmission` 默认保持既有 interactive 行可点击，只有能从客户端可靠观察前置条件的 presentation 才应收窄它。它只控制黄色可提交提示与本地命中，不能替代服务端校验。
- `displayedItem` 可从当前只读上下文选出紧凑行代表物品；`acceptedItems` 与 `hasCandidateMenu` 可声明一个只读候选列表入口。三个方法都有兼容默认值，返回的物品仅用于显示、Tooltip 与可选 JEI 查询，不能把客户端选择发送给服务器作为匹配结论。
- `ClientRewardPresentation.displayedItem` 接收已解析物品的副本，可仅为图标、数量角标、Tooltip 与可选配方查询调整显示数量；不得借此改变服务端实际奖励。
- 注册表按完整 `ResourceLocation` 查找。缺失 presentation 或仅路径同名时使用问号占位、完整类型 ID 标题且默认不可交互。
- 客户端注册表在 client setup 冻结；服务端任务/奖励实现不得引用这些 `client.ui` 类。

新增类型至少应覆盖：有效配置、Codec 失败诊断、完整命名空间隔离、手动/被动推进语义，以及未安装客户端展示时的占位行为。

仓库内 [`EXAMPLE_ADDON_zh.md`](EXAMPLE_ADDON_zh.md) 对应一个真实独立 NeoForge 附属模组，演示被动/提交任务、幂等奖励、presentation、字段描述和只读事件的完整公共 API 用法。

## ProgressOwner provider

`ProgressOwnerProviderRegistry.register` 接受服务端 provider，并在首次任务书 reload 前冻结。provider 必须返回自身命名空间下的稳定 `ProgressOwnerId`，并提供不可变成员快照、生命周期和归档证据；解析结果不包含当前玩家、provider ID 不匹配或调用线程错误时，BRNQuest 不会猜测后备队伍身份。

当前版本在未组队或 OPAC 不可用时激活 `brnquest:personal`，在受支持的 OPAC 0.30.3 party 中激活 `brnquest:openpac`。其他第三方 provider 注册不会自动生效。共享奖励扩展必须区分逐玩家和全队一次投递，详见 [OPAC 联动](OPAC_INTEGRATION_zh.md)。

## 编辑器字段描述

任务和奖励类型可覆盖 `configFields()`，用 `ConfigFieldDescriptor` 描述 schema 1 字符串 Map。描述应使用稳定字段键，并尽量给出默认值、范围、枚举、资源注册表提示和简短帮助文本；复杂跨字段规则可以使用 `ConfigFieldValidator` 返回字段级诊断。

字段描述是编辑器提示而不是新的配置 Codec。BRNQuest 始终保留完整 raw Map，未知字段不会因表单保存而自动删除；最终发布仍调用原有 `configCodec()`。返回空列表表示类型只支持原始配置后备视图，适合开放式或尚未冻结的外部配置。

内置 `ITEM_MATCHER` 字段会打开下属物品属性 Screen，`brnquest:item` 与兼容保留的 `brnquest:item_choice` 共用该能力。规范 matcher 是 version 2 entries JSON：每个条目保存自己的物品展示栈或 tag 与需求数量，目标所需条目数由上层 `required_entries` 编辑；物品条目按物品类型接受组件不同的同类栈，玩家提交时再选择具体背包格。旧单物品及旧 tag/list matcher 会投影到相同表单；多 tag 物品通过二级列表明确选择。完成子级编辑后才一次性回填，取消不会修改原配置。该能力及带玩家背包槽位选择的提交重载均为内部实现边界；扩展自己的 matcher 仍必须由服务端 Codec 和提交事务重新校验，不能信任客户端槽位、ItemStack 或库存快照。

内置 `brnquest:xp` 目标用 `value` 与 `points` 区分提交原始经验值或完整等级；`brnquest:xp`、`brnquest:xp_levels` 奖励分别发放原始经验值和完整等级。物品目标的 `only_from_crafting=true` 不接受手动背包提交，只统计服务端收到的玩家合成产出事件，并要求恰好一个匹配条目。

## 注册与 reload 顺序

common 插件门面与 Java task/reward/owner provider 在首次服务端资源 reload 前冻结；客户端 presentation 在 client setup 冻结。Java 注册只能发生在对应构造/setup 窗口，冻结后的重复或迟到注册都会明确抛出错误。KubeJS 脚本类型使用独立候选窗口：只在 server scripts 评估时开放，脚本无错误后先封存候选，再用候选解码并校验任务书；只有二者都成功时，才紧邻提交类型表与任务书指针。任一环节失败均保留上一组已成功的类型和任务书快照。

任务书 reload 先在候选对象上完成解码、所有已冻结类型的 Codec 校验和整本校验，只有没有 fatal 诊断时才原子替换当前快照。候选失败会更新诊断报告但保留上一 revision；成功替换后才发布只读事件并对账在线玩家。扩展不得把 reload 中获得的内部配置对象跨 revision 缓存。

## 新类型的实现边界（experimental.3）

新增任务或奖励应由具体实现、注册、客户端 presentation 和语言资源完成接入。`QuestScreen`、`DraftBookEditor`、`ProgressEngine` 不应增加按新类型 ID、字段名或枚举值判断的业务分支。需要新能力时，先补最小通用入口，再让具体类型接入；新控件属于通用编辑器能力，FTB 字段转换属于导入适配器，不放进通用 Screen。

- 字段标题使用 `ConfigFieldDescriptor.withLabel(translationKey)`；枚举显示使用 `withValueLabels(Map.of(rawValue, translationKey))`。类型负责提供对应语言资源。界面只翻译显示文本，配置仍保存原始键和值；没有枚举翻译时显示原值。旧字段描述构造器及内置字段标签后备继续兼容。新类型应显式声明标签，不能扩充 Screen 的旧标签 switch。
- `TaskType.normalizeConfig` / `RewardType.normalizeConfig` 是作者新增、更新及复制时的纯函数入口，默认原样返回。输入不可变；不得读写世界或执行奖励。只规范化自己拥有的字段，必须保留未知扩展数据。作者协调器会把返回值合并到原始 Map，未返回的键不会被删除；格式错误仍交由 Codec/发布校验报告。运行时也应兼容历史配置。
- `RewardType.claimHandler()` 默认为空，继续调用既有 `execute`。需要预检、独立尝试记录或等待结果时，返回 `RewardClaimHandler`。该接口在服务端线程、owner 锁内、完成/成员/重复领取检查之后调用，接收 `RewardClaimContext`（既有 RewardContext、owner 身份、完成周期、完整重置代号 claimGeneration）。不暴露可变账本。
- handler 返回 `RewardClaimResult`：`SUCCESS` 才写入普通领取记录、发出领取事件并推进周期；`PENDING` 不改领取记录，返回成功但未变化；`FAILURE` 不改领取记录并报告失败。code/message 为诊断，不用消息前缀判断状态。handler 自己负责配置预检、失败后恢复、重入与重复调用安全；抛异常会报告 `CLAIM_HANDLER_FAILED`，核心不会伪造成功。任意非幂等副作用必须有类型自己的持久化策略，不能把此接口当成通用事务保证。
- 异步结果若需完成领取，回到服务器线程，通过公共 `BrnQuestApi.claimRewardResult` 重走资格检查；类型必须核对原 owner、周期、claimGeneration 和奖励身份，读取自己的结果记录，返回成功而不重放副作用。禁止从异步线程改进度，禁止直接写普通领取账本。

命令奖励的规范化、权限与尝试记录现在由自己的实现承担。示例附属模组 `brnquest_example:guarded_tag` 使用相同扩展入口，以幂等玩家 tag 演示拒绝、等待、成功；它不是可用于任意命令的持久化日志模板。

新增类型的验证应覆盖：注册发现 → 字段/枚举显示元数据 → 作者新增/更新/复制与未知字段保留 → Codec/发布校验 → 服务端实际行为 → 重复请求与失败路径。自动测试通过不能代替新增界面的客户端验收。若类型必须改核心业务分支，应先说明缺失的通用能力并补入口，避免逐类型累积特例。

高级领取尝试必须把 `claimGeneration` 纳入记录身份：完整任务重置代表新的领取授权，历史记录保留但不阻止新代号下执行；同一代号内仍需防重复执行。空代号兼容升级前存档，不能在首次读取时随机生成代号，否则会绕过历史尝试记录。

## 被动采样与服务端字段来源（experimental.5）

被动目标覆盖 `pollingIntervalTicks()`（正整数，单位 tick）和 `sampledProgress(TaskContext, config)`（返回目标总进度）。默认间隔为 0，即不采样。回调运行在服务端线程和 owner 锁内；核心过滤未解锁及非当前顺序目标，保存进度增加、完成判定及同步。回调应只读、低开销、不加载新区块、不调用 locate。进度不会因离开区域而下降，完整重置仍由通用账本负责。重复任务开始新轮后重新采样；仍站在区域内会重新满足目标。

字段调用 `withServerSource(namespace:id)` 后，通用编辑器显示搜索选择入口。来源通过插件回调中的 `registrar.fieldSource(id, source)` 注册，与 task/reward 声明一起预检和提交；命名空间必须归属插件。旧 `resourceRegistry` 元数据只作提示，与此入口独立。

`Source.query(player, filter, selected)` 只读查询当前服务端状态，返回 `Result(entries, total, selectedCount, error, current, detail)`；`Entry` 保存原始值和解析数。`current` 非空时允许填入当前值，`detail` 供错误悬浮提示。旧 query 回调应返回完整集合，由默认 queryPage 切成每页 64 项；大量或昂贵的来源覆盖 queryPage，只构建请求页。不能先截断完整集合再把 total 写成更大的数量。客户端按总数滚动并加载可见页。`error` 为空表示无错误，否则对应 `screen.brnquest.field.error.<code>` 翻译键。来源负责相关语言资源。服务端统一要求权限等级 2，输入及响应长度受协议限制，结果不是任何作者写入的授权。

示例附属模组的 marker 类型展示采样和 player_tags 来源；新增同类扩展不需要修改 QuestScreen 或 ProgressEngine。注册表来源在资源 reload 后重新查询，原始 ID、#tag 或 #分组写入配置，不展开保存为具体成员列表。

`INTEGER_VECTOR3`（experimental.6）用于三个整数轴的同行编辑，仍存为原字符串。适用坐标、尺寸及扩展自定义向量；范围元数据逐轴校验。带 serverSource 时显示直接填入 current 的行尾按钮，响应关联到发起表单；等待期间继续输入、关闭或提交表单后，旧响应不会覆盖编辑内容。无 serverSource 时只显示三个输入框。原生 config Codec 仍是最终校验权威。


### 原版进度类型的扩展边界

内置 advancement 目标复用 TaskType 的 pollingIntervalTicks / sampledProgress，奖励复用 RewardType.claimHandler；字段选择器使用 ServerFieldSources 分页。原版事件只使模块读取缓存失效，不直接推进任务或领取奖励。其它新类型可沿用此模式，无需修改 QuestScreen、ProgressEngine 或平台入口。

字段描述的 helpText 可保存翻译键或原有字面帮助文本，作者悬浮字段标签时通过通用表单显示；这没有新增配置协议字段。服务端校验仍由类型 codec 和具体执行前校验负责。


### 纯展示图标

使用 presentation 的 `icon(view)` 返回 `Optional<EditorIcon>`，即可在目标行、奖励格、任务节点和作者列表绘制图标。需要原生物品外观时可调用 `EditorIcon.item(stack)`；此入口只有绘制能力，不代表可提交/可领取物品，不参与 JEI 查询或物品悬浮。未提供 icon 时沿用旧 itemSnbt/symbol 行为。原版进度实现使用客户端已同步的 display 图标，无 display、未同步或分组时回退到知识之书；tooltip 使用 interactionHint。

### 依赖字段与只读预览（experimental.9）

来源可覆盖 `queryPage(player, filter, selected, offset, context)`，从 `context` 读取同一表单尚未保存的字段值，解析所依赖的服务端对象并返回候选。上下文仅用于查询，必须重新校验 ID，不能作为权限或对象存在的依据。旧重载仍兼容。

`Result.previewSource` 非空时，选择器显示“成员预览”，打开该已注册来源的只读分页列表；原选择值作为 `selected` 传入，上下文保留。预览条目不能写回父字段。来源仍受服务端作者权限校验，且每页不超过 `ServerFieldSources.PAGE_SIZE`。依赖条件和成员展开均由具体来源负责，通用界面不检查类型 ID。

### 可重置的临时目标状态（experimental.10）

连续观察等目标可将未完成的计时保留在服务端模块内，仅在完成时通过 `sampledProgress` 返回持久化值。实现 `TaskType.resetTransientState(context)` 清理显式重置后的临时状态；引擎会覆盖共享同一进度所有者的在线玩家，包含尚未产生持久化进度的目标。该回调不得改写账本，默认空实现兼容已有类型。断线、重载等生命周期仍由模块自己的事件订阅处理。

### 累计目标显示（experimental.11）

累计型目标覆盖 `ClientTaskPresentation.confirmed(task, storedProgress)`，让目标行与 HUD 按所需总量判断完成。`satisfied` / `readyForSubmission` 仍负责本地满足/可提交含义；`confirmed` 只解释服务端账本。HUD 复用 `progressText` 显示实时进度，因此瞬时计时也可在关闭任务界面后阅读。

### 只读候选项（experimental.12）

`ClientTaskPresentation` 和 `ClientRewardPresentation` 可通过 `resolvedOptions(view)` 提供完整的已解析成员名称。使用本地化 Component，缺失名称时回退成员 ID，避免返回标签/组 ID 代替其内容。共享窗口使用已有平滑滚动列表，支持按名称搜索，无成员数截断。候选入口只读，在未解锁或已领取状态仍可查看；它不替代提交或领取接口。内置类型 tooltip 最多列三项，超出显示省略号。

候选项可在名称 Component 的 `SHOW_TEXT` hover 元数据中携带原始 ID；共享窗口将其作为条目悬浮提示。未提供 hover 的旧扩展仍显示名称提示，方法签名与协议不变。内置类型始终携带原始 ID。

### 可组合奖励（experimental.13）

只有 `RewardType.composition()` 返回能力声明的类型可以作为表叶子。`validateConfig` 用于纯发布校验；`prepare` 检查资源、权限和数量并返回可持久化字符串映射，严禁产生副作用。`execute` 在强制 STARTED 记录之后调用，接收根上下文、稳定逻辑路径和唯一 occurrence。不要递归调用顶层领取 API，不要给叶子单独写正常领取账本。

`PENDING` 暂停后续叶子；恢复调用 `recover`，默认按结果未知处理。只有可核实证据才能报告恢复成功；不能用默认重跑 execute 来实现 recover。适配器 `version()` 必须在准备数据或恢复语义不兼容时改变。内置 custom 只是 no-op acknowledgement，不是脚本执行器。

客户端复杂字段使用 `ClientConfigEditors.register(typeId, fieldKey, factory)`；工厂返回子 Screen，只在确认时调用 Consumer。注册只能发生在客户端，避免专服加载客户端类。参考 `GuardedTagReward` 的 public-only 组合示例和 [奖励表使用说明](REWARD_TABLES_zh.md)。
### 手动交互奖励（experimental.14）

`RewardType.requiresManualClaim(config)` 是无副作用的静态策略声明，默认 false。返回 true 的类型必须由玩家显式单项领取；核心自动触发及 claim-all 跳过该类型，非手动策略在发布配置校验中被拒绝。此方法不会自动创建选择协议或 UI，具体交互仍由类型模块拥有。内置自选表使用此声明，现有扩展保持原行为。
