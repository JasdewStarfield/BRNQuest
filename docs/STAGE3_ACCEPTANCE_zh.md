# BRNQuest 阶段 3 验收记录

## 2026-08-13 第一批：公共查询与操作结果

> 对应计划项：3.1、3.2、3.3。实现、自动测试、文档、构建和相关 GameTest 已通过；共享计划中的正式勾选仍等待代码提交哈希。

### 实现范围

- 增加 `ApiStatus` 和 `ApiStability`，将当前公共 API、任务/奖励 SPI、客户端 presentation 和已有事件统一标记为 `EXPERIMENTAL`。
- 建立公共面清单，明确未列出包默认为 `INTERNAL`，并记录阶段 2 SPI 对内部定义/进度类型的过渡性泄漏。
- 增加任务书、章节组、章节、任务、task 和 reward 的不可变视图及完整查询入口。
- 将玩家进度投影限制为当前任务所属的 task/reward，使用资源位置键并补充完成时间。
- 无效查询 ID 改为空结果，避免把 `ResourceLocation` 解析异常暴露给集成调用方。
- 将写操作结果扩展为 `SUCCESS`、`NO_CHANGE`、`REJECTED`、`INVALID_REQUEST`、`NOT_READY`、`FORBIDDEN` 和 `STALE_REVISION`。
- 为完成 task、推进 task、领取单项/全部奖励、追踪任务和先同步后开屏提供结构化入口，同时保留现有布尔便利包装。
- 重复完成、重复提交和重复领取明确返回成功的 `NO_CHANGE`，不再伪装为本次发生了状态修改。

### 自动验证

- 构建前进程检查发现一个从 08:47 运行的未知 Java 进程；沙箱无法读取其命令行。`gradlew --status` 确认没有 Gradle daemon，后续单次 daemon 均正常退出，未发现工作区锁。
- `gradlew.bat compileJava compileTestJava --no-configuration-cache --no-daemon --console=plain`：通过。
- `gradlew.bat test --no-configuration-cache --no-daemon --console=plain`：通过。
- JUnit 汇总：18 个 suite、45 项测试、0 failure、0 error、0 skipped。
- `gradlew.bat build --no-configuration-cache --no-daemon --console=plain`：通过。
- `gradlew.bat runGameTestServer --no-configuration-cache --no-daemon --console=plain`：9/9 required GameTest 通过并正常保存、关闭。
- 两个工作树相关文件的尾随空白检查通过；版本工作树 `git diff --check` 通过。

### 本批未包含

- 3.4 的显式 actor、跨玩家权限和审计上下文；当前 Java API 仍视为受信任服务端集成入口。
- 完整只读事件集与观察者异常隔离。
- 用公共不可变上下文替换 `TaskType`、`RewardType` 和 presentation 对内部定义/进度类型的引用。
- ProgressOwner、编辑器字段描述、示例附属模组和公共签名兼容门禁。
- 客户端 UI 没有变化，因此本批未要求新增人工 UI 回归。
