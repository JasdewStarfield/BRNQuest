# BRNQuest 扩展入口

公共面、稳定性、查询和写操作结果的总边界见 [`PUBLIC_API_zh.md`](PUBLIC_API_zh.md)。本文继续说明任务与奖励类型的阶段 2 实现契约；阶段 3 冻结前这些 SPI 仍标记为实验性。

阶段 2 的任务和奖励扩展采用“服务端行为 + 可选客户端展示”两条独立注册链。原生任务书继续使用 schema 1 的字符串 `config`，注册类型的 `Codec` 会在加载时将其解码为类型自己的不可变配置，并将失败写入诊断报告。

## 注册时间

- 服务端任务和奖励类型应在模组构造或 common setup 期间调用 `TaskTypeRegistry.register`、`RewardTypeRegistry.register`。
- 两个服务端注册表会在首次服务端资源 reload 监听器建立前冻结；冻结后注册会明确失败。
- 客户端展示应在客户端构造期间调用 `ClientTaskPresentationRegistry.register`、`ClientRewardPresentationRegistry.register`，并在 client setup 时冻结。
- 类型必须使用完整 `ResourceLocation`；`example:item` 不会继承 `brnquest:item` 的行为或展示。

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

`RewardType<TConfig>` 接收不可变 `RewardContext`，其中包含玩家、book/quest ID 和 `RewardView`。领取账本在调用扩展奖励前由 BRNQuest 持久化；扩展不得自行修改领取状态，并应把一次执行所需的全部副作用放在同一次调用中。

任务书 reload 成功后，BRNQuest 会在服务器线程重新对账所有在线玩家，并发送新定义和完整进度快照。新增的无前置任务因此应立即进入 `AVAILABLE`，而不是等待玩家重登。

## 客户端展示

客户端 presentation 只负责图标、符号、标题、进度文本、客户端预览和交互提示，不能成为进度权威来源。未注册展示的任务与奖励使用占位符，仍保留完整类型 ID 和服务端诊断。

- `ClientTaskPresentation` 的静态展示方法接收不可变 `TaskView`；标题、进度和提示方法接收 `TaskPresentationContext`，其中的物品栈已防御性复制。
- `ClientRewardPresentation` 接收不可变 `RewardView` 或 `RewardPresentationContext`，可根据仅供显示的 claimable/claimed 状态生成提示。
- `interactive` 和 `acceptsQuestCompletionIntent` 仅控制客户端是否建立点击入口；服务端仍会按注册的 `TaskType`、当前库存、权限和 revision 重新校验。
- 注册表按完整 `ResourceLocation` 查找。缺失 presentation 或仅路径同名时使用问号占位、完整类型 ID 标题且默认不可交互。
- 客户端注册表在 client setup 冻结；服务端任务/奖励实现不得引用这些 `client.ui` 类。

新增类型至少应覆盖：有效配置、Codec 失败诊断、完整命名空间隔离、手动/被动推进语义，以及未安装客户端展示时的占位行为。
