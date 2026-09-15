# 作者文本本地化

任务书的原文字符串始终可直接填写；需要多语言时，额外填写原生 JSON 的 `localization` 翻译表。现有 schema 无需升级，也不需要安装 FTB Quests。

```json
{
  "title": "My Book",
  "localization": {
    "fallback_locale": "en_us",
    "translations": {
      "zh_cn": {
        "title": "我的任务书",
        "chapter_group.example:main.title": "主线",
        "chapter.example:intro.title": "起步",
        "quest.example:first.title": "第一个任务",
        "quest.example:first.quest_subtitle": "准备出发",
        "quest.example:first.quest_desc": "# 起步\n\n第一行\n第二行",
        "quest.example:first.quest_desc_format": "markdown_v1"
      }
    }
  }
}
```

这是任务书字段片段；章节组、章节和任务 ID 必须对应实际对象。没有 `localization` 的旧任务书继续显示原文。不使用内联 locale map，也不会把像 `screen.example.title` 这样的作者原文自动解释成 Minecraft 翻译键。

## 文本键和回退顺序

- 任务书标题：`title`。
- 章节组、章节标题：`chapter_group.<ID>.title`、`chapter.<ID>.title`。
- 任务标题、副标题、正文：`quest.<ID>.title`、`quest.<ID>.quest_subtitle`、`quest.<ID>.quest_desc`；正文格式使用相邻的 `quest.<ID>.quest_desc_format`。
- 原生任务使用完整命名空间 ID；导入任务继续使用非空 `legacy_id`。章节和章节组有历史 alias 时使用 alias；多个 alias 按字符串排序选择第一个，排除以 `@` 开头的内部映射。没有 alias 才使用原生 ID。

每个字段按“客户端指定语言 → `fallback_locale` → 对象原文”读取；缺失或空白翻译会继续回退。任务正文与格式作为一对从同一来源读取，不能从请求语言取得正文却从 fallback 语言继承格式。语言代码统一小写并把 `-` 转成 `_`，例如 `zh-CN` 与 `zh_cn` 等价，不自动做 `zh_tw` 到 `zh_cn` 等语言推断。同义语言代码的不同键会合并；同一键存在不同值时拒绝加载候选，要求作者消除冲突，不静默覆盖。

原生任务正文的相邻字段为 `description_format`。字段缺失时一律是 `plain`，因此旧任务书中的 `*`、`_`、`[]()` 等字符仍按字面量处理；`markdown_v1` 是版本化的 BRNQuest 受控格式。未知格式会原样保存并安全降级为纯文本，作者请求不能新建未知格式。

`markdown_v1` 支持段落和显式换行、一级至三级标题、使用 `-` 或 `*` 的单层无序列表、粗体、斜体、行内代码，以及目标为绝对 HTTP／HTTPS 地址的链接。不启用有序／嵌套列表、`+` 列表标记、引用、围栏代码块、HTML、图片、命令或其他 URL scheme。不支持或畸形的结构会保留可见源码，并可向作者产生诊断。

任务详情把链接显示为带下划线的可交互文本；激活链接一律先进入 Minecraft 自带确认界面，取消不会产生外部动作。链接点击优先于同一像素下的任务／奖励动作，并且裁剪视口以外不能命中。

## 编辑与保存

任务文本编辑器可选择语言并编辑标题、副标题和多行正文。编辑 fallback 语言时写入对象原文字段并清除这三个字段的同语言重复翻译，避免旧翻译遮蔽后续原文编辑。其他语言仅更新对应翻译表。

正文编辑器提供明确的“纯文本”／“Markdown v1”格式切换。切换为 Markdown 会提示标记可能改变显示语义；切回纯文本只改变解释方式，不删除源码中的 Markdown 标记。宽窗口同时显示源码和实时预览，窄窗口使用一个“源码”／“预览”二态按钮切换视图；预览与任务详情共用同一套解析、断行和渲染实现。

Markdown 工具栏可以插入标题、粗体、斜体、无序列表、行内代码和 HTTP／HTTPS 链接骨架。已有选区会被对应标记包裹，没有选区时会插入并选中可替换占位文本。切换语言或调整窗口大小会保留各语言尚未提交的独立缓冲。只有“应用”会发起一次服务端编辑请求；取消不保存，服务端拒绝后重新打开同一任务可恢复最近一次提交内容以便修正后重试。

复制任务会将所有语言和该任务前缀下的扩展文本复制到新 ID；源任务文本保留，后续编辑相互独立。原生任务改 ID 时移动翻译键；导入任务保留历史文本身份。导入、原生 JSON 保存和语义 diff 保留所有语言及未知文本键。删除对象后残留的翻译键不会自动清理。

当前任务书、章节和章节组的翻译通过 JSON 翻译表维护；其属性表单仍编辑原文。任务界面标题、导航、依赖提示/选择器和追踪 HUD 按客户端语言显示。跨任务书选择列表仅持有服务器摘要，显示摘要原文；任务/奖励类型的自定义配置字段由扩展定义，不属于上述六种标准作者文本。

## Java API

`BrnQuestApi` 的 `getActiveBook(locale)`、`getChapterGroups(locale)`、`getChapterGroup(id, locale)`、`getChapters(locale)`、`getChapter(id, locale)`、`getQuests(locale)` 和 `getQuest(id, locale)` 返回指定语言的不可变投影。原有不带 locale 的方法继续返回原文；可用 `getTranslations()` 读取完整翻译表，或 `resolveText(locale, key, fallback)` 解析扩展键。解析不会修改活动任务书、revision 或其它语言。
