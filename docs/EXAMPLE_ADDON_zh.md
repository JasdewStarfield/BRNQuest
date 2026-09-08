# BRNQuest 示例附属模组

`src/exampleAddon` 是构建内独立 source set，并以真实 NeoForge mod `brnquest_example` 加载。它不与 BRNQuest 主源码合并编译，用于证明第三方模组只依赖公共接口也能完成一条任务链。

## 覆盖内容

- 被动任务 `brnquest_example:marker`：读取服务端玩家 scoreboard tag；
- 提交任务 `brnquest_example:signal`：通过公共提交入口推进；
- 幂等奖励 `brnquest_example:experience`：奖励账本保证重复领取不重复执行；
- 两种 task 和一种 reward 的客户端 presentation；
- 通过 `BrnQuestPlugin` 与暂存 registrar 原子注册 common 类型；
- task/reward 的编辑器字段描述；
- `QuestCompletedEvent` 只读订阅及监听器可观察副作用。

示例只导入 `api`、`extension`、`task`、`reward`、`event`、`editor` 和 `client.ui` 公共面，不读取进度存档、内部 definition、网络实现或 reload 管理器，也不使用反射。插件回调内先声明全部 common 类型，BRNQuest 预检成功后才一起提交；`ExampleAddonBoundaryTest` 会对源码做独立边界检查，公共 API 签名门禁和 GameTest 则验证编译及运行契约。

## 构建与验证

- 默认 `test`、`build` 和 `runGameTestServer` 会编译并加载示例附属模组。
- 添加 `-PexcludeExampleAddon` 时，运行配置只加载 BRNQuest 核心，用于验证可选消费者完全缺失的组合；示例源码仍在 `check` 中编译，避免文档样例悄然失效。
- 默认 GameTest 应显示 `BRNQuest Example Add-on` 并完成公共注册、被动/提交任务、事件和重复领取验收。
- 纯核心 GameTest 中同一测试会确认附属模组缺失后安全跳过其运行契约，其余专服测试必须全部通过。

示例代码是 API 消费范例，不是新的内置任务类型，也不会被打进 BRNQuest 核心 mod 的生产资源。

## 命令奖励函数示例

示例扩展提供 `brnquest_example:reward_experience` 原生函数，为执行者增加 4 点经验。可将命令奖励的 command 设为 `function brnquest_example:reward_experience`，以领取玩家作为 `@s`，展示命令奖励调用数据包函数的用法。

## 可选领取接口示例

`brnquest_example:guarded_tag` 在独立附属模组内声明字段标签、枚举翻译和 tag 的空白规范化，并注册 RewardClaimHandler。玩家带 `brnquest_example_block` 时拒绝，带 `brnquest_example_wait` 时等待，清除后再次领取会添加配置的 tag。两种未成功状态均不消耗普通领取次数。它不授予经验，不执行命令；tag 的集合插入本身幂等，不能照搬为非幂等副作用的日志方案。

示例的 source_mode 字段刻意复用命令奖励的原始枚举值，但使用自己的翻译，证明 UI 不根据字段名或值猜测所属类型。新增类型只改附属模组实现、插件注册、客户端展示和资源；核心只通过公共契约调用。
