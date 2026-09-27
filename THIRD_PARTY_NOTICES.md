# Third-party notices / 第三方声明

## English

BRNQuest embeds `org.commonmark:commonmark:0.30.0`, Copyright (c) 2015 Robin Stocker, under the BSD 2-Clause License. The complete license is shipped inside the mod JAR at `META-INF/licenses/commonmark.txt` and maintained in [the source resource](src/main/resources/META-INF/licenses/commonmark.txt).

The FTB Quests importer converts format-13 quest data into BRNQuest's native model; its rich-text compatibility baseline is FTB Quests v2101.1.34. FTB Quests and FTB Library source was consulted to clarify serialized-field meanings and text behavior. The compatibility implementation uses BRNQuest's own conversion model, text nodes, and diagnostics. This development process is described as independent compatibility implementation, without claiming a strict clean-room process.

Substitution properties use ASCII spaces as delimiters and the first colon as the key/value boundary. Bare keys have empty values, later entries replace earlier values, and only `%20` in values is decoded. The property scanner is maintained against these format rules, with synthetic image and link cases in `FtbRichTextParserTest`.

## 简体中文

BRNQuest 内嵌 `org.commonmark:commonmark:0.30.0`，Copyright (c) 2015 Robin Stocker，使用 BSD 2-Clause 许可证。完整许可证随模组 JAR 放置于 `META-INF/licenses/commonmark.txt`，仓库对应[许可证资源](src/main/resources/META-INF/licenses/commonmark.txt)。

FTB Quests 导入器将 format-13 任务数据转换为 BRNQuest 原生模型，富文本兼容基线为 FTB Quests v2101.1.34。开发过程中查阅过 FTB Quests 与 FTB Library 源码，以确认序列化字段含义及文本行为。兼容实现采用 BRNQuest 自身的转换模型、文本节点和诊断机制；该开发过程表述为独立兼容实现，不宣称采用严格的 clean-room 流程。

替换表达式的属性以 ASCII 空格分隔，首个冒号划分键和值。无冒号的键对应空值，后续同名属性覆盖先前值，仅解码值中的 `%20`。属性扫描器按这些格式规则维护，`FtbRichTextParserTest` 使用人工构造的图片与链接案例验证行为。
