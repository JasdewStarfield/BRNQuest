package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Maps the observed FTB Quests v13 subset into BRNQuest's independent immutable model. */
public final class FtbV13Importer {
    // Mapping, diagnostics and conversion reports must recognize the same source aliases.
    private static final Set<String> BUILT_IN_TYPES = Set.of("checkmark", "item", "custom", "xp",
            "xp_levels", "command", "dimension", "biome", "location", "structure", "advancement", "observation", "kill");

    private static String builtInPath(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return normalized.startsWith("ftbquests:") ? normalized.substring("ftbquests:".length()) : normalized;
    }

    private final SnbtReader reader = new SnbtReader();

    public FtbImportResult importBook(Path source, String namespace, String bookPath) {
        DiagnosticReport report = new DiagnosticReport();
        ResourceLocation bookId = ResourceLocation.fromNamespaceAndPath(namespace, bookPath);
        List<ChapterGroupDefinition> groups = new ArrayList<>();
        List<ChapterDefinition> chapters = new ArrayList<>();
        Map<String, ResourceLocation> aliases = new TreeMap<>();
        Map<String, Map<String, String>> localeTranslations = new TreeMap<>();
        List<FtbFieldConversion> conversions = new ArrayList<>();
        String fallbackLocale = "en_us";
        String defaultAutoClaim = "disabled";
        InheritedAppearance defaultAppearance = new InheritedAppearance("chamfer", 1.0);
        Map<String, InheritedAppearance> presets = new TreeMap<>();
        Map<String, String> fileExtensions = new TreeMap<>();

        try {
            CompoundTag data = reader.read(source.resolve("data.snbt"));
            if (data.getInt("version") != 13) {
                report.add(problem(Diagnostic.Severity.FATAL, "BQF-001", "data.snbt", "version", "",
                        "Expected FTB Quests format 13, got " + data.getInt("version")));
            }
            recordMappedFields(data, "data.snbt", "data", conversions, Map.ofEntries(
                    Map.entry("version", "schema_version"), Map.entry("fallback_locale", "localization.fallback_locale"),
                    Map.entry("default_autoclaim_rewards", "reward_defaults.claim_policy"),
                    Map.entry("default_quest_shape", "appearance_defaults.shape"),
                    Map.entry("presets", "appearance_presets"), Map.entry("preset", "appearance_defaults.preset")));
            defaultAutoClaim = data.getString("default_autoclaim_rewards");
            if (data.getBoolean("suppress_all_autoclaiming")) defaultAutoClaim = "disabled";
            fallbackLocale = BookLocalization.normalizeLocale(data.getString("fallback_locale"));
            presets.putAll(readPresets(data.getCompound("presets")));
            defaultAppearance = inheritedAppearance(data, defaultAppearance, presets);
            fileExtensions.putAll(extensions(data, Set.of("version", "title", "default_autoclaim_rewards"),
                    "data.snbt", "data", conversions));
            readAllTranslations(source.resolve("lang"), localeTranslations, report);
            String sourceTextLocale = localeTranslations.containsKey(fallbackLocale) ? fallbackLocale
                    : localeTranslations.containsKey("zh_cn") ? "zh_cn"
                    : localeTranslations.isEmpty() ? fallbackLocale : localeTranslations.keySet().iterator().next();
            Map<String, String> translations = localeTranslations.getOrDefault(sourceTextLocale, Map.of());
            readGroups(reader.read(source.resolve("chapter_groups.snbt")), bookId, namespace, translations, aliases, groups);

            Path chapterDir = source.resolve("chapters");
            String inheritedAutoClaim = defaultAutoClaim;
            InheritedAppearance inheritedAppearance = defaultAppearance;
            Map<String, InheritedAppearance> inheritedPresets = Map.copyOf(presets);
            try (var files = Files.list(chapterDir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".snbt"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(path -> readChapter(path, bookId, namespace, translations, aliases, chapters,
                                inheritedAutoClaim, inheritedAppearance, inheritedPresets, report, conversions));
            }
        } catch (Exception exception) {
            report.add(problem(Diagnostic.Severity.FATAL, "BQF-002", source.toString(), "", "", exception.getMessage()));
        }

        validateGraph(chapters, report);
        String sourceTextLocale = localeTranslations.containsKey(fallbackLocale) ? fallbackLocale
                : localeTranslations.containsKey("zh_cn") ? "zh_cn"
                : localeTranslations.isEmpty() ? fallbackLocale : localeTranslations.keySet().iterator().next();
        Map<String, String> fallbackTranslations = localeTranslations.getOrDefault(sourceTextLocale, Map.of());
        fileExtensions.put("ftb.default_autoclaim_rewards", defaultAutoClaim);
        QuestBookDefinition book = new QuestBookDefinition(bookId, BrnQuestConstants.DATA_SCHEMA,
                fallbackTranslations.getOrDefault("title", bookId.toString()), groups, chapters, aliases,
                new BookLocalization(fallbackLocale, localeTranslations), fileExtensions);
        int taskCount = book.quests().stream().mapToInt(q -> q.tasks().size()).sum();
        int rewardCount = book.quests().stream().mapToInt(q -> q.rewards().size()).sum();
        return new FtbImportResult(book, report, groups.size(), chapters.size(), book.quests().size(), taskCount,
                rewardCount, conversions);
    }

    private void readAllTranslations(Path directory, Map<String, Map<String, String>> target,
                                     DiagnosticReport report) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(file -> file.getFileName().toString().endsWith(".snbt"))
                    .sorted().toList()) {
                String filename = path.getFileName().toString();
                String locale = BookLocalization.normalizeLocale(filename.substring(0, filename.length() - 5));
                Map<String, String> translations = new TreeMap<>();
                try {
                    readTranslations(reader.read(path), translations);
                    // Multiple source filenames may normalize to one locale; do not overwrite a whole table.
                    Map<String, String> merged = new TreeMap<>(target.getOrDefault(locale, Map.of()));
                    translations.forEach((key, value) -> {
                        String previous = merged.putIfAbsent(key, value);
                        if (previous != null && !previous.equals(value)) {
                            report.add(problem(Diagnostic.Severity.FATAL, "BQF-106", filename, key, "",
                                    "Conflicting translation for normalized locale " + locale));
                        }
                    });
                    target.put(locale, merged);
                } catch (Exception exception) {
                    report.add(problem(Diagnostic.Severity.ERROR, "BQF-105", filename, "", "",
                            "Language file could not be read: " + exception.getMessage()));
                }
            }
        }
    }

    private void readTranslations(CompoundTag tag, Map<String, String> target) {
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            if (value instanceof ListTag lines) {
                // FTB stores quest descriptions as a list of localized lines.
                target.put(key, java.util.stream.IntStream.range(0, lines.size())
                        .mapToObj(lines::getString).collect(java.util.stream.Collectors.joining("\n")));
            } else {
                target.put(key, tag.getString(key));
            }
        }
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
                             List<ChapterDefinition> target, String defaultAutoClaim,
                             InheritedAppearance defaultAppearance, Map<String, InheritedAppearance> presets,
                             DiagnosticReport report,
                             List<FtbFieldConversion> conversions) {
        try {
            CompoundTag raw = reader.read(path);
            String legacy = raw.getString("id");
            ResourceLocation chapterId = remember(namespace, legacy, aliases);
            String groupLegacy = raw.getString("group");
            ResourceLocation groupId = groupLegacy.isBlank()
                    ? ResourceLocation.fromNamespaceAndPath(namespace, "ungrouped") : remember(namespace, groupLegacy, aliases);
            List<QuestDefinition> quests = new ArrayList<>();
            recordMappedFields(raw, path.getFileName().toString(), "chapter[" + legacy + "]", conversions,
                    Map.of("id", "id", "group", "group_id", "icon", "icon", "order_index", "order",
                            "quests", "quests", "default_quest_shape", "appearance_defaults.shape",
                            "default_quest_size", "appearance_defaults.size", "preset", "appearance_defaults.preset"));
            InheritedAppearance chapterAppearance = inheritedAppearance(raw, defaultAppearance, presets);
            InheritedBehavior chapterBehavior = new InheritedBehavior(
                    raw.getBoolean("hide_quest_until_deps_visible"), raw.getBoolean("hide_quest_until_deps_complete"),
                    raw.getBoolean("hide_quest_details_until_startable"), raw.getBoolean("hide_text_until_complete"),
                    raw.getBoolean("require_sequential_tasks"), raw.getBoolean("default_repeatable_quest"));
            ListTag questTags = raw.getList("quests", Tag.TAG_COMPOUND);
            for (int index = 0; index < questTags.size(); index++) {
                quests.add(readQuest(questTags.getCompound(index), bookId, chapterId, namespace,
                        translations, aliases, path.getFileName().toString(), index, defaultAutoClaim, report,
                        conversions, chapterAppearance, presets, chapterBehavior));
            }
            target.add(new ChapterDefinition(bookId, chapterId, groupId,
                    translations.getOrDefault("chapter." + legacy + ".title", legacy),
                    raw.contains("icon") ? raw.get("icon").toString() : "",
                    raw.getInt("order_index"), quests,
                    extensions(raw, Set.of("id", "group", "title", "icon", "order_index", "quests"),
                            path.getFileName().toString(), "chapter[" + legacy + "]", conversions)));
        } catch (Exception exception) {
            report.add(problem(Diagnostic.Severity.FATAL, "BQF-003", path.getFileName().toString(), "", "", exception.getMessage()));
        }
    }

    private QuestDefinition readQuest(CompoundTag raw, ResourceLocation bookId, ResourceLocation chapterId,
                                      String namespace, Map<String, String> translations,
                                      Map<String, ResourceLocation> aliases, String file, int index,
                                      String defaultAutoClaim, DiagnosticReport report,
                                      List<FtbFieldConversion> conversions,
                                      InheritedAppearance inheritedAppearance,
                                      Map<String, InheritedAppearance> presets,
                                      InheritedBehavior inheritedBehavior) {
        String legacy = raw.getString("id");
        ResourceLocation id = remember(namespace, legacy, aliases);
        List<ResourceLocation> dependencies = new ArrayList<>();
        ListTag dependencyTags = raw.getList("dependencies", Tag.TAG_STRING);
        for (int i = 0; i < dependencyTags.size(); i++) dependencies.add(remember(namespace, dependencyTags.getString(i), aliases));
        List<TaskDefinition> tasks = readTasks(raw.getList("tasks", Tag.TAG_COMPOUND), bookId, namespace,
                translations, aliases, file, legacy, report, conversions);
        List<RewardDefinition> rewards = readRewards(raw.getList("rewards", Tag.TAG_COMPOUND), bookId, namespace,
                translations, aliases, file, legacy, defaultAutoClaim, report, conversions);
        String translatedTitle = translations.getOrDefault("quest." + legacy + ".title", "");
        String title = translatedTitle.isBlank() ? defaultTaskTitle(tasks, legacy) : translatedTitle;
        recordMappedFields(raw, file, "quests[" + legacy + "]", conversions, Map.ofEntries(
                Map.entry("id", "id"), Map.entry("x", "x"), Map.entry("y", "y"),
                Map.entry("icon", "icon"), Map.entry("dependencies", "dependencies"),
                Map.entry("tasks", "tasks"), Map.entry("rewards", "rewards"),
                Map.entry("shape", "appearance.shape"), Map.entry("size", "appearance.size"),
                Map.entry("icon_scale", "appearance.icon_scale"), Map.entry("min_width", "appearance.min_width"),
                Map.entry("preset", "appearance.preset"),
                Map.entry("hide_until_deps_visible", "behavior.hide_until_dependencies_visible"),
                Map.entry("hide_until_deps_complete", "behavior.hide_until_dependencies_complete"),
                Map.entry("invisible", "behavior.invisible_until_complete"),
                Map.entry("invisible_until_tasks", "behavior.visible_after_tasks"),
                Map.entry("hide_details_until_startable", "behavior.hide_details_until_startable"),
                Map.entry("hide_text_until_complete", "behavior.hide_text_until_complete"),
                Map.entry("hide_lock_icon", "behavior.hide_lock_icon"),
                Map.entry("dependency_requirement", "behavior.dependency_requirement"),
                Map.entry("min_required_dependencies", "behavior.minimum_required_dependencies"),
                Map.entry("require_sequential_tasks", "behavior.sequential_tasks"),
                Map.entry("can_repeat", "behavior.repeatable"),
                Map.entry("repeat_cooldown", "behavior.repeat_cooldown_seconds"),
                Map.entry("ignore_reward_blocking", "behavior.ignore_reward_blocking")));
        InheritedAppearance resolved = inheritedAppearance(raw, inheritedAppearance, presets);
        QuestAppearance appearance = new QuestAppearance(resolved.shape(),
                raw.contains("size", Tag.TAG_ANY_NUMERIC) ? raw.getDouble("size") : resolved.size(),
                raw.contains("icon_scale", Tag.TAG_ANY_NUMERIC) ? raw.getDouble("icon_scale") : 1.0,
                raw.contains("min_width", Tag.TAG_ANY_NUMERIC) ? raw.getDouble("min_width") : 0.0);
        QuestBehavior behavior = new QuestBehavior(
                inheritedBoolean(raw, "hide_until_deps_visible", inheritedBehavior.hideUntilDependenciesVisible()),
                inheritedBoolean(raw, "hide_until_deps_complete", inheritedBehavior.hideUntilDependenciesComplete()),
                raw.getBoolean("invisible"), raw.getInt("invisible_until_tasks"),
                inheritedBoolean(raw, "hide_details_until_startable", inheritedBehavior.hideDetailsUntilStartable()),
                inheritedBoolean(raw, "hide_text_until_complete", inheritedBehavior.hideTextUntilComplete()),
                raw.getBoolean("hide_lock_icon"), DependencyRequirement.parse(raw.getString("dependency_requirement")),
                raw.getInt("min_required_dependencies"),
                inheritedBoolean(raw, "require_sequential_tasks", inheritedBehavior.sequentialTasks()),
                inheritedBoolean(raw, "can_repeat", inheritedBehavior.repeatable()), raw.getInt("repeat_cooldown"),
                raw.getBoolean("ignore_reward_blocking"));
        return new QuestDefinition(bookId, id, chapterId,
                title,
                translations.getOrDefault("quest." + legacy + ".quest_subtitle", ""),
                translations.getOrDefault("quest." + legacy + ".quest_desc", ""),
                raw.contains("icon") ? raw.get("icon").toString() : "", raw.getDouble("x"), raw.getDouble("y"),
                dependencies, tasks, rewards, legacy, appearance, behavior,
                extensions(raw, Set.of("id", "title", "subtitle", "description", "icon", "x", "y",
                        "dependencies", "tasks", "rewards", "shape", "size", "icon_scale", "min_width",
                        "hide_until_deps_visible", "hide_until_deps_complete", "invisible", "invisible_until_tasks",
                        "hide_details_until_startable", "hide_text_until_complete", "hide_lock_icon",
                        "dependency_requirement", "min_required_dependencies", "require_sequential_tasks",
                        "can_repeat", "repeat_cooldown", "ignore_reward_blocking"),
                        file, "quests[" + legacy + "]", conversions));
    }

    /** Gives untitled imported quests a stable author-facing label; clients localize item names at render time. */
    private String defaultTaskTitle(List<TaskDefinition> tasks, String fallback) {
        if (tasks.isEmpty()) return fallback;
        TaskDefinition first = tasks.getFirst();
        String custom = first.config().getOrDefault("title", "");
        if (!custom.isBlank()) return custom;
        if (TaskTypes.ITEM.equals(first.typeId())) {
            try {
                CompoundTag item = net.minecraft.nbt.TagParser.parseTag(first.config().getOrDefault("item", ""));
                String itemId = item.getString("id");
                if (!itemId.isBlank()) return itemId;
            } catch (Exception ignored) {
                // Malformed item data already receives its importer diagnostic; retain the legacy fallback here.
            }
        }
        return TaskTypes.CHECKMARK.equals(first.typeId()) ? "Check objective" : fallback;
    }

    private List<TaskDefinition> readTasks(ListTag list, ResourceLocation bookId, String namespace,
                                           Map<String, String> translations,
                                           Map<String, ResourceLocation> aliases, String file, String quest,
                                           DiagnosticReport report, List<FtbFieldConversion> conversions) {
        List<TaskDefinition> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag raw = list.getCompound(i);
            String legacy = raw.getString("id");
            String type = raw.getString("type");
            validateCount(raw, file, "quests[" + quest + "].tasks[" + legacy + "]", legacy, report);
            warnUnknown(type, file, "quests[" + quest + "].tasks[" + legacy + "]", legacy, report);
            ResourceLocation mappedType = typeId(type);
            recordTypeConversion(file, "quests[" + quest + "].tasks[" + legacy + "]", type, mappedType, conversions);
            Map<String, String> config = flatten(raw, Set.of("optional_task"));
            advancementFields(mappedType, raw, config);
            String encounterError=FtbEncounterFields.apply(mappedType,raw,config);
            if(!encounterError.isEmpty()) report.add(problem(Diagnostic.Severity.ERROR,"BQF-109",file,"tasks["+legacy+"]",legacy,encounterError));
            if(config.containsKey("ftb.observation_mode_conflict")) report.add(problem(Diagnostic.Severity.WARN,"BQF-110",file,"tasks["+legacy+"].observe_type",legacy,"Legacy observe_type takes precedence: "+config.get("ftb.observation_mode_conflict")));
            if(mappedType.equals(yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterConfig.OBSERVE) || mappedType.equals(yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterConfig.KILL))
                conversions.add(new FtbFieldConversion(file,"tasks["+legacy+"]","target/timer/value","config.target/duration/count",encounterError.isEmpty() ? FtbFieldConversion.Status.MAPPED : FtbFieldConversion.Status.UNSUPPORTED,"Server-authoritative ID/tag conversion; source constraints retained"));
            if (mappedType.getNamespace().equals("brnquest") && Set.of("dimension", "biome", "location", "structure").contains(mappedType.getPath())) {
                String kind = mappedType.getPath();
                String selectorKey = kind.equals("location") ? "dimension" : kind;
                config.put(selectorKey, raw.contains(selectorKey) ? raw.getString(selectorKey)
                        : kind.equals("dimension") ? "minecraft:the_nether" : kind.equals("location") ? "minecraft:overworld" : "");
                if (kind.equals("location")) {
                    config.put("ignore_dimension", Boolean.toString(raw.getBoolean("ignore_dimension")));
                    for (String vector : List.of("position", "size")) {
                        String original = raw.contains(vector) ? raw.get(vector).toString() : "";
                        if (!original.isEmpty()) config.put("ftb." + vector, original);
                        boolean valid = true;
                        try { config.put(vector, locationVector(raw.get(vector), vector.equals("size") ? "1,1,1" : "0,0,0")); }
                        catch (IllegalArgumentException error) {
                            valid = false;
                            config.put(vector, original);
                            report.add(problem(Diagnostic.Severity.ERROR, "BQF-108", file, "tasks["+legacy+"]."+vector, legacy, error.getMessage()));
                        }
                        conversions.add(new FtbFieldConversion(file, "tasks["+legacy+"]", vector, "config."+vector,
                                valid ? (original.isEmpty() ? FtbFieldConversion.Status.DEFAULTED : FtbFieldConversion.Status.MAPPED) : FtbFieldConversion.Status.UNSUPPORTED, original+" -> "+config.get(vector)));
                    }
                }
            }
            recordConfigFields(raw, mappedType.equals(ResourceLocation.parse("brnquest:location"))
                    ? Set.of("id", "type", "optional_task", "position", "size") : Set.of("id", "type", "optional_task"), file,
                    "quests[" + quest + "].tasks[" + legacy + "]", conversions);
            config.put("title", translations.getOrDefault("task." + legacy + ".title", ""));
            boolean optional = raw.getBoolean("optional_task");
            conversions.add(new FtbFieldConversion(file, "quests[" + quest + "].tasks[" + legacy + "]",
                    "optional_task", "optional", raw.contains("optional_task")
                    ? FtbFieldConversion.Status.MAPPED : FtbFieldConversion.Status.DEFAULTED,
                    Boolean.toString(optional)));
            result.add(new TaskDefinition(bookId, remember(namespace, legacy, aliases), mappedType, config, optional));
        }
        return result;
    }

    private List<RewardDefinition> readRewards(ListTag list, ResourceLocation bookId, String namespace,
                                               Map<String, String> translations,
                                               Map<String, ResourceLocation> aliases, String file, String quest,
                                               String defaultAutoClaim, DiagnosticReport report,
                                               List<FtbFieldConversion> conversions) {
        List<RewardDefinition> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag raw = list.getCompound(i);
            String legacy = raw.getString("id");
            String type = raw.getString("type");
            validateCount(raw, file, "quests[" + quest + "].rewards[" + legacy + "]", legacy, report);
            warnUnknown(type, file, "quests[" + quest + "].rewards[" + legacy + "]", legacy, report);
            ResourceLocation mappedType = typeId(type);
            recordTypeConversion(file, "quests[" + quest + "].rewards[" + legacy + "]", type, mappedType, conversions);
            Map<String, String> config = flatten(raw, Set.of("auto", "team_reward"));
            advancementFields(mappedType, raw, config);
            if (mappedType.equals(yourscraft.jasdewstarfield.brnquest.reward.RewardTypes.COMMAND)) {
                // FTB field values are semantic strings/booleans here, not their quoted SNBT representation.
                config.put("command", yourscraft.jasdewstarfield.brnquest.reward.CommandRewardConfig.normalize(raw.getString("command")));
                int permission = raw.getBoolean("elevate_perms") ? 2 : raw.getInt("permission_level");
                config.put("source_mode", permission == 0 ? "player" : "explicit");
                config.put("permission_level", Integer.toString(permission));
                config.put("silent", Boolean.toString(raw.getBoolean("silent")));
                config.put("feedback", "");
                config.remove("feedback_message");
                if (!raw.getString("feedback_message").isBlank()) {
                    // Quest SNBT language tables do not provide arbitrary resource-pack translation keys.
                    config.put("ftb.feedback_message", raw.getString("feedback_message"));
                    conversions.add(new FtbFieldConversion(file, "quests[" + quest + "].rewards[" + legacy + "]",
                            "feedback_message", "config.ftb.feedback_message", FtbFieldConversion.Status.PRESERVED_EXTENSION,
                            "Resource translation key requires author resolution"));
                    report.add(problem(Diagnostic.Severity.ERROR, "BQF-107", file, "rewards[" + legacy + "].feedback_message", legacy,
                            "Resolve the feedback translation key into native author text before publishing"));
                }
                conversions.add(new FtbFieldConversion(file, "rewards[" + legacy + "]", "permission_level/elevate_perms", "config.source_mode/permission_level",
                        FtbFieldConversion.Status.MAPPED, "Zero inherits player permission; legacy elevate_perms becomes explicit level 2"));
            }
            recordConfigFields(raw, mappedType.equals(yourscraft.jasdewstarfield.brnquest.reward.RewardTypes.COMMAND)
                            ? Set.of("id", "type", "auto", "team_reward", "feedback_message") : Set.of("id", "type", "auto", "team_reward"), file,
                    "quests[" + quest + "].rewards[" + legacy + "]", conversions);
            config.put("title", translations.getOrDefault("reward." + legacy + ".title", ""));
            String ftbAuto = raw.contains("auto", Tag.TAG_STRING) ? raw.getString("auto") : "default";
            String policy = rewardPolicy(ftbAuto, defaultAutoClaim);
            conversions.add(new FtbFieldConversion(file, "quests[" + quest + "].rewards[" + legacy + "]",
                    "auto", "claim_policy", FtbFieldConversion.Status.MAPPED, ftbAuto + " -> " + policy));
            result.add(new RewardDefinition(bookId, remember(namespace, legacy, aliases), mappedType, config,
                    policy, raw.getBoolean("team_reward")));
        }
        return result;
    }

    /** Advancement identifiers and criteria are semantic strings, not quoted SNBT literals. */
    private static void advancementFields(ResourceLocation type, CompoundTag raw, Map<String,String> config) {
        if (!type.equals(yourscraft.jasdewstarfield.brnquest.task.advancement.AdvancementConfig.ID)) return;
        config.put("advancement", raw.contains("advancement") ? raw.getString("advancement") : "minecraft:story/root");
        config.put("criterion", raw.getString("criterion"));
    }

    private Map<String, String> flatten(CompoundTag raw, Set<String> semanticFields) {
        Map<String, String> result = new TreeMap<>();
        for (String key : raw.getAllKeys()) if (!key.equals("id") && !key.equals("type")
                && !semanticFields.contains(key)) result.put(key, raw.get(key).toString());
        return result;
    }

    private void recordMappedFields(CompoundTag raw, String file, String path,
                                    List<FtbFieldConversion> conversions, Map<String, String> mappings) {
        mappings.forEach((source, target) -> {
            if (raw.contains(source)) conversions.add(new FtbFieldConversion(file, path, source, target,
                    FtbFieldConversion.Status.MAPPED, "Converted to native field"));
        });
    }

    private void recordConfigFields(CompoundTag raw, Set<String> excluded, String file, String path,
                                    List<FtbFieldConversion> conversions) {
        for (String key : raw.getAllKeys()) {
            if (!excluded.contains(key)) conversions.add(new FtbFieldConversion(file, path, key, "config." + key,
                    FtbFieldConversion.Status.MAPPED, "Preserved as typed configuration"));
        }
    }

    private Map<String, String> extensions(CompoundTag raw, Set<String> known, String file, String path,
                                           List<FtbFieldConversion> conversions) {
        Map<String, String> result = new TreeMap<>();
        for (String key : raw.getAllKeys()) {
            if (known.contains(key)) continue;
            result.put("ftb." + key, raw.get(key).toString());
            conversions.add(new FtbFieldConversion(file, path, key, "extensions.ftb." + key,
                    FtbFieldConversion.Status.PRESERVED_EXTENSION, "Preserved as SNBT"));
        }
        return result;
    }

    /** SNBT int arrays and numeric lists both map to three integer coordinates, never a radius. */
    private static String locationVector(Tag value, String fallback) {
        if (value == null) return fallback;
        int[] values;
        if (value instanceof net.minecraft.nbt.IntArrayTag array) values = array.getAsIntArray();
        else if (value instanceof ListTag list && list.size() == 3) {
            values = new int[3];
            for (int i=0;i<3;i++) {
                if (!(list.get(i) instanceof net.minecraft.nbt.NumericTag number) || number.getAsDouble() != number.getAsInt())
                    throw new IllegalArgumentException("Location vector must contain integers");
                values[i]=number.getAsInt();
            }
        } else throw new IllegalArgumentException("Location vector must contain exactly three integers");
        if (values.length != 3) throw new IllegalArgumentException("Location vector must contain exactly three integers");
        return values[0]+","+values[1]+","+values[2];
    }

    private void warnUnknown(String type, String file, String path, String id, DiagnosticReport report) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        boolean supported = BUILT_IN_TYPES.contains(builtInPath(type));
        if (!supported && !normalized.contains(":")) {
            report.add(problem(Diagnostic.Severity.ERROR, "BQF-102", file, path, id, "Unsupported type: " + type));
        }
    }

    private void recordTypeConversion(String file, String path, String sourceType, ResourceLocation targetType,
                                      List<FtbFieldConversion> conversions) {
        boolean builtIn = BUILT_IN_TYPES.contains(builtInPath(sourceType));
        boolean namespaced = sourceType != null && sourceType.contains(":");
        FtbFieldConversion.Status status = builtIn ? FtbFieldConversion.Status.MAPPED
                : namespaced ? FtbFieldConversion.Status.PRESERVED_EXTENSION
                : FtbFieldConversion.Status.UNSUPPORTED;
        conversions.add(new FtbFieldConversion(file, path, "type", "type", status,
                sourceType + " -> " + targetType));
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
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        String builtInPath = builtInPath(type);
        if (builtInPath.equals("observation")) return yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterConfig.OBSERVE;
        if (builtInPath.equals("kill")) return yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterConfig.KILL;
        if (BUILT_IN_TYPES.contains(builtInPath)) {
            return ResourceLocation.fromNamespaceAndPath("brnquest", builtInPath);
        }
        ResourceLocation namespaced = ResourceLocation.tryParse(normalized);
        if (namespaced != null && normalized.contains(":")) return namespaced;
        return ResourceLocation.fromNamespaceAndPath("ftbquests", normalized.isBlank() ? "unknown" : normalized);
    }

    private String rewardPolicy(String value, String inherited) {
        String resolved = value == null || value.isBlank() || value.equalsIgnoreCase("default") ? inherited : value;
        return switch (resolved == null ? "" : resolved.toLowerCase(Locale.ROOT)) {
            case "enabled" -> RewardClaimPolicy.AUTO_VISIBLE.serializedName();
            case "no_toast" -> RewardClaimPolicy.AUTO_SILENT.serializedName();
            case "invisible" -> RewardClaimPolicy.AUTO_HIDDEN.serializedName();
            default -> RewardClaimPolicy.MANUAL.serializedName();
        };
    }

    private Map<String, InheritedAppearance> readPresets(CompoundTag raw) {
        Map<String, InheritedAppearance> result = new TreeMap<>();
        for (String name : raw.getAllKeys()) {
            CompoundTag value = raw.getCompound(name);
            result.put(name, new InheritedAppearance(value.getString("shape"),
                    value.contains("size", Tag.TAG_ANY_NUMERIC) ? value.getDouble("size") : 1.0));
        }
        return result;
    }

    private InheritedAppearance inheritedAppearance(CompoundTag raw, InheritedAppearance parent,
                                                     Map<String, InheritedAppearance> presets) {
        InheritedAppearance preset = presets.getOrDefault(raw.getString("preset"), parent);
        String shape = raw.getString("shape");
        if (shape.isBlank()) shape = raw.getString("default_quest_shape");
        if (shape.isBlank()) shape = preset.shape();
        double size = raw.contains("size", Tag.TAG_ANY_NUMERIC) ? raw.getDouble("size")
                : raw.contains("default_quest_size", Tag.TAG_ANY_NUMERIC) ? raw.getDouble("default_quest_size")
                : preset.size();
        return new InheritedAppearance(shape, size);
    }

    private record InheritedAppearance(String shape, double size) {}

    private record InheritedBehavior(boolean hideUntilDependenciesVisible, boolean hideUntilDependenciesComplete,
                                     boolean hideDetailsUntilStartable, boolean hideTextUntilComplete,
                                     boolean sequentialTasks, boolean repeatable) {}

    /** FTB tristates are absent when inherited and explicit booleans when overridden. */
    private boolean inheritedBoolean(CompoundTag raw, String key, boolean inherited) {
        return raw.contains(key, Tag.TAG_BYTE) ? raw.getBoolean(key) : inherited;
    }

    private Diagnostic problem(Diagnostic.Severity severity, String code, String file, String path, String id, String message) {
        return new Diagnostic(severity, code, file, path, id, Objects.requireNonNullElse(message, "Unknown error"));
    }
}
