# Changelog

## Unreleased

- 将阶段 5 界面基础设施拆分为可复用组件：统一屏幕布局矩形、编辑按钮、文本框、滚动条、边缘收拢菜单、确认对话框和单一活动弹层；`QuestScreen` 继续负责渲染顺序与语义操作，画布节点仍使用适合缩放和裁剪的即时渲染。组件化不改变现有快捷栏、导航、画布、详情和弹窗像素尺寸。
- Stage 5 编辑器现在提供明确的“保存草稿”按钮；任务属性“完成”只更新服务端会话，保存成功后才清除未保存状态。退出编辑、切换任务书或按 Esc 时，未保存更改必须经过丢弃确认；界面被外部替换时保留仍有效的本地会话投影，以便重新打开后恢复。
- 草稿保存与增量编辑采用相同的诊断差量规则：原任务书已有的未知扩展错误可以逐字保留并保存，无关编辑不会再被旧诊断阻断，新引入或增加的 `ERROR/FATAL` 仍会拒绝保存。
- 任务书界面改为固定宽度的顶部与底部快捷栏：标题/草稿选择固定在顶部，状态/保存/退出固定在底部；导航、画布和详情抽屉仅占用中央内容区，左右抽屉展开或收起不再挤压快捷栏控件。
- 实现阶段 5.3–5.4 编辑操作：章节栏可创建、改名、排序和级联删除章节组/章节，危险删除显示章节、任务和依赖影响后再次确认；画布支持右键创建节点、节点复制/级联删除、Ctrl 多选及拖动，松开时把全部最终图坐标作为一个 revision 提交。
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
