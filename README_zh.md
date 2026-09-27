# BRNQuest

简体中文 | [English](README.md)

BRNQuest 是面向 Minecraft 整合包的服务端权威任务系统，提供游戏内可视化编辑器。作者可以创建任务书、连接目标、配置奖励，并将受支持的 FTB Quests v13 内容导入为原生数据。

## 安装

- 模组版本：**1.0.0**
- Minecraft **1.21.1**、NeoForge **21.1**、Java **21**
- 服务端与所有客户端安装同一版本的 BRNQuest；单人游戏使用同一个 JAR。
- FTB Quests 仅作为导入来源，运行时无需安装。

| 可选联动 | 用途 | 开发基线 |
| --- | --- | --- |
| JEI | 查询物品配方与用途 | 19.25.1.334 |
| Open Parties and Claims | 队伍进度与奖励资格 | 0.30.3 |
| KubeJS + Rhino | 服务端脚本、事件与自定义类型 | 2101.7.2-build.374 + 2101.2.8-build.91 |
| BRNTalk | 通过 BRNTalk 自带适配器提供对话目标与奖励 | 使用兼容 BRNQuest API 的版本 |

开发基线与加载版本范围分别维护。可选依赖允许 JEI `[19.8.4.110,20)`、KubeJS `[2101.7.0-build.126,2102)`；OPAC 不设最低版本门槛。BRNQuest 已通过上述两个下限版本的 API 编译检查，旧版联动仍需游戏内验证；Rhino 版本要求由 KubeJS 自身决定。

## 从这里开始

按 **J** 或点击物品栏左上角的任务图标打开任务书，可在控制设置中改键。作者操作需要权限等级 2。普通编辑即时修改当前世界，高级草稿用于审阅后发布。整合包应附带任务工作区；引用的美术资源通过资源包分发。

| 指引 | 简体中文 | English |
| --- | --- | --- |
| 作者指引：编辑、部署、队伍与恢复 | [阅读](docs/AUTHOR_GUIDE_zh.md) | [Read](docs/AUTHOR_GUIDE.md) |
| 内容配置参考：目标、奖励、多语言与导入限制 | [阅读](docs/CONTENT_REFERENCE_zh.md) | [Read](docs/CONTENT_REFERENCE.md) |
| Java API：查询、操作、作者会话与兼容策略 | [阅读](docs/API_zh.md) | [Read](docs/API.md) |
| 类型扩展：注册、界面与奖励恢复契约 | [阅读](docs/EXTENSION_API_zh.md) | [Read](docs/EXTENSION_API.md) |
| KubeJS 脚本 API | [阅读](docs/KUBEJS_API_zh.md) | [Read](docs/KUBEJS_API.md) |

## 从源码构建

使用 Java 21 与仓库自带的 Gradle Wrapper：

```powershell
.\gradlew.bat build --no-configuration-cache --no-daemon --console=plain
```

产物位于 `build/libs/`。`runClient` 和 `runServer` 启动开发实例，可加 `-PexcludeExampleAddon` 排除开发专用的示例附属模组。核心、内置类型与适配层独立编译，最终合并为一个 JAR。只依赖公共接口的示例附属说明见[类型扩展](docs/EXTENSION_API_zh.md)。

## AI 与开发者职责

BRNQuest 由开发者主导设计与维护，使用 AI 工具参与开发。

- **开发者**：负责功能设计、架构决策、美术素材、实际游戏测试与验收，以及版本发布和持续维护；决定实现方案与最终采用的内容。
- **AI**：根据开发者的需求与反馈生成和修改代码，协助重构、问题分析、自动化验证与文档编写。

## 反馈与许可证

通过 [GitHub Issues](https://github.com/JasdewStarfield/BRNQuest/issues) 反馈可复现问题，附模组与加载器版本、操作步骤及相关日志。公开日志前检查其中的私人路径与玩家信息。

BRNQuest 使用 [MIT 许可证](LICENSE)，内嵌依赖声明见[第三方声明](THIRD_PARTY_NOTICES.md)。
