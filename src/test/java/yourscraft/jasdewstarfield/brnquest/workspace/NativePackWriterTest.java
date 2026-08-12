package yourscraft.jasdewstarfield.brnquest.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativePackWriterTest {
    @TempDir Path temporary;

    @Test void writesIntoLoaderCreatedEmptyWorkspaceButRefusesAuthoredContent() throws Exception {
        Path workspace = temporary.resolve("workspace");
        Files.createDirectories(workspace);
        String json = "{\"chapter_groups\":[],\"chapters\":[]}";

        NativePackWriter.write(workspace, "test", "main", json, "test workspace");

        assertTrue(Files.isRegularFile(workspace.resolve("pack.mcmeta")));
        assertTrue(Files.isRegularFile(workspace.resolve("data/test/brnquest/books/main.json")));
        assertThrows(FileAlreadyExistsException.class,
                () -> NativePackWriter.write(workspace, "test", "main", json, "replacement"));
    }

    @Test void refusesAConflictingFileAtWorkspacePath() throws Exception {
        Path workspace = temporary.resolve("workspace");
        Files.writeString(workspace, "occupied", StandardCharsets.UTF_8);

        assertThrows(FileAlreadyExistsException.class,
                () -> NativePackWriter.write(workspace, "test", "main", "{}", "test"));
    }
}
