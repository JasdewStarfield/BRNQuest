# 目标与奖励扩展

[English](EXTENSION_API.md) | [首页](../README_zh.md) | [Java API](API_zh.md)

以下契约在 `0.1.0-experimental.28` 仍为实验性。类型负责服务端行为、配置及可选客户端展示。原生 schema 1 把配置叶值作为 JSON 字符串传给 Codec，数字和布尔值应从 `Codec.STRING` 校验解析。简单外部进度目标与事件奖励可使用 [KubeJS](KUBEJS_API_zh.md)。

## 注册与所有权

在构造或 common setup 期间通过 `BrnQuestPlugins.register` 注册 `BrnQuestPlugin`。回调用 `BrnQuestExtensionRegistrar` 暂存目标、奖励、owner provider 和字段来源，全部通过预检后才提交。ID 必须属于插件命名空间；重复、跨命名空间及迟到注册明确失败，不留下部分注册。

插件门面和 common 注册表在首次服务端资源重载监听器建立前冻结。低层注册方法保留实验性兼容入口，新插件优先使用门面。客户端构造期独立调用 `ClientTaskPresentationRegistry` / `ClientRewardPresentationRegistry`，在 client setup 冻结。common 代码隔离客户端类，可选兼容类只在确认依赖已加载后使用。

完整 ID 各自独立，`example:item` 不继承 `brnquest:item`。新类型应通过公共契约接入，避免在核心界面、网络或进度中添加类型 ID 分支。内置实现类属于内部代码。

`TaskType.hiddenFromCreation()` 与 `RewardType.hiddenFromCreation()` 默认 false。true 隐藏主创建栏和奖励表叶子创建候选，已有定义、编辑、执行、导入及校验保留。内置 `item_choice` 与 `xp_levels` 用此保留兼容。当前列表按类型 ID 排序，尚未引入插件排序契约。

## 目标执行与选择

`TaskType<TConfig>` 定义 Codec、权威检查、手动提交、可选整任务完成意图、背包重评估和消耗。`TaskContext` 提供在线玩家、书与任务 ID、不可变目标视图和当前数值进度，进度存储由核心管理。注册实例跨玩家和 reload 复用，不应把每个目标的进度存在实例字段。

`submit(context, config, selection)` 在服务端 owner 锁内执行。`TaskSubmissionSelection` 最多包含 36 个有序、不重复的主背包索引（0–35），空表示自动选择。必须重新检查实时库存，不能信任客户端物品副本。默认重载委托旧的双参数方法。拒绝或运行时异常时核心恢复主背包快照，但不回滚其他容器、经验、世界变化或外部 IO。成功后先记账，后续重复请求不会再次执行目标。

客户端 `submissionInteraction(context)` 可返回 `TaskSubmissionInteraction` 工厂。页面在客户端线程创建，确认后通过提供的回调最多提交一次，关闭返回父页面；创建页面时不能提交。核心检查当前页面、世界和玩家、revision 与等待状态。返回空保留直接提交。`candidateScreen(parent, context)` 只读，不接收提交回调，默认使用 `resolvedOptions`。

通过 `BrnQuestApi.submitTaskResult` 获取单目标提交结果。旧 `completeTaskResult` 返回整任务完成结果，可能在记录部分目标后仍报告未满足。

### 被动与事件进度

- `pollingIntervalTicks()` 默认 0（关闭）。正间隔在服务端线程和 owner 锁内调用 `sampledProgress(context, config)`，返回总进度而非增量。回调应低开销、只读，不加载区块、不运行 locate、不调用写 API。
- `craftedProgress(context, config, crafted)` 接收真实服务端合成产出副本，无副作用地返回累计进度，默认返回原值。
- 核心过滤未解锁和未轮到的顺序目标，只保存进度增加，判断完成并同步。背包重评估采用事件脏标记及 20 tick 调度，不能自动识别其他模组的任意直接背包写入。
- `resetTransientState(context)` 在显式重置后清理在线 owner 成员的临时状态，不得写账本。类型自行管理登出与 reload 缓存。新重复周期沿用核心重置规则。

