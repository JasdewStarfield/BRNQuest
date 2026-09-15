package yourscraft.jasdewstarfield.brnquest.compat.ftb.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Clean-room parser for the rich-text branches used by FTB Quests v2101.1.34. */
public final class FtbRichTextParser {
    public static final int DEFAULT_NODE_BUDGET = 4096;
    public static final int DEFAULT_RECURSION_BUDGET = 8;
    private static final String PAGE_BREAK = "{@pagebreak}";
    private static final Map<Character, Integer> COLORS = Map.ofEntries(
            Map.entry('0', 0x000000), Map.entry('1', 0x0000AA), Map.entry('2', 0x00AA00),
            Map.entry('3', 0x00AAAA), Map.entry('4', 0xAA0000), Map.entry('5', 0xAA00AA),
            Map.entry('6', 0xFFAA00), Map.entry('7', 0xAAAAAA), Map.entry('8', 0x555555),
            Map.entry('9', 0x5555FF), Map.entry('a', 0x55FF55), Map.entry('b', 0x55FFFF),
            Map.entry('c', 0xFF5555), Map.entry('d', 0xFF55FF), Map.entry('e', 0xFFFF55),
            Map.entry('f', 0xFFFFFF));
    private static final Map<String, Integer> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("dark_blue", 0x0000AA),
            Map.entry("dark_green", 0x00AA00), Map.entry("dark_aqua", 0x00AAAA),
            Map.entry("dark_red", 0xAA0000), Map.entry("dark_purple", 0xAA00AA),
            Map.entry("gold", 0xFFAA00), Map.entry("gray", 0xAAAAAA),
            Map.entry("dark_gray", 0x555555), Map.entry("blue", 0x5555FF),
            Map.entry("green", 0x55FF55), Map.entry("aqua", 0x55FFFF),
            Map.entry("red", 0xFF5555), Map.entry("light_purple", 0xFF55FF),
            Map.entry("yellow", 0xFFFF55), Map.entry("white", 0xFFFFFF));

    private final int nodeBudget;
    private final int recursionBudget;

    public FtbRichTextParser() {
        this(DEFAULT_NODE_BUDGET, DEFAULT_RECURSION_BUDGET);
    }

    public FtbRichTextParser(int nodeBudget, int recursionBudget) {
        this.nodeBudget = Math.max(1, nodeBudget);
        this.recursionBudget = Math.max(0, recursionBudget);
    }

    public Result parse(List<String> lines, String file, String key, Map<String, String> translations) {
        Context context = new Context(file, key, translations, nodeBudget, recursionBudget);
        for (int i = 0; i < lines.size(); i++) parseLine(Objects.requireNonNullElse(lines.get(i), ""), i + 1, context, 0);
        return new Result(context.nodes, context.diagnostics);
    }

    private void parseLine(String raw, int line, Context context, int depth) {
        FtbTextSource source = new FtbTextSource(context.file, context.key, line, 1, raw.length());
        if (raw.equals(PAGE_BREAK)) {
            context.add(new FtbTextNode.PageBreak(source));
            return;
        }
        String trimmed = raw.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                parseJson(JsonParser.parseString(unescapeUnicode(trimmed)), FtbTextStyle.EMPTY,
                        FtbTextNode.Action.NONE, source, context, depth);
                return;
            } catch (JsonParseException ignored) {
                // FTB deliberately falls back to its concise legacy parser when JSON parsing fails.
            }
        }
        parseLegacy(unescapeUnicode(raw).replace("\\n", "\n"), source, FtbTextStyle.EMPTY,
                FtbTextNode.Action.NONE, context, depth);
    }

    private void parseLegacy(String value, FtbTextSource source, FtbTextStyle initialStyle,
                             FtbTextNode.Action action, Context context, int depth) {
        int nodeStart = context.nodes.size();
        int diagnosticStart = context.diagnostics.size();
        FtbTextStyle style = initialStyle;
        StringBuilder text = new StringBuilder();
        int textStart = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            boolean escaped = i > 0 && value.charAt(i - 1) == '\\';
            if (!escaped && (current == '&' || current == '\u00A7')) {
                flush(text, textStart, i, style, action, source, context);
                if (i + 1 >= value.length()) {
                    malformed(value, source, context, nodeStart, diagnosticStart,
                            "Formatting marker cannot end an FTB text line");
                    return;
                }
                char code = value.charAt(++i);
                if (code == '#') {
                    if (i + 6 >= value.length()) {
                        malformed(value, source, context, nodeStart, diagnosticStart,
                                "RGB formatting requires exactly six hexadecimal digits");
                        return;
                    }
                    String rgb = value.substring(i + 1, i + 7);
                    if (!rgb.matches("[0-9A-Fa-f]{6}")) {
                        malformed(value, source, context, nodeStart, diagnosticStart,
                                "RGB formatting requires exactly six hexadecimal digits");
                        return;
                    }
                    style = style.withColor(Integer.parseInt(rgb, 16), false);
                    i += 6;
                } else if (COLORS.containsKey(code)) style = style.withColor(COLORS.get(code), false);
                else style = switch (code) {
                    case 'k' -> style.withObfuscated(true);
                    case 'l' -> style.withBold(true);
                    case 'm' -> style.withStrikethrough(true);
                    case 'n' -> style.withUnderlined(true);
                    case 'o' -> style.withItalic(true);
                    case 'r' -> FtbTextStyle.EMPTY;
                    case 'z' -> style.withColor(null, true);
                    default -> null;
                };
                if (style == null) {
                    malformed(value, source, context, nodeStart, diagnosticStart,
                            "Unknown FTB formatting code: " + code);
                    return;
                }
                textStart = i + 1;
                continue;
            }
            if (!escaped && current == '{') {
                flush(text, textStart, i, style, action, source, context);
                int end = value.indexOf('}', i + 1);
                if (end < 0 || value.indexOf('{', i + 1) >= 0 && value.indexOf('{', i + 1) < end) {
                    malformed(value, source, context, nodeStart, diagnosticStart,
                            "FTB substitutions must be closed and cannot nest");
                    return;
                }
                parseSubstitution(value.substring(i + 1, end), source.slice(i, end - i + 1), style,
                        action, context, depth);
                i = end;
                textStart = i + 1;
                continue;
            }
            if (current == '\\' && !escaped) continue;
            text.append(current);
        }
        flush(text, textStart, value.length(), style, action, source, context);
    }

    private void parseSubstitution(String value, FtbTextSource source, FtbTextStyle style,
                                   FtbTextNode.Action action, Context context, int depth) {
        Map<String, String> properties = splitProperties(value);
        if (properties.containsKey("image")) {
            String image = properties.get("image");
            int width = positiveInt(properties.get("width"), 100);
            int height = positiveInt(properties.get("height"), 100);
            context.add(new FtbTextNode.Image(image, properties.getOrDefault("text", ""), width, height,
                    properties.getOrDefault("align", "center"), source));
            if (properties.containsKey("click_action") && !properties.get("click_action").isBlank()) {
                context.diagnostic(FtbTextDiagnostic.Severity.ERROR, "BQF-TEXT-IMAGE-ACTION", source,
                        "Image click actions are not safe BRNQuest document actions");
            }
            return;
        }
        if (properties.containsKey("open_url")) {
            String label = properties.getOrDefault("text", "");
            FtbTextNode.Action urlAction = safeUrl(properties.get("open_url"))
                    ? new FtbTextNode.Action(FtbTextNode.Action.Kind.OPEN_URL, properties.get("open_url"))
                    : new FtbTextNode.Action(FtbTextNode.Action.Kind.UNSAFE, properties.get("open_url"));
            if (urlAction.kind() == FtbTextNode.Action.Kind.UNSAFE)
                context.diagnostic(FtbTextDiagnostic.Severity.ERROR, "BQF-TEXT-UNSAFE-URL", source,
                        "Only HTTP and HTTPS links can be imported");
            parseLegacy(label, source, style, urlAction, context, depth);
            return;
        }
        String translated = context.translations.get(value);
        if (translated != null && depth < context.recursionBudget) {
            parseLegacy(unescapeUnicode(translated).replace("\\n", "\n"), source, style, action, context, depth + 1);
            return;
        }
        context.add(new FtbTextNode.Unknown("{" + value + "}", source));
        context.diagnostic(depth >= context.recursionBudget ? FtbTextDiagnostic.Severity.ERROR
                        : FtbTextDiagnostic.Severity.WARN,
                depth >= context.recursionBudget ? "BQF-TEXT-RECURSION" : "BQF-TEXT-UNKNOWN-SUBSTITUTION", source,
                depth >= context.recursionBudget ? "FTB translation recursion budget exceeded"
                        : "FTB substitution has no unambiguous same-locale value");
    }

    private void parseJson(JsonElement element, FtbTextStyle inheritedStyle, FtbTextNode.Action inheritedAction,
                           FtbTextSource source, Context context, int depth) {
        if (depth > context.recursionBudget) {
            context.add(new FtbTextNode.Unknown(element.toString(), source));
            context.diagnostic(FtbTextDiagnostic.Severity.ERROR, "BQF-TEXT-RECURSION", source,
                    "Raw JSON component recursion budget exceeded");
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            context.add(new FtbTextNode.Text(element.getAsString(), inheritedStyle, inheritedAction, source));
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray())
                parseJson(child, inheritedStyle, inheritedAction, source, context, depth + 1);
            return;
        }
        if (!element.isJsonObject()) {
            unsupportedJson(element, source, context, "Unsupported raw JSON component value");
            return;
        }

        JsonObject object = element.getAsJsonObject();
        FtbTextStyle style = jsonStyle(object, inheritedStyle, source, context);
        FtbTextNode.Action action = jsonAction(object, inheritedAction, source, context);
        if (object.has("hoverEvent") || object.has("hover_event"))
            context.diagnostic(FtbTextDiagnostic.Severity.WARN, "BQF-TEXT-HOVER-DROPPED", source,
                    "Raw JSON hover text has no equivalent in markdown_v1");
        if (object.has("insertion"))
            context.diagnostic(FtbTextDiagnostic.Severity.ERROR, "BQF-TEXT-INSERTION-DROPPED", source,
                    "Raw JSON insertion actions cannot be imported");

        if (object.has("text") && object.get("text").isJsonPrimitive()) {
            context.add(new FtbTextNode.Text(object.get("text").getAsString(), style, action, source));
        } else if (object.has("translate") && object.get("translate").isJsonPrimitive()) {
            String key = object.get("translate").getAsString();
            String translated = context.translations.get(key);
            if (translated != null && depth < context.recursionBudget) {
                parseLegacy(unescapeUnicode(translated).replace("\\n", "\n"), source, style, action, context, depth + 1);
            } else {
                context.add(new FtbTextNode.Unknown(element.toString(), source));
                context.diagnostic(FtbTextDiagnostic.Severity.WARN, "BQF-TEXT-TRANSLATION-UNRESOLVED", source,
                        "Raw JSON translation has no unambiguous same-locale value: " + key);
            }
        } else if (!object.has("extra")) {
            unsupportedJson(element, source, context, "Unsupported raw JSON component contents");
        }

        JsonElement extra = object.get("extra");
        if (extra instanceof JsonArray array) {
            for (JsonElement child : array) parseJson(child, style, action, source, context, depth + 1);
        } else if (extra != null) {
            unsupportedJson(extra, source, context, "Raw JSON extra must be an array");
        }
    }

    private FtbTextStyle jsonStyle(JsonObject object, FtbTextStyle inherited,
                                   FtbTextSource source, Context context) {
        FtbTextStyle style = inherited;
        if (object.has("color") && object.get("color").isJsonPrimitive()) {
            String raw = object.get("color").getAsString().toLowerCase(Locale.ROOT);
            if (raw.equals("ftb:rainbow")) style = style.withColor(null, true);
            else {
                Integer color = NAMED_COLORS.get(raw);
                if (color == null && raw.matches("#[0-9a-f]{6}")) color = Integer.parseInt(raw.substring(1), 16);
                if (color == null) context.diagnostic(FtbTextDiagnostic.Severity.WARN,
                        "BQF-TEXT-COLOR-UNKNOWN", source, "Unknown raw JSON text color: " + raw);
                else style = style.withColor(color, false);
            }
        }
        style = jsonBoolean(object, "bold", style, 0);
        style = jsonBoolean(object, "italic", style, 1);
        style = jsonBoolean(object, "underlined", style, 2);
        style = jsonBoolean(object, "strikethrough", style, 3);
        return jsonBoolean(object, "obfuscated", style, 4);
    }

    private static FtbTextStyle jsonBoolean(JsonObject object, String key, FtbTextStyle style, int kind) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isBoolean()) return style;
        boolean value = object.get(key).getAsBoolean();
        return switch (kind) {
            case 0 -> style.withBold(value);
            case 1 -> style.withItalic(value);
            case 2 -> style.withUnderlined(value);
            case 3 -> style.withStrikethrough(value);
            default -> style.withObfuscated(value);
        };
    }

    private FtbTextNode.Action jsonAction(JsonObject object, FtbTextNode.Action inherited,
                                          FtbTextSource source, Context context) {
        JsonElement raw = object.has("clickEvent") ? object.get("clickEvent") : object.get("click_event");
        if (!(raw instanceof JsonObject click)) return inherited;
        String action = string(click, "action");
        String value = click.has("value") ? string(click, "value") : string(click, "command");
        if (action.equals("open_url") && safeUrl(value))
            return new FtbTextNode.Action(FtbTextNode.Action.Kind.OPEN_URL, value);
        if (action.equals("change_page"))
            return new FtbTextNode.Action(FtbTextNode.Action.Kind.CHANGE_PAGE, value);
        context.diagnostic(FtbTextDiagnostic.Severity.ERROR, "BQF-TEXT-UNSAFE-ACTION", source,
                "Raw JSON click action cannot be imported: " + action);
        return new FtbTextNode.Action(FtbTextNode.Action.Kind.UNSAFE, value);
    }

    private void unsupportedJson(JsonElement element, FtbTextSource source, Context context, String message) {
        context.add(new FtbTextNode.Unknown(element.toString(), source));
        context.diagnostic(FtbTextDiagnostic.Severity.ERROR, "BQF-TEXT-JSON-UNSUPPORTED", source, message);
    }

    private void malformed(String value, FtbTextSource source, Context context,
                           int nodeStart, int diagnosticStart, String message) {
        // FTB reports a whole-line parse failure; retain the whole source exactly once rather than mixing partial runs.
        context.rollback(nodeStart, diagnosticStart);
        context.add(new FtbTextNode.Unknown(value, source));
        context.diagnostic(FtbTextDiagnostic.Severity.ERROR, "BQF-TEXT-MALFORMED", source, message);
    }

    private static void flush(StringBuilder text, int start, int end, FtbTextStyle style,
                              FtbTextNode.Action action, FtbTextSource source, Context context) {
        if (text.isEmpty()) return;
        context.add(new FtbTextNode.Text(text.toString(), style, action,
                source.slice(start, Math.max(0, end - start))));
        text.setLength(0);
    }

    private static Map<String, String> splitProperties(String value) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        for (String token : value.split(" ")) {
            if (token.isEmpty()) continue;
            String[] pair = token.split(":", 2);
            result.put(pair[0], pair.length == 2 ? pair[1].replace("%20", " ") : "");
        }
        return result;
    }

    private static int positiveInt(String value, int fallback) {
        try { return Math.max(1, Integer.parseInt(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static boolean safeUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = Objects.requireNonNullElse(uri.getScheme(), "").toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    /** Matches the unicode-only pre-pass used by the pinned FTB Quests source without consuming other escapes. */
    private static String unescapeUnicode(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\\' && i + 5 < value.length() && value.charAt(i + 1) == 'u') {
                String digits = value.substring(i + 2, i + 6);
                if (digits.matches("[0-9A-Fa-f]{4}")) {
                    result.append((char) Integer.parseInt(digits, 16));
                    i += 5;
                    continue;
                }
            }
            result.append(value.charAt(i));
        }
        return result.toString();
    }

    public record Result(List<FtbTextNode> nodes, List<FtbTextDiagnostic> diagnostics) {
        public Result {
            nodes = List.copyOf(nodes);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static final class Context {
        private final String file;
        private final String key;
        private final Map<String, String> translations;
        private final int recursionBudget;
        private final List<FtbTextNode> nodes = new ArrayList<>();
        private final List<FtbTextDiagnostic> diagnostics = new ArrayList<>();
        private int remainingNodes;
        private boolean budgetReported;

        private Context(String file, String key, Map<String, String> translations,
                        int nodeBudget, int recursionBudget) {
            this.file = Objects.requireNonNullElse(file, "");
            this.key = Objects.requireNonNullElse(key, "");
            this.translations = Map.copyOf(translations);
            this.remainingNodes = nodeBudget;
            this.recursionBudget = recursionBudget;
        }

        private void add(FtbTextNode node) {
            if (remainingNodes-- > 0) nodes.add(node);
            else if (!budgetReported) {
                budgetReported = true;
                diagnostic(FtbTextDiagnostic.Severity.ERROR, "BQF-TEXT-NODE-BUDGET", node.source(),
                        "FTB rich-text node budget exceeded");
            }
        }

        private void diagnostic(FtbTextDiagnostic.Severity severity, String code,
                                FtbTextSource source, String message) {
            diagnostics.add(new FtbTextDiagnostic(severity, code, source, message));
        }

        private void rollback(int nodeSize, int diagnosticSize) {
            int removed = nodes.size() - nodeSize;
            if (removed > 0) {
                nodes.subList(nodeSize, nodes.size()).clear();
                remainingNodes += removed;
            }
            if (diagnostics.size() > diagnosticSize)
                diagnostics.subList(diagnosticSize, diagnostics.size()).clear();
        }
    }
}
