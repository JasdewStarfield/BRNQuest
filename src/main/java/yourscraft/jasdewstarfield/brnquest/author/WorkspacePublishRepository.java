package yourscraft.jasdewstarfield.brnquest.author;

import yourscraft.jasdewstarfield.brnquest.diagnostic.FileIoTrace;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspacePaths;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.UUID;

/** Atomic writer for publishing a single draft while preserving the rest of the workspace pack. */
final class WorkspacePublishRepository {
    private static final int MOVE_ATTEMPTS = 4;
    private static final long MOVE_RETRY_MILLIS = 25L;
    private static final String PACK_METADATA = "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"BRNQuest author workspace\"\n  }\n}\n";
    private final TransactionHook transactionHook;
    private final DirectoryMove directoryMove;

    WorkspacePublishRepository() {
        this(stage -> {}, WorkspacePublishRepository::moveOnce);
    }

    WorkspacePublishRepository(TransactionHook transactionHook) {
        this(transactionHook, WorkspacePublishRepository::moveOnce);
    }

    WorkspacePublishRepository(TransactionHook transactionHook, DirectoryMove directoryMove) {
        this.transactionHook = transactionHook;
        this.directoryMove = directoryMove;
    }

    AuthorOperationResult<DraftPublishResult> publish(MinecraftServer server, DraftSnapshot draft,
                                                       String expectedWorkspaceRevision) {
        return publish(WorkspacePaths.workspace(server), WorkspacePaths.backups(server), draft,
                expectedWorkspaceRevision);
    }

    AuthorOperationResult<DraftPublishResult> publish(Path workspace, Path backupsRoot, DraftSnapshot draft,
                                                       String expectedWorkspaceRevision) {
        try {
            WorkspaceState current = inspect(workspace, draft.book().id());
            if (!current.revision().equals(expectedWorkspaceRevision)) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                        "WORKSPACE_CHANGED", "Expected workspace " + expectedWorkspaceRevision
                                + " but disk has " + current.revision());
            }
            String encoded = NativeBookJson.encode(draft.book());
            if (current.bookFile() != null && FileIoTrace.readString(current.bookFile(), StandardCharsets.UTF_8).equals(encoded)) {
                return AuthorOperationResult.noChange("WORKSPACE_ALREADY_PUBLISHED",
                        "Workspace already contains this draft", basicResult(draft, current.revision(), null));
            }

