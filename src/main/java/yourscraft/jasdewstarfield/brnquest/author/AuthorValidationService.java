package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookValidator;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchemas;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Author-time validation facade used identically by commands and future UI requests. */
public final class AuthorValidationService {
    private static final int MAX_TITLE = 256;
    private static final int MAX_DESCRIPTION = 32_768;
    private static final int MAX_GROUPS = 256;
    private static final int MAX_CHAPTERS = 2_048;
    private static final int MAX_QUESTS = 65_536;

    private AuthorValidationService() {}

    public static List<Diagnostic> incremental(QuestBookDefinition book, List<ResourceLocation> affectedObjects) {
        // Structural validation is intentionally run on the immutable candidate: books are
        // small enough at this stage, and it guarantees a local edit cannot break distant references.
        DiagnosticReport report = structural(book);
        validateLimits(book, report);
        Set<ResourceLocation> affected = new HashSet<>(affectedObjects);
        validatePresentation(book, affected, report);
        validateEditorFields(book, affected, report);
        return report.diagnostics();
    }

    public static List<Diagnostic> full(QuestBookDefinition book) {
        DiagnosticReport report = structural(book);
        validateLimits(book, report);
        validatePresentation(book, null, report);
        validateEditorFields(book, null, report);
        return report.diagnostics();
    }

    private static void validateLimits(QuestBookDefinition book, DiagnosticReport report) {
        limit(report, book.id(), "chapter_groups", book.chapterGroups().size(), MAX_GROUPS);
        limit(report, book.id(), "chapters", book.chapters().size(), MAX_CHAPTERS);
        limit(report, book.id(), "quests", book.quests().size(), MAX_QUESTS);
    }

    private static void limit(DiagnosticReport report, ResourceLocation bookId, String path,
                              int actual, int maximum) {
        if (actual > maximum) add(report, "BQA-201", bookId, path,
                "Count " + actual + " exceeds authoring limit " + maximum);
    }

    public static boolean blocksCommit(List<Diagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(value -> value.severity().ordinal() >= Diagnostic.Severity.ERROR.ordinal());
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

    private static boolean included(ResourceLocation id, Set<ResourceLocation> affected) {
        return affected == null || affected.contains(id);
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
}
