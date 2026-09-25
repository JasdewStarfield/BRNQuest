package yourscraft.jasdewstarfield.brnquest.author;

import yourscraft.jasdewstarfield.brnquest.diagnostic.FileIoTrace;
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
    private static final int MAX_VISIBLE_DRAFT_VERSIONS = 40;

    /** Lists only the selected book's validated backups, newest first. */
    public AuthorOperationResult<List<DraftBackupVersion>> listDraftVersions(ServerPlayer player,
                                                                              ResourceLocation bookId) {
        MinecraftServer server = authorizedServer(player);
        if (server == null) return authorizationFailure(player);
        if (bookId == null) return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                "INVALID_BOOK_ID", "A task book ID is required");
        Path root = backupRoot(server, BackupKind.DRAFT).toAbsolutePath().normalize();
        Path directory = root.resolve(bookId.getNamespace()).resolve(bookId.getPath()).normalize();
        if (!directory.startsWith(root)) return AuthorOperationResult.failure(
                AuthorOperationResult.Status.INVALID_REQUEST, "INVALID_BOOK_ID", "Unsafe task book ID");
        if (!Files.isDirectory(directory)) return AuthorOperationResult.success(
                "DRAFT_VERSIONS_EMPTY", "No saved versions exist for this task book", List.of());
        try (var paths = Files.list(directory)) {
            List<DraftBackupVersion> versions = new ArrayList<>();
            for (Path candidate : paths.filter(Files::isDirectory).toList()) {
                // Validation also checks the manifest, book ID and content revision before
                // a backup becomes selectable from the editor.
                validate(candidate, BackupKind.DRAFT, bookId);
                var snapshot = DraftRepository.readDirectoryAllowCanonicalDrift(candidate, bookId).value();
                versions.add(new DraftBackupVersion(root.relativize(candidate).toString().replace('\\', '/'),
                        snapshot.draftRevision(), snapshot.book().title(),
                        Files.getLastModifiedTime(candidate.resolve("draft.json")).toMillis()));
            }
            versions.sort(Comparator.comparingLong(DraftBackupVersion::savedAtEpochMillis).reversed()
                    .thenComparing(DraftBackupVersion::id));
            return AuthorOperationResult.success("DRAFT_VERSIONS_LISTED", "Saved draft versions listed",
                    List.copyOf(versions.stream().limit(MAX_VISIBLE_DRAFT_VERSIONS).toList()));
        } catch (Exception exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE,
                    "DRAFT_VERSIONS_FAILED", exception.getMessage());
        }
    }

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
        return restore(player, kind, null, backupId, expectedCurrentRevision);
    }

    /** Pins the chosen task book as well as the current revision for editor restores. */
    public AuthorOperationResult<BackupRestoreResult> restoreDraftVersion(ServerPlayer player,
            ResourceLocation bookId, String backupId, String expectedCurrentRevision) {
        if (bookId == null) return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                "INVALID_BOOK_ID", "A task book ID is required");
        MinecraftServer server = authorizedServer(player);
        if (server == null) return authorizationFailure(player);
        try {
            Path target = WorkspacePaths.drafts(server).resolve(bookId.getNamespace())
                    .resolve(bookId.getPath()).normalize();
            // The catalog exposes a draft content revision, while the atomic restore guard
            // compares whole directory trees. Pin both before moving either directory.
            String treeBefore = treeRevision(target);
            var current = new DraftRepository().load(server, bookId);
            String contentBefore = current.success() ? current.value().draftRevision() : "";
            if (!current.success() && current.status() != AuthorOperationResult.Status.NOT_FOUND) {
                return AuthorOperationResult.failure(current.status(), current.code(), current.message());
            }
            if (!contentBefore.equals(normalize(expectedCurrentRevision))
                    || !treeBefore.equals(treeRevision(target))) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                        "RESTORE_TARGET_CHANGED", "The saved draft changed after version history opened");
            }
            return restore(player, BackupKind.DRAFT, bookId, backupId, treeBefore);
        } catch (IOException exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE,
                    "BACKUP_RESTORE_FAILED", exception.getMessage());
        }
    }

    private AuthorOperationResult<BackupRestoreResult> restore(ServerPlayer player, BackupKind kind,
            ResourceLocation expectedBookId, String backupId, String expectedCurrentRevision) {
        MinecraftServer server = authorizedServer(player);
        if (server == null) return authorizationFailure(player);
        AuthorOperationResult<BackupRestoreResult> result;
        String before = "";
        try {
            ResolvedBackup backup = resolve(server, kind, backupId);
            if (expectedBookId != null && !expectedBookId.equals(backup.bookId())) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                        "BACKUP_BOOK_MISMATCH", "The selected backup belongs to another task book");
            }
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
        Path overwritten = backup.kind() == BackupKind.DRAFT
                // Keep the version displaced by a restore in the same in-game history.
                ? backupRoot(server, BackupKind.DRAFT).resolve(backup.bookId().getNamespace())
                    .resolve(backup.bookId().getPath())
                    .resolve((before.isBlank() ? "EMPTY" : before) + "-" + UUID.randomUUID())
                : WorkspacePaths.backups(server).resolve("restore-overwritten")
                    .resolve(backup.kind().name().toLowerCase(Locale.ROOT))
                    .resolve((before.isBlank() ? "EMPTY" : before) + "-" + UUID.randomUUID());
        boolean previousMoved = false;
        boolean stagedMovedToTarget = false;
        try {
            FileIoTrace.createDirectories(parent);
            copyTree(backup.path(), staging);
            validate(staging, backup.kind(), backup.bookId());
            if (Files.exists(target)) {
                FileIoTrace.createDirectories(overwritten.getParent());
                move(target, overwritten);
                previousMoved = true;
            }
            move(staging, target);
            stagedMovedToTarget = true;
            String restoredRevision = treeRevision(target);
            if (!restoredRevision.equals(backup.revision())) throw new IOException("Restored target failed verification");
            return AuthorOperationResult.success("BACKUP_RESTORED", "Backup restored atomically",
                    new BackupRestoreResult(describe(backup), before, restoredRevision,
                            previousMoved ? overwritten : null));
        } catch (IOException exception) {
            if (previousMoved && Files.exists(overwritten)) {
                safeDelete(target, parent);
                move(overwritten, target);
            } else if (stagedMovedToTarget) {
                // A failed staging copy leaves the original target untouched.
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
            String manifest = FileIoTrace.readString(path.resolve("draft.json"), StandardCharsets.UTF_8);
            var value = DraftManifest.decode(com.google.gson.JsonParser.parseString(manifest).getAsJsonObject());
            ResourceLocation bookId = expectedBook == null ? value.bookId() : expectedBook;
            // Legacy canonical drafts remain valid recovery points; reopening them
            // later performs the normal migration into the current representation.
            var loaded = DraftRepository.readDirectoryAllowCanonicalDrift(path, bookId);
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
                if (Files.isDirectory(path)) FileIoTrace.createDirectories(output);
                else {
                    FileIoTrace.createDirectories(output.getParent());
                    FileIoTrace.copy(path, output, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MOVE_ATTEMPTS; attempt++) {
            try {
                try {
                    FileIoTrace.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    FileIoTrace.move(source, target);
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
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) FileIoTrace.deleteIfExists(path);
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
