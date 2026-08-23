package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorkspacePublishRepositoryTest {
    @TempDir Path temporary;

    @Test void createsWorkspaceFromEmptyServerScaffoldAndRepeatedPublishIsIdempotent() throws Exception {
        WorkspacePublishRepository repository = new WorkspacePublishRepository();
        DraftSnapshot draft = draft("test:published", "Published");
        Path workspace = temporary.resolve("workspace");
        Files.createDirectories(workspace);

        var first = repository.publish(workspace, temporary.resolve("backups"), draft, "");
        var repeated = repository.publish(workspace, temporary.resolve("backups"), draft, draft.draftRevision());

        assertTrue(first.success());
        assertNull(first.value().backup());
        assertEquals(DraftOrigin.WORKSPACE, first.value().snapshot().origin());
        assertTrue(Files.isRegularFile(workspace.resolve("pack.mcmeta")));
        assertTrue(Files.isRegularFile(workspace.resolve("data/test/brnquest/books/published.json")));
        assertEquals(AuthorOperationResult.Status.NO_CHANGE, repeated.status());
    }

    @Test void replacementPreservesOtherWorkspaceFilesAndBacksUpPreviousPack() throws Exception {
        WorkspacePublishRepository repository = new WorkspacePublishRepository();
        Path workspace = temporary.resolve("workspace");
        DraftSnapshot original = draft("test:replace", "Before");
        assertTrue(repository.publish(workspace, temporary.resolve("backups"), original, "").success());
        Files.writeString(workspace.resolve("notes.txt"), "preserve", StandardCharsets.UTF_8);
        DraftSnapshot changed = draft("test:replace", "After");

        var result = repository.publish(workspace, temporary.resolve("backups"), changed,
                original.draftRevision());

        assertTrue(result.success());
        assertEquals("preserve", Files.readString(workspace.resolve("notes.txt"), StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(result.value().backup().resolve(
                "data/test/brnquest/books/replace.json")));
    }

    @Test void equalOrderGroupsPublishAfterCanonicalSorting() {
        ResourceLocation bookId = ResourceLocation.parse("test:equal_order");
        // This insertion order intentionally differs from the encoder's ID tiebreaker.
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Equal order",
                List.of(new ChapterGroupDefinition(bookId, ResourceLocation.parse("test:z_group"), "Z", 0),
                        new ChapterGroupDefinition(bookId, ResourceLocation.parse("test:a_group"), "A", 0)),
                List.of(), Map.of());
        DraftSnapshot draft = DraftSnapshot.from(book, DraftOrigin.EMPTY, "");

        var result = new WorkspacePublishRepository().publish(temporary.resolve("workspace"),
                temporary.resolve("backups"), draft, "");

        assertTrue(result.success(), () -> result.code() + ": " + result.message());
        assertTrue(Files.isRegularFile(temporary.resolve(
                "workspace/data/test/brnquest/books/equal_order.json")));
    }

    @Test void activatedFailureRestoresPreviousWorkspace() throws Exception {
        Path workspace = temporary.resolve("workspace");
        Path backups = temporary.resolve("backups");
        DraftSnapshot original = draft("test:rollback", "Before");
        assertTrue(new WorkspacePublishRepository().publish(workspace, backups, original, "").success());
        WorkspacePublishRepository failing = new WorkspacePublishRepository(stage -> {
            if (stage == WorkspacePublishRepository.TransactionStage.ACTIVATED) throw new java.io.IOException("injected");
        });

        var result = failing.publish(workspace, backups, draft("test:rollback", "After"),
                original.draftRevision());

        assertEquals(AuthorOperationResult.Status.IO_FAILURE, result.status());
        var restored = new DraftRepository().readWorkspace(workspace, original.book().id());
        assertEquals(original.book(), restored.value().book());
        try (var entries = Files.list(workspace.getParent())) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith(".workspace.staging-")));
        }
    }

    @Test void staleWorkspaceRevisionCannotBeOverwritten() {
        WorkspacePublishRepository repository = new WorkspacePublishRepository();
        Path workspace = temporary.resolve("workspace");
        DraftSnapshot original = draft("test:conflict", "Before");
        assertTrue(repository.publish(workspace, temporary.resolve("backups"), original, "").success());

        var result = repository.publish(workspace, temporary.resolve("backups"),
                draft("test:conflict", "After"), "stale");

        assertEquals(AuthorOperationResult.Status.CONFLICT, result.status());
        assertEquals("WORKSPACE_CHANGED", result.code());
    }

    @Test void failedFirstActivationRestoresEmptyServerScaffold() throws Exception {
        Path workspace = temporary.resolve("workspace");
        Files.createDirectories(workspace);
        WorkspacePublishRepository repository = new WorkspacePublishRepository(stage -> {
            if (stage == WorkspacePublishRepository.TransactionStage.ACTIVATED) throw new java.io.IOException("injected");
        });

        var result = repository.publish(workspace, temporary.resolve("backups"),
                draft("test:first_failure", "Failed"), "");

        assertEquals(AuthorOperationResult.Status.IO_FAILURE, result.status());
        assertTrue(Files.isDirectory(workspace));
        try (var entries = Files.list(workspace)) {
            assertTrue(entries.findAny().isEmpty());
        }
    }

    @Test void transientWindowsDirectoryMoveIsRetriedInsideOnePublish() throws Exception {
        Path workspace = temporary.resolve("workspace");
        Files.createDirectories(workspace);
        AtomicInteger attempts = new AtomicInteger();
        WorkspacePublishRepository repository = new WorkspacePublishRepository(stage -> {}, (source, target) -> {
            if (attempts.getAndIncrement() == 0) {
                throw new java.nio.file.AccessDeniedException(source.toString(), target.toString(),
                        "injected transient directory handle");
            }
            Files.move(source, target);
        });

        var result = repository.publish(workspace, temporary.resolve("backups"),
                draft("test:retry_move", "Retried"), "");

        assertTrue(result.success(), () -> result.code() + ": " + result.message());
        assertEquals(2, attempts.get());
        assertTrue(Files.isRegularFile(workspace.resolve("data/test/brnquest/books/retry_move.json")));
    }

    private static DraftSnapshot draft(String id, String title) {
        QuestBookDefinition book = new QuestBookDefinition(ResourceLocation.parse(id), 1, title,
                List.of(), List.of(), Map.of());
        return DraftSnapshot.from(book, DraftOrigin.EMPTY, "");
    }
}
