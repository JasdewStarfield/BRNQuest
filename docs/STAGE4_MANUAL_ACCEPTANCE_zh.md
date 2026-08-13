# 阶段 4 远程专服人工验收

自动测试已经覆盖权限、revision、磁盘事务、备份恢复和 deploy/reload 的服务端语义。以下项目依赖真实客户端网络连接、聊天回显与浏览界面，GameTest 无法完整代替；建议在阶段 5 开始前执行一次。

## 准备

- 使用临时专用服务器和可删除的新测试世界，客户端与服务端安装本次构建的同一 BRNQuest JAR。
- 准备一个权限等级 2 的管理员账号和一个普通玩家账号；不要使用正式整合包世界。
- 记下测试开始前 `/brnquest open` 显示的任务书标题，并备份服务器 `config/brnquest/` 与世界目录。

## A. 远程权限与会话回显

1. 普通玩家输入 `/brnquest author`，确认命令不可见或无法执行。
2. 管理员执行 `/brnquest author create empty manual:stage4 阶段4人工验收`。
3. 管理员执行 `/brnquest author open manual:stage4`，复制回显的 `session=` 与 `revision=`。
4. 用该 revision 执行 `add_group`；确认成功消息同时给出新的 `revision=`。
5. 故意再用旧 revision 执行一次 `set_title`；应返回 `STALE_DRAFT_REVISION`，随后 `status manual:stage4` 显示的 revision 不变。

## B. 纯命令建书

每一步都把上一条回显的新 revision 代入下一条命令：

```text
/brnquest author add_group <session> manual:stage4 <revision> manual:main 0 主线
/brnquest author add_chapter <session> manual:stage4 <revision> manual:start manual:main 0 minecraft:stone 起点
/brnquest author add_quest <session> manual:stage4 <revision> manual:first manual:start -2 0 minecraft:stone 第一项
/brnquest author add_quest <session> manual:stage4 <revision> manual:second manual:start 2 0 minecraft:diamond 第二项
/brnquest author add_dependency <session> manual:stage4 <revision> manual:second manual:first
/brnquest author add_task <session> manual:stage4 <revision> manual:first manual:confirm brnquest:checkmark false {"title":"确认第一项"}
/brnquest author add_reward <session> manual:stage4 <revision> manual:first manual:reward brnquest:custom manual false {}
```

执行 `validate`，应返回 `DRAFT_VALID`；执行 `diff ... workspace`，应列出新增的章节组、章节、任务、依赖、task 与 reward。

## C. 发布、生效与客户端展示

1. 依次执行 `save`、`publish`，每一步都应成功；此时重新打开任务界面，仍应是旧 active 内容。
2. 执行 `/brnquest author deploy --replace`；任务界面仍不应提前变化。
3. 执行 `/brnquest author reload`，等待聊天出现 `RELOAD_COMPLETE`，不要只依据 `RELOAD_REQUESTED`。
4. 执行 `/brnquest open`，确认标题为“阶段4人工验收”，两个节点位置正确，第二项连向/依赖第一项，第一项详情中显示 checkmark task 与 custom reward。
5. 退出服务器再重连，确认任务书仍能打开且内容一致。

## D. 最小恢复检查

1. 执行 `/brnquest author backups workspace`，选择一条测试过程中产生的 backup ID。
2. 执行 `restore_preview workspace <backup-id>`；若 ID 含 `/`，用双引号包住。复制回显的 `current_revision`。
3. 执行 `restore workspace <backup-id> <current_revision>`，确认返回 `BACKUP_RESTORED`。
4. 恢复后先打开任务界面，确认 active 尚未变化；只有后续显式 deploy/reload 才允许切换。

若任一步不符合预期，请保留服务端 `latest.log`、命令完整回显，以及 `config/brnquest/reports/author-audit.jsonl` 对应行。
