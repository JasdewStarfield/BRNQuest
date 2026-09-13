package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Compares decoded definitions so whitespace and JSON field order are ignored. */
public final class QuestBookDiffer {
    private QuestBookDiffer() {}

    public static QuestBookDiff diff(QuestBookDefinition before, QuestBookDefinition after) {
        String fromRevision = before == null ? "" : QuestBookSnapshot.of(before).revision();
        String toRevision = after == null ? "" : QuestBookSnapshot.of(after).revision();
        List<SemanticDiffEntry> entries = new ArrayList<>();
        if (before == null && after != null) {
            addWholeBook(entries, after, SemanticDiffEntry.Kind.ADDED);
        } else if (before != null && after == null) {
            addWholeBook(entries, before, SemanticDiffEntry.Kind.REMOVED);
        } else if (before != null) {
            property(entries, SemanticDiffEntry.ObjectKind.BOOK, after.id(), "title", before.title(), after.title());
            property(entries, SemanticDiffEntry.ObjectKind.BOOK, after.id(), "localization.fallback_locale",
                    before.localization().fallbackLocale(), after.localization().fallbackLocale());
            mapProperties(entries, SemanticDiffEntry.ObjectKind.BOOK, after.id(), "extensions.",
                    before.extensions(), after.extensions());
            mapProperties(entries, SemanticDiffEntry.ObjectKind.BOOK, after.id(), "localization.",
                    flattenTranslations(before.localization()), flattenTranslations(after.localization()));
            mapProperties(entries, SemanticDiffEntry.ObjectKind.BOOK, after.id(), "quest_defaults.", before.questDefaults().values(), after.questDefaults().values());
            mapProperties(entries, SemanticDiffEntry.ObjectKind.BOOK, after.id(), "settings.", before.settings().values(), after.settings().values());
            compareGroups(entries, before, after);
            compareChapters(entries, before, after);
            compareQuests(entries, before, after);
        }
        entries.sort(Comparator.comparing((SemanticDiffEntry entry) -> entry.objectKind().ordinal())
                .thenComparing(entry -> entry.objectId().toString())
                .thenComparing(SemanticDiffEntry::path)
                .thenComparing(entry -> entry.kind().ordinal()));
        return new QuestBookDiff(fromRevision, toRevision, entries);
    }

    private static void compareGroups(List<SemanticDiffEntry> entries, QuestBookDefinition before,
                                      QuestBookDefinition after) {
        Map<ResourceLocation, ChapterGroupDefinition> oldValues = map(before.chapterGroups(), ChapterGroupDefinition::id);
        Map<ResourceLocation, ChapterGroupDefinition> newValues = map(after.chapterGroups(), ChapterGroupDefinition::id);
        ids(oldValues, newValues).forEach(id -> {
            var oldValue = oldValues.get(id);
            var newValue = newValues.get(id);
            if (oldValue == null) add(entries, SemanticDiffEntry.Kind.ADDED, SemanticDiffEntry.ObjectKind.CHAPTER_GROUP, id, "", "", summary(newValue));
            else if (newValue == null) add(entries, SemanticDiffEntry.Kind.REMOVED, SemanticDiffEntry.ObjectKind.CHAPTER_GROUP, id, "", summary(oldValue), "");
            else {
                property(entries, SemanticDiffEntry.ObjectKind.CHAPTER_GROUP, id, "title", oldValue.title(), newValue.title());
                property(entries, SemanticDiffEntry.ObjectKind.CHAPTER_GROUP, id, "icon", oldValue.icon(), newValue.icon());
                property(entries, SemanticDiffEntry.ObjectKind.CHAPTER_GROUP, id, "description", oldValue.description(), newValue.description());
                mapProperties(entries, SemanticDiffEntry.ObjectKind.CHAPTER_GROUP, id, "extensions.", oldValue.extensions(), newValue.extensions());
                order(entries, SemanticDiffEntry.ObjectKind.CHAPTER_GROUP, id, oldValue.order(), newValue.order());
            }
        });
    }

