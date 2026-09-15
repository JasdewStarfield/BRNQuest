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

- `plain` displays Markdown punctuation literally. Minecraft legacy formatting codes remain meaningful: `§0`-`§9` and `§a`-`§f` select the vanilla palette, `§k` obfuscates, `§l` bolds, `§m` strikes through, `§n` underlines, `§o` italicizes, and `§r` resets. Codes are case-insensitive; unknown or incomplete codes remain visible. A missing format is always `plain`.
- `markdown_v1` identifies BRNQuest's versioned, controlled Markdown subset. It does not promise full CommonMark compatibility.
- Unknown values are preserved when reading and writing, but clients must render them as plain text. Author mutations cannot create unknown values.

`markdown_v1` supports paragraphs and explicit line breaks, level 1-3 headings, one-level unordered lists using `-` or `*`, bold, emphasis, inline code, links with an absolute HTTP or HTTPS destination, and in-book task links. Ordered or nested lists, the `+` list marker, block quotes, fenced code, HTML, remote images, commands, and other URL schemes are not enabled. Unsupported or malformed constructs remain visible as literal source and may produce an author diagnostic.

An in-book task link uses `[label](brnquest:quest/namespace:quest_id)`. The target is always a full, stable BRNQuest quest ID; raw FTB IDs are not runtime link targets. Activating a visible target opens that task, including across chapters, and re-resolves the ID against the current book snapshot. Missing or player-hidden targets remain unavailable and do not reveal hidden task text.

Minecraft styles not provided by base Markdown use scoped BRNQuest targets: `[text](brnquest:style/underline)`, `[text](brnquest:style/strikethrough)`, `[text](brnquest:style/obfuscated)`, and `[text](brnquest:style/color/red)`. Color accepts the 16 lowercase vanilla names or an opaque six-digit RGB value such as `brnquest:style/color/12abef`. Multiple extension styles use one `+`-joined target, for example `[text](brnquest:style/color/red+underline+strikethrough)`; duplicate colors or unknown parts make the whole target literal. The label may contain ordinary bold or emphasis markup. These targets are styles, not clickable links.

Loaded textures and registered items use Markdown image syntax: `![alt](texture:namespace:textures/path.png)` and `![alt](item:namespace:item_id)`. The namespace is required. A node that occupies its whole paragraph renders as block content; a node surrounded by text is an indivisible inline icon aligned to the text baseline. Inline items fit 16×16 logical pixels, while inline textures preserve aspect ratio within 32×16; block textures preserve aspect ratio within the document width and height cap. Clients only resolve textures already supplied by resource packs and items already present in the registry; no remote content is downloaded and item SNBT/data components are not accepted. Missing content remains as a size-stable recoverable placeholder. Items use their native tooltip; when JEI is installed, that tooltip advertises its live recipe/use keys and those keys work over the icon. Texture content deliberately has no hover tooltip. Neither content kind has ordinary click, submission, or gameplay side effects. Textures in the `brnquest_local:` namespace exist only on the importing computer and are not distributed with the book or by the server.

Task details render links as underlined interactive text. External HTTP/HTTPS links open Minecraft's confirmation screen; cancelling performs no external action. In-book task links navigate directly inside the task book. Link clicks take priority over task/reward actions beneath the same pixels and cannot be activated outside the clipped document viewport.

Updating a fallback-locale description writes the native text and format and removes duplicate translation keys. Updating another locale writes its text and format atomically in the translation table. Compatibility callers that omit the format preserve that locale's current format.

Copying quests preserves native and localized formats. Native JSON saves, draft recovery, publishing, undo/redo, and semantic diffs treat the description and its format as one revision-bound edit.

## FTB Quests v13 rich-text import

FTB Quests remains a read-only SNBT import source and is never a runtime dependency. Every localized `quest_desc` list is parsed independently using the behavior fixed by FTB Quests v2101.1.34 and FTB Library 2101.1.35, then stored as ordinary BRNQuest source with `quest_desc_format=markdown_v1`.

Legacy `&`/`§` palette and decoration codes, `&#RRGGBB`, reset, same-locale translation substitutions, safe HTTP/HTTPS links, raw JSON text/style, and namespaced FTB image substitutions are converted to native Markdown constructs. A standalone `{@pagebreak}` becomes a paragraph boundary. `change_page` becomes an in-book link only when its hexadecimal target resolves to an actual quest in the imported source; a subpage suffix opens that quest's single BRNQuest details view. Explicit FTB image dimensions/alignment are replaced by BRNQuest's intrinsic fitting and reported.

Dynamic rainbow color, hover text, unresolved substitutions, malformed input, unsafe URLs, command/insertion/unknown click events, non-quest page targets, and unsupported JSON contents remain visible where possible and receive source-positioned diagnostics. Actions are removed, never copied into Markdown. A non-equivalent original description is retained under the book's `ftb.rich_text_source.*` provenance extensions, while the generated runtime document contains no FTB syntax or class dependency. The import report records each description as `MAPPED` or `UNSUPPORTED` instead of treating preserved source as behavioral compatibility.

## Source editor and preview

The quest text editor provides an explicit Plain/Markdown v1 format selector. Switching to Markdown warns that punctuation may gain display semantics; switching back changes only interpretation and does not remove Markdown markers from the source. Wide windows show source and live preview side by side, while narrow windows use one binary Source/Preview view toggle. The preview and task details use the same parser, line breaking, and renderer. The two-row Markdown toolbar also inserts underline, strikethrough, obfuscated text, and colors; its color picker offers the 16 vanilla colors plus exact `#RRGGBB` input.

The Markdown toolbar inserts heading, bold, italic, unordered-list, inline-code, and HTTP/HTTPS link skeletons; it also opens a searchable task-link picker, the loaded-texture browser, or the item selector. It wraps an existing selection or inserts and selects replaceable placeholder text; after a choice, the editor returns with the visible label or alt text selected. Task links in the child preview show their resolved target but do not leave the editor, so an unsaved localized buffer cannot be lost through preview navigation. A `brnquest_local:` texture produces an explicit warning that it is not distributed with the book. Unsaved buffers remain independent for each locale across locale changes and window resize. Apply starts one server edit request; Cancel sends none. If the server rejects a submitted edit, reopening the same quest restores that submitted buffer for correction and retry.

## Java API

Locale-aware `BrnQuestApi` queries return immutable projections for the requested language. `QuestView.description()` and `descriptionFormat()` are a pair and use the same locale source. Queries without a locale continue to return native source values. Extensions may use `getTranslations()` for the complete table or `resolveText(locale, key, fallback)` for their own text keys without modifying the active book or revision.
