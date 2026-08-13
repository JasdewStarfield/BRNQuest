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

    private static QuestBookDefinition book(String id, String title) {
        return new QuestBookDefinition(ResourceLocation.parse(id), 1, title, List.of(), List.of(), Map.of());
    }
}
