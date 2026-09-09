# 原版进度目标与奖励

`brnquest:advancement` 同时注册为目标和奖励，使用原版 advancement 数据。目标完成记录仍由 BRNQuest 保存，原版进度保留在原版玩家数据中。

## 配置

| 字段 | 目标 | 奖励 |
| --- | --- | --- |
| `title` | 可选显示标题 | 可选显示标题 |
| `advancement` | 原版进度 ID 或 `#分组` | 原版进度 ID 或 `#分组` |
| `criterion` | 留空检查完整进度；非空检查单个条件 | 留空授予全部条件；非空仅授予该条件 |
| `mode` | `any`（默认）或 `all` | 不使用此字段，分组始终授予全部成员 |

编辑器提供服务端 ID/分组搜索和分页选择。`criterion` 仅支持单 ID，不能与分组同时使用；不存在的条件不会满足目标或吞掉奖励。悬浮在字段标签上可查看说明，包括重置行为和原版授予副作用。任务和奖励优先使用客户端已同步的原版进度 display 图标；未同步、无 display 或分组使用知识之书回退。图标仅用于绘制，不显示物品名称或 JEI 快捷键。目标默认标题为“达成指定进度”，作者标题仍优先。目标/奖励 tooltip 分别以“需要达成以下进度：”/“达成以下进度：”开头，其后优先显示原版进度本地化名称，无可用名称时回退 ID。奖励 tooltip 保留所选进度、criterion 与领取状态；详细机制仅保留在编辑器字段帮助中。

## 分组

原版 advancement 没有此处可使用的通用注册表 tag；`#` 表示 BRNQuest 自有数据包分组。文件路径：

`data/<namespace>/brnquest/target_groups/advancement/<path>.json`

```json
{
  "replace": false,
  "values": ["minecraft:story/mine_stone", "#example:other_advancements"]
}
```

与维度分组复用相同规则：数据包从低优先级到高优先级追加，`replace: true` 替换此前内容。支持嵌套；缺失成员、空组、重复成员、循环或超过 32 层的分组均拒绝。ANY 也要求整组引用有效；ALL 空组不成立。通过 `/reload` 刷新。

## 目标行为

- 任务成为可推进状态时，先前取得的原版进度也计入。依赖、顺序任务、团队账本沿用现有规则。
- 原版授予或撤销事件只使读取缓存失效；现有服务端采样循环在下一 tick 检查并写入任务进度，避免授予奖励时同步递归推进任务。
- 任务完成后锁定；后来撤销原版进度不会让任务倒退。重置 BRNQuest 任务不会撤销原版进度，原版进度仍满足时，新周期会再次完成。
- 缓存按玩家和配置隔离，登录及资源重载后重新检查。不发送逐 tick 无变化同步。该机制不等同于完成大型任务书性能压测。

## 奖励行为

先解析全部成员并校验 criterion，再走原版授予路径。缺失引用时返回失败，奖励保持待领。授予对象为本次合资格领取玩家，即使是团队奖励也不会默认授予所有成员。

授予可能触发原版经验、配方、奖励函数与提示；不会自动授予父进度。已经授予的条件视为成功无变化，不重复触发其完成奖励。领取路径复用 `RewardType.claimHandler()`，同一奖励执行期间拒绝同步重入，成功后才确认领取记录。原版奖励函数自身的其它副作用仍遵循原版行为。

## FTB 导入与扩展边界

字段语义参考 FTB v2101.1.34 的 [AdvancementTask](https://github.com/FTBTeam/FTB-Quests/blob/02bd2f0efe68721d1e87cd5cc69d9d3d43798b85/common/src/main/java/dev/ftb/mods/ftbquests/quest/task/AdvancementTask.java) 与 [AdvancementReward](https://github.com/FTBTeam/FTB-Quests/blob/02bd2f0efe68721d1e87cd5cc69d9d3d43798b85/common/src/main/java/dev/ftb/mods/ftbquests/quest/reward/AdvancementReward.java)。

FTB 的 `advancement` / `ftbquests:advancement` 目标和奖励映射到此类型，保留 `advancement`、`criterion` 及未知配置；不把单条件转换成整个进度。

类型通过现有任务/奖励和 presentation 注册表接入，字段由类型自身声明，查询来源复用 `ServerFieldSources`，缓存生命周期由模块自行订阅 NeoForge 事件。无需给 QuestScreen、ProgressEngine 或平台入口增加 advancement 类型判断。本功能保持公共 API experimental.8、网络协议 11 和任务书 schema 1。