    private static void compareChapters(List<SemanticDiffEntry> entries, QuestBookDefinition before,
                                        QuestBookDefinition after) {
        Map<ResourceLocation, ChapterDefinition> oldValues = map(before.chapters(), ChapterDefinition::id);
        Map<ResourceLocation, ChapterDefinition> newValues = map(after.chapters(), ChapterDefinition::id);
        ids(oldValues, newValues).forEach(id -> {
            var oldValue = oldValues.get(id);
            var newValue = newValues.get(id);
            if (oldValue == null) add(entries, SemanticDiffEntry.Kind.ADDED, SemanticDiffEntry.ObjectKind.CHAPTER, id, "", "", summary(newValue));
            else if (newValue == null) add(entries, SemanticDiffEntry.Kind.REMOVED, SemanticDiffEntry.ObjectKind.CHAPTER, id, "", summary(oldValue), "");
            else {
                property(entries, SemanticDiffEntry.ObjectKind.CHAPTER, id, "title", oldValue.title(), newValue.title());
                property(entries, SemanticDiffEntry.ObjectKind.CHAPTER, id, "icon", oldValue.icon(), newValue.icon());
                property(entries, SemanticDiffEntry.ObjectKind.CHAPTER, id, "default_hide_dependency_lines", Boolean.toString(oldValue.defaultHideDependencyLines()), Boolean.toString(newValue.defaultHideDependencyLines()));
                property(entries, SemanticDiffEntry.ObjectKind.CHAPTER, id, "autofocus_id", java.util.Objects.toString(oldValue.autofocusQuestId(), ""), java.util.Objects.toString(newValue.autofocusQuestId(), ""));
                property(entries, SemanticDiffEntry.ObjectKind.CHAPTER, id, "consume_items", java.util.Objects.toString(oldValue.consumeItems(), "default"), java.util.Objects.toString(newValue.consumeItems(), "default"));
                mapProperties(entries, SemanticDiffEntry.ObjectKind.CHAPTER, id, "quest_defaults.", oldValue.questDefaults().values(), newValue.questDefaults().values());
                mapProperties(entries, SemanticDiffEntry.ObjectKind.CHAPTER, id, "extensions.",
                        oldValue.extensions(), newValue.extensions());
                if (!oldValue.groupId().equals(newValue.groupId())) add(entries, SemanticDiffEntry.Kind.MOVED,
                        SemanticDiffEntry.ObjectKind.CHAPTER, id, "group_id", oldValue.groupId().toString(), newValue.groupId().toString());
                order(entries, SemanticDiffEntry.ObjectKind.CHAPTER, id, oldValue.order(), newValue.order());
            }
        });
    }

    private static void compareQuests(List<SemanticDiffEntry> entries, QuestBookDefinition before,
                                      QuestBookDefinition after) {
        Map<ResourceLocation, QuestDefinition> oldValues = map(before.quests(), QuestDefinition::id);
        Map<ResourceLocation, QuestDefinition> newValues = map(after.quests(), QuestDefinition::id);
        pairRenamedQuests(entries, oldValues, newValues);
        ids(oldValues, newValues).forEach(id -> {
            QuestDefinition oldValue = oldValues.get(id);
            QuestDefinition newValue = newValues.get(id);
            if (oldValue == null) add(entries, SemanticDiffEntry.Kind.ADDED, SemanticDiffEntry.ObjectKind.QUEST, id, "", "", summary(newValue));
            else if (newValue == null) add(entries, SemanticDiffEntry.Kind.REMOVED, SemanticDiffEntry.ObjectKind.QUEST, id, "", summary(oldValue), "");
            else compareQuest(entries, oldValue, newValue);
        });
    }

