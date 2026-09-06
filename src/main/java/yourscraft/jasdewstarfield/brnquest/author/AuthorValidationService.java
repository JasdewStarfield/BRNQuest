package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookValidator;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchemas;
import yourscraft.jasdewstarfield.brnquest.task.ItemChoiceMatcher;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Author-time validation facade used identically by commands and future UI requests. */
public final class AuthorValidationService {
    private static final int MAX_TITLE = 256;
    private static final int MAX_DESCRIPTION = 32_768;
    private static final int MAX_GROUPS = 256;
    private static final int MAX_CHAPTERS = 2_048;

    private AuthorValidationService() {}

    public static List<Diagnostic> incremental(QuestBookDefinition book, List<ResourceLocation> affectedObjects) {
        // Structural validation is intentionally run on the immutable candidate: books are
        // small enough at this stage, and it guarantees a local edit cannot break distant references.
        DiagnosticReport report = structural(book);
        validateLimits(book, report);
        Set<ResourceLocation> affected = new HashSet<>(affectedObjects);
        validatePresentation(book, affected, report);
        validateLocalization(book, affected, report);
        validateEditorFields(book, affected, report);
        return report.diagnostics();
    }

    public static List<Diagnostic> full(QuestBookDefinition book) {
        DiagnosticReport report = structural(book);
        validateLimits(book, report);
        validatePresentation(book, null, report);
        validateLocalization(book, null, report);
        validateEditorFields(book, null, report);
        return report.diagnostics();
    }

    private static void validateLimits(QuestBookDefinition book, DiagnosticReport report) {
        limit(report, book.id(), "chapter_groups", book.chapterGroups().size(), MAX_GROUPS);
        limit(report, book.id(), "chapters", book.chapters().size(), MAX_CHAPTERS);
        limit(report, book.id(), "quests", book.quests().size(), BrnQuestConstants.MAX_QUESTS);
    }

    private static void limit(DiagnosticReport report, ResourceLocation bookId, String path,
                              int actual, int maximum) {
        if (actual > maximum) add(report, "BQA-201", bookId, path,
                "Count " + actual + " exceeds authoring limit " + maximum);
    }

    public static boolean blocksCommit(List<Diagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(value -> value.severity().ordinal() >= Diagnostic.Severity.ERROR.ordinal());
    }

    /** Allows unchanged legacy/extension diagnostics while rejecting every newly introduced blocking issue. */
    public static boolean blocksCommit(List<Diagnostic> candidate, List<Diagnostic> baseline) {
        Map<DiagnosticFingerprint, Integer> acceptedCounts = new HashMap<>();
        baseline.stream().filter(AuthorValidationService::blocking).forEach(diagnostic ->
                acceptedCounts.merge(DiagnosticFingerprint.of(diagnostic), 1, Integer::sum));
        for (Diagnostic diagnostic : candidate) {
            if (!blocking(diagnostic)) continue;
            DiagnosticFingerprint fingerprint = DiagnosticFingerprint.of(diagnostic);
            int accepted = acceptedCounts.getOrDefault(fingerprint, 0);
            if (accepted == 0) return true;
            acceptedCounts.put(fingerprint, accepted - 1);
        }
        return false;
    }

    private static boolean blocking(Diagnostic diagnostic) {
        return diagnostic.severity().ordinal() >= Diagnostic.Severity.ERROR.ordinal();
    }

    private record DiagnosticFingerprint(Diagnostic.Severity severity, String code, String file, String path,
                                         String objectId, String message) {
        static DiagnosticFingerprint of(Diagnostic diagnostic) {
            return new DiagnosticFingerprint(diagnostic.severity(), diagnostic.code(), diagnostic.file(),
                    diagnostic.path(), diagnostic.objectId(), diagnostic.message());
        }
    }

    private static DiagnosticReport structural(QuestBookDefinition book) {
        DiagnosticReport report = new DiagnosticReport();
        QuestBookValidator.validate(book, report);
        return report;
    }

