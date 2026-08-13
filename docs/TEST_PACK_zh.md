# BRNQuest 原生验收任务包

此测试包用于日常客户端和服务端回归。它只使用 BRNQuest 原生 schema 和原版物品，因此比 EOW 导入结果更适合复现单一功能问题；EOW fixture 继续用于 FTB 格式 13 导入兼容验收。

## 准备工作区

关闭正在使用 `run` 目录的客户端后，在版本目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\validation\prepare-acceptance-workspace.ps1
```

如果开发工作区已经含有 EOW 或作者数据，脚本默认拒绝覆盖。确认需要切换到验收包时执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\validation\prepare-acceptance-workspace.ps1 -Replace
```

`-Replace` 会先把现有目录移动为 `run/config/brnquest/workspace.backup-时间戳`。fixture 源文件不会被修改，复制前后都会校验 SHA-256。

## 加载到测试世界

新建允许作弊的世界时，工作区会自动部署。复用旧世界时执行：

```text
/brnquest workspace deploy --replace
/brnquest validate
/brnquest open
```

部署命令也会备份世界中原有的 BRNQuest workspace 数据包。`validate` 应报告 `15 quests` 和 revision，并且只出现两条刻意保留的未知类型诊断：`BQV-117`（`missing_test_mod:counter`）与 `BQV-118`（`missing_test_mod:token`）。自动 fixture 测试固定 3 个章节组、24 个章节、15 个任务及这两条诊断，不允许出现额外问题。旧世界可能保留相同 ID 的进度；需要重测某个任务时使用 `/brnquest progress reset <玩家> <任务ID>`，或直接创建新世界。

## 覆盖矩阵

| 章节/任务 | 主要验收点 |
| --- | --- |
| 01 / 欢迎 | checkmark 点击、默认图标、物品 Tooltip、单奖励 |
| 01 / 工作台 | 观察型物品、可完成状态、完成后不消耗 |
| 01 / 提交圆石 | 提交型物品、精确消耗数量、依赖解锁 |
| 01 / 多目标 | 三个目标独立完成、三项奖励独立领取 |
| 02 / 分支 | 追踪 HUD、纵向/斜向连线、双依赖汇合 |
| 03 / 长详情 | 描述换行、详情滚到底、十项物品奖励和 custom 奖励 |
| 04 / 跨章节 | “未解锁”悬浮中显示来源章节与任务名称 |
| 05 / 画布边界 | 部分可见节点、抽屉/把手裁剪、50%–200% 中心缩放 |
| 06 / 自定义类型 | 已注册 custom 类型的安全占位和管理员完成路径 |
| 06 / 未知类型占位 | 未注册任务/奖励显示问号、完整类型 ID，并产生预期 BQV-117/BQV-118 诊断 |
| 章节滚动压力 | 18 个空章节确保左栏能滚动到底并保持紧凑布局 |

未知类型任务是这套标准验收包中唯一刻意保留的负面案例。它不得阻止任务书加载，也不得被静默删除；除上述两条预期诊断外，标准验收不应产生其他诊断。
