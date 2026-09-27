# 作者指引

[English](AUTHOR_GUIDE.md) | [首页](../README_zh.md)

## 游玩与编辑

按 **J** 打开任务书，选择章节与任务，查看目标、奖励，并将任务追踪到 HUD。提交物品时可以选择背包格，服务端重新检查实际库存。请求等待期间抑制重复点击。自选奖励需要明确确认；重新打开会恢复同一次已保存的尝试。

作者需要权限等级 2。章节和画布右键菜单提供创建与属性入口。任务书属性包含标题、回退语言和创建默认值；章节属性还提供分组、排序与自动聚焦。齿轮打开本机的滚动、吸附、缩放、动画及面板宽度设置，不修改任务书数据。

| 操作 | 效果 |
| --- | --- |
| 应用子选择器或子编辑器 | 只更新父表单；取消父表单仍可丢弃 |
| 确认普通任务书编辑 | 服务端接受后保存到当前世界并立即生效 |
| 保存高级草稿 | 保存独立草稿，游玩仍使用当前生效书 |
| 游戏内发布并应用草稿 | 审阅、发布、部署并重载，全部成功后生效 |

编辑器可能在等待服务端时预览待确认修改，以状态栏确认为准。拒绝后恢复原数据并显示错误。发布遇到 revision 冲突时，需要重新获取审阅结果。

### 操作与复制

- Tab/Shift+Tab 切换焦点，方向键浏览选择器，Enter 激活，Esc 返回或取消。物品提交用 F6 切换焦点组、Space 选择格子。
- 按住节点拖动；Ctrl 点击可混选任务与装饰，整体移动保留相对位置。锁定装饰会在移动和删除时跳过。
- Ctrl+C/Ctrl+V 复制粘贴画布选择，文本框保留普通文本快捷键。右键粘贴使用点击位置，键盘粘贴使用可见画布中心。
- 复制章节创建新的任务、目标及奖励 ID，重映射内部依赖和自动聚焦；外部依赖与不透明配置引用保留原值，不复制玩家进度。
- 配置和画布剪贴板限当前进程、世界或服务器及同一本任务书，快照上限 64 KiB。外部依赖已不存在时整次粘贴拒绝；批量修改占一个撤销步骤。
- 创建模板按核心 → 任务书 → 章节 → 显式值解析一次。已有、移动与复制的对象保留有效值。空白或默认值继承，显式 0 和关闭覆盖上级。

任务书设置还可抑制自动领奖、控制单人任务界面暂停。抑制自动领奖时保留奖励策略，并将隐藏自动奖励显示为可手动领取。多人游戏不会暂停。依赖线隐藏与章节自动聚焦只影响导航和显示。

## 美术资源与多语言

图标、装饰与背景可使用“浏览已加载贴图”。任务书保存 `namespace:textures/path.png` 资源 ID，其他客户端需要安装相同资源包。画布背景随平移缩放，界面背景随窗口适配；两者支持平铺、包含、覆盖、不透明度及缩放。应用背景子页后，还需确认外层属性表单。

“导入本地 PNG”和拖放支持不超过 8 MiB、每边不超过 4096 像素的静态 PNG。图片使用 `brnquest_local:textures/imported/<hash>.png` ID，保存在游戏目录 `brnquest/local-assets/assets/brnquest_local/textures/imported/`。本地库需单独备份；分发任务书时通过资源包附带贴图。

语言按钮可在不切换游戏语言的情况下编辑译文。缺失译文显示回退占位，未编辑字段不会新增翻译。切换语言保留待提交输入。正文编辑器提供纯文本、Markdown v1 及预览；语法见[内容配置参考](CONTENT_REFERENCE_zh.md)。

## 工作区与分发

```text
config/brnquest/
├─ imports/<source>/
├─ drafts/<namespace>/<book-path>/
├─ backups/
├─ reports/
└─ workspace/
   ├─ pack.mcmeta
   └─ data/
      ├─ brnquest/brnquest/active_book.json
      └─ <namespace>/brnquest/
         ├─ books/<book>.json
         ├─ chapter_groups/*.json
         └─ chapters/*.json
```