    private static void validatePresentation(QuestBookDefinition book, Set<ResourceLocation> affected,
                                             DiagnosticReport report) {
        if (included(book.id(), affected)) length(report, book.id(), "title", book.title(), MAX_TITLE, true);
        book.chapterGroups().stream().filter(group -> included(group.id(), affected))
                .forEach(group -> length(report, group.id(), "title", group.title(), MAX_TITLE, true));
        book.chapters().stream().filter(chapter -> included(chapter.id(), affected))
                .forEach(chapter -> length(report, chapter.id(), "title", chapter.title(), MAX_TITLE, true));
        book.quests().stream().filter(quest -> included(quest.id(), affected)).forEach(quest -> {
            length(report, quest.id(), "title", quest.title(), MAX_TITLE, true);
            length(report, quest.id(), "subtitle", quest.subtitle(), MAX_TITLE, false);
            length(report, quest.id(), "description", quest.description(), MAX_DESCRIPTION, false);
            if (!Double.isFinite(quest.x()) || !Double.isFinite(quest.y())) {
                add(report, "BQA-103", quest.id(), "position", "Quest coordinates must be finite numbers");
            }
            if (!Double.isFinite(quest.appearance().size()) || quest.appearance().size() <= 0
                    || !Double.isFinite(quest.appearance().iconScale()) || quest.appearance().iconScale() <= 0
                    || !Double.isFinite(quest.appearance().minWidth()) || quest.appearance().minWidth() < 0) {
                add(report, "BQA-104", quest.id(), "appearance",
                        "Appearance size/icon scale must be positive and minimum width must be non-negative");
            }
            if (quest.behavior().minimumRequiredDependencies() > quest.dependencies().size()) {
                add(report, "BQA-105", quest.id(), "behavior.minimum_required_dependencies",
                        "Minimum required dependencies exceeds the dependency count");
            }
            if (!quest.behavior().repeatable() && quest.behavior().repeatCooldownSeconds() > 0) {
                warn(report, "BQA-303", quest.id(), "behavior.repeat_cooldown_seconds",
                        "Repeat cooldown has no effect while the quest is not repeatable");
            }
            quest.tasks().stream().filter(task -> booleanConfig(task.config(), "only_from_crafting"))
                    .forEach(task -> ItemChoiceMatcher.parseConfig(task.config()).result().ifPresent(spec -> {
                        if (spec.entries().size() != 1 || spec.requiredEntries() != 1) {
                            add(report, "BQA-106", task.id(), "config.only_from_crafting",
                                    "Crafting-only item objectives require exactly one accepted entry");
                        }
                    }));
        });
    }

    private static void validateEditorFields(QuestBookDefinition book, Set<ResourceLocation> affected,
                                             DiagnosticReport report) {
        book.quests().forEach(quest -> {
            quest.tasks().stream().filter(task -> included(task.id(), affected)).forEach(task ->
                    ConfigEditorSchemas.forTask(ApiViews.task(task)).issues().forEach(issue ->
                            add(report, "BQA-T-" + issue.code(), task.id(), "config." + issue.fieldKey(), issue.message())));
            quest.rewards().stream().filter(reward -> included(reward.id(), affected)).forEach(reward ->
                    ConfigEditorSchemas.forReward(ApiViews.reward(reward)).issues().forEach(issue ->
                            add(report, "BQA-R-" + issue.code(), reward.id(), "config." + issue.fieldKey(), issue.message())));
        });
    }

    private static void validateLocalization(QuestBookDefinition book, Set<ResourceLocation> affected,
                                             DiagnosticReport report) {
        if (book.localization().translations().isEmpty()) return;
        for (var quest : book.quests().stream().filter(value -> included(value.id(), affected)).toList()) {
            String sourceId = quest.legacyId().isBlank() ? quest.id().toString() : quest.legacyId();
            String prefix = "quest." + sourceId + ".";
            for (var locale : book.localization().translations().entrySet()) {
                if (!locale.getValue().containsKey(prefix + "title")) {
                    warn(report, "BQA-301", quest.id(), "localization." + locale.getKey() + ".title",
                            "Translation is missing; native/fallback-locale text will be used");
                }
            }
            if (!Set.of("chamfer", "square", "circle", "diamond")
                    .contains(quest.appearance().shape().toLowerCase(java.util.Locale.ROOT))) {
                warn(report, "BQA-302", quest.id(), "appearance.shape",
                        "Unknown shape will render with the chamfer fallback");
            }
        }
    }

    private static boolean included(ResourceLocation id, Set<ResourceLocation> affected) {
        return affected == null || affected.contains(id);
    }

    private static boolean booleanConfig(Map<String, String> config, String key) {
        String value = config.getOrDefault(key, "false");
        return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
    }

    private static void length(DiagnosticReport report, ResourceLocation id, String path, String value,
                               int maximum, boolean required) {
        if (required && (value == null || value.isBlank())) {
            add(report, "BQA-101", id, path, "Required author-visible text is blank");
        } else if (value != null && value.length() > maximum) {
            add(report, "BQA-102", id, path, "Text exceeds " + maximum + " characters");
        }
    }

    private static void add(DiagnosticReport report, String code, ResourceLocation id, String path, String message) {
        report.add(new Diagnostic(Diagnostic.Severity.ERROR, code, "", path, id.toString(), message));
    }

    private static void warn(DiagnosticReport report, String code, ResourceLocation id, String path, String message) {
        report.add(new Diagnostic(Diagnostic.Severity.WARN, code, "", path, id.toString(), message));
    }
}
