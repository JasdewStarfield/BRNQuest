package yourscraft.jasdewstarfield.brnquest.workspace;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Central path contract for the global author workspace and per-world deployment. */
public final class WorkspacePaths {
    public static final String PACK_DIRECTORY = "brnquest-workspace";
    public static final String PACK_ID = "file/" + PACK_DIRECTORY;

    private WorkspacePaths() {}

    public static Path root(MinecraftServer server) {
        return server.getServerDirectory().resolve("config/brnquest").toAbsolutePath().normalize();
    }

    public static Path workspace(MinecraftServer server) {
        return root(server).resolve("workspace");
    }

    public static Path imports(MinecraftServer server) {
        return root(server).resolve("imports");
    }

    public static Path reports(MinecraftServer server) {
        return root(server).resolve("reports");
    }

    /** Append-only machine-readable history for authoring operations. */
    public static Path authorAudit(MinecraftServer server) {
        return reports(server).resolve("author-audit.jsonl");
    }

    /** Server-local author drafts are deliberately outside every data-pack search path. */
    public static Path drafts(MinecraftServer server) {
        return root(server).resolve("drafts");
    }

    /** Backups stay outside workspace and datapack discovery paths. */
    public static Path backups(MinecraftServer server) {
        return root(server).resolve("backups");
    }

    /** Creates the stable author-facing directories without manufacturing an invalid empty data pack. */
    public static void ensureAuthorDirectories(MinecraftServer server) throws IOException {
        ensureAuthorDirectories(root(server));
    }

    static void ensureAuthorDirectories(Path root) throws IOException {
        Files.createDirectories(root.resolve("imports"));
        Files.createDirectories(root.resolve("workspace"));
        Files.createDirectories(root.resolve("reports"));
        Files.createDirectories(root.resolve("drafts"));
        Files.createDirectories(root.resolve("backups"));
    }

    public static Path deployed(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                .resolve("datapacks").resolve(PACK_DIRECTORY);
    }
}
