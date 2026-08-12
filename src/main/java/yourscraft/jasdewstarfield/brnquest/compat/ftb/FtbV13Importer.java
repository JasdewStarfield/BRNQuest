package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Maps the observed FTB Quests v13 subset into BRNQuest's independent immutable model. */
public final class FtbV13Importer {
    private final SnbtReader reader = new SnbtReader();

    public FtbImportResult importBook(Path source, String namespace, String bookPath) {
        DiagnosticReport report = new DiagnosticReport();
        ResourceLocation bookId = ResourceLocation.fromNamespaceAndPath(namespace, bookPath);
        List<ChapterGroupDefinition> groups = new ArrayList<>();
        List<ChapterDefinition> chapters = new ArrayList<>();
        Map<String, ResourceLocation> aliases = new TreeMap<>();
        Map<String, String> translations = new HashMap<>();

        try {
            CompoundTag data = reader.read(source.resolve("data.snbt"));
            if (data.getInt("version") != 13) {
                report.add(problem(Diagnostic.Severity.FATAL, "BQF-001", "data.snbt", "version", "",
                        "Expected FTB Quests format 13, got " + data.getInt("version")));
            }
            Path lang = source.resolve("lang/zh_cn.snbt");
            if (Files.isRegularFile(lang)) readTranslations(reader.read(lang), translations);
            readGroups(reader.read(source.resolve("chapter_groups.snbt")), bookId, namespace, translations, aliases, groups);

            Path chapterDir = source.resolve("chapters");
            try (var files = Files.list(chapterDir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".snbt"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(path -> readChapter(path, bookId, namespace, translations, aliases, chapters, report));
            }
        } catch (Exception exception) {
            report.add(problem(Diagnostic.Severity.FATAL, "BQF-002", source.toString(), "", "", exception.getMessage()));
        }

        validateGraph(chapters, report);
        QuestBookDefinition book = new QuestBookDefinition(bookId, BrnQuestConstants.DATA_SCHEMA,
                translations.getOrDefault("title", bookId.toString()), groups, chapters, aliases);
        int taskCount = book.quests().stream().mapToInt(q -> q.tasks().size()).sum();
        int rewardCount = book.quests().stream().mapToInt(q -> q.rewards().size()).sum();
        return new FtbImportResult(book, report, groups.size(), chapters.size(), book.quests().size(), taskCount, rewardCount);
    }

    private void readTranslations(CompoundTag tag, Map<String, String> target) {
        for (String key : tag.getAllKeys()) target.put(key, tag.getString(key));
    }

    private void readGroups(CompoundTag root, ResourceLocation bookId, String namespace,
                            Map<String, String> translations, Map<String, ResourceLocation> aliases,
                            List<ChapterGroupDefinition> target) {
        ListTag list = root.getList("chapter_groups", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag raw = list.getCompound(index);
            String legacy = raw.getString("id");
            ResourceLocation id = remember(namespace, legacy, aliases);
            target.add(new ChapterGroupDefinition(bookId, id,
                    translations.getOrDefault("chapter_group." + legacy + ".title", legacy), index));
        }
    }

    private void readChapter(Path path, ResourceLocation bookId, String namespace,
                             Map<String, String> translations, Map<String, ResourceLocation> aliases,
                             List<ChapterDefinition> target, DiagnosticReport report) {
        try {
            CompoundTag raw = reader.read(path);
            String legacy = raw.getString("id");
            ResourceLocation chapterId = remember(namespace, legacy, aliases);
            String groupLegacy = raw.getString("group");
            ResourceLocation groupId = groupLegacy.isBlank()
                    ? ResourceLocation.fromNamespaceAndPath(namespace, "ungrouped") : remember(namespace, groupLegacy, aliases);
            List<QuestDefinition> quests = new ArrayList<>();
            ListTag questTags = raw.getList("quests", Tag.TAG_COMPOUND);
            for (int index = 0; index < questTags.size(); index++) {
                quests.add(readQuest(questTags.getCompound(index), bookId, chapterId, namespace,
                        translations, aliases, path.getFileName().toString(), index, report));
            }
            target.add(new ChapterDefinition(bookId, chapterId, groupId,
                    translations.getOrDefault("chapter." + legacy + ".title", legacy),
                    raw.contains("icon") ? raw.get("icon").toString() : "",
                    raw.getInt("order_index"), quests));
        } catch (Exception exception) {
            report.add(problem(Diagnostic.Severity.FATAL, "BQF-003", path.getFileName().toString(), "", "", exception.getMessage()));
        }
    }

    private QuestDefinition readQuest(CompoundTag raw, ResourceLocation bookId, ResourceLocation chapterId,
                                      String namespace, Map<String, String> translations,
                                      Map<String, ResourceLocation> aliases, String file, int index,
                                      DiagnosticReport report) {
        String legacy = raw.getString("id");
        ResourceLocation id = remember(namespace, legacy, aliases);
        List<ResourceLocation> dependencies = new ArrayList<>();
        ListTag dependencyTags = raw.getList("dependencies", Tag.TAG_STRING);
        for (int i = 0; i < dependencyTags.size(); i++) dependencies.add(remember(namespace, dependencyTags.getString(i), aliases));
        List<TaskDefinition> tasks = readTasks(raw.getList("tasks", Tag.TAG_COMPOUND), bookId, namespace, aliases, file, legacy, report);
        List<RewardDefinition> rewards = readRewards(raw.getList("rewards", Tag.TAG_COMPOUND), bookId, namespace, aliases, file, legacy, report);
        return new QuestDefinition(bookId, id, chapterId,
                translations.getOrDefault("quest." + legacy + ".title", legacy),
                raw.contains("icon") ? raw.get("icon").toString() : "", raw.getDouble("x"), raw.getDouble("y"),
                dependencies, tasks, rewards, legacy);
    }

    private List<TaskDefinition> readTasks(ListTag list, ResourceLocation bookId, String namespace,
                                           Map<String, ResourceLocation> aliases, String file, String quest,
                                           DiagnosticReport report) {
        List<TaskDefinition> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag raw = list.getCompound(i);
            String legacy = raw.getString("id");
            String type = raw.getString("type");
            validateCount(raw, file, "quests[" + quest + "].tasks[" + legacy + "]", legacy, report);
            warnUnknown(type, file, "quests[" + quest + "].tasks[" + legacy + "]", legacy, report);
            result.add(new TaskDefinition(bookId, remember(namespace, legacy, aliases), typeId(type), flatten(raw), raw.getBoolean("optional")));
        }
        return result;
    }

