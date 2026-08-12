package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.regex.Pattern;

/** Applies path containment and non-overwrite rules around the pure v13 importer. */
public final class FtbImportService {
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[a-zA-Z0-9._-]+");

    public ImportExecution execute(MinecraftServer server, String sourceName, String namespace, String bookId, boolean dryRun) throws IOException {
        requireSegment(sourceName, "source");
        requireSegment(namespace, "namespace");
        requireSegment(bookId, "book_id");
        Path importRoot = server.getServerDirectory().resolve("brnquest-import").toAbsolutePath().normalize();
        Path source = importRoot.resolve(sourceName).normalize();
        if (!source.startsWith(importRoot) || !Files.isDirectory(source) || Files.isSymbolicLink(source)) {
            throw new IOException("Import source is outside the allowed root or does not exist: " + sourceName);
        }
        validateSourceTree(importRoot, source);
        FtbImportResult result = new FtbV13Importer().importBook(source, namespace.toLowerCase(Locale.ROOT), bookId.toLowerCase(Locale.ROOT));
        validateItems(server, result);
        String json = NativeBookJson.encode(result.book());
        if (!dryRun && !result.report().hasFatal()) write(server, namespace, bookId, json, result);
        return new ImportExecution(result, json, dryRun);
    }

    private void write(MinecraftServer server, String namespace, String bookId, String json, FtbImportResult result) throws IOException {
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path pack = world.resolve("datapacks/brnquest-import-" + namespace + "-" + bookId).normalize();
        if (!pack.startsWith(world) || Files.exists(pack)) throw new FileAlreadyExistsException(pack.toString());
        Path bookFile = pack.resolve("data").resolve(namespace).resolve("brnquest/books").resolve(bookId + ".json");
        Files.createDirectories(bookFile.getParent());
        Files.writeString(pack.resolve("pack.mcmeta"), "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"BRNQuest deterministic import\"\n  }\n}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(bookFile, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        // Schema 1 keeps an assembled book for atomic loading and emits canonical standalone
        // resources so pack authors can edit chapter boundaries without reverse engineering it.
        var root = JsonParser.parseString(json).getAsJsonObject();
        var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        for (var element : root.getAsJsonArray("chapter_groups")) {
            var value = element.getAsJsonObject();
            String pathName = ResourceName.path(value.get("id").getAsString());
            Path file = pack.resolve("data").resolve(namespace).resolve("brnquest/chapter_groups").resolve(pathName + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(value) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        for (var element : root.getAsJsonArray("chapters")) {
            var value = element.getAsJsonObject();
            String pathName = ResourceName.path(value.get("id").getAsString());
            Path file = pack.resolve("data").resolve(namespace).resolve("brnquest/chapters").resolve(pathName + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(value) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        Path reports = world.resolve("brnquest-reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("import-" + namespace + "-" + bookId + ".json"), result.report().toJson() + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void requireSegment(String value, String name) {
        if (!SAFE_SEGMENT.matcher(value).matches() || value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("Unsafe " + name + ": " + value);
        }
    }

    private void validateSourceTree(Path importRoot, Path source) throws IOException {
        Path realRoot = importRoot.toRealPath();
        Path realSource = source.toRealPath();
        if (!realSource.startsWith(realRoot)) throw new IOException("Import source escapes the allowed root");
        // Files.walk does not follow directory links by default. Rejecting every
        // link also prevents a linked individual SNBT file from escaping later.
        try (var paths = Files.walk(source)) {
            if (paths.anyMatch(Files::isSymbolicLink)) {
                throw new IOException("Symbolic links are not allowed in import sources");
            }
        }
    }

    private void validateItems(MinecraftServer server, FtbImportResult result) {
        for (QuestDefinition quest : result.book().quests()) {
            quest.tasks().stream().filter(task -> task.typeId().getPath().equals("item"))
                    .forEach(task -> validateItem(server, result, task.config().get("item"), task.id().toString(), "task"));
            quest.rewards().stream().filter(reward -> reward.typeId().getPath().equals("item"))
                    .forEach(reward -> validateItem(server, result, reward.config().get("item"), reward.id().toString(), "reward"));
        }
    }

    private void validateItem(MinecraftServer server, FtbImportResult result, String snbt, String objectId, String kind) {
        if (snbt == null || snbt.isBlank()) {
            addItemDiagnostic(result, Diagnostic.Severity.ERROR, objectId, kind, "Item definition is missing");
            return;
        }
        try {
            var tag = TagParser.parseTag(snbt);
            ResourceLocation itemId = ResourceLocation.tryParse(tag.getString("id"));
            if (itemId == null) {
                addItemDiagnostic(result, Diagnostic.Severity.ERROR, objectId, kind,
                        "Item definition has no valid resource location");
                return;
            }
            var itemKey = ResourceKey.create(Registries.ITEM, itemId);
            if (server.registryAccess().lookupOrThrow(Registries.ITEM).get(itemKey).isEmpty()) {
                // A syntactically valid stack from an optional mod remains in the imported book.
                // It becomes usable automatically when that mod is installed, so this is not data loss.
                addItemDiagnostic(result, Diagnostic.Severity.WARN, objectId, kind,
                        "Item " + itemId + " is not registered on this server; the imported definition was preserved");
                return;
            }
            ItemStack stack = ItemStack.parseOptional(server.registryAccess(), tag);
            if (!stack.isEmpty()) return;
            addItemDiagnostic(result, Diagnostic.Severity.ERROR, objectId, kind,
                    "Registered item definition resolved to an empty stack");
        } catch (Exception exception) {
            // The structured diagnostic deliberately avoids leaking an implementation exception.
            addItemDiagnostic(result, Diagnostic.Severity.ERROR, objectId, kind,
                    "Item definition cannot be parsed");
        }
    }

    private void addItemDiagnostic(FtbImportResult result, Diagnostic.Severity severity, String objectId, String kind, String message) {
        result.report().add(new Diagnostic(severity, "BQF-103", "", kind + ".item", objectId, message));
    }

    public record ImportExecution(FtbImportResult result, String nativeJson, boolean dryRun) {}
    private static final class ResourceName {
        private static String path(String id) { return id.substring(id.indexOf(':') + 1); }
    }
}