随整合包分发整个 `config/brnquest/workspace/`。运行时定义来自世界数据包。启动世界时，若 `datapacks/brnquest-workspace/` 尚不存在，BRNQuest 校验并复制工作区、启用数据包并请求重载。空工作区合法，符号链接会被拒绝。整合包升级不会静默覆盖世界里已有的部署。

`active_book.json` 选择最近发布的任务书。旧包没有此文件时按资源 ID 排序选择；指定 ID 无效时报告诊断并回退。玩家进度位于世界的 `data/brnquest_progress.dat`。

### 创建、导入与发布

游戏内草稿目录可从生效书创建草稿、保存版本和恢复历史。无图形界面时可从以下命令开始：

```text
/brnquest author create empty <book> <title>
/brnquest author open <book>
/brnquest author status <book>
```

`open` 返回会话 UUID 与 revision。内容命令先接 `<session> <book> <revision>`，然后接下列参数：

| 命令 | 后续参数 |
| --- | --- |
| `set_title` | `<title>` |
| `add_group` | `<group> <order> <title>` |
| `add_chapter` | `<chapter> <group> <order> <icon> <title>` |
| `add_quest` | `<quest> <chapter> <x> <y> <icon> <title>` |
| `add_dependency` | `<quest> <dependency>` |
| `add_task` | `<quest> <task> <type> <optional> <config-json>` |
| `add_reward` | `<quest> <reward> <type> <claim-policy> <team> <config-json>` |

使用完整 `namespace:path` ID，并在每次修改后采用返回的新 revision。配置 JSON 为只含原始值的扁平对象，不接受嵌套对象或数组；最终类型接收字符串值。

直接转换当前整合包已保存的 FTB v13 任务书，使用权限等级 2 的管理员命令：

```text
/brnquest import_ftb_local
/brnquest import_ftb_local --dry-run
/brnquest import_ftb_local <namespace> [book_id] [--dry-run]
```