## 编辑配置

`configFields()` 返回 `ConfigFieldDescriptor`：稳定键、类型、默认值、必填或范围、枚举、注册表提示、帮助及可选校验器。空列表选择原始配置入口。字段描述指导表单，最终仍由服务端 Codec 和整书校验决定。编辑保留未知 Map 字段。

使用 `withLabel(key)`、`withValueLabels(Map.of(rawValue, key))` 和插件自带语言资源；`helpText` 支持翻译键或字面回退。翻译只改变展示，原始键和值保持稳定。`INTEGER_VECTOR3` 编辑三个整数并逐轴限制范围。内置匹配器控件属于内部实现，自定义匹配仍需自己的 Codec 和服务端检查。

`normalizeConfig(ConfigNormalizationContext, config)` 用于作者新增、更新、复制，包括任务、章节、剪贴板复制与服务端奖励叶子预检。默认调用旧 Map 方法一次。只返回自己拥有的字段，调用方合并到原 Map 以保留不透明键。规范化须纯函数且可重复，非法输入可抛 `IllegalArgumentException`。失败不提交部分候选或撤销项，撤销重做直接恢复快照而不重新规范化。

`context.registries()` 为可选的当前只读查找器。服务端作者写入提供，离线工具可能没有；无查找器时保留无法解析的资源值，不跨 reload 缓存。客户端预检仅作提示。核心复制重映射对象 ID 与依赖，配置私有引用保持原值，当前没有公共 config-remap SPI。

`TaskType.creationConfig(config, defaults)` 是纯函数创建提示钩子，也用于客户端预填。当前提示包括 `consume_items`，只消费类型理解的提示并保留显式字段，更新和复制不重新应用创建默认值。

### 服务端字段与子编辑器

通过 `registrar.fieldSource(id, source)` 注册来源，字段用 `withServerSource(id)` 指向它。`Source.query(player, filter, selected)` 返回带条目与计数的 `Result`；`current` 可提供当前值，`detail` 解释错误。非空错误码对应 `screen.brnquest.field.error.<code>`。

默认 `queryPage` 从完整结果中按 64 项分页。大来源应覆盖分页，只构造请求页并提供真实总数。带上下文重载接收同表单未保存字段作为查询提示，需重新验证 ID 与权限。`previewSource` 打开另一已注册来源的只读成员预览，不回填父字段。所有查询要求作者权限 2，且受传输长度限制。

`ClientConfigEditors.register(typeId, fieldKey, factory)` 创建子页面，确认时才提交。原单值工厂保持兼容，带上下文重载接收不可变配置并返回字段补丁，由作者服务合并。`label(value)` / `icon(value)` 描述字段按钮。`registerCreation(reward, type, factory)` 提供类型创建页，权限、revision 和最终校验仍归核心。这些注册只在客户端执行。

## 客户端展示

目标和奖励 presentation 使用不可变视图及物品副本。缺失展示时回退占位、完整类型 ID，不猜测交互能力。以下钩子均不能授权进度或发奖：

| 钩子 | 契约 |
| --- | --- |
| `interactive`、`acceptsQuestCompletionIntent`、`readyForSubmission` | 本地点击及满足提示，服务端重验 |
| `objectiveTitle`、`progressText`、`confirmed` | 目标与 HUD 语义，confirmed 解释服务端已存进度 |
| `displayedItem`、`acceptedItems`、`hasCandidateMenu` | 展示栈、Tooltip、可选 JEI 与候选入口 |
| `resolvedOptions` | 完整本地化成员列表，可用 SHOW_TEXT hover 携带原始 ID |
| `icon(view)` | 通过 `EditorIcon` 绘制图标 |
| `contentSummary(reward)` | 仅奖励内容，通用布局组合标题，不查询领取状态 |
| `titleDecoration`、`requiredCount` | 可选目标标题样式与展示数量 |

`typeIcon()` 没有实例参数，可返回 `EditorIcon`，也可在注册时提供静态图标：

