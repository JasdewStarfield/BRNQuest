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
                                  Map<String, String> extensions, QuestCreationDefaults questDefaults, BookSettings settings) {
    /** Preserve the previous constructor and its historical policy defaults. */
    public QuestBookDefinition(ResourceLocation id, int schemaVersion, String title,
            List<ChapterGroupDefinition> groups, List<ChapterDefinition> chapters,
            Map<String, ResourceLocation> legacyIds, BookLocalization localization,
            Map<String, String> extensions, QuestCreationDefaults defaults) {
        this(id, schemaVersion, title, groups, chapters, legacyIds, localization, extensions, defaults, BookSettings.DEFAULT);
    }
    /** Compatibility constructor: historical definitions contain no creation template. */
    public QuestBookDefinition(ResourceLocation id, int schemaVersion, String title,
            List<ChapterGroupDefinition> chapterGroups, List<ChapterDefinition> chapters,
            Map<String, ResourceLocation> legacyIds, BookLocalization localization, Map<String, String> extensions) {
        this(id, schemaVersion, title, chapterGroups, chapters, legacyIds, localization, extensions, QuestCreationDefaults.EMPTY);
    }

    public QuestBookDefinition(ResourceLocation id, int schemaVersion, String title,
                               List<ChapterGroupDefinition> chapterGroups,
                               List<ChapterDefinition> chapters,
                               Map<String, ResourceLocation> legacyIds) {
        this(id, schemaVersion, title, chapterGroups, chapters, legacyIds, BookLocalization.EMPTY, Map.of());
    }

    public QuestBookDefinition {
        settings = settings == null ? BookSettings.DEFAULT : settings;
        chapterGroups = List.copyOf(chapterGroups);
        chapters = List.copyOf(chapters);
        legacyIds = Map.copyOf(legacyIds);
        localization = localization == null ? BookLocalization.EMPTY : localization;
        extensions = Map.copyOf(extensions);
        questDefaults = questDefaults == null ? QuestCreationDefaults.EMPTY : questDefaults;
    }

    public List<QuestDefinition> quests() { return chapters.stream().flatMap(c -> c.quests().stream()).toList(); }
}
