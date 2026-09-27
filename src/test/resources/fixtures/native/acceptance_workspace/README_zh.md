# BRNQuest 原生验收工作区

此目录是只读测试 fixture，不是发布内容，也不依赖 EOW、FTB Quests 或任何可选模组。

固定基线：

- schema 1；
- 4 个章节组、28 个章节、38 个任务；
- 18 个空章节专用于稳定触发章节栏滚动；
- 全部物品均来自原版 Minecraft；
- `SHA256SUMS` 记录可部署文件的固定哈希。

覆盖范围包括旧版 checkmark、观察型和提交型物品目标、逐目标完成、多奖励、长详情滚动、追踪、分支和纵向连线、跨章节依赖提示、画布边界裁剪、中心缩放、自定义类型、未知类型安全占位及 legacy alias；第二批新增节点/详情/文本/锁图标隐藏、四种依赖策略、最少依赖数、顺序目标、真实合成产出计数、经验点/等级目标与奖励，以及受冷却和奖励阻塞控制的可重复任务。未知类型固定产生 BQV-117 和 BQV-118 两条预期诊断；出现其他诊断即为回归。

不要让客户端、部署脚本或测试直接修改此目录。使用 `tools/validation/prepare-acceptance-workspace.ps1` 将经过哈希校验的副本放入开发实例。

## English

This is a read-only native acceptance fixture, excluded from release content and independent of EOW, FTB Quests and optional mods. Its fixed schema-1 baseline has 4 chapter groups, 28 chapters and 38 quests; 18 empty chapters exercise scrolling. Items are vanilla, and `SHA256SUMS` fixes the deployable file hashes.

Coverage includes checkmarks, observed/submitted items, per-objective submission, multiple rewards, scrolling, tracking, dependency lines, clipping, zoom, custom/unknown types and legacy aliases. Additional cases cover visibility, dependency modes/minimum counts, sequential objectives, actual crafting output, XP points/levels and repeating quests with cooldown/reward blocking. Unknown types intentionally produce BQV-117 and BQV-118; other diagnostics indicate a regression.

Do not modify this directory from a client, deployment script or test. Use `tools/validation/prepare-acceptance-workspace.ps1` to stage a hash-verified copy in a development instance.
