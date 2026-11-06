package yourscraft.jasdewstarfield.brnquest.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Keeps both shipped languages complete for the server's actual publish validators. */
class EditorDiagnosticPresentationTest {
    private static final String DIAGNOSTIC = "screen.brnquest.editor.diagnostic.";
    private static final Path ROOT = Path.of(System.getProperty("brnquest.projectDir"));

    @Test void everyBuiltInPublishDiagnosticHasEnglishAndChineseText() throws Exception {
        Set<String> codes = new HashSet<>();
        var pattern = Pattern.compile("\"(BQ[AV]-[0-9]+)\"");
        for (String file : new String[]{"data/QuestBookValidator.java", "author/AuthorValidationService.java"})
            collect(pattern, Files.readString(ROOT.resolve("src/main/java/yourscraft/jasdewstarfield/brnquest/" + file)), codes);
        try (var files = Files.walk(ROOT.resolve("src/builtin/java"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList())
                collect(pattern, Files.readString(file), codes);
        }
        assertTrue(codes.size() >= 29, "Read the real publish validators, including built-in custom issues");
        for (String locale : new String[]{"en_us", "zh_cn"}) {
            var language = language(locale);
            for (String code : codes) assertText(language, DIAGNOSTIC + code, locale);
            for (String severity : new String[]{"warn", "error", "fatal", "info"})
                assertText(language, DIAGNOSTIC + "severity." + severity, locale);
        }
    }

    @Test void taskAndRewardConfigIssuesAndRevisionConflictsUseLocalizedSummaries() throws Exception {
        Set<String> issues = new HashSet<>();
        collect(Pattern.compile("issue\\(field, \"([A-Z_]+)\""), Files.readString(ROOT.resolve(
                "src/main/java/yourscraft/jasdewstarfield/brnquest/editor/ConfigEditorSchemas.java")), issues);
        issues.addAll(Set.of("ENTRY_COUNT", "INVALID_ITEM_MATCHER", "STATISTIC_MISSING"));
        for (String locale : new String[]{"en_us", "zh_cn"}) {
            var language = language(locale);
            for (String issue : issues) for (String kind : new String[]{"T", "R"}) {
                String key = EditorDiagnosticPresentation.translationKey("BQA-" + kind + "-" + issue, language::has);
                assertEquals(DIAGNOSTIC + "config." + issue, key);
                assertText(language, key, locale);
            }
            for (String code : new String[]{"DISK_DRAFT_CHANGED", "ACTIVE_BASE_CHANGED", "WORKSPACE_BASE_CHANGED",
                    "WORKSPACE_CREATED", "WORKSPACE_ALREADY_EXISTS"}) {
                String key = EditorDiagnosticPresentation.translationKey(code, language::has);
                assertText(language, key, locale);
            }
        }
    }

    @Test void extensionTranslationsOverrideSharedConfigTextAndUnknownCodesKeepServerFallback() {
        assertEquals(DIAGNOSTIC + "BQA-T-TYPE", EditorDiagnosticPresentation.translationKey("BQA-T-TYPE",
                Set.of(DIAGNOSTIC + "BQA-T-TYPE", DIAGNOSTIC + "config.TYPE")::contains));
        assertEquals("", EditorDiagnosticPresentation.translationKey("ADDON_CUSTOM_PROBLEM", ignored -> false));
    }

    private static JsonObject language(String locale) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve("src/main/resources/assets/brnquest/lang/" + locale + ".json"))).getAsJsonObject();
    }
    private static void collect(Pattern pattern, String source, Set<String> values) {
        pattern.matcher(source).results().forEach(match -> values.add(match.group(1)));
    }
    private static void assertText(JsonObject language, String key, String locale) {
        assertTrue(language.has(key), locale + " is missing " + key);
        assertFalse(language.get(key).getAsString().isBlank(), key);
        if (locale.equals("zh_cn")) assertTrue(language.get(key).getAsString().codePoints().anyMatch(
                value -> Character.UnicodeScript.of(value) == Character.UnicodeScript.HAN), key + " needs Chinese text");
    }
}
