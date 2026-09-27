package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the on-disk modpack layout with the same multi-file fixture as inbox imports. */
class FtbLocalImportTest {
    @TempDir Path instance;

    @Test void convertsOriginalDirectoryWithoutAnInboxCopyOrSourceChanges() throws Exception {
        Path fixture = Path.of(getClass().getResource("/fixtures/ftb_v13/eow").toURI());
        Path source = instance.resolve(FtbImportService.LOCAL_SOURCE);
        try (var paths = Files.walk(fixture)) {
            for (Path file : paths.toList()) {
                // NeoForge may expose test resources through its own filesystem provider.
                Path target = source.resolve(fixture.relativize(file).toString());
                if (Files.isDirectory(file)) Files.createDirectories(target);
                else Files.copy(file, target);
            }
        }
        var before = contents(source);
        var direct = FtbImportService.withImportSource(new FtbV13Importer().importBook(
                FtbImportService.localSource(instance), "converted", "main"), FtbImportService.LOCAL_SOURCE);
        var expected = FtbImportService.withImportSource(new FtbV13Importer().importBook(
                fixture, "converted", "main"), FtbImportService.LOCAL_SOURCE);
        assertFalse(direct.report().hasFatal(), direct.report().toJson());
        assertTrue(direct.questCount() > 0);
        assertEquals(NativeBookJson.encode(expected.book()), NativeBookJson.encode(direct.book()));
        assertEquals(FtbImportService.LOCAL_SOURCE, direct.book().extensions().get("ftb.import_source"));
        assertEquals(before, contents(source));
        assertFalse(Files.exists(instance.resolve("config/brnquest")));
    }

    @Test void missingOrEmptyDirectoryDoesNotManufactureAnEmptyBook() throws Exception {
        assertThrows(NoSuchFileException.class, () -> FtbImportService.localSource(instance));
        Path source = Files.createDirectories(instance.resolve(FtbImportService.LOCAL_SOURCE));
        assertThrows(NoSuchFileException.class, () -> FtbImportService.localSource(instance));
        assertEquals(0, contents(source).size());
    }

    @Test void requiresDataFileInTheSelectedServerInstance() throws Exception {
        Path source = Files.createDirectories(instance.resolve(FtbImportService.LOCAL_SOURCE));
        Files.createDirectory(source.resolve("data.snbt"));
        assertThrows(NoSuchFileException.class, () -> FtbImportService.localSource(instance));
        assertThrows(NoSuchFileException.class, () -> FtbImportService.localSource(instance.resolve("other-server")));
    }

    // Compare every source byte so language and reward-table files are covered along with chapters.
    private static TreeMap<String, String> contents(Path source) throws Exception {
        var result = new TreeMap<String, String>();
        try (var paths = Files.walk(source)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                result.put(source.relativize(path).toString(), java.util.HexFormat.of().formatHex(Files.readAllBytes(path)));
            }
        }
        return result;
    }
}