            Path root = workspace.toAbsolutePath().normalize();
            Path parent = root.getParent();
            Path staging = parent.resolve(".workspace.staging-" + UUID.randomUUID());
            Path backup = backupsRoot.toAbsolutePath().normalize().resolve("workspace")
                    .resolve((current.revision().isBlank() ? "EMPTY" : current.revision()) + "-" + UUID.randomUUID());
            boolean previousMoved = false;
            boolean emptyScaffoldRemoved = false;
            boolean activated = false;
            try {
                FileIoTrace.createDirectories(parent);
                if (current.packExists()) copyTree(root, staging);
                else {
                    FileIoTrace.createDirectories(staging);
                    writeForced(staging.resolve("pack.mcmeta"), PACK_METADATA);
                }
                Path stagedBook = bookPath(staging, draft.book().id());
                FileIoTrace.createDirectories(stagedBook.getParent());
                FileIoTrace.deleteIfExists(stagedBook);
                writeForced(stagedBook, encoded);
                verifyBook(stagedBook, draft);
                transactionHook.checkpoint(TransactionStage.STAGING_WRITTEN);

                if (current.packExists()) {
                    FileIoTrace.createDirectories(backup.getParent());
                    move(root, backup);
                    previousMoved = true;
                    transactionHook.checkpoint(TransactionStage.BACKUP_MOVED);
                } else if (Files.isDirectory(root)) {
                    // ensureAuthorDirectories creates an intentionally empty workspace
                    // scaffold; it contains no author data worth backing up.
                    Files.delete(root);
                    emptyScaffoldRemoved = true;
                }
                move(staging, root);
                activated = true;
                transactionHook.checkpoint(TransactionStage.ACTIVATED);
                return AuthorOperationResult.success("DRAFT_PUBLISHED", "Draft published to author workspace",
                        basicResult(draft, current.revision(), previousMoved ? backup : null));
            } catch (Exception exception) {
                if (previousMoved && Files.exists(backup)) {
                    try {
                        safeDelete(root, parent);
                        move(backup, root);
                    } catch (IOException restoreFailure) {
                        exception.addSuppressed(restoreFailure);
                    }
                } else if (emptyScaffoldRemoved) {
                    try {
                        if (activated) safeDelete(root, parent);
                        FileIoTrace.createDirectories(root);
                    } catch (IOException restoreFailure) {
                        exception.addSuppressed(restoreFailure);
                    }
                } else if (activated) {
                    safeDelete(root, parent);
                }
                safeDelete(staging, parent);
                return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE,
                        "WORKSPACE_PUBLISH_FAILED", exception.getMessage());
            }
        } catch (Exception exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE,
                    "WORKSPACE_READ_FAILED", exception.getMessage());
        }
    }

    private static WorkspaceState inspect(Path workspace, ResourceLocation bookId) throws IOException {
        Path root = workspace.toAbsolutePath().normalize();
        boolean packExists = Files.isRegularFile(root.resolve("pack.mcmeta"));
        if (!packExists) {
            if (Files.isDirectory(root)) {
                try (var entries = Files.list(root)) {
                    if (entries.findAny().isPresent()) throw new IOException("Workspace is non-empty but has no pack.mcmeta");
                }
            }
            return new WorkspaceState(false, "", null);
        }
        Path file = bookPath(root, bookId);
        if (!Files.isRegularFile(file)) return new WorkspaceState(true, "", null);
        var book = NativeBookJson.decode(JsonParser.parseString(
                FileIoTrace.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        if (!book.id().equals(bookId)) throw new IOException("Workspace book path and content identify different books");
        return new WorkspaceState(true, DraftSnapshot.from(book, DraftOrigin.WORKSPACE,
                yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot.of(book).revision()).draftRevision(), file);
    }

    private static DraftPublishResult basicResult(DraftSnapshot draft, String previousRevision, Path backup) {
        DraftSnapshot published = DraftSnapshot.from(draft.book(), DraftOrigin.WORKSPACE, draft.draftRevision());
        return new DraftPublishResult(published, previousRevision, backup, null, java.util.List.of());
    }

    private static Path bookPath(Path workspace, ResourceLocation bookId) {
        Path root = workspace.toAbsolutePath().normalize();
        Path target = root.resolve("data").resolve(bookId.getNamespace()).resolve("brnquest/books")
                .resolve(bookId.getPath() + ".json").normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Unsafe workspace book path");
        return target;
    }

    private static void verifyBook(Path file, DraftSnapshot expected) throws IOException {
        var decoded = NativeBookJson.decode(JsonParser.parseString(
                FileIoTrace.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        // Group and chapter lists are canonically sorted by the encoder. Comparing record
        // list order would reject an otherwise lossless publish when equal-order objects
        // were created in a different session order, so verify the canonical bytes instead.
        if (!NativeBookJson.encode(decoded).equals(NativeBookJson.encode(expected.book()))) {
            throw new IOException("Staged workspace book failed verification");
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path output = target.resolve(source.relativize(path)).normalize();
                if (!output.startsWith(target)) throw new IOException("Workspace path escaped staging directory");
                if (Files.isDirectory(path)) FileIoTrace.createDirectories(output);
                else {
                    FileIoTrace.createDirectories(output.getParent());
                    FileIoTrace.copy(path, output, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void writeForced(Path file, String content) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private void move(Path source, Path target) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MOVE_ATTEMPTS; attempt++) {
            try {
                directoryMove.move(source, target);
                return;
            } catch (IOException exception) {
                // Windows can briefly retain a directory handle after copy/verification. A short,
                // bounded retry preserves the atomic transaction without asking authors to publish twice.
                lastFailure = exception;
                if (attempt == MOVE_ATTEMPTS) break;
                try {
                    Thread.sleep(MOVE_RETRY_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Workspace move retry was interrupted", exception);
                }
            }
        }
        throw new IOException("Workspace move failed after " + MOVE_ATTEMPTS + " attempts ["
                + lastFailure.getClass().getSimpleName() + "]: " + lastFailure.getMessage(), lastFailure);
    }

    private static void moveOnce(Path source, Path target) throws IOException {
        try {
            FileIoTrace.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            FileIoTrace.move(source, target);
        }
    }

    private static void safeDelete(Path target, Path allowedParent) {
        Path resolved = target.toAbsolutePath().normalize();
        Path parent = allowedParent.toAbsolutePath().normalize();
        if (!resolved.startsWith(parent) || !Files.exists(resolved)) return;
        try (var paths = Files.walk(resolved)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) FileIoTrace.deleteIfExists(path);
        } catch (IOException ignored) {
            // A uniquely named staging directory is inert and can be inspected later.
        }
    }

    private record WorkspaceState(boolean packExists, String revision, Path bookFile) {}
    enum TransactionStage { STAGING_WRITTEN, BACKUP_MOVED, ACTIVATED }

    @FunctionalInterface
    interface TransactionHook {
        void checkpoint(TransactionStage stage) throws IOException;
    }

    @FunctionalInterface
    interface DirectoryMove {
        void move(Path source, Path target) throws IOException;
    }
}
