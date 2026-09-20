package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import net.minecraft.server.MinecraftServer;
import yourscraft.jasdewstarfield.brnquest.author.AuthorOperationResult;
import yourscraft.jasdewstarfield.brnquest.author.DraftOrigin;
import yourscraft.jasdewstarfield.brnquest.author.DraftRepository;
import yourscraft.jasdewstarfield.brnquest.author.DraftSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspacePaths;

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
        // Both the modern workspace flow and the compatibility world flow share one visible inbox.
        Path importRoot = WorkspacePaths.imports(server);
        Path source = importRoot.resolve(sourceName).normalize();
        if (!source.startsWith(importRoot) || !Files.isDirectory(source) || Files.isSymbolicLink(source)) {
            throw new IOException("Import source is outside the allowed root or does not exist: " + sourceName);
        }
        validateSourceTree(importRoot, source);
        FtbImportResult result = withImportSource(FtbImportBackend.installed().importBook(source,
                namespace.toLowerCase(Locale.ROOT), bookId.toLowerCase(Locale.ROOT)), sourceName);
        validateItems(server, result);
        String json = NativeBookJson.encode(result.book());
        AuthorOperationResult<DraftSnapshot> draft = null;
        if (!dryRun && !result.report().hasFatal()) draft = writeDraft(server, namespace, bookId, result);
        return new ImportExecution(result, json, dryRun, ImportTarget.DRAFT, draft);
    }

    /** Keeps the safe inbox name with the draft so catalog entries can distinguish separate FTB imports. */
    static FtbImportResult withImportSource(FtbImportResult result, String sourceName) {
        var sourceBook = result.book();
        var extensions = new java.util.TreeMap<>(sourceBook.extensions());
        extensions.put("ftb.import_source", sourceName);
        var book = new yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition(sourceBook.id(),
                sourceBook.schemaVersion(), sourceBook.title(), sourceBook.chapterGroups(), sourceBook.chapters(),
                sourceBook.legacyIds(), sourceBook.localization(), extensions, sourceBook.questDefaults(), sourceBook.settings());
        return new FtbImportResult(book, result.report(), result.chapterGroupCount(), result.chapterCount(),
                result.questCount(), result.taskCount(), result.rewardCount(), result.fieldConversions());
    }

    private AuthorOperationResult<DraftSnapshot> writeDraft(MinecraftServer server, String namespace, String bookId,
                                                             FtbImportResult result) throws IOException {
        DraftSnapshot draft = DraftSnapshot.from(result.book(), DraftOrigin.IMPORT, "");
        AuthorOperationResult<DraftSnapshot> created = new DraftRepository().create(server, draft);
        writeReport(WorkspacePaths.reports(server), namespace, bookId, result);
        return created;
    }

    private void writeReport(Path reports, String namespace, String bookId, FtbImportResult result) throws IOException {
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("import-" + namespace + "-" + bookId + ".json"), result.reportJson() + "\n",
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
            quest.tasks().stream().filter(task -> TaskTypes.ITEM.equals(task.typeId()))
                    .forEach(task -> validateItem(server, result, task.config().get("item"), task.id().toString(), "task"));
            quest.rewards().stream().filter(reward -> RewardTypes.ITEM.equals(reward.typeId()))
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

    public enum ImportTarget { DRAFT }
    public record ImportExecution(FtbImportResult result, String nativeJson, boolean dryRun, ImportTarget target,
                                  AuthorOperationResult<DraftSnapshot> draftResult) {}
}