    private static void pairRenamedQuests(List<SemanticDiffEntry> entries,
                                          Map<ResourceLocation, QuestDefinition> oldValues,
                                          Map<ResourceLocation, QuestDefinition> newValues) {
        Map<String, QuestDefinition> oldLegacy = uniqueLegacy(oldValues.values());
        Map<String, QuestDefinition> newLegacy = uniqueLegacy(newValues.values());
        for (String legacy : new TreeSet<>(oldLegacy.keySet())) {
            QuestDefinition oldValue = oldLegacy.get(legacy);
            QuestDefinition newValue = newLegacy.get(legacy);
            if (newValue == null || oldValue.id().equals(newValue.id())
                    || newValues.containsKey(oldValue.id()) || oldValues.containsKey(newValue.id())) continue;
            oldValues.remove(oldValue.id());
            newValues.remove(newValue.id());
            add(entries, SemanticDiffEntry.Kind.RENAMED, SemanticDiffEntry.ObjectKind.QUEST, newValue.id(), "id",
                    oldValue.id().toString(), newValue.id().toString());
            compareQuest(entries, oldValue, newValue);
        }
    }

    private static void compareQuest(List<SemanticDiffEntry> entries, QuestDefinition oldValue,
                                     QuestDefinition newValue) {
        ResourceLocation id = newValue.id();
        property(entries, SemanticDiffEntry.ObjectKind.QUEST, id, "title", oldValue.title(), newValue.title());
        property(entries, SemanticDiffEntry.ObjectKind.QUEST, id, "subtitle", oldValue.subtitle(), newValue.subtitle());
        property(entries, SemanticDiffEntry.ObjectKind.QUEST, id, "description", oldValue.description(), newValue.description());
        property(entries, SemanticDiffEntry.ObjectKind.QUEST, id, "icon", oldValue.icon(), newValue.icon());
        property(entries, SemanticDiffEntry.ObjectKind.QUEST, id, "hide_dependency_lines", java.util.Objects.toString(oldValue.appearance().hideDependencyLines(), "default"), java.util.Objects.toString(newValue.appearance().hideDependencyLines(), "default"));
        property(entries, SemanticDiffEntry.ObjectKind.QUEST, id, "appearance.shape",
                oldValue.appearance().shape(), newValue.appearance().shape());
        property(entries, SemanticDiffEntry.ObjectKind.QUEST, id, "appearance.size",
                oldValue.appearance().size(), newValue.appearance().size());
        property(entries, SemanticDiffEntry.ObjectKind.QUEST, id, "appearance.icon_scale",
                oldValue.appearance().iconScale(), newValue.appearance().iconScale());
        property(entries, SemanticDiffEntry.ObjectKind.QUEST, id, "appearance.min_width",
                oldValue.appearance().minWidth(), newValue.appearance().minWidth());
        mapProperties(entries, SemanticDiffEntry.ObjectKind.QUEST, id, "behavior.",
                behaviorValues(oldValue.behavior()), behaviorValues(newValue.behavior()));
        mapProperties(entries, SemanticDiffEntry.ObjectKind.QUEST, id, "extensions.",
                oldValue.extensions(), newValue.extensions());
        if (!oldValue.chapterId().equals(newValue.chapterId())) add(entries, SemanticDiffEntry.Kind.MOVED,
                SemanticDiffEntry.ObjectKind.QUEST, id, "chapter_id", oldValue.chapterId().toString(), newValue.chapterId().toString());
        if (Double.compare(oldValue.x(), newValue.x()) != 0 || Double.compare(oldValue.y(), newValue.y()) != 0) {
            add(entries, SemanticDiffEntry.Kind.MOVED, SemanticDiffEntry.ObjectKind.QUEST, id, "position",
                    oldValue.x() + "," + oldValue.y(), newValue.x() + "," + newValue.y());
        }
        Set<ResourceLocation> oldDependencies = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        oldDependencies.addAll(oldValue.dependencies());
        Set<ResourceLocation> newDependencies = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        newDependencies.addAll(newValue.dependencies());
        oldDependencies.stream().filter(dependency -> !newDependencies.contains(dependency)).forEach(dependency ->
                add(entries, SemanticDiffEntry.Kind.DEPENDENCY_REMOVED, SemanticDiffEntry.ObjectKind.QUEST, id,
                        "dependencies", dependency.toString(), ""));
        newDependencies.stream().filter(dependency -> !oldDependencies.contains(dependency)).forEach(dependency ->
                add(entries, SemanticDiffEntry.Kind.DEPENDENCY_ADDED, SemanticDiffEntry.ObjectKind.QUEST, id,
                        "dependencies", "", dependency.toString()));
        compareTasks(entries, oldValue, newValue);
        compareRewards(entries, oldValue, newValue);
    }

