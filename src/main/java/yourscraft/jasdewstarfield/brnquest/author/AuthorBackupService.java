package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspaceDeploymentService;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspacePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Lists, previews, and atomically restores BRNQuest-owned backups on the target server. */
public final class AuthorBackupService {
    private static final int MOVE_ATTEMPTS = 4;
    private static final long MOVE_RETRY_MILLIS = 25L;

    public AuthorOperationResult<List<BackupDescriptor>> list(ServerPlayer player, BackupKind kind) {
        MinecraftServer server = authorizedServer(player);
        if (server == null) return authorizationFailure(player);
        try {
            List<BackupDescriptor> backups = list(server, kind);
            return backups.isEmpty()
                    ? AuthorOperationResult.noChange("NO_BACKUPS", "No backups are available", backups)
                    : AuthorOperationResult.success("BACKUPS_LISTED", "Backups listed", backups);
        } catch (IOException exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "BACKUP_LIST_FAILED",
                    exception.getMessage());
        }
    }

    public AuthorOperationResult<BackupRestorePreview> preview(ServerPlayer player, BackupKind kind, String backupId) {
        MinecraftServer server = authorizedServer(player);
        if (server == null) return authorizationFailure(player);
        try {
            ResolvedBackup backup = resolve(server, kind, backupId);
            Path target = target(server, backup);
            String current = treeRevision(target);
            BackupRestorePreview preview = new BackupRestorePreview(describe(backup), current, Files.exists(target),
                    !current.equals(backup.revision()));
            return preview.willReplace()
                    ? AuthorOperationResult.success("RESTORE_PREVIEW", "Restore would replace the current target", preview)
                    : AuthorOperationResult.noChange("RESTORE_ALREADY_CURRENT", "Backup already matches the target", preview);
        } catch (NoSuchFileException exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, "BACKUP_NOT_FOUND",
                    exception.getMessage());
        } catch (Exception exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "BACKUP_PREVIEW_FAILED",
                    exception.getMessage());
        }
    }

    public AuthorOperationResult<BackupRestoreResult> restore(ServerPlayer player, BackupKind kind, String backupId,
                                                               String expectedCurrentRevision) {
        MinecraftServer server = authorizedServer(player);
        if (server == null) return authorizationFailure(player);
        AuthorOperationResult<BackupRestoreResult> result;
        String before = "";
        try {
            ResolvedBackup backup = resolve(server, kind, backupId);
            Path target = target(server, backup);
            before = treeRevision(target);
            if (kind == BackupKind.DRAFT
                    && EditSessionService.get().inspect(player, backup.bookId()).success()) {
                result = AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                        "ACTIVE_EDIT_SESSION", "Close the draft edit session before restoring its files");
            } else if (!before.equals(normalize(expectedCurrentRevision))) {
                result = AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "RESTORE_TARGET_CHANGED",
                        "Expected target " + normalize(expectedCurrentRevision) + " but disk has " + before);
            } else if (before.equals(backup.revision())) {
                result = AuthorOperationResult.noChange("RESTORE_ALREADY_CURRENT", "Backup already matches the target",
                        new BackupRestoreResult(describe(backup), before, before, null));
            } else {
                result = restore(server, backup, target, before);
            }
        } catch (NoSuchFileException exception) {
            result = AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, "BACKUP_NOT_FOUND",
                    exception.getMessage());
        } catch (Exception exception) {
            result = AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "BACKUP_RESTORE_FAILED",
                    exception.getMessage());
        }
        String after = result.success() && result.value() != null ? result.value().currentRevision() : before;
        AuthorAuditLog.record(player, "backup_restore_" + kind.name().toLowerCase(Locale.ROOT), backupId,
                before, after, result);
        return result;
    }

    private static AuthorOperationResult<BackupRestoreResult> restore(MinecraftServer server, ResolvedBackup backup,
                                                                       Path target, String before) throws IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        Path staging = parent.resolve("." + target.getFileName() + ".restore-staging-" + UUID.randomUUID());
        Path overwritten = WorkspacePaths.backups(server).resolve("restore-overwritten")
                .resolve(backup.kind().name().toLowerCase(Locale.ROOT))
                .resolve((before.isBlank() ? "EMPTY" : before) + "-" + UUID.randomUUID());
        boolean previousMoved = false;
        try {
            Files.createDirectories(parent);
            copyTree(backup.path(), staging);
            validate(staging, backup.kind(), backup.bookId());
            if (Files.exists(target)) {
                Files.createDirectories(overwritten.getParent());
                move(target, overwritten);
                previousMoved = true;
            }
            move(staging, target);
            String restoredRevision = treeRevision(target);
            if (!restoredRevision.equals(backup.revision())) throw new IOException("Restored target failed verification");
            return AuthorOperationResult.success("BACKUP_RESTORED", "Backup restored atomically",
                    new BackupRestoreResult(describe(backup), before, restoredRevision,
                            previousMoved ? overwritten : null));
        } catch (IOException exception) {
            if (previousMoved && Files.exists(overwritten)) {
                safeDelete(target, parent);
                move(overwritten, target);
            } else {
                safeDelete(target, parent);
            }
            safeDelete(staging, parent);
            throw exception;
        }
    }

    private static List<BackupDescriptor> list(MinecraftServer server, BackupKind kind) throws IOException {
        Path root = backupRoot(server, kind);
        if (!Files.isDirectory(root)) return List.of();
        List<Path> directories;
        if (kind == BackupKind.DRAFT) {
            try (var paths = Files.walk(root)) {
                directories = paths.filter(path -> Files.isRegularFile(path.resolve("draft.json"))).toList();
            }
        } else {
            try (var paths = Files.list(root)) {
                directories = paths.filter(Files::isDirectory).toList();
            }
        }
        List<BackupDescriptor> result = new ArrayList<>();
        for (Path directory : directories) {
            validate(directory, kind, null);
            result.add(new BackupDescriptor(kind, root.relativize(directory).toString().replace('\\', '/'),
                    treeRevision(directory), fileCount(directory)));
        }
        result.sort(Comparator.comparing(BackupDescriptor::id));
        return List.copyOf(result);
    }

    private static ResolvedBackup resolve(MinecraftServer server, BackupKind kind, String id) throws IOException {
        if (id == null || id.isBlank() || id.contains("\\") || id.startsWith("/") || id.contains(":")) {
            throw new NoSuchFileException("Unsafe backup ID");
        }
        Path relative = Path.of(id);
        for (Path segment : relative) {
            if (segment.toString().equals(".") || segment.toString().equals("..")) {
                throw new NoSuchFileException("Unsafe backup ID");
            }
        }
        Path root = backupRoot(server, kind).toAbsolutePath().normalize();
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || !Files.isDirectory(path)) throw new NoSuchFileException(id);
        ResourceLocation bookId = validate(path, kind, null);
        return new ResolvedBackup(kind, id, path, treeRevision(path), fileCount(path), bookId);
    }

    private static ResourceLocation validate(Path path, BackupKind kind, ResourceLocation expectedBook) throws IOException {
        if (kind == BackupKind.DRAFT) {
            if (!Files.isRegularFile(path.resolve("draft.json"))) throw new IOException("Draft backup has no manifest");
            String manifest = Files.readString(path.resolve("draft.json"), StandardCharsets.UTF_8);
            var value = DraftManifest.decode(com.google.gson.JsonParser.parseString(manifest).getAsJsonObject());
            ResourceLocation bookId = expectedBook == null ? value.bookId() : expectedBook;
            var loaded = new DraftRepository().readDirectoryForTest(path, bookId);
            if (!loaded.success()) throw new IOException(loaded.code() + ": " + loaded.message());
            return bookId;
        }
        WorkspaceDeploymentService.validateWorkspace(path);
        return null;
    }

    private static Path backupRoot(MinecraftServer server, BackupKind kind) {
        return switch (kind) {
            case DRAFT -> WorkspacePaths.backups(server).resolve("drafts");
            case WORKSPACE -> WorkspacePaths.backups(server).resolve("workspace");
            case DEPLOYED -> WorkspacePaths.deployed(server).getParent().getParent().resolve("brnquest-backups");
        };
    }

    private static Path target(MinecraftServer server, ResolvedBackup backup) {
        return switch (backup.kind()) {
            case DRAFT -> WorkspacePaths.drafts(server).resolve(backup.bookId().getNamespace())
                    .resolve(backup.bookId().getPath()).normalize();
            case WORKSPACE -> WorkspacePaths.workspace(server);
            case DEPLOYED -> WorkspacePaths.deployed(server);
        };
    }

    private static BackupDescriptor describe(ResolvedBackup backup) {
        return new BackupDescriptor(backup.kind(), backup.id(), backup.revision(), backup.fileCount());
    }

    private static long fileCount(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static String treeRevision(Path root) throws IOException {
        if (!Files.exists(root)) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    digest.update(root.relativize(path).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                    digest.update(Files.readAllBytes(path));
                    digest.update((byte) 0);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not allowed in backups");
                Path output = target.resolve(source.relativize(path)).normalize();
                if (!output.startsWith(target)) throw new IOException("Backup path escaped restore staging");
                if (Files.isDirectory(path)) Files.createDirectories(output);
                else {
                    Files.createDirectories(output.getParent());
                    Files.copy(path, output, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MOVE_ATTEMPTS; attempt++) {
            try {
                try {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(source, target);
                }
                return;
            } catch (IOException exception) {
                // Windows can briefly retain the old workspace handle after it is moved aside.
                // The bounded retry mirrors publish activation without weakening atomic restore.
                lastFailure = exception;
                if (attempt == MOVE_ATTEMPTS) break;
                try {
                    Thread.sleep(MOVE_RETRY_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Backup restore move retry was interrupted", exception);
                }
            }
        }
        throw new IOException("Backup restore move failed after " + MOVE_ATTEMPTS + " attempts ["
                + lastFailure.getClass().getSimpleName() + "]: " + lastFailure.getMessage(), lastFailure);
    }

    private static void safeDelete(Path target, Path allowedParent) {
        Path resolved = target.toAbsolutePath().normalize();
        Path parent = allowedParent.toAbsolutePath().normalize();
        if (!resolved.startsWith(parent) || !Files.exists(resolved)) return;
        try (var paths = Files.walk(resolved)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the primary restore error; the inert staging path remains inspectable.
        }
    }

    private static MinecraftServer authorizedServer(ServerPlayer player) {
        if (player == null || player.getServer() == null) return null;
        MinecraftServer server = player.getServer();
        return server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.createCommandSourceStack().hasPermission(2) ? server : null;
    }

    private static <T> AuthorOperationResult<T> authorizationFailure(ServerPlayer player) {
        if (player == null || player.getServer() == null
                || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                    "PLAYER_NOT_CONNECTED", "Editor must be connected to the target server");
        }
        return AuthorOperationResult.failure(AuthorOperationResult.Status.FORBIDDEN,
                "EDITOR_PERMISSION_REQUIRED", "Permission level 2 is required for backup recovery");
    }

    private static String normalize(String value) { return value == null ? "" : value; }

    private record ResolvedBackup(BackupKind kind, String id, Path path, String revision, long fileCount,
                                  ResourceLocation bookId) {}
}
