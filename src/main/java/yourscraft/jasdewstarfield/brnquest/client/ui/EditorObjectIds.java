package yourscraft.jasdewstarfield.brnquest.client.ui;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import java.util.Set;
/** Generates unused authoring IDs while reserving historical migration aliases. */
final class EditorObjectIds {
    private EditorObjectIds() {}
    static ResourceLocation suggest(QuestBookDefinition book, String stem) {
        String namespace = book.id().getNamespace();
        Set<ResourceLocation> ids = new java.util.HashSet<>();
        book.chapterGroups().forEach(value -> ids.add(value.id()));
        book.chapters().forEach(value -> ids.add(value.id()));
        book.quests().forEach(value -> {
            ids.add(value.id());
            value.tasks().forEach(task -> ids.add(task.id()));
            value.rewards().forEach(reward -> ids.add(reward.id()));
        });
        book.legacyIds().keySet().forEach(alias -> {
            // Migration sources are retired IDs. Reusing one would make a reload move
            // the newly created entry's ledger state into the historical target.
            String raw = alias.startsWith("@task:") ? alias.substring("@task:".length())
                    : alias.startsWith("@reward:") ? alias.substring("@reward:".length()) : alias;
            ResourceLocation retired = ResourceLocation.tryParse(raw);
            if (retired != null) ids.add(retired);
        });
        // A timestamp-derived generation keeps deleted stable IDs from being silently reused.
        // Reuse would attach an old player's completion or claim ledger to a new object.
        String generation = Long.toUnsignedString(System.currentTimeMillis(), 36);
        for (int suffix = 0; suffix < 10_000; suffix++) {
            String path = stem + "_" + generation + (suffix == 0 ? "" : "_" + suffix);
            ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(namespace, path);
            if (!ids.contains(candidate)) return candidate;
        }
        return ResourceLocation.fromNamespaceAndPath(namespace, stem + "_" + java.util.UUID.randomUUID()
                .toString().replace("-", ""));
    }
}