    private List<RewardDefinition> readRewards(ListTag list, ResourceLocation bookId, String namespace,
                                               Map<String, ResourceLocation> aliases, String file, String quest,
                                               DiagnosticReport report) {
        List<RewardDefinition> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag raw = list.getCompound(i);
            String legacy = raw.getString("id");
            String type = raw.getString("type");
            validateCount(raw, file, "quests[" + quest + "].rewards[" + legacy + "]", legacy, report);
            warnUnknown(type, file, "quests[" + quest + "].rewards[" + legacy + "]", legacy, report);
            result.add(new RewardDefinition(bookId, remember(namespace, legacy, aliases), typeId(type), flatten(raw),
                    raw.getBoolean("autoclaim") ? "auto" : "manual", raw.getBoolean("team_reward")));
        }
        return result;
    }

    private Map<String, String> flatten(CompoundTag raw) {
        Map<String, String> result = new TreeMap<>();
        for (String key : raw.getAllKeys()) if (!key.equals("id") && !key.equals("type")) result.put(key, raw.get(key).toString());
        return result;
    }

    private void warnUnknown(String type, String file, String path, String id, DiagnosticReport report) {
        if (!Set.of("checkmark", "item", "custom").contains(type)) {
            report.add(problem(Diagnostic.Severity.ERROR, "BQF-102", file, path, id, "Unsupported type: " + type));
        }
    }

    private void validateCount(CompoundTag raw, String file, String path, String id, DiagnosticReport report) {
        if (!raw.contains("count", Tag.TAG_ANY_NUMERIC)) return;
        long count = raw.getLong("count");
        if (count < 1 || count > Integer.MAX_VALUE) {
            report.add(problem(Diagnostic.Severity.ERROR, "BQF-104", file, path + ".count", id,
                    "Count is outside the supported range: " + count));
        }
    }

    private void validateGraph(List<ChapterDefinition> chapters, DiagnosticReport report) {
        Map<ResourceLocation, QuestDefinition> quests = new LinkedHashMap<>();
        for (QuestDefinition quest : chapters.stream().flatMap(c -> c.quests().stream()).toList()) {
            if (quests.putIfAbsent(quest.id(), quest) != null) report.add(problem(Diagnostic.Severity.FATAL, "BQV-101", "", "", quest.id().toString(), "Duplicate quest ID"));
        }
        for (QuestDefinition quest : quests.values()) for (ResourceLocation dependency : quest.dependencies()) {
            if (!quests.containsKey(dependency)) report.add(problem(Diagnostic.Severity.ERROR, "BQV-102", "", "dependencies", quest.id().toString(), "Missing dependency " + dependency));
        }
        Set<ResourceLocation> visiting = new HashSet<>();
        Set<ResourceLocation> visited = new HashSet<>();
        for (QuestDefinition quest : quests.values()) detectCycle(quest.id(), quests, visiting, visited, report);
    }

    private void detectCycle(ResourceLocation id, Map<ResourceLocation, QuestDefinition> quests, Set<ResourceLocation> visiting,
                             Set<ResourceLocation> visited, DiagnosticReport report) {
        if (visited.contains(id) || !quests.containsKey(id)) return;
        if (!visiting.add(id)) {
            report.add(problem(Diagnostic.Severity.FATAL, "BQV-103", "", "dependencies", id.toString(), "Dependency cycle"));
            return;
        }
        for (ResourceLocation dependency : quests.get(id).dependencies()) detectCycle(dependency, quests, visiting, visited, report);
        visiting.remove(id);
        visited.add(id);
    }

    private ResourceLocation remember(String namespace, String legacy, Map<String, ResourceLocation> aliases) {
        ResourceLocation id = QuestIds.normalize(namespace, legacy);
        if (QuestIds.isLegacy(legacy)) aliases.put(legacy, id);
        return id;
    }

    private ResourceLocation typeId(String type) {
        return ResourceLocation.fromNamespaceAndPath("brnquest", type.isBlank() ? "unknown" : type.toLowerCase(Locale.ROOT));
    }

    private Diagnostic problem(Diagnostic.Severity severity, String code, String file, String path, String id, String message) {
        return new Diagnostic(severity, code, file, path, id, Objects.requireNonNullElse(message, "Unknown error"));
    }
}
