# 阶段 4 验收记录

## 4.1 生效快照与作者草稿分离

- 草稿固定存放于目标服务器的 `config/brnquest/drafts/<namespace>/<book-path>/`，不进入资源包 `data/` 搜索路径。
- `book.json` 使用 `NativeBookJson` 的确定性 UTF-8 表示；`draft.json` 只记录格式版本、任务书 ID、基础 revision 和草稿 revision。
- 可从当前生效任务书、服务器 workspace 或空任务书创建草稿。创建接口要求操作者是目标服务器当前在线且具有权限等级 2 的玩家。
- 草稿使用不可变 `QuestBookDefinition` 快照，不写 `QuestBookManager.active()`；只有后续显式 deploy + reload 才能切换生效快照。
- 目录写入先进入 UUID staging 目录再移动；重复创建返回 `DRAFT_EXISTS`，外部修改内容但未更新 manifest 返回 `DRAFT_REVISION_MISMATCH`。

## 4.2 服务端编辑会话

- `EditSessionService` 按 `MinecraftServer` 实例隔离，同一服务器、同一任务书只允许一个写者，不同服务器互不占锁。
- 开启、续租、关闭和查看均从目标服务器玩家列表确认连接身份，并重新检查当前权限等级 2；客户端本地 OP 信息不参与授权。
- 会话绑定随机 token、任务书 ID、操作者 UUID/名称、基础 revision、草稿 revision 和到期 tick；默认空闲超时 5 分钟。
- 续租和关闭必须提交预期草稿 revision，旧客户端返回 `STALE_DRAFT_REVISION`；其他管理员只能查看不含 token 的占用状态。
- 玩家断线、权限撤销后被清理、服务器停止或租约超时都会释放内存会话，已落盘草稿不受影响。

## 远程服务器与同步边界

- 管理员连接远程服务器时，草稿文件、会话表、权限判断和未来 CRUD 全部位于该远程服务器；客户端不会读取或覆盖自己的本地 `config/brnquest`。
- 4.1–4.2 尚未增加编辑器网络协议或界面入口；阶段 5 客户端只能通过后续受鉴权协议提交结构化操作，不能上传整本任务书覆盖服务器状态。
- 草稿和租约状态只面向获授权管理员，不向普通玩家广播。正式 `publish` 仍只修改 workspace；只有显式 deploy 并成功 reload 后，现有 `reconcileOnlinePlayers` 才向所有在线玩家同步新生效任务书和进度，之后加入的玩家取得同一 active revision。

## 自动验收

- `DraftRepositoryTest`：草稿目录隔离、UTF-8/LF manifest、确定性加载、重复创建冲突、外部修改冲突和 workspace 来源。
- `EditSessionServiceTest`：同服单写者、跨服隔离、重复打开幂等、旧 revision、过期、断线释放和重开。
- `remoteAdministratorsUseTargetServerPermissionsAndLeases` GameTest：目标服务器 2 级管理员与普通玩家边界、服务器草稿创建、第二管理员占用冲突和断线接管。
- `runGameTestServer`：15/15 required GameTest 通过。

## 4.3 草稿 CRUD 服务

- `DraftBookEditor` 只接收不可变任务书并返回新候选，不持有或修改 `QuestBookManager.active()`。
- `DraftEditService` 为任务书标题、章节组、章节、任务节点、依赖、task 和 reward 提供稳定 ID 驱动的创建、复制、修改、排序/移动和删除入口。
- 普通 update 不能改对象 ID，也不能借更新父容器覆盖其子对象；移动任务会同步更新 `chapterId`，保留任务内容和稳定 ID。
- 删除被依赖任务、非空章节或非空章节组默认返回结构化冲突；名称明确的 `remove*WithContents` / `removeQuestAndReferences` 才执行级联，并返回完整受影响对象集合。
- 每次成功操作先构造并校验完整候选，最后只替换一次会话内 `DraftSnapshot`；无效操作、旧 revision 或错误任务书不会产生部分修改。

## 4.4 增量与完整校验

- 每次 CRUD 对不可变候选检查所有权、重复 ID、容器与依赖引用、循环、不可达任务、legacy alias 目标、未知 task/reward type 和 type Codec。
- 对受影响对象检查必填标题、文本上限、有限坐标，以及第三方 `ConfigFieldDescriptor` 的类型、范围、枚举和自定义 validator。
- 显式完整校验扫描整本任务书，并附加章节组、章节和任务数量上限；诊断保留稳定代码、对象 ID 与字段路径。
- 错误或 fatal 诊断返回 `DRAFT_VALIDATION_FAILED`，结果附带诊断但仍指向原合法 snapshot；无效候选不计算 revision、不进入会话，也不广播给玩家。
- 专服 GameTest 额外验证远程管理员成功编辑、延迟旧 revision 被拒绝、无效编辑被拒绝且服务器会话 revision 保持不变。
- 完整 JUnit：85 项通过；`runGameTestServer`：15/15 required GameTest 通过；完整 `build` 通过。