命令直接读取服务端实例的 `config/ftbquests/quests/`（单人游戏为游戏目录），包括章节、语言和奖励表，无需移动文件，也不需要运行 FTB 模组。请先在 FTB Quests 中保存待导入的编辑。默认创建 `ftbquests:main` 草稿；指定命名空间后，省略任务书 ID 时仍使用 `main`。多人服务器读取服务端文件。`/brnquest workspace import_ftb_local` 支持相同参数。任务定义沿用现有[导入限制](CONTENT_REFERENCE_zh.md#ftb-导入)，不迁移玩家或队伍进度。源文件保持原样，同 ID 草稿已存在时拒绝覆盖；再次导入可指定新的 ID。

导入其他 FTB v13 任务书时，将源文件放在 `config/brnquest/imports/<source>/`：

```text
/brnquest workspace import_ftb <source> <namespace> [book_id] --dry-run
/brnquest workspace import_ftb <source> <namespace> [book_id]
```

Dry-run 显示诊断，并将转换报告写入 `config/brnquest/reports/`，不创建草稿。转换出现致命错误时也会保存报告。正式导入在没有致命错误时创建 `IMPORT` 草稿，保留源文件，同 ID 草稿已存在时拒绝覆盖。旧 `/brnquest import_ftb` 名称遵循同样的草稿导入流程。在编辑器的草稿目录中打开导入的任务书、检查诊断，再发布并应用到当前世界即可使用，具体转换规则见[导入限制](CONTENT_REFERENCE_zh.md#ftb-导入)。

命令行发布按以下顺序执行，任一步失败即停止：

```text
/brnquest author validate <session> <book> <revision>
/brnquest author diff <session> <book> <revision> workspace
/brnquest author save <session> <book> <revision>
/brnquest author publish <session> <book> <revision>
/brnquest author deploy
/brnquest author reload
```

每步读取返回的 revision 再继续。`publish` 要求草稿已保存且有效，只更新全局工作区。`deploy` 仅向尚无部署的世界复制；更新已有部署必须显式 `deploy --replace`，并先备份旧部署。`reload` 只重载已部署包，不从 config 复制。重载成功后才改变游玩内容。游戏内“发布并应用”在审阅后串联这些阶段。

会话在最近一次成功活动后保持 30 分钟，编辑器自动续租。命令可用 `author renew <session> <revision>`。断线、服务器停止或权限撤销会释放会话。`author close` 与 `author discard` 使用相同的两个参数；discard 丢弃会话内未保存状态，保留磁盘草稿。

## 队伍与奖励

安装兼容 OPAC 后，队伍使用稳定 party UUID。任务书 `share_team_progress` 默认 true，关闭后使用个人历史，同时保留队伍历史。入队、退队、换队及切换此设置都不会复制或合并历史。OPAC 缺失或不兼容时回退个人历史，原队伍账本保留。

普通共享任务共用目标进度，HUD 追踪与普通奖励收据各自独立。完成时冻结成员名单，包含离线成员。名单内成员各领一次普通奖励；`team_reward=true` 整队只发给首位合资格领取者。后来入队者没有旧轮次奖励资格。离线成员的自动奖励等待其在同一队伍重新上线后补领。

`require_all_team_members` 要求当前每位成员独立完成所有必需目标，离线成员也计入。个人完成后显示“等待队友”，直到全员完成。完成前的新成员需补做目标，退出成员不再阻塞；完成后的新成员不会让任务重新锁定。个人模式或未组队时按普通个人任务处理。

完整重置清空成员目标与收据，并创建新的领取代号。只重置某个目标会清空所有成员的该目标，保留收据。新重复周期清空成员目标。默认重复任务等待所有合资格普通奖励和团队奖励领完，包括离线或已退队成员。作者可启用 `ignore_reward_blocking`；进入新周期后，旧周期未领取资格结束。

## 备份、恢复与诊断

按需要备份整个世界、工作区、草稿与本地素材。奖励恢复需要玩家进度与命令、奖励表日志，应一并备份。进度 schema 2 升级保留旧个人记录。回退到不支持 schema 2 的版本前，需恢复升级前备份。

作者备份分为 `draft`、`workspace` 和 `deployed`：

```text
/brnquest author backups <kind>
/brnquest author restore_preview <kind> <backup-id>
/brnquest author restore <kind> <backup-id> <current-revision|->
```

使用预览返回的准确当前 revision，不存在时传 `-`。目标发生变化会拒绝；被覆盖的当前内容会再备份。恢复草稿前关闭相关会话，完成后重新打开。恢复只改变所选磁盘层，部署与重载仍需显式执行。部署备份位于世界的 `brnquest-backups/`，不进入数据包搜索目录。

| 命令或日志 | 用途 |
| --- | --- |
| `/brnquest health [details]` | 生效任务书和最近加载结果；需要权限 2 |
| `/brnquest health player <player> [details]` | 服务端近期发送记录 |
| `/brnquest_client health [details]` | 本机收到及应用的 revision、拒收历史；无需 OP |
| `/brnquest diagnose` | 当前完整校验报告 |
| `config/brnquest/reports/author-audit.jsonl` | 作者操作及稳定结果代码 |
| `logs/latest.log` 中的 `[BRNQuest/FILE_IO]` | 失败 IO 操作、路径、耗时与完整异常堆栈 |

服务端 `SUBMITTED` 只表示已交给网络发送器，需对照服务端、玩家发送记录和客户端实际 revision。客户端拒收历史保留到断线，可能包含失败后的成功恢复。IO 日志不能识别外部文件占用进程；反馈故障时保留完整堆栈和操作时间。可选的 `tools/diagnostics/Start-FileIoCapture.ps1` 辅助脚本需要限定目录的 ProcMon 配置及明确的过滤确认。

命令和奖励表在中断后可能结果未知，其状态查询、确认及受限重试命令见[内容配置参考](CONTENT_REFERENCE_zh.md)。不要通过删除日志修复领奖。
