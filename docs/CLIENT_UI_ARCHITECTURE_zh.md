# BRNQuest 客户端 UI 架构约定

本文面向维护 BRNQuest 客户端界面或为 task/reward 提供客户端展示的开发者。服务端行为、权限和进度写入仍以 [`PUBLIC_API_zh.md`](PUBLIC_API_zh.md) 与 [`EXTENSION_API_zh.md`](EXTENSION_API_zh.md) 为准。

## 顶层 Screen 的职责

`QuestScreen` 是界面生命周期和状态编排器，负责：

- 从 `ClientQuestState`、`ClientEditorState` 选取当前权威快照；
- 构造一帧一致的布局和身份信息；
- 按 overlay、详情/导航、画布的顺序派发输入；
- 将子区域返回的语义 intent 转换为网络请求或子 Screen 跳转；
- 在派发前重新检查当前任务书、revision、权限和 pending 状态。

子区域不得持有整个 `QuestScreen`，不得直接读取可变客户端单例，也不得发送网络请求。客户端可用性和 pending 标记只改善交互，服务端仍会重新验证权限、revision、任务条件、物品消耗和奖励领取。

## Model、Frame 与 Intent

新增可交互区域沿用以下数据流：

1. `Model` 是本帧展示所需的不可变输入，只包含已经投影或计算好的状态。
2. `Layout` 描述该区域的矩形、裁剪范围和指针位置，不自行读取 Screen 尺寸。
3. `Frame` 保存实际绘制后可命中的几何与 `QuestScreenFrameIdentity`；输入只能命中同一任务书、revision、编辑模式和尺寸产生的 Frame。
4. `Intent` 只表达“选择章节”“提交目标”“领取奖励”等语义，不包含网络调用。
5. 顶层 Screen 收到 intent 后重新解析实时对象并派发操作；旧 Frame 的 intent 必须被拒绝。

纯展示 widget 可以直接返回包含命中区、Tooltip 或 JEI 目标的 `Result`。只有需要跨帧输入状态的区域才应保存 Frame；不要为了统一形式给无状态组件增加缓存。

## 当前组合边界

- `QuestNavigationPanel`：章节/分组导航、滚动和导航 intent。
- `QuestDetailsPanel`、`QuestTaskRowWidget`、`QuestRewardCellWidget`：详情排版、目标与奖励条目。
- `QuestCanvasRenderer`、`QuestCanvasController`：节点画布绘制、相机、选择、平移、缩放和拖拽 intent。
- `QuestTypedEntryListSection`、`QuestTypedPropertySection`：类型条目列表、属性表单状态和提交值。
- `QuestEditorChrome`、`QuestPublishReviewSection`：编辑器工具栏、诊断与发布审阅。
- `client.ui.component`：按钮、列表、表单、弹窗、滚动和几何等可复用原语。

这些名称表示职责边界，不要求继承同一个 widget 基类。只有边界稳定并出现多个真实调用方时才提炼通用组件；画布节点等高数量对象继续使用批量 Frame 和集中绘制。

## 渲染与输入规则

- 从同一个布局快照生成绘制和命中几何，不能用上一帧位置处理当前输入。
- 命中区域必须与可见裁剪矩形求交；部分可见的条目只在可见像素内响应，完全不可见时不响应。
- Tooltip 和 JEI 查询目标在不透明面板及基础 Screen 完成后统一绘制；被遮挡对象不得保留 hover。
- overlay 和子编辑器优先于详情/导航，详情/导航优先于画布；动画未稳定时应屏蔽可能落到错误区域的输入。
- task/reward 的文本、图标和交互提示通过 presentation registry 扩展，不在 `QuestScreen` 增加类型 ID 分支。

## 新增区域检查清单

- 输入是否可表示为不可变 Model/Layout，输出是否为 Result/Intent？
- 是否绑定 `QuestScreenFrameIdentity` 并拒绝旧 revision 或 resize 前的 Frame？
- 是否只命中裁剪后的可见区域，并保持 Tooltip 的最终层级？
- 是否避免网络包、`QuestScreen` 引用及 `ClientEditorState.get()`/`ClientQuestState.get()`？
- 是否为纯状态转换、坐标、动作优先级和旧帧拒绝补充无 Minecraft 运行时的单元测试？
- 是否在提交前运行 `ArchitectureBoundaryTest`、`UiCompositionBoundaryTest` 和对应交互回归？

`ArchitectureBoundaryTest` 从编译后字节码检查组合 UI 对网络和顶层 Screen 的依赖；`UiCompositionBoundaryTest` 补充检查直接访问可变客户端单例的源码调用。两者用于防止后续功能把已拆出的职责重新塞回子区域。
