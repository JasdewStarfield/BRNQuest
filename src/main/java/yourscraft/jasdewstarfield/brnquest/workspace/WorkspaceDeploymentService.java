package yourscraft.jasdewstarfield.brnquest.workspace;

import net.minecraft.server.MinecraftServer;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;

/** Copies the global author workspace into a world without ever silently replacing it. */
public final class WorkspaceDeploymentService {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC);

    public DeploymentResult deploy(MinecraftServer server, boolean replace) throws IOException {
        return deploy(WorkspacePaths.workspace(server), WorkspacePaths.deployed(server), replace);
    }

    static DeploymentResult deploy(Path source, Path target, boolean replace) throws IOException {
        validateWorkspace(source);
        if (Files.exists(target) && !replace) return new DeploymentResult(Status.ALREADY_DEPLOYED, target, null, 0);

        Path datapacks = target.getParent();
        Files.createDirectories(datapacks);
        Path staging = datapacks.resolve("." + WorkspacePaths.PACK_DIRECTORY + ".staging");
        if (Files.exists(staging)) deleteTree(staging);
        int files = copyTree(source, staging);

        Path backup = null;
        if (Files.exists(target)) {
            // Keep backups outside datapacks so Minecraft cannot discover stale copies as packs.
            Path backupRoot = datapacks.getParent().resolve("brnquest-backups");
            Files.createDirectories(backupRoot);
            backup = availableBackupPath(backupRoot);
            move(target, backup);
        }
        try {
            move(staging, target);
        } catch (IOException exception) {
            if (backup != null && Files.exists(backup) && !Files.exists(target)) move(backup, target);
            throw exception;
        }
        return new DeploymentResult(backup == null ? Status.DEPLOYED : Status.REPLACED, target, backup, files);
    }

    private static Path availableBackupPath(Path backupRoot) {
        String base = WorkspacePaths.PACK_DIRECTORY + ".backup-" + BACKUP_TIME.format(Instant.now());
        Path candidate = backupRoot.resolve(base);
        for (int suffix = 2; Files.exists(candidate); suffix++) candidate = backupRoot.resolve(base + "-" + suffix);
        return candidate;
    }

    public void autoDeployAndReload(MinecraftServer server) {
        try {
            WorkspacePaths.ensureAuthorDirectories(server);
            DeploymentResult result = deploy(server, false);
            if (result.status() != Status.DEPLOYED) return;
            BRNQuest.LOGGER.info("[BRNQuest] Auto-deployed {} workspace files to {}", result.files(), result.target());
            reloadIncludingWorkspace(server);
        } catch (NoWorkspaceException exception) {
            BRNQuest.LOGGER.info("[BRNQuest] No author workspace found at {}; automatic deployment skipped", exception.path());
        } catch (IOException exception) {
            // Author content may be incomplete while a pack is being prepared; never mutate a world in that case.
            BRNQuest.LOGGER.warn("[BRNQuest] Author workspace is not deployable; automatic deployment skipped: {}", exception.getMessage());
        } catch (Exception exception) {
            BRNQuest.LOGGER.error("[BRNQuest] Automatic workspace deployment failed; no existing world pack was replaced", exception);
        }
    }

    public java.util.concurrent.CompletableFuture<Void> reloadIncludingWorkspace(MinecraftServer server) {
        var repository = server.getPackRepository();
        repository.reload();
        var selected = new ArrayList<>(repository.getSelectedIds());
        if (repository.isAvailable(WorkspacePaths.PACK_ID) && !selected.contains(WorkspacePaths.PACK_ID)) selected.add(WorkspacePaths.PACK_ID);
        return server.reloadResources(selected);
    }

    static void validateWorkspace(Path source) throws IOException {
        if (!Files.isDirectory(source)) throw new NoWorkspaceException(source);
        try (var entries = Files.list(source)) {
            // The loader creates this directory for discoverability; empty means no authored pack yet.
            if (entries.findAny().isEmpty()) throw new NoWorkspaceException(source);
        }
        if (!Files.isRegularFile(source.resolve("pack.mcmeta"))) throw new IOException("Workspace is missing pack.mcmeta: " + source);
        try (var paths = Files.walk(source)) {
            if (paths.anyMatch(Files::isSymbolicLink)) throw new IOException("Symbolic links are not allowed in the workspace");
        }
        try (var books = Files.walk(source.resolve("data"))) {
            if (books.noneMatch(path -> path.toString().replace('\\', '/').contains("/brnquest/books/") && path.toString().endsWith(".json"))) {
                throw new IOException("Workspace contains no data/<namespace>/brnquest/books/*.json");
            }
        }
    }

    private static int copyTree(Path source, Path target) throws IOException {
        int[] count = {0};
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path relative = source.relativize(path);
                Path output = target.resolve(relative).normalize();
                if (!output.startsWith(target)) throw new IOException("Workspace path escaped deployment target");
                if (Files.isDirectory(path)) Files.createDirectories(output);
                else {
                    Files.createDirectories(output.getParent());
                    Files.copy(path, output, StandardCopyOption.COPY_ATTRIBUTES);
                    count[0]++;
                }
            }
        }
        return count[0];
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target); }
    }

    private static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    public enum Status { DEPLOYED, REPLACED, ALREADY_DEPLOYED }
    public record DeploymentResult(Status status, Path target, Path backup, int files) {}
    static final class NoWorkspaceException extends IOException {
        private final Path path;
        NoWorkspaceException(Path path) { super("No workspace at " + path); this.path = path; }
        Path path() { return path; }
    }
}
