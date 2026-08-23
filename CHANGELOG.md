# Changelog

## Unreleased

- 将阶段 5 界面基础设施拆分为可复用组件：统一屏幕布局矩形、编辑按钮、文本框、滚动条、边缘收拢菜单、确认对话框和单一活动弹层；`QuestScreen` 继续负责渲染顺序与语义操作，画布节点仍使用适合缩放和裁剪的即时渲染。组件化不改变现有快捷栏、导航、画布、详情和弹窗像素尺寸。
- Stage 5 编辑器现在提供明确的“保存草稿”按钮；任务属性“完成”只更新服务端会话，保存成功后才清除未保存状态。退出编辑、切换任务书或按 Esc 时，未保存更改必须经过丢弃确认；界面被外部替换时保留仍有效的本地会话投影，以便重新打开后恢复。
- 草稿保存与增量编辑采用相同的诊断差量规则：原任务书已有的未知扩展错误可以逐字保留并保存，无关编辑不会再被旧诊断阻断，新引入或增加的 `ERROR/FATAL` 仍会拒绝保存。
- 任务书界面改为固定宽度的顶部与底部快捷栏：标题/草稿选择固定在顶部，状态/保存/退出固定在底部；导航、画布和详情抽屉仅占用中央内容区，左右抽屉展开或收起不再挤压快捷栏控件。
- 实现阶段 5.3–5.4 编辑操作：章节栏可创建、改名、排序和级联删除章节组/章节，危险删除显示章节、任务和依赖影响后再次确认；画布支持右键创建节点、节点复制/级联删除、Ctrl 多选及拖动，松开时把全部最终图坐标作为一个 revision 提交。
- 实现阶段 5.5 依赖关系编辑：右侧详情面板可查看明确的“前置任务 → 当前任务”方向、移除已有依赖，并从全任务书搜索添加跨章节前置任务；客户端即时标记可能形成的循环，服务端仍通过会话、revision 和增量验证拒绝非法候选。
- 实现阶段 5.6 任务基础属性编辑：紧凑表单可修改稳定 ID、标题、副标题和说明；图标可在易用的物品 ID 与高级纹理 Resource Location 之间切换并即时预览，旧物品 SNBT 保持兼容。仅用于序列化的任务顺序不再暴露。稳定 ID 变化必须经过影响确认，并由服务端在单一 revision 中改写依赖与旧 ID 别名；发布重载后迁移玩家任务级状态，稳定的 task/reward 账本保持不变。
- 实现阶段 5.7 task/reward 列表编辑：详情栏新增条件与奖励页签，可添加、服务端无损复制、上下排序、删除及编辑既有条目；列表按任务界面相同的 presentation registry 显示图标和本地化类型名，并把稳定条目 ID 作为次要信息。描述驱动属性表单可修改内置配置、optional、claim policy、team reward 与稳定 ID；ID 重命名经二次确认并记录独立 task/reward 迁移别名，发布重载不会丢失任务进度或重复领取奖励。未知类型原配置继续无损保留且不能绕过发布校验。`brnquest:item` task/reward 可打开独立幽灵物品栏，从玩家背包点击或拖放复制不消耗的 ItemStack 标记；JEI 拖入支持留待下一阶段。
- task/reward 客户端 presentation SPI 新增向后兼容的默认 `typeName(...)`，扩展类型可为编辑列表提供本地化名称；未实现时安全回退为完整类型 ID。
- 实现阶段 5.8 JEI 可选联动与字段诊断候选：开发客户端默认加载与 NeoForge 21.1.216 兼容的 JEI 19.25.1.334，发布元数据仅声明客户端 optional 依赖且 Maven POM 不携带硬依赖；独立 `compat.jei` 插件让物品选择屏幕显示 JEI 列表，并把 ItemStack 拖入 count=1 的幽灵目标。无 JEI 的核心类加载路径不解析任何 JEI 类型，可用 `-PexcludeJei` 验证。描述驱动表单现在按字段显示 `!` 标记和具体错误文本，服务端拒绝后保持表单及输入值，待可信草稿同步完成后才关闭。
- 修复物品选择器的 JEI 生命周期：改用带明确父 `QuestScreen` 的普通子 Screen，使首次打开即可拖入 JEI 物品；选择器持续绘制并 tick 父编辑器，进入配方页再返回后背景仍显示任务界面，完成、取消或 Esc 均恢复同一表单与草稿。渲染时先完成父编辑器画面，再模糊并暗化该画面，最后绘制选择器与 JEI 前景，避免父界面、选择器和 JEI 发生层级穿插。Windows 偶发占用 workspace 目录时，publish 原子移动会短暂有界重试并报告最终异常类型。
- 实现阶段 5.9 原始配置后备视图候选：已注册但没有字段描述、或字段描述加载失败的 task/reward 类型可从属性表单打开多行 JSON 编辑器；文本严格往返 schema 1 的扁平字符串 map，格式错误、嵌套值和协议越界会在本地阻止应用。应用只更新当前表单，最终完成仍经原有服务端 revision、完整候选增量校验和对应 Codec；拒绝时输入保持且原始配置行显示诊断。缺失注册的未知类型继续只读无损保留，不能借原始模式绕过未知类型发布阻断。
- 修复从原始 JSON 或物品选择子屏幕返回时父 `QuestScreen` 重新初始化输入控件、导致稳定 ID 及其它未提交字段变空的问题；父界面现在复用并重新注册原有控件，完整保留当前表单值、光标和选区。
- 修复发布期间已在途的租约续期响应把客户端从 `PUBLISHING` 提前切回可编辑状态、从而允许重复发布与重叠 reload 的竞态；忙碌状态不再发起周期续期，晚到响应只刷新租约数据而不会结束前台操作。发布流水线阶段记录、既有高频审计与任务书加载状态改为 DEBUG，默认日志保持简洁，独立作者审计报告继续完整保留。
- 修复 task/reward 类型选择弹层遗漏关闭分支、导致 Esc 和点击弹层外均无法退出的问题；标题栏新增明确的“×”关闭入口，三种退出方式复用同一状态清理路径。
- 实现阶段 5.10 会话级撤销与重做候选：底栏显示服务端确认的可撤销/可重做步数，每次操作仍以 session、book ID 和预期 revision 为边界，并在完整草稿分块验证后更新画面。历史只记录产生新 revision 的有效编辑，最多保留 64 步；撤销后进行新编辑会丢弃 redo 分支。保存不清空历史，发布成功、切换/关闭会话、断线、过期或 revision 冲突会清空历史；尚未提交的客户端表单打开时禁用历史按钮，避免静默丢弃输入。
- 编辑器固定底栏新增“发布并应用”：经确认后由服务端依次保存、完整校验、发布 workspace、带备份部署世界数据包并 reload；各阶段继续执行权限、会话和 revision 检查，失败消息会指出已经完成的持久化边界。
- 修复“发布并应用”失败后底栏只剩红色“编辑器”的问题：固定底栏状态不再被展开的导航抽屉挤压，错误正文和首条诊断可见并可悬停查看。ACTIVE/WORKSPACE 草稿发布现在与增量保存采用相同的诊断差量规则，允许原样保留来源版本已有的未知扩展占位，同时继续拒绝新引入的校验错误。
- 修复 ACTIVE 草稿发布时把“workspace 仍等于草稿来源基线”误判为 revision 冲突的问题；只有 workspace 已独立变化时才继续返回 `WORKSPACE_ALREADY_EXISTS`。冲突消息现在附带具体冲突代码及期望/实际 revision 摘要。
- 修复草稿发布并 reload 后，关闭再打开编辑会话会从旧磁盘 manifest 恢复过期基线并导致后续保存被 revision guard 拦截的问题；打开时仅在草稿与 workspace 内容 revision 完全相同时恢复或推进 WORKSPACE 基线，覆盖首次 ACTIVE→WORKSPACE 和后续重复发布的 WORKSPACE→WORKSPACE，分叉内容不会自动合并。保存冲突也会显示具体冲突代码与 revision 摘要。
- 修复编辑器输入框首次打开时内容看似为空的问题：字段在隐藏状态写值后，会在取得最终布局宽度时重算水平文本视口，不再需要逐个点击聚焦才能看到已有内容。
- 新增统一的服务端权威编辑 mutation 协议；所有结构和坐标意图继续经过目标服务器权限、会话 token、预期 revision、增量校验和完整草稿回传，快速重复操作在客户端提交状态中被抑制。

