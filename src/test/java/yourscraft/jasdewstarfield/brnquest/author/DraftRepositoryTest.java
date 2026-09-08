package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
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

    @Test void migratesUntouchedLegacyCanonicalDraftAndKeepsRecoverableOriginal() throws Exception {
        DraftRepository repository = new DraftRepository();
        QuestBookDefinition book = book("test:legacy_encoder", "Legacy encoder");
        String legacyJson = NativeBookJson.encode(book)
                .replace("  \"localization\": {\n    \"fallback_locale\": \"en_us\",\n    \"translations\": {}\n  },\n", "")
                .replace("  \"extensions\": {},\n", "");
        String legacyRevision = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(legacyJson.getBytes(StandardCharsets.UTF_8)));
        Path drafts = tempDirectory.resolve("drafts");
        Path directory = drafts.resolve("test/legacy_encoder");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("book.json"), legacyJson, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("draft.json"), """
                {
                  "format_version": 2,
                  "book_id": "test:legacy_encoder",
                  "origin": "ACTIVE",
                  "base_revision": "active-base",
                  "draft_revision": "%s"
                }
                """.formatted(legacyRevision), StandardCharsets.UTF_8);

        AuthorOperationResult<DraftSnapshot> loaded = repository.loadForEditing(drafts,
                tempDirectory.resolve("workspace"), tempDirectory.resolve("backups"), book.id());

        assertTrue(loaded.success());
        assertEquals(book, loaded.value().book());
        assertEquals(NativeBookJson.encode(book), Files.readString(directory.resolve("book.json"),
                StandardCharsets.UTF_8));
        try (var paths = Files.walk(tempDirectory.resolve("backups/drafts/test/legacy_encoder"))) {
            Path backup = paths.filter(path -> Files.isRegularFile(path.resolve("book.json"))).findFirst().orElseThrow();
            assertEquals(legacyJson, Files.readString(backup.resolve("book.json"), StandardCharsets.UTF_8));
            assertTrue(DraftRepository.readDirectoryAllowCanonicalDrift(backup, book.id()).success());
        }
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

    @Test void openingPublishedContentRestoresItsWorkspaceConcurrencyBaseline() throws Exception {
        DraftRepository repository = new DraftRepository();
        QuestBookDefinition book = book("test:published", "Published");
        DraftSnapshot staleManifest = DraftSnapshot.from(book, DraftOrigin.ACTIVE, "old-active");
        Path drafts = tempDirectory.resolve("drafts");
        Path workspace = tempDirectory.resolve("workspace");
        assertTrue(repository.create(drafts, staleManifest).success());
        Path workspaceBook = workspace.resolve("data/test/brnquest/books/published.json");
        Files.createDirectories(workspaceBook.getParent());
        Files.writeString(workspaceBook, NativeBookJson.encode(book), StandardCharsets.UTF_8);

        AuthorOperationResult<DraftSnapshot> loaded = repository.loadForEditing(drafts, workspace, book.id());

        assertTrue(loaded.success());
        assertEquals("DRAFT_WORKSPACE_BASELINE_RESTORED", loaded.code());
        assertEquals(DraftOrigin.WORKSPACE, loaded.value().origin());
        assertEquals(loaded.value().draftRevision(), loaded.value().baseRevision());

        DraftSnapshot edited = DraftSnapshot.from(book("test:published", "Edited again"),
                loaded.value().origin(), loaded.value().baseRevision());
        AuthorOperationResult<DraftSaveResult> saved = repository.save(drafts, tempDirectory.resolve("backups"),
                edited, loaded.value().draftRevision());
        assertTrue(saved.success(), "A reopened published draft must remain saveable");
        DraftSnapshot persisted = repository.load(drafts, book.id()).value();
        assertEquals(DraftOrigin.WORKSPACE, persisted.origin());
        assertEquals(loaded.value().draftRevision(), persisted.baseRevision());
    }

    @Test void openingDivergentWorkspaceNeverRebasesOrMergesTheDraft() throws Exception {
        DraftRepository repository = new DraftRepository();
        QuestBookDefinition draftBook = book("test:divergent", "Draft");
        DraftSnapshot draft = DraftSnapshot.from(draftBook, DraftOrigin.ACTIVE, "active-base");
        Path drafts = tempDirectory.resolve("drafts");
        Path workspace = tempDirectory.resolve("workspace");
        assertTrue(repository.create(drafts, draft).success());
        Path workspaceBook = workspace.resolve("data/test/brnquest/books/divergent.json");
        Files.createDirectories(workspaceBook.getParent());
        Files.writeString(workspaceBook, NativeBookJson.encode(book("test:divergent", "Other")),
                StandardCharsets.UTF_8);

        DraftSnapshot loaded = repository.loadForEditing(drafts, workspace, draftBook.id()).value();

        assertEquals(DraftOrigin.ACTIVE, loaded.origin());
        assertEquals("active-base", loaded.baseRevision());
        assertEquals(draftBook, loaded.book());
    }

    @Test void reopeningAfterAnotherPublishAdvancesAnExistingWorkspaceBaseline() throws Exception {
        DraftRepository repository = new DraftRepository();
        QuestBookDefinition republishedBook = book("test:republished", "Second publish");
        DraftSnapshot staleWorkspaceBaseline = DraftSnapshot.from(republishedBook,
                DraftOrigin.WORKSPACE, "previous-workspace");
        Path drafts = tempDirectory.resolve("drafts");
        Path workspace = tempDirectory.resolve("workspace");
        assertTrue(repository.create(drafts, staleWorkspaceBaseline).success());
        Path workspaceBook = workspace.resolve("data/test/brnquest/books/republished.json");
        Files.createDirectories(workspaceBook.getParent());
        Files.writeString(workspaceBook, NativeBookJson.encode(republishedBook), StandardCharsets.UTF_8);

        DraftSnapshot loaded = repository.loadForEditing(drafts, workspace, republishedBook.id()).value();

        assertEquals(DraftOrigin.WORKSPACE, loaded.origin());
        assertEquals(loaded.draftRevision(), loaded.baseRevision(),
                "A repeated publish must advance the workspace concurrency baseline");
        assertNotEquals("previous-workspace", loaded.baseRevision());
    }

    @Test void catalogListsNestedValidDraftsInStableBookIdOrder() throws Exception {
        DraftRepository repository = new DraftRepository();
        Path drafts = tempDirectory.resolve("drafts");
        DraftSnapshot later = DraftSnapshot.from(book("test:zeta", "Zeta"), DraftOrigin.ACTIVE, "active");
        DraftSnapshot earlier = DraftSnapshot.from(book("example:path/to/book", "Nested"), DraftOrigin.EMPTY, "");
        assertTrue(repository.create(drafts, later).success());
        assertTrue(repository.create(drafts, earlier).success());

        AuthorOperationResult<List<DraftCatalogEntry>> result = repository.list(drafts);

        assertTrue(result.success());
        assertEquals(List.of("example:path/to/book", "test:zeta"), result.value().stream()
                .map(entry -> entry.bookId().toString()).toList());
        assertEquals("Nested", result.value().getFirst().title());
        assertEquals(DraftOrigin.EMPTY, result.value().getFirst().origin());
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

    @Test void replacingSelectedDraftCreatesVersionAndRejectsStaleSelection() throws Exception {
        DraftRepository repository = new DraftRepository();
        Path drafts = tempDirectory.resolve("drafts");
        Path backups = tempDirectory.resolve("backups");
        DraftSnapshot original = DraftSnapshot.from(book("test:versioned", "Before"), DraftOrigin.ACTIVE, "base");
        DraftSnapshot replacement = DraftSnapshot.from(book("test:versioned", "After"), DraftOrigin.ACTIVE, "new");
        assertTrue(repository.create(drafts, original).success());

        AuthorOperationResult<DraftSnapshot> stale = repository.replace(drafts, backups, replacement, "stale");
        AuthorOperationResult<DraftSnapshot> replaced = repository.replace(drafts, backups, replacement,
                original.draftRevision());

        assertEquals("DRAFT_SELECTION_STALE", stale.code());
        assertEquals("DRAFT_VERSION_CREATED", replaced.code(), replaced::message);
        assertEquals(replacement, repository.load(drafts, replacement.book().id()).value());
        try (var paths = Files.walk(backups.resolve("drafts/test/versioned"))) {
            Path version = paths.filter(path -> Files.isRegularFile(path.resolve("book.json"))).findFirst().orElseThrow();
            assertEquals(original, repository.readDirectoryForTest(version, original.book().id()).value());
        }
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