    private static void compareTasks(List<SemanticDiffEntry> entries, QuestDefinition oldQuest,
                                     QuestDefinition newQuest) {
        Map<ResourceLocation, TaskDefinition> oldValues = map(oldQuest.tasks(), TaskDefinition::id);
        Map<ResourceLocation, TaskDefinition> newValues = map(newQuest.tasks(), TaskDefinition::id);
        ids(oldValues, newValues).forEach(id -> {
            var oldValue = oldValues.get(id);
            var newValue = newValues.get(id);
            if (oldValue == null) add(entries, SemanticDiffEntry.Kind.ADDED, SemanticDiffEntry.ObjectKind.TASK, id, "", "", summary(newValue));
            else if (newValue == null) add(entries, SemanticDiffEntry.Kind.REMOVED, SemanticDiffEntry.ObjectKind.TASK, id, "", summary(oldValue), "");
            else {
                type(entries, SemanticDiffEntry.ObjectKind.TASK, id, oldValue.typeId(), newValue.typeId());
                property(entries, SemanticDiffEntry.ObjectKind.TASK, id, "optional", oldValue.optional(), newValue.optional());
                config(entries, SemanticDiffEntry.ObjectKind.TASK, id, oldValue.config(), newValue.config());
                order(entries, SemanticDiffEntry.ObjectKind.TASK, id, index(oldQuest.tasks(), id, TaskDefinition::id),
                        index(newQuest.tasks(), id, TaskDefinition::id));
            }
        });
    }

    private static void compareRewards(List<SemanticDiffEntry> entries, QuestDefinition oldQuest,
                                       QuestDefinition newQuest) {
        Map<ResourceLocation, RewardDefinition> oldValues = map(oldQuest.rewards(), RewardDefinition::id);
        Map<ResourceLocation, RewardDefinition> newValues = map(newQuest.rewards(), RewardDefinition::id);
        ids(oldValues, newValues).forEach(id -> {
            var oldValue = oldValues.get(id);
            var newValue = newValues.get(id);
            if (oldValue == null) add(entries, SemanticDiffEntry.Kind.ADDED, SemanticDiffEntry.ObjectKind.REWARD, id, "", "", summary(newValue));
            else if (newValue == null) add(entries, SemanticDiffEntry.Kind.REMOVED, SemanticDiffEntry.ObjectKind.REWARD, id, "", summary(oldValue), "");
            else {
                type(entries, SemanticDiffEntry.ObjectKind.REWARD, id, oldValue.typeId(), newValue.typeId());
                property(entries, SemanticDiffEntry.ObjectKind.REWARD, id, "claim_policy", oldValue.claimPolicy(), newValue.claimPolicy());
                property(entries, SemanticDiffEntry.ObjectKind.REWARD, id, "team_reward", oldValue.teamReward(), newValue.teamReward());
                config(entries, SemanticDiffEntry.ObjectKind.REWARD, id, oldValue.config(), newValue.config());
                order(entries, SemanticDiffEntry.ObjectKind.REWARD, id, index(oldQuest.rewards(), id, RewardDefinition::id),
                        index(newQuest.rewards(), id, RewardDefinition::id));
            }
        });
    }

