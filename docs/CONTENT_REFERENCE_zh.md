# 内容配置参考

[English](CONTENT_REFERENCE.md) | [首页](../README_zh.md) | [作者流程](AUTHOR_GUIDE_zh.md)

## 数据约定

使用完整 `namespace:path` ID。原生任务书 schema 1 的类型配置为字符串 Map，数字和布尔值也以字符串保存。嵌套物品匹配器与奖励树建议通过编辑器配置，其结构序列化在配置字符串中。编辑外部内容时保留未知字段。

下文集中说明具有重要行为约束的配置，游戏内字段帮助提供默认值与校验提示。任务依赖、顺序目标、完成可见性及重复规则在任务属性中配置；队伍进度与部署流程见作者指引。

## 物品与经验

- `brnquest:item` 支持观察与消耗。匹配器按物品或标签条目保存数量，`required_entries` 控制需满足的条目数。物品条目接受同类型但组件不同的栈，提交时由玩家选择实际格子。旧 `brnquest:item_choice` 仍可读取编辑，但隐藏于创建栏。
- `only_from_crafting=true` 只累计服务端观察到的新合成产出，要求恰好一个匹配条目，不接受手动背包提交。已有背包物品不作为合成证据。
- `brnquest:xp` 目标使用 `value` 和 `points`：true 为原始经验点，false 为完整等级。
- 新建经验奖励使用 `brnquest:xp`，数量为 `xp`，`points=true` 发经验点（旧配置默认值），false 发等级。旧 `brnquest:xp_levels` 仍按 `xp_levels` 发等级，可编辑和领取，但隐藏于创建栏。FTB 导入保留源奖励类型。

## 探索、观察与击杀

| 类型 | 字段 | 行为 |
| --- | --- | --- |
| `brnquest:dimension` | `dimension` | 维度 ID 或 BRNQuest `#分组` |
| `brnquest:biome` | `biome` | 玩家方块位置的群系 ID 或原生 `#tag` |
| `brnquest:structure` | `structure` | 结构 ID 或原生 `#tag`；玩家须位于真实结构 piece 内 |
| `brnquest:location` | `dimension`、`ignore_dimension`、`position`、`size` | 按方块坐标检测长方体 |
| `brnquest:observe` | `kind`、`target`、`distance`、`duration` | 连续观察同一方块或实体 |
| `brnquest:kill_entity` | `target`、`count` | 新发生且归属于玩家的有效击杀 |

均支持可选 `title`。探索每 10 tick 检查可推进的非旁观玩家，只读取已加载区块，不执行 locate 或生成区块。缺失或空选择器不匹配。离开后保留完成记录；重置或新周期时若仍在目标位置，可以立即再次完成。

坐标配置示例：

```json
{
  "dimension": "minecraft:overworld",
  "ignore_dimension": "false",
  "position": "100,64,-20",
  "size": "4,3,5"
}
```

`position` 为最小方块坐标，三个尺寸须为正整数。示例包含 x=100…103、y=64…66、z=-20…-16，最大端点不包含。忽略维度仍保留坐标限制。

观察使用 `kind=block|entity`、ID 或 `#tag` 目标、>0 且 ≤64 的距离（默认 8），及 0–1200 tick 的持续时间（默认 0）。方块和实体会遮挡视线；换目标、移开视线、超距或遇到未加载区块会中断。流体不作为目标。连续计时属于个人，断线、维度或配置变化、重载及重置清理临时计时；完整观察后才写入进度。

击杀默认 `minecraft:zombie`、数量 1（正 long）。玩家直接致命伤害及其投射物击杀计数一次；宠物、环境、取消死亡与历史击杀不计入。共享任务只增加一次账本，仍遵守依赖与顺序目标规则。

## 原版进度与目标分组

`brnquest:advancement` 同时可作为目标和奖励：

| 字段 | 目标 | 奖励 |
| --- | --- | --- |
| `advancement` | 原版 ID 或 BRNQuest `#分组` | 相同 |
| `criterion` | 空白检查完整进度，否则检查单条件 | 空白授予全部条件，否则授予单条件 |
| `mode` | `any`（默认）或 `all` | 分组授予全部成员 |
| `title` | 可选 | 可选 |

criterion 使用单个原版进度 ID。缺失引用会使校验或执行失败。目标可推进后，已有原版进度可以满足要求；之后撤销原版进度不会倒退已记录的任务。BRNQuest 重置不撤销原版进度，因此新周期可再次完成。

奖励先校验全部选择，再授予实际领取者，团队一次奖励也如此。可能触发原版经验、配方、函数及提示，不自动授予父进度。已有条件视为无变化，不重复触发完成奖励。

维度和原版进度使用 BRNQuest 自有分组，不使用 dimension_type 标签或通用 advancement 标签：

