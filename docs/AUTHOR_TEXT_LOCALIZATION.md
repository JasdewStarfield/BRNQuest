# Author Text Localization

Book source strings remain valid as-is. To provide additional languages, add a `localization` table to the native book JSON; this does not require a schema upgrade or FTB Quests.

```json
{
  "title": "My Book",
  "localization": {
    "fallback_locale": "en_us",
    "translations": {
      "zh_cn": {
        "title": "我的任务书",
        "quest.example:first.title": "第一个任务",
        "quest.example:first.quest_subtitle": "准备出发",
        "quest.example:first.quest_desc": "# 起步\n\n第一行\n第二行",
        "quest.example:first.quest_desc_format": "markdown_v1"
      }
    }
  }
}
```

The standard keys are `title`, `chapter_group.<ID>.title`, `chapter.<ID>.title`, and `quest.<ID>.title`, `quest.<ID>.quest_subtitle`, and `quest.<ID>.quest_desc`. A translated quest description stores its format in the adjacent `quest.<ID>.quest_desc_format` key. Native fallback descriptions use the adjacent root field `description_format`.

Resolution checks the requested locale, then `fallback_locale`, then the native source string. Description text and format always come from the same source locale. Missing or blank translations continue to fall back. Locale names are normalized to lowercase with `_`, so `zh-CN` and `zh_cn` are equivalent.

## Description formats

- `plain` displays the description literally. A missing format is always `plain`, preserving the behavior of existing books even when their text contains Markdown punctuation.
- `markdown_v1` identifies BRNQuest's versioned, controlled Markdown subset. It does not promise full CommonMark compatibility.
- Unknown values are preserved when reading and writing, but clients must render them as plain text. Author mutations cannot create unknown values.

`markdown_v1` supports paragraphs and explicit line breaks, level 1-3 headings, one-level unordered lists using `-` or `*`, bold, emphasis, inline code, and links with an absolute HTTP or HTTPS destination. Ordered or nested lists, the `+` list marker, block quotes, fenced code, HTML, images, commands, and other URL schemes are not enabled. Unsupported or malformed constructs remain visible as literal source and may produce an author diagnostic.

Task details render links as underlined interactive text. Activating one always opens Minecraft's confirmation screen; cancelling performs no external action. Link clicks take priority over task/reward actions beneath the same pixels and cannot be activated outside the clipped document viewport.

Updating a fallback-locale description writes the native text and format and removes duplicate translation keys. Updating another locale writes its text and format atomically in the translation table. Compatibility callers that omit the format preserve that locale's current format.

Copying quests preserves native and localized formats. Native JSON saves, draft recovery, publishing, undo/redo, and semantic diffs treat the description and its format as one revision-bound edit.

## Source editor and preview

The quest text editor provides an explicit Plain/Markdown v1 format selector. Switching to Markdown warns that punctuation may gain display semantics; switching back changes only interpretation and does not remove Markdown markers from the source. Wide windows show source and live preview side by side, while narrow windows use Source and Preview tabs. The preview and task details use the same parser, line breaking, and renderer.

The Markdown toolbar inserts heading, bold, italic, unordered-list, inline-code, and HTTP/HTTPS link skeletons. It wraps an existing selection or inserts and selects replaceable placeholder text. Unsaved buffers remain independent for each locale across locale changes and window resize. Apply starts one server edit request; Cancel sends none. If the server rejects a submitted edit, reopening the same quest restores that submitted buffer for correction and retry.

## Java API

Locale-aware `BrnQuestApi` queries return immutable projections for the requested language. `QuestView.description()` and `descriptionFormat()` are a pair and use the same locale source. Queries without a locale continue to return native source values. Extensions may use `getTranslations()` for the complete table or `resolveText(locale, key, fallback)` for their own text keys without modifying the active book or revision.
