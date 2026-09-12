# Quest status badges / 任务状态角标

Editable 10 × 10 PNG GUI sprites, including the background, border and symbol:

- `tracked.png`: tracked quest / 跟踪中。
- `completed.png`: completed quests and claimed reward cells / 已完成任务与已领取奖励格共用。
- `blocked.png`: locked or unavailable / 未解锁或不可用。

Keep the canvas square and use crisp pixel edges. The renderer displays each sprite at 10 GUI pixels; no nine-slice metadata is needed. Resource reload applies edits through Minecraft's GUI atlas. These decorations do not change node click or JEI lookup targets.

背景、边框和符号均包含在 PNG 中，可直接手动修改。建议保持 10 × 10 像素；无需九宫格配置，重载资源即可更新。角标只作装饰，不改变节点点击范围或 JEI 查询对象。