```text
data/<namespace>/brnquest/target_groups/dimension/<path>.json
data/<namespace>/brnquest/target_groups/advancement/<path>.json
```

```json
{
  "replace": false,
  "values": ["minecraft:overworld", "minecraft:the_nether"]
}
```

此维度示例按对应命名空间和路径保存后，可写为 `#example:explorable`；原版进度分组则填写进度 ID。支持嵌套 `#分组`，数据包按优先级追加，`replace=true` 替换。缺失或重复成员、空组、循环及超过 32 层均拒绝。`/reload` 刷新分组，只读候选列表可查看解析成员而不授予内容。

## 命令奖励

`brnquest:command` 执行一条服务端命令，多步效果可使用原版函数。领取者是命令源实体和 `@s`，使用其当前位置、维度，不授予永久 OP。

| 字段 | 含义 |
| --- | --- |
| `command` | 必填单行，≤32767 字符；可带前导斜杠 |
| `source_mode` | `explicit`（默认）或 `player` |
| `permission_level` | explicit 模式 0–4，默认 2 |
| `silent` | 抑制原版输出，默认 false |
| `feedback` | 可选作者原文领取提示 |
| `title` | 可选显示标题 |

示例：`give @s minecraft:paper 1`、`function example:rewards/intro`。`{p}`、`{x}`、`{y}`、`{z}` 分别替换玩家名和当前方块坐标，JSON/SNBT 花括号保持原意。不支持的 FTB 团队、章节及任务占位符需作者修正。

`serverconfig/brnquest-server.toml` 的 `commandRewardPermissionLimit` 默认 2。explicit 超出上限时预检拒绝；player 模式取当前玩家权限与服务端上限的较低值。预检检查语法与权限，执行结果取决于当前目标及所调用的命令。

尝试记录位于世界 `data/brnquest-command-rewards/`，执行前强制保存意图。失败或结果未知时停止自动重放。权限 2 可查询，权限 4 可在核实效果后确认：

```text
/brnquest command_reward status <player> "namespace:reward_id"
/brnquest command_reward acknowledge <player> "namespace:reward_id" <attempt UUID>
```

确认消耗本次奖励，不再执行命令；没有自动重试。完整任务重置创建新领取代号，单目标重置保留收据。世界备份需包含日志。任意命令与世界保存无法成为一个原子事务，结果未知时需核查。

## 奖励表

`brnquest:reward_table` 支持嵌套的**全部**、**随机**与**自选**模式。叶子包括物品、经验、命令、原版进度、原生战利品表、自定义确认及明确提供组合能力的 Java 类型；没有组合支持的脚本奖励不能作为叶子。

从“编辑配置 → 添加条目”进入。条目菜单可排序、复制、换类型或删除；排序保留 ID，复制创建新 ID。子级确认修改父草稿，只有外层确认才保存；取消根表丢弃全部本地修改。未知树版本保持只读。

- **全部：**按列表顺序发放。
- **随机：**1–64 次抽取，可配置放回与空结果权重。正条目权重为相对比例，保底项先按顺序各发一次且不进入随机池。不放回时已选条目移出池，空结果桶保留。全部抽空也会消耗领取资格。
- **自选：**打开服务端冻结的候选列表，明确选择后确认。关闭或重连保留候选，重复确认不重复发奖。任何嵌套自选使整个根奖励只能手动领取，自动与一键全领会跳过。

所有候选先通过预检，选择结果在副作用前保存，预览不抽奖。重复抽到子表会形成独立 occurrence；所有必需的嵌套选择完成后才开始交付。作者修改不能重抽已有尝试。共享尝试绑定首位领取者。每 tick 最多执行 8 个同步叶子，遇待处理操作暂停。

限制：配置 UTF-8 64 KiB、256 节点、8 层表、最坏展开 1024 叶子与 16384 节点、每物品叶子 4096 件、每次尝试 2 MiB。权重支持 34 位有效数字、规范化小数位 -308…324，值及总和须为有限 double。空全部表和自选表无效，无条目的随机表需正空权重。未完成尝试达到 1024 个或日志目录达到 256 MiB 时拒绝新增，不删除证据。

日志位于世界 `data/brnquest-reward-tables/`。中断或不确定叶子不自动重放，资源变化可阻塞未完成尝试并保留原数据。权限 2 查询，权限 4 确认或重试：

```text
/brnquest reward_table status <player> "<root reward ID>"
/brnquest reward_table acknowledge <player> "<root reward ID>" <attempt> "<occurrence>"
/brnquest reward_table retry <player> "<root reward ID>" <attempt> "<occurrence>"
```

