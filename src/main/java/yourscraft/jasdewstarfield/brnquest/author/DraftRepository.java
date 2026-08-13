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
import java.util.Comparator;
import java.util.UUID;

/** Reads and creates server-local drafts without exposing them as Minecraft resources. */
public final class DraftRepository {
    private static final String BOOK_FILE = "book.json";
    private static final String MANIFEST_FILE = "draft.json";

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
            Files.createDirectories(staging);
            Files.writeString(staging.resolve(BOOK_FILE), NativeBookJson.encode(draft.book()), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            Files.writeString(staging.resolve(MANIFEST_FILE), draft.manifest().encode(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            move(staging, target);
            return AuthorOperationResult.success("DRAFT_CREATED", "Draft created", draft);
        } catch (IOException exception) {
            safeDelete(staging, draftsRoot);
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "DRAFT_WRITE_FAILED",
                    exception.getMessage());
        }
    }

    public AuthorOperationResult<DraftSnapshot> load(MinecraftServer server, ResourceLocation bookId) {
        return load(WorkspacePaths.drafts(server), bookId);
    }

    AuthorOperationResult<DraftSnapshot> load(Path draftsRoot, ResourceLocation bookId) {
        Path directory = draftDirectory(draftsRoot, bookId);
        if (!Files.isDirectory(directory)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, "DRAFT_NOT_FOUND",
                    "No draft exists for " + bookId);
        }
        try {
            String bookJson = Files.readString(directory.resolve(BOOK_FILE), StandardCharsets.UTF_8);
            String manifestJson = Files.readString(directory.resolve(MANIFEST_FILE), StandardCharsets.UTF_8);
            var book = NativeBookJson.decode(JsonParser.parseString(bookJson).getAsJsonObject());
            var manifest = DraftManifest.decode(JsonParser.parseString(manifestJson).getAsJsonObject());
            if (!book.id().equals(bookId) || !manifest.bookId().equals(bookId)) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "DRAFT_ID_MISMATCH",
                        "Draft path and content identify different books");
            }
            DraftSnapshot snapshot = DraftSnapshot.of(book, manifest.baseRevision());
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
                    DraftSnapshot.of(book, yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot.of(book).revision()));
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
}
