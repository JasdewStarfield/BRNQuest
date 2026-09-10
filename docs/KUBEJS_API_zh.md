# BRNQuest KubeJS 服务端脚本 API

BRNQuest 在安装兼容版本的 KubeJS 与 Rhino 后，为 `server_scripts` 注册全局对象 `BRNQuest`。该对象不会出现在 `startup_scripts` 或 `client_scripts`，所有进度写入仍由 Minecraft 服务端主线程和 BRNQuest 事务处理。

当前验证基线：

- Minecraft `1.21.1`
- NeoForge `21.1.216`
- KubeJS `2101.7.2-build.374`
- Rhino `2101.2.8-build.91`

KubeJS 是可选依赖。未安装时不会加载 `compat.kubejs` 中的类，也不影响任务书加载、进度或奖励。

## 服务端观察事件

`BRNQuestEvents` 提供不可取消的服务端观察事件。它们只在 BRNQuest 权威状态已提交后发布，脚本异常由 KubeJS 按来源脚本记录，不会回滚已经合法完成的任务事务。

| 事件 | 可选目标 | 主要字段 |
| --- | --- | --- |
| `questCompleted` | quest ID | `player`、`playerId`、`playerName`、`bookId`、`questId`、`quest`、`progress` |
| `taskProgressChanged` | task ID | `player`、`playerId`、`playerName`、`bookId`、`questId`、`taskId`、`task`、`previousValue`、`currentValue`、`delta` |
| `rewardClaimed` | reward ID | `player`、`playerId`、`playerName`、`bookId`、`questId`、`rewardId`、`reward`、`progress` |

```js
// 观察所有任务完成。
BRNQuestEvents.questCompleted(event => {
  console.info(`${event.playerName} completed ${event.questId}`)
})

// 只观察一个稳定 ID；目标匹配使用完整命名空间。
BRNQuestEvents.taskProgressChanged('eow:radio_signal', event => {
  console.info(`${event.previousValue} -> ${event.currentValue}`)
})
```

`quest`、`task`、`reward` 和 `progress` 与全局 API 一样是普通只读投影，不是可变 Java 运行时对象。玩家已离线时不会向脚本转发该玩家的事件。

## 脚本任务与奖励类型

类型声明必须位于 `server_scripts` 的顶层加载过程：

```js
BRNQuest.registerTaskType('eow:radio_signal')
BRNQuest.registerRewardType('eow:play_dialogue')
```

- 必须使用完整 `namespace:path`；`minecraft` 和 `brnquest` 命名空间予以保留。
- 脚本不能覆盖 Java 插件或内置类型，同一批次的重复 ID 也会作为脚本错误拒绝。
- 每次服务端脚本加载先建立候选批次；只有整批脚本 0 errors 时才一次性替换活动类型。本批失败时保留上一份已成功类型快照。
- `getScriptExtensions()` 返回当前活动的 `taskTypeIds`、`rewardTypeIds` 和诊断用 `registrationOpen`。

脚本 task 是一种受控的“外部进度”目标，任务书 `config` 支持：

| 键 | 必需 | 语义 |
| --- | --- | --- |
| `required_progress` | 否，默认 `1` | 正整数阈值 |
| `title` | 否 | 目标行标题；空值回退为完整 type ID |

只能通过 `BRNQuest.addTaskProgress(player, taskId, amount)` 在服务端权威地推进；它不会将任意 KubeJS 回调当作客户端完成证据。

脚本 reward 的 `config` 可以携带任意字符串键值（约定 `title` 用于编辑器标题），副作用由必需目标的 `customReward` 事件执行：

```js
BRNQuestEvents.customReward('eow:play_dialogue', event => {
  // 对外部持久化、命令或消息发送，都以此键去重。
  const once = event.idempotencyKey
  console.info(`reward ${event.rewardId}: ${once}`)
})
```

`customReward` 还提供 `player`、`bookId`、`questId`、`rewardId`、`typeId` 和只读 `config`。`idempotencyKey` 由稳定 owner、reward ID 与已持久化的本次完成时刻派生；普通共享奖励额外包含领取者 UUID；脚本必须把它当作奖励副作的去重键，不要只按玩家或 reward ID 去重，否则可重复任务的后续周期会被误判为旧奖励。未注册对应 `customReward` 监听器时，奖励执行会明确失败并记录原因。

