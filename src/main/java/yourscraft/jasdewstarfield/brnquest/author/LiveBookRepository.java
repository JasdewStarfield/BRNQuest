package yourscraft.jasdewstarfield.brnquest.author;

import yourscraft.jasdewstarfield.brnquest.diagnostic.FileIoTrace;
import com.google.gson.JsonParser;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Writes one world-local book, never publishing unrelated author drafts or workspace files. */
final class LiveBookRepository {
    private LiveBookRepository() {}

    static void save(Path pack, Path backups, String expectedRevision, QuestBookDefinition book) throws IOException {
        save(pack, backups, expectedRevision, book, book.id());
    }

    static void save(Path pack, Path backups, String expectedRevision, QuestBookDefinition book,
                     net.minecraft.resources.ResourceLocation resource) throws IOException {
        Path root = pack.toAbsolutePath().normalize();
        Path file = root.resolve("data").resolve(resource.getNamespace()).resolve("brnquest/books")
                .resolve(resource.getPath() + ".json").normalize();
        if (!file.startsWith(root)) throw new IOException("Unsafe task book path");
        // Refuse links before reading or creating directories; edits must stay inside this world's pack.
        for (Path path = file; path != null && path.startsWith(root); path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("Linked task book paths are not editable");
        }
        if (Files.exists(file)) {
            String stored = FileIoTrace.readString(file, StandardCharsets.UTF_8);
            var decoded = NativeBookJson.decode(JsonParser.parseString(stored).getAsJsonObject());
            if (!decoded.id().equals(book.id()) || !QuestBookSnapshot.of(decoded).revision().equals(expectedRevision)) {
                throw new IOException("World task book changed outside this edit session");
            }
            Path backup = backups.toAbsolutePath().normalize().resolve(book.id().getNamespace()).resolve(book.id().getPath())
                    .resolve(expectedRevision + ".json");
            rejectLinks(backup, backups.toAbsolutePath().normalize());
            FileIoTrace.createDirectories(backup.getParent());
            if (!Files.exists(backup)) FileIoTrace.copy(file, backup);
        }
        FileIoTrace.createDirectories(file.getParent());
        preparePack(root);
        atomicWrite(file, NativeBookJson.encode(book));
    }

    /** An empty pack can be selected before the book's atomic replacement becomes the commit point. */
    static void preparePack(Path pack) throws IOException {
        Path root = pack.toAbsolutePath().normalize();
        Path metadata = root.resolve("pack.mcmeta");
        rejectLinks(metadata, root);
        FileIoTrace.createDirectories(root);
        if (!Files.exists(metadata)) atomicWrite(metadata,
                "{\"pack\":{\"pack_format\":48,\"description\":\"BRNQuest world edits\"}}\n");
    }

    private static void rejectLinks(Path file, Path root) throws IOException {
        for (Path path = file; path != null && path.startsWith(root); path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("Linked world edit paths are not editable");
        }
    }

    /** A failed replacement leaves the old complete file in place; no delete-then-write fallback. */
    private static void atomicWrite(Path target, String content) throws IOException {
        Path temporary = FileIoTrace.createTempFile(target.getParent(), ".brnquest-", ".tmp");
        try {
            FileIoTrace.run("forced-write live-book", null, temporary, () -> {
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    ByteBuffer bytes = StandardCharsets.UTF_8.encode(content);
                    while (bytes.hasRemaining()) channel.write(bytes);
                    channel.force(true);
                }
                return null;
            });
            FileIoTrace.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { FileIoTrace.deleteIfExists(temporary); }
    }
}