    private static void config(List<SemanticDiffEntry> entries, SemanticDiffEntry.ObjectKind kind,
                               ResourceLocation id, Map<String, String> before, Map<String, String> after) {
        mapProperties(entries, kind, id, "config.", before, after);
    }

    private static void mapProperties(List<SemanticDiffEntry> entries, SemanticDiffEntry.ObjectKind kind,
                                      ResourceLocation id, String prefix,
                                      Map<String, String> before, Map<String, String> after) {
        Set<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        keys.forEach(key -> {
            String oldValue = before.getOrDefault(key, "");
            String newValue = after.getOrDefault(key, "");
            if (!Objects.equals(oldValue, newValue)) add(entries, SemanticDiffEntry.Kind.CONFIG_CHANGED, kind, id,
                    prefix + key, oldValue, newValue);
        });
    }

    private static Map<String, String> flattenTranslations(BookLocalization localization) {
        Map<String, String> result = new TreeMap<>();
        localization.translations().forEach((locale, values) -> values.forEach((key, value) ->
                result.put(locale + "." + key, value)));
        return result;
    }

    private static void addWholeBook(List<SemanticDiffEntry> entries, QuestBookDefinition book,
                                     SemanticDiffEntry.Kind kind) {
        boolean adding = kind == SemanticDiffEntry.Kind.ADDED;
        add(entries, kind, SemanticDiffEntry.ObjectKind.BOOK, book.id(), "",
                adding ? "" : book.title(), adding ? book.title() : "");
        book.chapterGroups().forEach(value -> add(entries, kind, SemanticDiffEntry.ObjectKind.CHAPTER_GROUP,
                value.id(), "", adding ? "" : summary(value), adding ? summary(value) : ""));
        book.chapters().forEach(value -> add(entries, kind, SemanticDiffEntry.ObjectKind.CHAPTER,
                value.id(), "", adding ? "" : summary(value), adding ? summary(value) : ""));
        book.quests().forEach(value -> {
            add(entries, kind, SemanticDiffEntry.ObjectKind.QUEST, value.id(), "",
                    adding ? "" : summary(value), adding ? summary(value) : "");
            value.tasks().forEach(task -> add(entries, kind, SemanticDiffEntry.ObjectKind.TASK, task.id(), "",
                    adding ? "" : summary(task), adding ? summary(task) : ""));
            value.rewards().forEach(reward -> add(entries, kind, SemanticDiffEntry.ObjectKind.REWARD, reward.id(), "",
                    adding ? "" : summary(reward), adding ? summary(reward) : ""));
        });
    }

    private static Map<String, QuestDefinition> uniqueLegacy(Collection<QuestDefinition> values) {
        Map<String, QuestDefinition> result = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        values.stream().filter(value -> !value.legacyId().isBlank()).forEach(value -> {
            if (result.putIfAbsent(value.legacyId(), value) != null) duplicates.add(value.legacyId());
        });
        duplicates.forEach(result::remove);
        return result;
    }