确认接受已核实的不确定效果，不重复执行，并允许后续未开始叶子继续。仅 `FAILED_NO_EFFECT` 可在重新检查资源和适配器后显式重试。损坏记录或过期周期保持阻塞。新尝试使用日志版本 2，旧版本 1 保留原恢复语义。不要删除收据来解锁奖励。

### 原生战利品表

`brnquest:loot_table` 使用单个 `loot_table` 资源 ID 与可选 `title`。原生文件路径为 `data/<namespace>/loot_table/<path>.json`。通过 NeoForge 原生路径及全局战利品修饰器生成，使用领取者的世界、位置、实体和幸运值。不会虚构受害者、工具、伤害源或方块状态，缺少所需上下文时拒绝。

交付前保存物品栈与组件，背包溢出掉落在领取者位置，空结果也消耗资格。限制为 1024 个非空栈、4096 件、512 KiB 生成数据，同时受整次尝试 2 MiB 限制。保存结果不因重连或编辑而改变。重复选中的 occurrence 各自生成，未确认选择不生成。恢复沿用奖励表命令和不确定结果规则。

## 多语言与正文

在原生任务书 JSON 中添加：

```json
{
  "title": "My Book",
  "localization": {
    "fallback_locale": "en_us",
    "translations": {
      "zh_cn": {
        "title": "我的任务书",
        "quest.example:first.title": "第一个任务",
        "quest.example:first.quest_subtitle": "准备出发",
        "quest.example:first.quest_desc": "# 起步\n\n第一行",
        "quest.example:first.quest_desc_format": "markdown_v1"
      }
    }
  }
}
```

支持 `title`、`chapter_group.<ID>.title`、`chapter.<ID>.title` 及上面的任务键。按请求语言 → `fallback_locale` → 原文回退，缺失或空译文继续回退。语言名统一为小写及下划线。正文与格式始终来自同一语言，原生正文字段使用 `description_format`；复制、保存、撤销及发布成对保留。

`plain` 按字面显示 Markdown 标点，但支持原版 `§` 格式。`markdown_v1` 支持段落、显式换行、1–3 级标题、单层 `-`/`*` 列表、粗体、强调、行内代码及绝对 HTTP/HTTPS 链接。不支持有序或嵌套列表、引用块、代码围栏、HTML、远程图片、命令及其他 URL 协议。未知格式保留并按纯文本显示，作者写入不能创建未知格式。

附加语法（以下为原文示例）：

```text
[任务](brnquest:quest/example:first)
[下划线](brnquest:style/underline)
[样式](brnquest:style/color/red+underline+strikethrough)
[RGB](brnquest:style/color/12abef)
[乱码](brnquest:style/obfuscated)
![贴图](texture:example:textures/guide.png)
![物品](item:minecraft:stone)
```

任务链接使用当前任务书完整 ID，遵守隐藏规则。外部链接打开 Minecraft 确认页。颜色支持原版名称或六位 RGB。贴图须已加载，物品须已注册，不下载远程内容，不支持物品 SNBT 或组件。独立图片为块，行内图片适配文本行；缺失资源保留占位。物品图片支持原版 Tooltip 和 JEI，贴图没有物品交互。本地贴图不随发布分发。

## FTB 导入

FTB Quests v13 作为只读导入格式。富文本行为以固定的 Quests v2101.1.34 / Library 2101.1.35 为基线，发布前逐项查看诊断：

- 映射可见性、依赖策略、顺序目标、重复及冷却配置和受支持的经验类型。`only_from_crafting` 要求恰好一个匹配条目。
- 观察映射目标种类与计时，距离使用 8。击杀优先采用 `entityTypeTag`，其次 `entity`；FTB 省略数量时为 100。不支持的方块状态、实体 NBT 或名称筛选保留源数据及阻断标记 `ftb.encounter_error`，确定替代语义后才能移除。
- 地点数组转为逗号分隔坐标和尺寸，保留源 SNBT；非法形状拒绝，不用半径近似。原版进度 criterion 保持单条件语义。
- 命令映射权限和 silent。无法解析的 `feedback_message` 保留为 `ftb.feedback_message` 并报告 BQF-107，需填写原生 `feedback`；不支持的占位符需修正。
- `all_table`、`random`、`loot`、`choice` 与引用表转为独立原生奖励表快照，循环和缺失引用产生诊断。random 忽略源空权重，loot 保留；有效 `loot_size` 映射为放回抽取，零权重条目转为保底。FTB loot 与原生 `loot_table` 不同。
- 每种语言正文独立转换。支持的旧颜色与样式、安全 JSON 文本和 URL、图片及可解析任务链接转为原生 Markdown；不支持的动作移除，可见文本和非等价原文保留于诊断与来源扩展 `ftb.rich_text_source.*`，来源文本不参与运行。

替换已有部署前，先 dry-run 并在游戏内检查导入草稿。
