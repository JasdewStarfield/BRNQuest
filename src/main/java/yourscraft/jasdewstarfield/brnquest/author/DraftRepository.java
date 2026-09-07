package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspacePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Reads and creates server-local drafts without exposing them as Minecraft resources. */
public final class DraftRepository {
    private static final String BOOK_FILE = "book.json";
    private static final String MANIFEST_FILE = "draft.json";
    private final TransactionHook transactionHook;

    public DraftRepository() {
        this(stage -> {});
    }

    DraftRepository(TransactionHook transactionHook) {
        this.transactionHook = transactionHook;
    }

    public AuthorOperationResult<DraftSnapshot> create(MinecraftServer server, DraftSnapshot draft) {
        return create(WorkspacePaths.drafts(server), draft);
    }

    AuthorOperationResult<DraftSnapshot> create(Path draftsRoot, DraftSnapshot draft) {
        Path target = draftDirectory(draftsRoot, draft.book().id());
        if (Files.exists(target)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "DRAFT_EXISTS",
                    "Draft already exists for " + draft.book().id());
        }
        Path parent = target.getParent();
        Path staging = parent.resolve("." + target.getFileName() + ".staging-" + UUID.randomUUID());
        try {
            Files.createDirectories(parent);
            writeDirectory(staging, draft);
            move(staging, target);
            return AuthorOperationResult.success("DRAFT_CREATED", "Draft created", draft);
        } catch (IOException exception) {
            safeDelete(staging, draftsRoot);
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "DRAFT_WRITE_FAILED",
                    exception.getMessage());
        }
    }

    public AuthorOperationResult<DraftSnapshot> load(MinecraftServer server, ResourceLocation bookId) {
        return loadMigrating(WorkspacePaths.drafts(server), WorkspacePaths.backups(server), bookId);
    }

    /**
     * Loads a draft for a new edit lease and restores the workspace baseline when
     * the same content was published by an earlier session. The exact revision
     * equality makes this a metadata repair, never an implicit content merge.
     */
    public AuthorOperationResult<DraftSnapshot> loadForEditing(MinecraftServer server, ResourceLocation bookId) {
        return loadForEditing(WorkspacePaths.drafts(server), WorkspacePaths.workspace(server),
                WorkspacePaths.backups(server), bookId);
    }

    AuthorOperationResult<DraftSnapshot> loadForEditing(Path draftsRoot, Path workspaceRoot,
                                                         ResourceLocation bookId) {
        return loadForEditing(draftsRoot, workspaceRoot, draftsRoot.resolveSibling("backups"), bookId);
    }

    AuthorOperationResult<DraftSnapshot> loadForEditing(Path draftsRoot, Path workspaceRoot, Path backupsRoot,
                                                         ResourceLocation bookId) {
        AuthorOperationResult<DraftSnapshot> loaded = loadMigrating(draftsRoot, backupsRoot, bookId);
        if (!loaded.success()) return loaded;
        DraftSnapshot draft = loaded.value();
        AuthorOperationResult<DraftSnapshot> workspace = readWorkspace(workspaceRoot, bookId);
        if (!workspace.success()) {
            return workspace.status() == AuthorOperationResult.Status.NOT_FOUND ? loaded : failureLike(workspace);
        }
        if (!draft.draftRevision().equals(workspace.value().draftRevision())) return loaded;
        if (draft.origin() == DraftOrigin.WORKSPACE
                && draft.baseRevision().equals(workspace.value().draftRevision())) return loaded;
        // This also advances a previously published WORKSPACE draft after a later
        // publish: its manifest base names the prior workspace while its content
        // and the current workspace already share the new revision.
        DraftSnapshot rebased = DraftSnapshot.from(draft.book(), DraftOrigin.WORKSPACE, draft.draftRevision());
        return AuthorOperationResult.success("DRAFT_WORKSPACE_BASELINE_RESTORED",
                "Draft content matches workspace; restored its published baseline", rebased);
    }

    /** Lists only drafts that can be decoded and safely opened by the author service. */
    public AuthorOperationResult<List<DraftCatalogEntry>> list(MinecraftServer server) {
        return list(WorkspacePaths.drafts(server));
    }

    AuthorOperationResult<List<DraftCatalogEntry>> list(Path draftsRoot) {
        Path root = draftsRoot.toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            return AuthorOperationResult.success("DRAFT_CATALOG_EMPTY", "No server drafts exist", List.of());
        }
        try (var paths = Files.walk(root)) {
            List<DraftCatalogEntry> entries = paths
                    .filter(path -> path.getFileName().toString().equals(MANIFEST_FILE))
                    .map(Path::getParent)
                    .map(directory -> catalogEntry(root, directory))
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(entry -> entry.bookId().toString()))
                    .toList();
            return AuthorOperationResult.success("DRAFT_CATALOG", "Server draft catalog loaded", entries);
        } catch (IOException exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE,
                    "DRAFT_CATALOG_FAILED", exception.getMessage());
        }
    }

    AuthorOperationResult<DraftSnapshot> load(Path draftsRoot, ResourceLocation bookId) {
        Path directory = draftDirectory(draftsRoot, bookId);
        if (!Files.isDirectory(directory)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, "DRAFT_NOT_FOUND",
                    "No draft exists for " + bookId);
        }
        try {
            return readDirectory(directory, bookId);
        } catch (Exception exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "DRAFT_READ_FAILED",
                    exception.getMessage());
        }
    }

    /**
     * Migrates a draft only when its original UTF-8 bytes still match the stored
     * revision. This distinguishes encoder evolution from external file edits.
     */
    private AuthorOperationResult<DraftSnapshot> loadMigrating(Path draftsRoot, Path backupsRoot,
                                                                ResourceLocation bookId) {
        AuthorOperationResult<DraftSnapshot> loaded = load(draftsRoot, bookId);
        if (loaded.success() || !"DRAFT_REVISION_MISMATCH".equals(loaded.code())) return loaded;
        Path target = draftDirectory(draftsRoot, bookId);
        AuthorOperationResult<DraftSnapshot> compatible = readDirectoryAllowCanonicalDrift(target, bookId);
        if (!compatible.success() || !"DRAFT_CANONICAL_MIGRATION_REQUIRED".equals(compatible.code())) {
            return loaded;
        }
        DraftSnapshot migrated = compatible.value();
        Path parent = target.getParent();
        Path staging = parent.resolve("." + target.getFileName() + ".migration-staging-" + UUID.randomUUID());
        Path backup = backupDirectory(backupsRoot, bookId, storedRevision(target));
        boolean backupMoved = false;
        try {
            Files.createDirectories(parent);
            writeDirectory(staging, migrated);
            AuthorOperationResult<DraftSnapshot> staged = readDirectory(staging, bookId);
            if (!staged.success() || !staged.value().draftRevision().equals(migrated.draftRevision())) {
                throw new IOException("Migrated draft failed verification");
            }
            Files.createDirectories(backup.getParent());
            move(target, backup);
            backupMoved = true;
            move(staging, target);
            return AuthorOperationResult.success("DRAFT_CANONICAL_FORMAT_MIGRATED",
                    "Draft serialization was upgraded; the original was backed up", migrated);
        } catch (Exception exception) {
            if (backupMoved && Files.exists(backup)) {
                try {
                    safeDelete(target, draftsRoot);
                    move(backup, target);
                } catch (IOException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            safeDelete(staging, draftsRoot);
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE,
                    "DRAFT_MIGRATION_FAILED", exception.getMessage());
        }
    }

    public AuthorOperationResult<DraftSaveResult> save(MinecraftServer server, DraftSnapshot draft,
                                                        String expectedDiskRevision) {
        return save(WorkspacePaths.drafts(server), WorkspacePaths.backups(server), draft, expectedDiskRevision);
    }

    /** Replaces the selected current draft while retaining its former directory as a restorable version. */
    public AuthorOperationResult<DraftSnapshot> replace(MinecraftServer server, DraftSnapshot replacement,
                                                         String expectedDiskRevision) {
        return replace(WorkspacePaths.drafts(server), WorkspacePaths.backups(server), replacement,
                expectedDiskRevision);
    }

    AuthorOperationResult<DraftSnapshot> replace(Path draftsRoot, Path backupsRoot, DraftSnapshot replacement,
                                                  String expectedDiskRevision) {
        String expected = expectedDiskRevision == null ? "" : expectedDiskRevision;
        Path target = draftDirectory(draftsRoot, replacement.book().id());
        if (!Files.exists(target)) {
            if (!expected.isBlank()) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                        "DRAFT_SELECTION_STALE", "The selected draft no longer exists");
            }
            AuthorOperationResult<DraftSnapshot> created = create(draftsRoot, replacement);
            return created.success()
                    ? AuthorOperationResult.success("DRAFT_VERSION_CREATED", "Draft created from current book",
                    created.value()) : created;
        }
        AuthorOperationResult<DraftSnapshot> loaded = loadMigrating(draftsRoot, backupsRoot, replacement.book().id());
        if (!loaded.success()) return loaded;
        if (expected.isBlank() || !loaded.value().draftRevision().equals(expected)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                    "DRAFT_SELECTION_STALE", "The selected draft changed; review it before replacing it");
        }
        AuthorOperationResult<DraftSaveResult> saved = save(draftsRoot, backupsRoot, replacement, expected);
        if (!saved.success()) return failureLike(saved);
        return saved.status() == AuthorOperationResult.Status.NO_CHANGE
                ? AuthorOperationResult.noChange("DRAFT_VERSION_ALREADY_CURRENT",
                "Draft already matches the current book", replacement)
                : AuthorOperationResult.success("DRAFT_VERSION_CREATED",
                "Previous draft version backed up; current book is now the draft", replacement);
    }

    AuthorOperationResult<DraftSaveResult> save(Path draftsRoot, Path backupsRoot, DraftSnapshot draft,
                                                 String expectedDiskRevision) {
        AuthorOperationResult<DraftSnapshot> loaded = load(draftsRoot, draft.book().id());
        if (!loaded.success()) return failureLike(loaded);
        DraftSnapshot disk = loaded.value();
        if (!disk.draftRevision().equals(expectedDiskRevision)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "DISK_DRAFT_CHANGED",
                    "Expected saved draft " + expectedDiskRevision + " but disk has " + disk.draftRevision());
        }
        String expectedBook = NativeBookJson.encode(draft.book());
        String expectedManifest = draft.manifest().encode();
        Path target = draftDirectory(draftsRoot, draft.book().id());
        try {
            if (Files.readString(target.resolve(BOOK_FILE), StandardCharsets.UTF_8).equals(expectedBook)
                    && Files.readString(target.resolve(MANIFEST_FILE), StandardCharsets.UTF_8).equals(expectedManifest)) {
                return AuthorOperationResult.noChange("DRAFT_ALREADY_SAVED", "Draft files already match the session",
                        new DraftSaveResult(draft, disk.draftRevision(), null));
            }
        } catch (IOException exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "DRAFT_READ_FAILED",
                    exception.getMessage());
        }

        Path parent = target.getParent();
        Path staging = parent.resolve("." + target.getFileName() + ".staging-" + UUID.randomUUID());
        Path backup = backupDirectory(backupsRoot, draft.book().id(), disk.draftRevision());
        boolean backupMoved = false;
        try {
            Files.createDirectories(parent);
            writeDirectory(staging, draft);
            transactionHook.checkpoint(TransactionStage.STAGING_WRITTEN);
            AuthorOperationResult<DraftSnapshot> staged = readDirectory(staging, draft.book().id());
            if (!staged.success() || !staged.value().draftRevision().equals(draft.draftRevision())) {
                throw new IOException("Staged draft failed verification");
            }
            Files.createDirectories(backup.getParent());
            move(target, backup);
            backupMoved = true;
            transactionHook.checkpoint(TransactionStage.BACKUP_MOVED);
            move(staging, target);
            transactionHook.checkpoint(TransactionStage.ACTIVATED);
            return AuthorOperationResult.success("DRAFT_SAVED", "Draft saved atomically",
                    new DraftSaveResult(draft, disk.draftRevision(), backup));
        } catch (Exception exception) {
            if (backupMoved && Files.exists(backup)) {
                try {
                    safeDelete(target, draftsRoot);
                    move(backup, target);
                } catch (IOException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            safeDelete(staging, draftsRoot);
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "DRAFT_SAVE_FAILED",
                    exception.getMessage());
        }
    }

    public AuthorOperationResult<DraftSnapshot> readWorkspace(MinecraftServer server, ResourceLocation bookId) {
        return readWorkspace(WorkspacePaths.workspace(server), bookId);
    }

    AuthorOperationResult<DraftSnapshot> readWorkspace(Path workspaceRoot, ResourceLocation bookId) {
        Path workspace = workspaceRoot.toAbsolutePath().normalize();
        Path file = workspace.resolve("data").resolve(bookId.getNamespace()).resolve("brnquest/books")
                .resolve(bookId.getPath() + ".json").normalize();
        if (!file.startsWith(workspace)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, "UNSAFE_BOOK_PATH",
                    "Book ID escaped the workspace");
        }
        if (!Files.isRegularFile(file)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, "WORKSPACE_BOOK_NOT_FOUND",
                    "Workspace has no book " + bookId);
        }
        try {
            var book = NativeBookJson.decode(JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
            if (!book.id().equals(bookId)) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "WORKSPACE_ID_MISMATCH",
                        "Workspace file contains " + book.id());
            }
            return AuthorOperationResult.success("WORKSPACE_BOOK_LOADED", "Workspace book loaded",
                    DraftSnapshot.from(book, DraftOrigin.WORKSPACE,
                            yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot.of(book).revision()));
        } catch (Exception exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "WORKSPACE_READ_FAILED",
                    exception.getMessage());
        }
    }

    static Path draftDirectory(Path draftsRoot, ResourceLocation bookId) {
        Path root = draftsRoot.toAbsolutePath().normalize();
        Path target = root.resolve(bookId.getNamespace()).resolve(bookId.getPath()).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Unsafe draft book path");
        return target;
    }

    private static AuthorOperationResult<DraftSnapshot> readDirectory(Path directory, ResourceLocation bookId) {
        try {
            String bookJson = Files.readString(directory.resolve(BOOK_FILE), StandardCharsets.UTF_8);
            String manifestJson = Files.readString(directory.resolve(MANIFEST_FILE), StandardCharsets.UTF_8);
            var book = NativeBookJson.decode(JsonParser.parseString(bookJson).getAsJsonObject());
            var manifest = DraftManifest.decode(JsonParser.parseString(manifestJson).getAsJsonObject());
            if (!book.id().equals(bookId) || !manifest.bookId().equals(bookId)) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "DRAFT_ID_MISMATCH",
                        "Draft path and content identify different books");
            }
            DraftSnapshot snapshot = DraftSnapshot.from(book, manifest.origin(), manifest.baseRevision());
            if (!snapshot.draftRevision().equals(manifest.draftRevision())) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "DRAFT_REVISION_MISMATCH",
                        "Draft content changed outside the author service");
            }
            return AuthorOperationResult.success("DRAFT_LOADED", "Draft loaded", snapshot);
        } catch (Exception exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "DRAFT_READ_FAILED",
                    exception.getMessage());
        }
    }

    /** Accepts an older canonical encoder only when the stored bytes prove they were not edited afterwards. */
    static AuthorOperationResult<DraftSnapshot> readDirectoryAllowCanonicalDrift(Path directory,
                                                                                  ResourceLocation bookId) {
        try {
            String bookJson = Files.readString(directory.resolve(BOOK_FILE), StandardCharsets.UTF_8);
            String manifestJson = Files.readString(directory.resolve(MANIFEST_FILE), StandardCharsets.UTF_8);
            var book = NativeBookJson.decode(JsonParser.parseString(bookJson).getAsJsonObject());
            var manifest = DraftManifest.decode(JsonParser.parseString(manifestJson).getAsJsonObject());
            if (!book.id().equals(bookId) || !manifest.bookId().equals(bookId)) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "DRAFT_ID_MISMATCH",
                        "Draft path and content identify different books");
            }
            DraftSnapshot snapshot = DraftSnapshot.from(book, manifest.origin(), manifest.baseRevision());
            if (snapshot.draftRevision().equals(manifest.draftRevision())) {
                return AuthorOperationResult.success("DRAFT_LOADED", "Draft loaded", snapshot);
            }
            if (!sha256(bookJson).equalsIgnoreCase(manifest.draftRevision())) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                        "DRAFT_REVISION_MISMATCH", "Draft content changed outside the author service");
            }
            return AuthorOperationResult.success("DRAFT_CANONICAL_MIGRATION_REQUIRED",
                    "Draft uses an older canonical serialization", snapshot);
        } catch (Exception exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "DRAFT_READ_FAILED",
                    exception.getMessage());
        }
    }

    AuthorOperationResult<DraftSnapshot> readDirectoryForTest(Path directory, ResourceLocation bookId) {
        return readDirectory(directory, bookId);
    }

    private DraftCatalogEntry catalogEntry(Path root, Path directory) {
        Path relative = root.relativize(directory.toAbsolutePath().normalize());
        if (relative.getNameCount() < 2) return null;
        String namespace = relative.getName(0).toString();
        StringBuilder path = new StringBuilder();
        for (int index = 1; index < relative.getNameCount(); index++) {
            if (!path.isEmpty()) path.append('/');
            path.append(relative.getName(index));
        }
        ResourceLocation bookId = ResourceLocation.tryBuild(namespace, path.toString());
        if (bookId == null) return null;
        // Catalog listing stays read-only. A legacy canonical draft is displayed
        // here and migrated only when an administrator actually opens it.
        AuthorOperationResult<DraftSnapshot> loaded = readDirectoryAllowCanonicalDrift(directory, bookId);
        if (!loaded.success()) return null;
        DraftSnapshot draft = loaded.value();
        return new DraftCatalogEntry(bookId, draft.book().title(), draft.draftRevision(), draft.origin());
    }

    private static void writeDirectory(Path directory, DraftSnapshot draft) throws IOException {
        Files.createDirectories(directory);
        writeForced(directory.resolve(BOOK_FILE), NativeBookJson.encode(draft.book()));
        writeForced(directory.resolve(MANIFEST_FILE), draft.manifest().encode());
    }

    private static void writeForced(Path file, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static Path backupDirectory(Path backupsRoot, ResourceLocation bookId, String revision) {
        Path root = backupsRoot.toAbsolutePath().normalize().resolve("drafts");
        String safeRevision = revision.isBlank() ? "EMPTY" : revision;
        Path target = root.resolve(bookId.getNamespace()).resolve(bookId.getPath())
                .resolve(safeRevision + "-" + UUID.randomUUID()).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Unsafe draft backup path");
        return target;
    }

    private static String storedRevision(Path directory) {
        try {
            String manifest = Files.readString(directory.resolve(MANIFEST_FILE), StandardCharsets.UTF_8);
            return DraftManifest.decode(JsonParser.parseString(manifest).getAsJsonObject()).draftRevision();
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }

    private static <T> AuthorOperationResult<T> failureLike(AuthorOperationResult<?> source) {
        return AuthorOperationResult.failure(source.status(), source.code(), source.message());
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private static void safeDelete(Path target, Path draftsRoot) {
        Path root = draftsRoot.toAbsolutePath().normalize();
        Path resolved = target.toAbsolutePath().normalize();
        if (!resolved.startsWith(root) || !Files.exists(resolved)) return;
        try (var paths = Files.walk(resolved)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The original write error remains the useful failure; a uniquely named
            // staging directory is safe to inspect or clean on a later startup.
        }
    }

    enum TransactionStage { STAGING_WRITTEN, BACKUP_MOVED, ACTIVATED }

    @FunctionalInterface
    interface TransactionHook {
        void checkpoint(TransactionStage stage) throws IOException;
    }
}