```java
// 从客户端专用初始化入口注册，贴图由此附属模组提供。
ClientTaskPresentationRegistry.register(typeId, presentation,
    EditorIcon.sprite(ResourceLocation.parse("myaddon:quest_types/example")));
```

资源路径为 `assets/myaddon/textures/gui/sprites/quest_types/example.png`，建议 16×16 透明 PNG。顺序为 presentation 图标 → 注册图标 → 通用回退，未知外部类型不会按路径借用内置图标。已提供但缺失的资源使用图集 missing sprite，不应逐帧读取文件。

奖励 `refresh(reward)` 在客户端 tick 为当前选中的已完成任务调用，可做限频只读查询。`prepareClaim(revision, reward)` 准备预期类型响应。服务端 `clientClaimResponse(player, reward, result)` 在 revision 匹配的网络领取后调用，可发送选择页响应，但不得再次执行或修改收据。直接 Java API 领取不触发该网络响应钩子。

## 奖励领取与组合

`RewardContext` 包含玩家、ID 和不可变奖励视图。默认 execute 路径先标记普通收据并请求保存，再执行；保存请求不等于强制落盘。类型不得自行修改核心收据。

`claimHandler()` 可提供 `RewardClaimHandler`，在服务端线程和 owner 锁内、完成与成员及重复检查后调用。`RewardClaimContext` 带 owner、完成周期和 `claimGeneration`。`SUCCESS` 提交普通收据与事件；`PENDING` 保持未领并报告成功但无变化；`FAILURE` 保持未领并失败。异常报告 `CLAIM_HANDLER_FAILED`。

handler 负责副作用的预检、重入、持久化与恢复。非幂等效果需要持久证据。去重身份包含 owner、奖励、周期、领取代号及普通奖励领取者。完整重置创建新代号，旧存档保留空代号，读取时不能随机生成。异步完成需回到服务端线程、重查身份并重新进入 `claimRewardResult`，读取已存结果，不重放副作用。

`requiresManualClaim(config)` 为纯策略声明，true 要求逐项手动领取，自动与全领跳过，不兼容策略在发布时拒绝。类型仍自行提供选择界面和协议。

只有通过 `composition()` 声明能力的类型能作为表叶子：

1. `validateConfig` 纯配置校验。
2. `prepare` 预检资源、权限、数量并返回可持久化字符串数据，无副作用、不抽随机结果，可重复调用。
3. `freeze` 为已选 occurrence 固定一次数据，默认委托 prepare。协调器在副作用前强制保存。
4. `execute` 在强制 STARTED 证据后接收根上下文、稳定逻辑路径及唯一 occurrence。不得递归调用顶层领取或为叶子写普通收据。
5. PENDING 暂停后续叶子。`recover` 需可核实证据，默认结果未知，不能通过盲目重跑 execute 恢复。持久数据或恢复语义不兼容时改变适配器 `version()`。

恢复时需核对效果与收据，结果不确定时停止并等待核查。内置 custom 用于记录确认。管理命令见[内容恢复说明](CONTENT_REFERENCE_zh.md)。

## 示例附属与集成检查

[`src/exampleAddon`](../src/exampleAddon) 为独立编译的真实 NeoForge 模组 `brnquest_example`，开发运行默认加载，可用 `-PexcludeExampleAddon` 排除。这是使用公共 API 的开发专用示例。

示例包括按标签采样的 `marker`、单项提交 `signal`、支持整任务意图的 `checkmark`、独立槽位选择与合成计数 `item`、经验 `experience`，以及等待、拒绝、成功三态的 `guarded_tag`。后者展示幂等标签写入。`player_tags` 展示服务端字段来源，`brnquest_example:reward_experience` 函数给执行者增加 4 点经验。

适合的定向验证包括 `compileExampleAddonJava`、相关契约或单元测试及受影响的服务端、客户端场景。覆盖命名空间注册、Codec 拒绝、未知键保留、重复请求、失败恢复、专服隔离与缺失展示回退；新增 UI 仍需实际客户端观察。
