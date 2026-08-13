# BRNQuest 示例附属模组

`src/exampleAddon` 是构建内独立 source set，并以真实 NeoForge mod `brnquest_example` 加载。它不与 BRNQuest 主源码合并编译，用于证明第三方模组只依赖公共接口也能完成一条任务链。

## 覆盖内容

- 被动任务 `brnquest_example:marker`：读取服务端玩家 scoreboard tag；
- 提交任务 `brnquest_example:signal`：通过公共提交入口推进；
- 幂等奖励 `brnquest_example:experience`：奖励账本保证重复领取不重复执行；
- 两种 task 和一种 reward 的客户端 presentation；
- task/reward 的编辑器字段描述；
- `QuestCompletedEvent` 只读订阅及监听器可观察副作用。

示例只导入 `api`、`task`、`reward`、`event`、`editor` 和 `client.ui` 公共面，不读取进度存档、内部 definition、网络实现或 reload 管理器，也不使用反射。`ExampleAddonBoundaryTest` 会对源码做独立边界检查；公共 API 签名门禁和 GameTest 则验证编译及运行契约。

## 构建与验证

- 默认 `test`、`build` 和 `runGameTestServer` 会编译并加载示例附属模组。
- 添加 `-PexcludeExampleAddon` 时，运行配置只加载 BRNQuest 核心，用于验证可选消费者完全缺失的组合；示例源码仍在 `check` 中编译，避免文档样例悄然失效。
- 默认 GameTest 应显示 `BRNQuest Example Add-on` 并完成公共注册、被动/提交任务、事件和重复领取验收。
- 纯核心 GameTest 中同一测试会确认附属模组缺失后安全跳过其运行契约，其余专服测试必须全部通过。

示例代码是 API 消费范例，不是新的内置任务类型，也不会被打进 BRNQuest 核心 mod 的生产资源。
