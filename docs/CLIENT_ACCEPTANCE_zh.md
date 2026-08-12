# BRNQuest 1.21.1 NeoForge 客户端验收

这份清单用于需要真实窗口、分辨率和人工观察的验证。每次测试请使用本批代码重新启动客户端，避免旧 JVM 继续加载修复前的 class。

## 1. 准备 EOW 导入源

在 `1.21.1-neoforge` 目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\validation\prepare-eow-fixture.ps1
```

预期输出为 `Prepared 9 verified EOW fixture files`。脚本只复制测试 fixture，不修改仓库中的源文件。

## 2. 主菜单与语言

1. 启动 `runClient`，进入主菜单。
2. 打开 Mods，选择 BRNQuest。
3. 确认版本 `0.1.0-alpha.1`、作者和 MIT 许可证正常显示。
4. 将语言切换为简体中文，确认菜单可正常刷新，无缺字方框和崩溃。

## 3. 导入、重载与网络回归

创建一个允许作弊的新世界，然后依次执行：

```text
/brnquest import_ftb eow embers_of_winter main --dry-run
/brnquest import_ftb eow embers_of_winter main
/brnquest import_ftb eow embers_of_winter main
/reload
/brnquest validate
/brnquest open
```

预期结果：

- dry-run 和实际导入都报告 `6 chapters, 53 quests`；仅安装 BRNQuest 的开发环境应为 `0 errors, 6 warnings`，这些 warning 对应未安装的 Farmer's Delight 物品和 FTB 缺失物品占位符；
- 第二次实际导入拒绝覆盖已有数据包；
- validate 报告 `53 quests` 和一个 64 位十六进制 revision；
- `/brnquest open` 不得再出现 `String too big`、`EncoderException` 或断线；
- 任务书显示 6 个章节，节点可以选择，拖动画布与滚轮缩放有效；
- 初次开屏不显示详情栏，任务画布使用右侧空间；点击节点后详情以覆盖式抽屉打开，可用右上角 `×` 或 `Esc` 收起；
- 背景、章节文字、任务节点和详情文字均保持清晰，不受原版世界背景模糊效果影响；
- 带有效原版物品的显式图标正常显示，缺失模组物品或未知类型保留节点且使用安全占位显示。

## 4. UI 与进度回归

分别在 1280×720 和 1920×1080 窗口执行：

1. 逐个切换章节，检查章节栏、画布、右侧详情区不重叠或越界。
2. 选择可用 checkmark 任务并完成，确认状态更新且依赖任务随即解锁。
3. 追踪一个可用任务，确认 HUD 能显示追踪目标；再次点击取消追踪。
4. 获得某个 item 任务要求的物品，确认观察型任务自动完成。
5. 领取一个可领取奖励，连续点击两次，确认只发放一次。
6. 背包填满后领取物品奖励，确认剩余物品安全掉落在玩家位置。
7. 退出并重新进入世界，确认完成、追踪和领取状态保持。

## 5. 回传证据

请回传以下内容即可：

- 720p 和 1080p 各一张任务书截图；
- 上述第 3、4 节每项是“通过”还是“失败”；
- 若失败，附 `run/logs/latest.log` 中异常前后约 40 行，并说明当时选中的章节和任务。