- Created the Minecraft 1.21.1 NeoForge and Java 21 development scaffold.
- Added client, dedicated-server, GameTest, and data-generation run configurations.
- Established loader metadata, UTF-8 resources, licensing, and version-specific worktree conventions.
- Stabilized stage-2 task and reward extension boundaries with full type IDs, typed config decoding, and frozen registration windows.
- Added client task/reward presentation registries so new types no longer require built-in UI type branches.
- Added deliberate unknown task and reward types to the native acceptance workspace for placeholder and diagnostic regression testing.
- Reconciled and fully synchronized online player progress after BRNQuest and workspace reloads.
- Preserved partially uninserted item rewards by dropping the remaining stack when inventory space is exhausted.
- Made consuming task submissions idempotent, guarded duplicate UI clicks, and immediately synchronized inventory counts after submission.
- Added the first stage-5 editor slice: target-server draft catalogs, permission-gated edit leases, verified chunked draft preview, automatic lease renewal, and multi-book viewport isolation.
- Added non-reflowing quest-screen editor overlays for the always-visible book title/ID, draft selector, edit-mode entry, and session/save status; the established navigation, canvas, and detail-panel dimensions remain unchanged when modes switch.
- Made “Edit current book” create or reuse the server-authoritative draft for the displayed active book, reduced the selector to the matching draft unless the administrator searches, and added two-line title/ID rows with full hover details.
- Added the first in-client quest mutation form for title, subtitle, and description; updates retain stable IDs and structural fields, pass server permission/lease/revision validation, and replace the client preview only after verified draft chunks arrive.
- Fixed editor popover depth and unfocused field readability, and changed draft mutation validation to reject only newly introduced blocking diagnostics so preserved unknown extensions do not make unrelated text edits unsavable.
- Raised the network protocol to `2`; clients and servers must use matching BRNQuest builds because protocol `1` does not contain authoring payloads.
