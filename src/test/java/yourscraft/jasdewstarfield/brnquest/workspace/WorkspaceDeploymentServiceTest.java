package yourscraft.jasdewstarfield.brnquest.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceDeploymentServiceTest {
    @TempDir Path temporary;

    @Test void authorDirectoriesAreCreatedTogether() throws Exception {
        Path root = temporary.resolve("config/brnquest");

        WorkspacePaths.ensureAuthorDirectories(root);

        assertTrue(Files.isDirectory(root.resolve("imports")));
        assertTrue(Files.isDirectory(root.resolve("reports")));
        assertTrue(Files.isDirectory(root.resolve("workspace")));
        assertTrue(Files.isDirectory(root.resolve("backups")));
        assertThrows(WorkspaceDeploymentService.NoWorkspaceException.class,
                () -> WorkspaceDeploymentService.validateWorkspace(root.resolve("workspace")));
    }

    @Test void firstDeployCopiesWorkspaceAndSecondDeployDoesNotOverwrite() throws Exception {
        Path source = workspace("first");
        Path target = temporary.resolve("world/datapacks/brnquest-workspace");

        var first = WorkspaceDeploymentService.deploy(source, target, false);
        Files.writeString(target.resolve("world-marker.txt"), "keep", StandardCharsets.UTF_8);
        var second = WorkspaceDeploymentService.deploy(source, target, false);

        assertEquals(WorkspaceDeploymentService.Status.DEPLOYED, first.status());
        assertEquals(WorkspaceDeploymentService.Status.ALREADY_DEPLOYED, second.status());
        assertEquals("keep", Files.readString(target.resolve("world-marker.txt"), StandardCharsets.UTF_8));
    }

    @Test void explicitReplaceBacksUpPreviousWorldPack() throws Exception {
        Path source = workspace("new");
        Path target = temporary.resolve("world/datapacks/brnquest-workspace");
        Files.createDirectories(target);
        Files.writeString(target.resolve("old.txt"), "old", StandardCharsets.UTF_8);

        var result = WorkspaceDeploymentService.deploy(source, target, true);

        assertEquals(WorkspaceDeploymentService.Status.REPLACED, result.status());
        assertEquals(temporary.resolve("world/brnquest-backups"), result.backup().getParent());
        assertTrue(Files.isRegularFile(result.backup().resolve("old.txt")));
        assertTrue(Files.isRegularFile(target.resolve("data/test/brnquest/books/main.json")));
    }

    @Test void invalidWorkspaceIsRejectedBeforeTargetMutation() throws Exception {
        Path source = temporary.resolve("invalid");
        Files.createDirectories(source);
        Path target = temporary.resolve("world/datapacks/brnquest-workspace");

        assertThrows(IOException.class, () -> WorkspaceDeploymentService.deploy(source, target, false));
        assertTrue(Files.notExists(target));
    }

    @Test void activatedFailureRestoresPreviousWorldPackAndCleansStaging() throws Exception {
        Path source = workspace("replacement");
        Path target = temporary.resolve("world/datapacks/brnquest-workspace");
        Files.createDirectories(target);
        Files.writeString(target.resolve("old.txt"), "old", StandardCharsets.UTF_8);
        WorkspaceDeploymentService service = new WorkspaceDeploymentService(stage -> {
            if (stage == WorkspaceDeploymentService.TransactionStage.ACTIVATED) throw new IOException("injected");
        });

        assertThrows(IOException.class, () -> service.deployTransaction(source, target, true));

        assertEquals("old", Files.readString(target.resolve("old.txt"), StandardCharsets.UTF_8));
        try (var paths = Files.list(target.getParent())) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().contains(".staging-")));
        }
    }

    @Test void failedFirstActivationLeavesNoWorldPack() throws Exception {
        Path source = workspace("first-failure");
        Path target = temporary.resolve("new-world/datapacks/brnquest-workspace");
        WorkspaceDeploymentService service = new WorkspaceDeploymentService(stage -> {
            if (stage == WorkspaceDeploymentService.TransactionStage.ACTIVATED) throw new IOException("injected");
        });

        assertThrows(IOException.class, () -> service.deployTransaction(source, target, false));

        assertTrue(Files.notExists(target));
    }

    private Path workspace(String title) throws IOException {
        Path source = temporary.resolve("workspace-" + title);
        Path book = source.resolve("data/test/brnquest/books/main.json");
        Files.createDirectories(book.getParent());
        Files.writeString(source.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"test\"}}", StandardCharsets.UTF_8);
        Files.writeString(book, "{\"title\":\"" + title + "\"}", StandardCharsets.UTF_8);
        return source;
    }
}
