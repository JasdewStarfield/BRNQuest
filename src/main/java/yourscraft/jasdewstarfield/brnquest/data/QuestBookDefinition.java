package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;

/** Complete immutable schema-1 task-book definition before runtime indexing. */
public record QuestBookDefinition(ResourceLocation id, int schemaVersion, String title,
                                  List<ChapterGroupDefinition> chapterGroups,
                                  List<ChapterDefinition> chapters,
                                  Map<String, ResourceLocation> legacyIds,
                                  BookLocalization localization,
                                  Map<String, String> extensions) {
    public QuestBookDefinition(ResourceLocation id, int schemaVersion, String title,
                               List<ChapterGroupDefinition> chapterGroups,
                               List<ChapterDefinition> chapters,
                               Map<String, ResourceLocation> legacyIds) {
        this(id, schemaVersion, title, chapterGroups, chapters, legacyIds, BookLocalization.EMPTY, Map.of());
    }

    public QuestBookDefinition {
        chapterGroups = List.copyOf(chapterGroups);
        chapters = List.copyOf(chapters);
        legacyIds = Map.copyOf(legacyIds);
        localization = localization == null ? BookLocalization.EMPTY : localization;
        extensions = Map.copyOf(extensions);
    }

    public List<QuestDefinition> quests() { return chapters.stream().flatMap(c -> c.quests().stream()).toList(); }
}
