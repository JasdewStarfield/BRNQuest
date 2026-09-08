# 作者网络层职责

本文面向维护者和扩展作者，说明作者编辑消息的内部边界。扩展应使用公共 `AuthorApi`、类型注册及编辑 schema；下列 package-private 实现类不是公共扩展 API。

## 消息经过哪些层

| 所有者 | 职责 |
| --- | --- |
| `AuthoringNetwork` | payload/wire 契约、客户端便利发送方法，以及注册委托 |
| `AuthoringPayloadRegistrar` | 11 条 C2S 和 3 条 S2C 的方向、Codec、玩家侧检查及 decoder/handler 接线 |
| `AuthoringRequestDecoder` | 字符串/JSON 边界、ID/UUID、大小限制、有限坐标及不可变请求 |
| `AuthoringSessionHandler` | 目录、打开实时/高级草稿、续租、关闭及冲突恢复 |
| `AuthoringPublicationHandler` | 保存、审阅和发布应用的服务调用顺序 |
| `AuthoringQuestUpdateHandler` | 任务属性更新与领域输入构造 |
| `AuthoringMutationHandler` | 结构、历史、位置、翻译和 task/reward 操作 |
| `AuthoringResponseSender` | 服务结果与诊断映射、字节预算、裁剪、分块及有序发送 |

请求先由 decoder 拒绝非法输入，再进入对应 handler。decoder 不查询玩家、会话或服务，也不发包。handler 通过现有 `AuthorApi`、`EditSessionService` 和 editor 执行；权限、租约、revision、校验、持久化和审计仍由服务层负责。handler 不直接依赖客户端类或网络发送器。

通用服务拒绝由 response 层保留 action、status、code 和 message。发布流程的阶段提示仍由 publication handler 决定，因为保存、工作区发布或部署可能已经落盘。response 层不能代替服务决定业务成功，也不能自行关闭会话；超限草稿的关闭操作由 handler 显式传入回调。

## 有意保留的兼容面

`AuthoringNetwork` 保留全部 14 个 payload 和 10 个 wire record，包括已有的附加响应字段构造器、客户端发送方法重载及注册委托。此次职责拆分保持协议号 `9`、payload ID、Codec 字段、JSON 字段、11/3 条方向映射和注册顺序。

façade 不再包含服务端请求桥接、JSON 解码、领域构造或响应辅助。客户端编码 wire 的 Gson 仅用于发送；服务端请求的 Gson 解码只有 decoder 一个所有者。客户端类只由 registrar 中受物理侧保护的嵌套 delegate 引用。

## 服务端语义

- 发布应用保持 `save → workspace publish → backup/deploy → renew → reload`；异步 reload 的响应回到服务端线程，后续失败明确说明已完成的阶段。
- task/reward 和任务复制从服务端当前快照生成，保留未知扩展 config、optional、claim policy 和 team 语义。
- 多节点位置输入有界、不可变且保持顺序；服务端返回 revision 绑定的 patch，超出 metadata 预算时退回完整草稿分块。
- 不跨请求缓存 session/book/revision。不同服务入口的权威检查、提交后的续租及恢复后的快照读取各有生命周期作用，不能为减少查询而跳过。`REVIEW` 仍先经过 mutation 的会话检查，以保留早期拒绝的 action，再调用发布预览与 diff 服务。

## 扩展与门禁

新增已有 envelope 内的操作时，在 decoder/action 和对应 handler 中实现；只有新增 payload 才需要变更注册。共享的纯输入规则属于 decoder，共享的响应格式属于 response 层，避免引入同时操作会话、业务和传输的通用工具类。

`ArchitectureBoundaryTest` 检查 façade、decoder、handler、response 和 registrar 的依赖方向；边界所有者必须存在，负例验证规则确实能拒绝非法依赖。`AuthoringProtocolTest` 检查真实注册、Codec 回环、方向和专用服务器类加载。真实玩家界面仍需要客户端回归，自动测试不能代替交互验收。
