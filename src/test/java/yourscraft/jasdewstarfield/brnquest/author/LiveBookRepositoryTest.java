package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Disk commits must preserve complete revisions and never publish unrelated files. */
class LiveBookRepositoryTest {
    @TempDir Path directory;

    @Test void replacesOnlySelectedResourceAndBacksUpPreviousUtf8Revision() throws Exception {
        var before = book("原始标题");
        var after = book("修改后的标题");
        Path pack = directory.resolve("pack"), backups = directory.resolve("backups");
        var resource = ResourceLocation.parse("source:different/filename");
        LiveBookRepository.save(pack, backups, "original", before, resource);
        Path unrelated = pack.resolve("unrelated.txt");
        Files.writeString(unrelated, "do not publish", StandardCharsets.UTF_8);
        String revision = QuestBookSnapshot.of(before).revision();
        LiveBookRepository.save(pack, backups, revision, after, resource);
        assertEquals(NativeBookJson.encode(after), Files.readString(
                pack.resolve("data/source/brnquest/books/different/filename.json"), StandardCharsets.UTF_8));
        assertEquals(NativeBookJson.encode(before), Files.readString(
                backups.resolve("test/book/" + revision + ".json"), StandardCharsets.UTF_8));
        assertEquals("do not publish", Files.readString(unrelated, StandardCharsets.UTF_8));
        assertFalse(Files.exists(pack.resolve("data/test/brnquest/books/book.json")));
        assertTrue(Files.exists(pack.resolve("pack.mcmeta")));
    }

    @Test void rejectsExternalChangesWithoutOverwritingTheFile() throws Exception {
        var before = book("Before");
        Path pack = directory.resolve("pack"), backups = directory.resolve("backups");
        LiveBookRepository.save(pack, backups, "initial", before);
        assertThrows(java.io.IOException.class, () -> LiveBookRepository.save(pack, backups, "stale", book("After")));
        assertEquals(NativeBookJson.encode(before), Files.readString(
                pack.resolve("data/test/brnquest/books/book.json"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(backups));
    }

    @Test void failedBackupLeavesPreviousBookIntact() throws Exception {
        var before = book("Before");
        Path pack = directory.resolve("pack"), blocked = directory.resolve("not-a-directory");
        LiveBookRepository.save(pack, blocked, "initial", before);
        Files.writeString(blocked, "blocked", StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> LiveBookRepository.save(pack, blocked,
                QuestBookSnapshot.of(before).revision(), book("After")));
        assertEquals(NativeBookJson.encode(before), Files.readString(
                pack.resolve("data/test/brnquest/books/book.json"), StandardCharsets.UTF_8));
    }

    private static QuestBookDefinition book(String title) {
        return new QuestBookDefinition(ResourceLocation.parse("test:book"), 1, title, List.of(), List.of(), Map.of());
    }
}
