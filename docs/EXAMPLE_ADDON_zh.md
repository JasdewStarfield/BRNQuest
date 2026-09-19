# BRNQuest 示例附属模组

`src/exampleAddon` 是构建内独立 source set，并以真实 NeoForge mod `brnquest_example` 加载。它不与 BRNQuest 主源码合并编译，用于证明第三方模组只依赖公共接口也能完成一条任务链。

## 覆盖内容

- 被动任务 `brnquest_example:marker`：读取服务端玩家 scoreboard tag；
- 提交任务 `brnquest_example:signal`：通过公共提交入口推进；
- 确认任务 `brnquest_example:checkmark`：通过目标点击或任务级完成意图确认，服务端与客户端分别声明该能力；
- 物品任务 `brnquest_example:item`：独立实现物品匹配、槽位选择页和合成计数；
- 幂等奖励 `brnquest_example:experience`：奖励账本保证重复领取不重复执行；
- 任务与奖励的客户端 presentation，包括独立于领取状态的 XP 内容摘要；
- 通过 `BrnQuestPlugin` 与暂存 registrar 原子注册 common 类型；
- task/reward 的编辑器字段描述与带上下文的配置规范化；
- `QuestCompletedEvent` 只读订阅及监听器可观察副作用。

示例只导入 `api`、`extension`、`task`、`reward`、`event`、`editor` 和 `client.ui` 公共面，不读取进度存档、内部 definition、网络实现或 reload 管理器，也不使用反射。插件回调内先声明全部 common 类型，BRNQuest 预检成功后才一起提交。

## 运行示例

开发环境运行 `runClient` 或 `runServer` 可加载示例；`-PexcludeExampleAddon` 可排除示例附属模组。

示例代码是 API 消费范例，不是新的内置任务类型，也不会被打进 BRNQuest 核心 mod 的生产资源。

`checkmark` 使用可选 `title`；`experience` 使用正整数 `amount`（默认 3）。XP presentation 的 `contentSummary` 提供经验内容，通用布局再组合作者设置的 `title`，因此自定义名称不会隐藏数量。`signal` 继续只接受逐目标提交，适合对比两种完成意图。

`checkmark` 和 `experience` 使用 `normalizeConfig(context, config)`：作者写入时分别去掉标题首尾空白、把经验数量规范为正整数字符串，只返回自己拥有的字段。通用作者事务保留未知配置键；例如 `" 007 "` 保存为 `"7"`，无效或非正数量拒绝写入。两者无需查询注册表；需要解析资源的类型可使用 `context.registries()` 中的当前只读查找器。完整契约见 [类型扩展 API](EXTENSION_API_zh.md)。

## 独立物品交互与合成计数

物品任务使用 `item`（默认 `minecraft:stone`）、`count`（默认 `2`，范围 1–2304）、`consume`（默认 `true`）和 `crafting_only`（默认 `false`）。按物品 ID 匹配，剩余物品的组件保持原样。消耗模式由附属自己的页面选择背包格，再通过公共 `TaskSubmissionSelection` 提交；服务端重新检查这些格子，只消耗所需数量。不消耗模式直接检查当前持有量。`crafting_only=true` 时仅累计真实合成产出，背包已有物品和手动点击不计数。

该示例拥有自己的 Codec、规范化、服务端行为和客户端页面，不调用内置物品匹配器或内置选择页。它与 `brnquest:item` 分别注册，核心不会用示例类型替换内置类型；后续内置实现迁移仍保留自己的类型 ID。

## 命令奖励函数示例

示例扩展提供 `brnquest_example:reward_experience` 原生函数，为执行者增加 4 点经验。可将命令奖励的 command 设为 `function brnquest_example:reward_experience`，以领取玩家作为 `@s`，展示命令奖励调用数据包函数的用法。

## 可选领取接口示例

`brnquest_example:guarded_tag` 在独立附属模组内声明字段标签、枚举翻译和 tag 的空白规范化，并注册 RewardClaimHandler。玩家带 `brnquest_example_block` 时拒绝，带 `brnquest_example_wait` 时等待，清除后再次领取会添加配置的 tag。两种未成功状态均不消耗普通领取次数。它不授予经验，不执行命令；tag 的集合插入本身幂等，不能照搬为非幂等副作用的日志方案。

示例的 source_mode 字段刻意复用命令奖励的原始枚举值，但使用自己的翻译，证明 UI 不根据字段名或值猜测所属类型。新增类型只改附属模组实现、插件注册、客户端展示和资源；核心只通过公共契约调用。

## 被动采样与字段来源

marker 类型每 10 tick 观察玩家 tag，命中后由核心进度账本保留。tag 字段使用附属模组原子注册的 `brnquest_example:player_tags` 服务端来源，可搜索当前玩家已有的 tag，也可直接输入尚不存在的值。此示例仅依赖公共 SPI，不修改内部 Screen 或进度引擎。
