# 探索与地点目标

BRNQuest 提供四种被动目标。玩家进入目标位置后自动完成，无需提交；离开位置或重登不会清除已记录的完成状态。服务端每 10 tick 检查当前可推进目标，正常 20 TPS 下延迟最多约 0.5 秒。旁观者不计入。依赖和顺序目标限制、个人/团队归属沿用任务书规则。

## 字段

所有类型可以填写 `title`。任务书 schema 1 的 config 值均为字符串。

| 类型 ID | 主要字段 | 匹配方式 |
|---|---|---|
| `brnquest:dimension` | `dimension` | 当前世界维度 ID 或 `#维度分组` |
| `brnquest:biome` | `biome` | 玩家方块位置的原生 biome ID 或 `#tag` |
| `brnquest:structure` | `structure` | 原生 structure ID 或 `#tag`，玩家须在有效结构 piece 范围内 |
| `brnquest:location` | `dimension`、`ignore_dimension`、`position`、`size` | 指定维度内的长方体区域；允许忽略维度 |

例子：`minecraft:the_nether`、`#minecraft:is_overworld`（biome）、`#minecraft:village`（structure）。不存在或空的选择器不会完成目标。结构检测只读取玩家所在区块及已经加载的结构起始区块，不执行 locate、不生成区块；结构包围盒内不属于任何 piece 的空隙不算发现。尚未加载的结构起始区块不会为检测而主动加载。

坐标示例：

```json
{
  "dimension": "minecraft:overworld",
  "ignore_dimension": "false",
  "position": "100,64,-20",
  "size": "4,3,5"
}
```

`position` 是最小方块坐标，`size` 是三个正整数。以上包含 x=100…103、y=64…66、z=-20…-16；最大端点不包含。按玩家方块坐标检测，不是球形距离。`ignore_dimension=true` 时不限制维度，坐标约束仍生效。

## 编辑器

在目标类型选择器选择对应类型；ID 字段打开通用搜索面板，可搜索原始 ID、`#tag` 或 `#分组`，查看当前值解析出的成员数量。搜索最多展示 64 个候选，可缩小搜索词。不存在的值可以保留，避免暂时缺失的数据包引用被静默改写；错误提示可悬浮查看解析原因。

维度和坐标字段各自提供“用当前值”，从服务端读取当前维度或方块坐标。生物群系也支持当前值；结构没有当前位置自动选择。点击完成才回填字段，取消不回填；仍需在父级作者表单保存并应用任务书。配置保存原始表达式，不保存解析后的成员快照。长 ID 在行内缩短显示，打开面板可编辑完整值。

完整重置会清除探索记录；仍在目标区域内时，下次采样会再次完成。重复任务开始新周期时遵循同样规则，不要求先离开再进入。已完成目标不会因数据包重载后标签变化而倒退；未完成目标和新周期使用重载后的数据。

## 维度分组

维度 key 与 dimension_type 不是同一个注册对象。维度目标不借用原版 dimension_type 标签，使用 BRNQuest 数据包分组：

`data/<namespace>/brnquest/target_groups/dimension/<path>.json`

```json
{
  "replace": false,
  "values": ["minecraft:overworld", "minecraft:the_nether"]
}
```

上述文件若为 `data/mypack/brnquest/target_groups/dimension/explorable.json`，字段填写 `#mypack:explorable`。values 可引用 `#mypack:other_group`。多个数据包按优先级追加，`replace=true` 清除更低优先级成员。循环引用、超过 32 层、重复维度、缺失维度/分组、空分组、错误 JSON 均不匹配，并在字段查询中报告。原版 `/reload` 刷新分组缓存。维度列表来自服务器实际加载的世界；新增维度本身可能需要按原版规则重启服务器。

## FTB 导入

支持 FTB Quests 1.21.1 的 dimension、biome、location、structure 目标及带 `ftbquests:` 前缀的类型 ID。ID 和 tag 保留原表达式。缺省访问维度沿用 FTB 的下界默认值；坐标目标维度默认主世界。

FTB location 的 position/size 三整数数组转成逗号分隔字段，原 SNBT 同时保留为 `ftb.position` / `ftb.size`。非法数组报告 BQF-108，非法尺寸或选择器由类型 Codec 拒绝；不会用半径近似源长方体。未知扩展字段继续保留。原生 biome/structure 标签直接使用；自定义维度分组需在数据包中定义。
