package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DraftRepositoryTest {
    @TempDir Path tempDirectory;

    @Test void createsDeterministicServerLocalDraftWithoutTouchingDatapackData() throws Exception {
        DraftRepository repository = new DraftRepository();
        QuestBookDefinition book = book("test:campaign", "Campaign");
        DraftSnapshot draft = DraftSnapshot.of(book, "published-revision");

        AuthorOperationResult<DraftSnapshot> created = repository.create(tempDirectory.resolve("drafts"), draft);

        assertEquals(AuthorOperationResult.Status.SUCCESS, created.status());
        Path directory = tempDirectory.resolve("drafts/test/campaign");
        assertTrue(Files.isRegularFile(directory.resolve("book.json")));
        assertTrue(Files.isRegularFile(directory.resolve("draft.json")));
        assertFalse(Files.exists(tempDirectory.resolve("data")), "Draft creation must not expose an unfinished data pack");
        String manifest = Files.readString(directory.resolve("draft.json"), StandardCharsets.UTF_8);
        assertFalse(manifest.contains("\r"));
        assertEquals(draft, repository.load(tempDirectory.resolve("drafts"), book.id()).value());
        assertEquals(AuthorOperationResult.Status.CONFLICT,
                repository.create(tempDirectory.resolve("drafts"), draft).status());
    }

    @Test void detectsContentChangedOutsideAuthorService() throws Exception {
        DraftRepository repository = new DraftRepository();
        QuestBookDefinition book = book("test:tamper", "Before");
        Path drafts = tempDirectory.resolve("drafts");
        assertTrue(repository.create(drafts, DraftSnapshot.of(book, "base")).success());
        Path bookFile = drafts.resolve("test/tamper/book.json");
        Files.writeString(bookFile, NativeBookJson.encode(book("test:tamper", "After")), StandardCharsets.UTF_8);

        AuthorOperationResult<DraftSnapshot> loaded = repository.load(drafts, book.id());

        assertEquals(AuthorOperationResult.Status.CONFLICT, loaded.status());
        assertEquals("DRAFT_REVISION_MISMATCH", loaded.code());
    }

    @Test void importsWorkspaceBookAsAnImmutableDraftSource() throws Exception {
        DraftRepository repository = new DraftRepository();
        QuestBookDefinition source = book("example:path/to/book", "Workspace");
        Path workspace = tempDirectory.resolve("workspace");
        Path sourceFile = workspace.resolve("data/example/brnquest/books/path/to/book.json");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, NativeBookJson.encode(source), StandardCharsets.UTF_8);

        DraftSnapshot snapshot = repository.readWorkspace(workspace, source.id()).value();

        assertEquals(source, snapshot.book());
        assertEquals(snapshot.draftRevision(), snapshot.baseRevision());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.book().chapters().add(null));
    }

    @Test void loadsVersionOneManifestAsUnknownOrigin() throws Exception {
        DraftRepository repository = new DraftRepository();
        QuestBookDefinition book = book("test:legacy", "Legacy");
        String revision = DraftSnapshot.of(book, "base").draftRevision();
        Path directory = tempDirectory.resolve("drafts/test/legacy");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("book.json"), NativeBookJson.encode(book), StandardCharsets.UTF_8);
        // Format 1 predates explicit draft origin metadata. Treat it conservatively
        // instead of rejecting server drafts created by an earlier BRNQuest build.
        Files.writeString(directory.resolve("draft.json"), """
                {
                  "format_version": 1,
                  "book_id": "test:legacy",
                  "base_revision": "base",
                  "draft_revision": "%s"
                }
                """.formatted(revision), StandardCharsets.UTF_8);

        AuthorOperationResult<DraftSnapshot> loaded = repository.load(tempDirectory.resolve("drafts"), book.id());

        assertTrue(loaded.success());
        assertEquals(DraftOrigin.UNKNOWN, loaded.value().origin());
        assertEquals(book, loaded.value().book());
    }

    @Test void saveIsDeterministicAndBacksUpThePreviousDirectory() throws Exception {
        DraftRepository repository = new DraftRepository();
        Path drafts = tempDirectory.resolve("drafts");
        Path backups = tempDirectory.resolve("backups");
        DraftSnapshot original = DraftSnapshot.from(book("test:saved", "Before"), DraftOrigin.EMPTY, "");
        assertTrue(repository.create(drafts, original).success());
        DraftSnapshot changed = DraftSnapshot.from(book("test:saved", "After"), DraftOrigin.EMPTY, "");

        AuthorOperationResult<DraftSaveResult> saved = repository.save(drafts, backups, changed,
                original.draftRevision());
        AuthorOperationResult<DraftSaveResult> repeated = repository.save(drafts, backups, changed,
                changed.draftRevision());

        assertEquals(AuthorOperationResult.Status.SUCCESS, saved.status());
        assertTrue(Files.isRegularFile(saved.value().backup().resolve("book.json")));
        assertEquals(original, repository.readDirectoryForTest(saved.value().backup(), original.book().id()).value());
        assertEquals(AuthorOperationResult.Status.NO_CHANGE, repeated.status());
        assertNull(repeated.value().backup());
        assertEquals(NativeBookJson.encode(changed.book()), Files.readString(
                drafts.resolve("test/saved/book.json"), StandardCharsets.UTF_8));
    }

    @Test void injectedFailureRestoresTheOriginalDraft() throws Exception {
        DraftRepository repository = new DraftRepository(stage -> {
            if (stage == DraftRepository.TransactionStage.BACKUP_MOVED) throw new java.io.IOException("injected");
        });
        Path drafts = tempDirectory.resolve("drafts");
        DraftSnapshot original = DraftSnapshot.from(book("test:rollback", "Before"), DraftOrigin.EMPTY, "");
        assertTrue(repository.create(drafts, original).success());
        DraftSnapshot changed = DraftSnapshot.from(book("test:rollback", "After"), DraftOrigin.EMPTY, "");

        AuthorOperationResult<DraftSaveResult> result = repository.save(drafts, tempDirectory.resolve("backups"),
                changed, original.draftRevision());

        assertEquals(AuthorOperationResult.Status.IO_FAILURE, result.status());
        assertEquals(original, repository.load(drafts, original.book().id()).value());
        try (var paths = Files.walk(drafts)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().contains(".staging-")));
        }
    }

    @Test void externalDiskRevisionCannotBeSilentlyOverwritten() {
        DraftRepository repository = new DraftRepository();
        Path drafts = tempDirectory.resolve("drafts");
        DraftSnapshot disk = DraftSnapshot.from(book("test:conflict", "Disk"), DraftOrigin.EMPTY, "");
        assertTrue(repository.create(drafts, disk).success());
        DraftSnapshot session = DraftSnapshot.from(book("test:conflict", "Session"), DraftOrigin.EMPTY, "");

        AuthorOperationResult<DraftSaveResult> result = repository.save(drafts, tempDirectory.resolve("backups"),
                session, "stale-revision");

        assertEquals(AuthorOperationResult.Status.CONFLICT, result.status());
        assertEquals("DISK_DRAFT_CHANGED", result.code());
        assertEquals(disk, repository.load(drafts, disk.book().id()).value());
    }

    private static QuestBookDefinition book(String id, String title) {
        return new QuestBookDefinition(ResourceLocation.parse(id), 1, title, List.of(), List.of(), Map.of());
    }
}