## `/reload` 顺序与失败恢复

服务端资源重载按以下顺序处理 BRNQuest 状态：

1. KubeJS 评估 `server_scripts`，BRNQuest 只收集本次 task/reward 候选类型。
2. 脚本 0 errors 时封存候选，但不替换活动注册表。
3. 解码候选任务书，仅在当前验证线程中用候选类型执行 Codec 和整书校验。
4. 全部通过后，先切换脚本类型，紧接着以单次指针写入切换任务书，然后才发布 reload 事件、对账在线玩家并同步。

脚本错误会由 KubeJS 记录原始脚本和行号，BRNQuest 同时记录 fatal 诊断 `BQV-006`。脚本或任务书任意一方失败时，新类型候选会被丢弃，上一份任务书 revision、来源资源键和脚本类型注册表继续生效。中断在 BRNQuest 应用阶段之前的 reload 也不会替换活动状态；下一次尝试会先覆盖上次未完成的候选。

KubeJS 会在每次脚本 reload 时卸载它自己的 JavaScript 监听器。BRNQuest 不保留旧 Rhino 函数或把它们跨 reload 重新调用；脚本失败后，旧任务书和类型仍可查询/推进，但依赖 `customReward` 回调的奖励会在无当前监听器时明确拒绝，而不是进入已卸载的脚本上下文。

所有查询结果都是调用时生成的普通不可变投影。脚本只应跨 tick/reload 保存稳定 ID，不应缓存 `quest`、`task`、`reward`、`progress` 或事件对象。

## 查询

| 方法 | 返回值 |
| --- | --- |
| `getActiveBook()` | 当前任务书普通对象；未加载时为 `null` |
| `getQuest(id)` | 任务普通对象；ID 非法或不存在时为 `null` |
| `getQuest(id, locale)` | 按任务书回退规则解析标题、副标题和描述 |
| `getProgress(player, questId)` | 玩家当前 owner 的进度对象；不可查询时为 `null` |
| `isQuestCompleted(player, questId)` | 完成或已经领奖时为 `true` |

返回的 ID 和枚举均为字符串；嵌套数据只包含字符串、数字、布尔值、只读 Map 和只读 List。每次调用都会查询当前活动任务书，不要跨 `/reload` 缓存返回对象。

## 写操作

| 方法 | 语义 |
| --- | --- |
| `completeQuest(player, questId)` | 集成级强制完成，明确绕过普通目标要求 |
| `submitQuest(player, questId[, checkmarkIntent])` | 按普通任务行规则尝试完成 |
| `completeTask(player, questId, taskId)` | 使用确定性的自动选择提交单个目标 |
| `addTaskProgress(player, taskId, amount)` | 为扩展目标增加正数进度 |
| `claimReward(player, rewardId)` | 幂等领取单个奖励 |
| `claimAllRewards(player, questId)` | 逐项经过既有领取事务 |
| `toggleTracked(player, questId)` | 切换当前玩家的追踪状态 |
| `openQuest(player[, questId])` | 先同步权威快照，再请求客户端打开任务界面 |

所有写操作固定使用集成来源 `brnquest:kubejs`，脚本不能自造管理员或系统权限，也不能调用重置接口。玩家为空、离线、当前不在服务端主线程、任务书未就绪或 ID 非法时不会造成脚本侧崩溃，而是返回结构化结果：

```js
{
  status: 'SUCCESS',
  code: 'OK',
  message: '...',
  success: true,
  changed: true
}
```

`NO_CHANGE` 的 `success` 仍为 `true`、`changed` 为 `false`，便于安全重试。脚本逻辑应判断 `status` 或 `code`，不要解析可能调整或本地化的 `message`。

## 示例

```js
PlayerEvents.loggedIn(event => {
  const quest = BRNQuest.getQuest('eow:repair_radio')
  if (quest === null) return

  const result = BRNQuest.submitQuest(event.player, quest.id)
  if (!result.success) {
    console.warn(`BRNQuest submit failed: ${result.code}`)
  }
})
```

```js
// 外部事件已经证明目标完成时，才使用强制完成入口。
const result = BRNQuest.completeQuest(player, 'eow:heard_emergency_broadcast')
if (result.changed) {
  console.info('Quest completed by integration')
}
```
