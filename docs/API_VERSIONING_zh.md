# BRNQuest API 版本与兼容策略

## 当前版本线

- 当前公共 API 基线为 `0.1.0-experimental.2`，由仓库内的编译后签名快照持续保护。
- 首个承诺稳定的 API 版本为 `1.0.0`。在到达该版本前，代码中标为 `EXPERIMENTAL` 的类型仍可调整，但每次变更必须同时更新文档、迁移说明和签名门禁。
- `1.0.0` 起，标为 `STABLE` 的公开签名在同一 major 版本内保持源码与二进制兼容；删除、改名、缩窄可见性或改变参数/返回类型都需要下一个 major 版本。
- `INTERNAL` 类型和未列入公共清单的包不进入兼容承诺，即使 Java 可见性是 `public` 也不能被外部集成依赖。

API 版本独立于模组发布版本：补丁发布可以在不改变 API 基线的情况下修复实现；如果实验性接口发生变化，则递增实验性序号并给出迁移说明。

## 自动门禁

`PublicApiSnapshotTest` 从编译后的类中读取运行时 `ApiStatus`，按确定顺序记录公开类型、构造器、字段和方法签名，并把完整结果写入：

`build/reports/public-api-signatures.actual.txt`

仓库保存该文本的 SHA-256 基线。普通 `test`/`build` 若发现签名变化会失败；维护者必须先审阅完整报告，确认稳定性等级、文档、示例附属模组和迁移影响，再有意更新基线。只更新哈希而不审阅报告不属于合法兼容变更。

## 版本升级规则

- 只新增 `EXPERIMENTAL` 公共面：递增实验性序号并更新示例与文档。
- 兼容地新增 `STABLE` 方法或类型：允许在 `1.x` minor 版本加入，但需要新契约测试。
- 修正文档、实现缺陷且签名不变：只递增模组 patch 版本。
- 删除或改变 `STABLE` 签名/语义：进入下一个 API major，并提供迁移说明和至少一个发布周期的弃用路径（安全或漏洞修复除外）。
- 将 `EXPERIMENTAL` 提升为 `STABLE`：必须已有外部消费者、专服/客户端隔离验证、失败回退和可重复的兼容测试。

## 调用约束

公共查询返回不可变投影；服务端写操作只能在目标玩家所属服务器线程执行，并通过 `OperationContext` 与 `OperationResult` 表达权限和结果。客户端 presentation 不是进度权威。事件只读、不可取消，监听器异常不会回滚已提交事务。更完整的包边界和生命周期规则见 [`PUBLIC_API_zh.md`](PUBLIC_API_zh.md)。

## experimental.1 → experimental.2

新增作者文本查询的六个 locale 重载：`getActiveBook(locale)`、`getChapterGroups(locale)`、`getChapterGroup(id, locale)`、`getChapters(locale)`、`getChapter(id, locale)`、`getQuests(locale)`。原有 `getQuest(id, locale)` 继续可用。已有签名和不带 locale 的原文查询行为不变，无需迁移；需要展示译文的调用方显式传入语言。数据 schema 和网络协议没有变化。