    private static <T> Map<ResourceLocation, T> map(List<T> values, Function<T, ResourceLocation> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity(), (first, ignored) -> first, TreeMap::new));
    }

    private static <T> Set<ResourceLocation> ids(Map<ResourceLocation, T> before, Map<ResourceLocation, T> after) {
        Set<ResourceLocation> ids = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        ids.addAll(before.keySet());
        ids.addAll(after.keySet());
        return ids;
    }

    private static <T> int index(List<T> values, ResourceLocation id, Function<T, ResourceLocation> getter) {
        for (int index = 0; index < values.size(); index++) if (getter.apply(values.get(index)).equals(id)) return index;
        return -1;
    }

    private static void type(List<SemanticDiffEntry> entries, SemanticDiffEntry.ObjectKind kind,
                             ResourceLocation id, ResourceLocation before, ResourceLocation after) {
        if (!before.equals(after)) add(entries, SemanticDiffEntry.Kind.TYPE_CHANGED, kind, id, "type",
                before.toString(), after.toString());
    }

    private static void order(List<SemanticDiffEntry> entries, SemanticDiffEntry.ObjectKind kind,
                              ResourceLocation id, int before, int after) {
        if (before != after) add(entries, SemanticDiffEntry.Kind.ORDER_CHANGED, kind, id, "order",
                Integer.toString(before), Integer.toString(after));
    }

    private static void property(List<SemanticDiffEntry> entries, SemanticDiffEntry.ObjectKind kind,
                                 ResourceLocation id, String path, Object before, Object after) {
        if (!Objects.equals(before, after)) add(entries, SemanticDiffEntry.Kind.PROPERTY_CHANGED, kind, id, path,
                Objects.toString(before, ""), Objects.toString(after, ""));
    }

    private static void add(List<SemanticDiffEntry> entries, SemanticDiffEntry.Kind kind,
                            SemanticDiffEntry.ObjectKind objectKind, ResourceLocation id,
                            String path, String before, String after) {
        entries.add(new SemanticDiffEntry(kind, objectKind, id, path, before, after));
    }

    /** A behavior edit produces one row per changed field, rather than an opaque record dump. */
    private static Map<String, String> behaviorValues(QuestBehavior behavior) {
        var result = new TreeMap<String, String>();
        result.put("hide_until_dependencies_visible", Boolean.toString(behavior.hideUntilDependenciesVisible()));
        result.put("hide_until_dependencies_complete", Boolean.toString(behavior.hideUntilDependenciesComplete()));
        result.put("invisible_until_complete", Boolean.toString(behavior.invisibleUntilComplete()));
        result.put("visible_after_tasks", Integer.toString(behavior.visibleAfterTasks()));
        result.put("hide_details_until_startable", Boolean.toString(behavior.hideDetailsUntilStartable()));
        result.put("hide_text_until_complete", Boolean.toString(behavior.hideTextUntilComplete()));
        result.put("hide_lock_icon", Boolean.toString(behavior.hideLockIcon()));
        result.put("dependency_requirement", behavior.dependencyRequirement().serializedName());
        result.put("minimum_required_dependencies", Integer.toString(behavior.minimumRequiredDependencies()));
        result.put("sequential_tasks", Boolean.toString(behavior.sequentialTasks()));
        result.put("repeatable", Boolean.toString(behavior.repeatable()));
        result.put("repeat_cooldown_seconds", Integer.toString(behavior.repeatCooldownSeconds()));
        result.put("ignore_reward_blocking", Boolean.toString(behavior.ignoreRewardBlocking()));
        return result;
    }

    /** Use the public data codecs so removed entries retain names and readable field values in review. */
    private static String summary(Object value) {
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        return switch (value) {
            case ChapterGroupDefinition v -> com.google.gson.JsonParser.parseString(NativeBookJson.encode(new QuestBookDefinition(v.bookId(), 1, "", List.of(v), List.of(), Map.of()))).getAsJsonObject().getAsJsonArray("chapter_groups").get(0).toString();
            case ChapterDefinition v -> com.google.gson.JsonParser.parseString(NativeBookJson.encode(new QuestBookDefinition(v.bookId(), 1, "", List.of(), List.of(v), Map.of()))).getAsJsonObject().getAsJsonArray("chapters").get(0).toString();
            case QuestDefinition v -> QuestDefinition.CODEC.encodeStart(ops, v).getOrThrow().toString();
            case TaskDefinition v -> TaskDefinition.CODEC.encodeStart(ops, v).getOrThrow().toString();
            case RewardDefinition v -> RewardDefinition.CODEC.encodeStart(ops, v).getOrThrow().toString();
            default -> Objects.toString(value, "");
        };
    }
}
