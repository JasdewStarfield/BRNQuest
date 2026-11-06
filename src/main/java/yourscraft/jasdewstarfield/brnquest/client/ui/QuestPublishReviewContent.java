package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewModel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewText;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.NativeText;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchema;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchemas;

import java.util.*;
import java.util.function.Function;

/** Resolves review rows from an immutable draft; bounded wire JSON is reserved for technical details. */
final class QuestPublishReviewContent {
    private static final String PREFIX = "screen.brnquest.editor.publish.review.content.";
    record View(Component heading, Component detail, List<Component> fields, ResourceLocation target) {
        View { fields = List.copyOf(fields); }
    }
    private record Translation(ResourceLocation target, String kind, String field) {}
    private final QuestBookSnapshot snapshot;
    private final String locale;
    private final Map<ResourceLocation, Object> objects = new HashMap<>();
    private final Map<ResourceLocation, ResourceLocation> parents = new HashMap<>();
    private final Map<String, Translation> translations = new HashMap<>();
    private final Map<ResourceLocation, List<Component>> summaries = new HashMap<>();
    private final Map<ResourceLocation, ConfigEditorSchema> schemas = new HashMap<>();
    private final Map<ResourceLocation, List<Component>> metadata = new HashMap<>();
    private final Map<ResourceLocation, List<Component>> details = new HashMap<>();

    QuestPublishReviewContent(QuestBookSnapshot snapshot, String locale) {
        this.snapshot = snapshot;
        this.locale = locale;
        if (snapshot == null) return;
        var book = snapshot.book();
        objects.put(book.id(), book);
        translations.put("title", new Translation(book.id(), "BOOK", "title"));
        for (var group : book.chapterGroups()) {
            objects.put(group.id(), group); parents.put(group.id(), book.id());
            translations.put(BookText.structureTitleKey(book, "chapter_group", group.id()),
                    new Translation(group.id(), "CHAPTER_GROUP", "title"));
        }
        for (var chapter : book.chapters()) {
            objects.put(chapter.id(), chapter); parents.put(chapter.id(), chapter.groupId());
            translations.put(BookText.structureTitleKey(book, "chapter", chapter.id()),
                    new Translation(chapter.id(), "CHAPTER", "title"));
            for (var quest : chapter.quests()) {
                objects.put(quest.id(), quest); parents.put(quest.id(), chapter.id());
                for (String field : List.of("title", "quest_subtitle", "quest_desc", "quest_desc_format"))
                    translations.put(BookText.questPrefix(quest) + field, new Translation(quest.id(), "QUEST", field));
                for (var task : quest.tasks()) { objects.put(task.id(), task); parents.put(task.id(), quest.id()); }
                for (var reward : quest.rewards()) { objects.put(reward.id(), reward); parents.put(reward.id(), quest.id()); }
            }
        }
        // Both native IDs and imported aliases can occur in older translation tables.
        var aliases = new HashMap<>(book.legacyIds());
        objects.keySet().forEach(id -> aliases.put(id.toString(), id));
        aliases.forEach((alias, id) -> {
            if (alias.startsWith("@")) return;
            Object object = objects.get(id);
            if (object instanceof QuestDefinition) {
                for (String field : List.of("title", "quest_subtitle", "quest_desc", "quest_desc_format"))
                    translations.putIfAbsent("quest." + alias + "." + field, new Translation(id, "QUEST", field));
            } else if (object instanceof ChapterDefinition || object instanceof ChapterGroupDefinition) {
                String kind = object instanceof ChapterDefinition ? "chapter" : "chapter_group";
                translations.putIfAbsent(kind + "." + alias + ".title", new Translation(id, kind.toUpperCase(Locale.ROOT), "title"));
            }
        });
    }

    View change(EditorPublishReviewModel.Change change, Function<ResourceLocation, QuestPublishReviewSection.Presentation> resolver) {
        ResourceLocation id = ResourceLocation.tryParse(change.objectId());
        var localized = translation(change.path(), id);
        if (localized != null) {
            List<Component> fields = translationFields(change.path(), localized);
            var heading = namedHeading(name(localized.target(), resolver), local("translation", objectKind(localized.kind())));
            var values = EditorPublishReviewText.textValue(change.before()).copy().append(" → ")
                    .append(EditorPublishReviewText.textValue(change.after()));
            var detail = join(fields).copy().append(": ").append(values);
            var details = new ArrayList<>(fields);
            details.add(local("before", EditorPublishReviewText.textValue(change.before())));
            details.add(local("after", EditorPublishReviewText.textValue(change.after())));
            return new View(heading, detail, details, localized.target());
        }
        // Removed objects may no longer be in the draft. Prefer the server's stored old name when available.
        boolean removed = change.kind().equals("REMOVED");
        String stored = EditorPublishReviewText.storedTitle(removed ? change.before() : change.after());
        if (stored.isBlank() && change.objectKind().equals("BOOK") && (removed || change.kind().equals("ADDED")))
            stored = EditorPublishReviewText.compactValue(removed ? change.before() : change.after());
        Component name = removed ? Component.literal(stored) : name(id, resolver);
        if (name.getString().isBlank()) name = stored.isBlank() ? local("unnamed") : Component.literal(stored);
        List<Component> fields = removed || !objects.containsKey(id) ? List.of(local(removed ? "removed" : "unavailable"))
                : summaries.computeIfAbsent(id, key -> describe(key, resolver));
        boolean wholeObject = change.kind().equals("ADDED") || removed;
        Component detail = wholeObject ? join(fields) : EditorPublishReviewText.readableDetail(change);
        List<Component> fullFields = wholeObject && !removed && objects.containsKey(id)
                ? details.computeIfAbsent(id, key -> details(key, fields)) : fields;
        return new View(namedHeading(name, EditorPublishReviewText.heading(change)), detail, fullFields, id);
    }

    /** Diagnostics use the same resolved owner and field labels as translation changes. */
    View diagnostic(EditorPublishReviewModel.Diagnostic diagnostic,
                    Function<ResourceLocation, QuestPublishReviewSection.Presentation> resolver) {
        ResourceLocation id = ResourceLocation.tryParse(diagnostic.objectId());
        var localized = translation(diagnostic.path(), id);
        ResourceLocation target = localized == null ? id : localized.target();
        var fields = new ArrayList<Component>();
        fields.add(name(target, resolver));
        if (localized != null) fields.addAll(translationFields(diagnostic.path(), localized));
        else if (!diagnostic.path().isBlank()) fields.add(EditorPublishReviewText.readablePath(diagnostic.path()));
        return new View(EditorDiagnosticPresentation.severity(diagnostic.severity()).copy().append(" · ")
                .append(EditorDiagnosticPresentation.diagnostic(diagnostic)), join(fields), fields, target);
    }

    private Component name(ResourceLocation id, Function<ResourceLocation, QuestPublishReviewSection.Presentation> resolver) {
        if (snapshot == null) return Component.empty();
        var book = snapshot.book();
        Object object = objects.get(id);
        String title = object instanceof QuestBookDefinition ? BookText.title(book, locale)
                : object instanceof ChapterGroupDefinition group ? BookText.structureTitle(book, "chapter_group", id, locale, group.title())
                : object instanceof ChapterDefinition chapter ? BookText.structureTitle(book, "chapter", id, locale, chapter.title())
                : object instanceof QuestDefinition quest ? BookText.quest(book, quest, locale, "title", quest.title()) : "";
        if (!title.isBlank()) return NativeText.component(title);
        var presentation = id == null ? null : resolver.apply(id);
        return presentation == null ? Component.empty() : presentation.name();
    }

    private List<Component> describe(ResourceLocation id, Function<ResourceLocation, QuestPublishReviewSection.Presentation> resolver) {
        var fields = new ArrayList<Component>();
        Object object = objects.get(id);
        ResourceLocation parent = parents.get(id);
        if (parent != null) fields.add(local("parent", name(parent, resolver)));
        if (object instanceof QuestBookDefinition book) {
            fields.add(local("book_counts", book.chapterGroups().size(), book.chapters().size(), snapshot.quests().size()));
        } else if (object instanceof ChapterGroupDefinition group) {
            fields.add(local("chapters", snapshot.book().chapters().stream().filter(c -> c.groupId().equals(group.id())).count()));
        } else if (object instanceof ChapterDefinition chapter) {
            fields.add(local("quests", chapter.quests().size()));
        } else if (object instanceof QuestDefinition quest) {
            fields.add(local("quest_counts", quest.tasks().size(), quest.rewards().size(), quest.dependencies().size()));
        } else if (object instanceof TaskDefinition task) {
            var presentation = resolver.apply(id);
            if (presentation != null && !presentation.objective().getString().isBlank())
                fields.add(presentation.objective());
            fields.add(local("type", ClientTaskPresentationRegistry.get(task.typeId()).typeName(ApiViews.task(task))));
            fields.add(local(task.optional() ? "optional" : "required"));
            metadata.put(id, List.copyOf(fields));
            configFields(fields, schemas.computeIfAbsent(id, key -> ConfigEditorSchemas.forTask(ApiViews.task(task))));
        } else if (object instanceof RewardDefinition reward) {
            fields.add(local("type", ClientRewardPresentationRegistry.get(reward.typeId()).typeName(ApiViews.reward(reward))));
            fields.add(EditorPublishReviewText.readableDetail(new EditorPublishReviewModel.Change(
                    "PROPERTY_CHANGED", "REWARD", id.toString(), "claim_policy", "", reward.claimPolicy())));
            fields.add(local(reward.teamReward() ? "team" : "personal"));
            metadata.put(id, List.copyOf(fields));
            configFields(fields, schemas.computeIfAbsent(id, key -> ConfigEditorSchemas.forReward(ApiViews.reward(reward))));
        }
        return List.copyOf(fields);
    }

    /** The full draft still contains long config values even when the wire preview was shortened. */
    private List<Component> details(ResourceLocation id, List<Component> summary) {
        var schema = schemas.get(id);
        if (schema == null) return summary;
        var fields = new ArrayList<>(metadata.getOrDefault(id, summary));
        new TreeMap<>(schema.rawConfig()).forEach((key, value) -> {
            var descriptor = schema.fields().stream().filter(field -> field.key().equals(key)).findFirst();
            Component label = descriptor.filter(field -> !field.labelKey().isBlank())
                    .<Component>map(field -> Component.translatable(field.labelKey()))
                    .orElseGet(() -> EditorPublishReviewText.readablePath("config." + key));
            fields.add(label.copy().append(": ").append(EditorPublishReviewText.textValue(value)));
        });
        return List.copyOf(fields);
    }

    /** Descriptor labels and enum labels belong to each type; extension fields retain their literal keys. */
    private static void configFields(List<Component> fields, ConfigEditorSchema schema) {
        Set<String> described = new HashSet<>();
        for (var descriptor : schema.fields()) {
            described.add(descriptor.key());
            String value = schema.rawConfig().getOrDefault(descriptor.key(), descriptor.defaultValue().orElse(""));
            if (value.isBlank() || descriptor.key().equals("title")) continue;
            Component label = descriptor.labelKey().isBlank() ? Component.literal(descriptor.key()) : Component.translatable(descriptor.labelKey());
            Component display = descriptor.valueLabelKeys().containsKey(value) ? ConfigFieldLabels.value(descriptor, value)
                    : Component.literal(EditorPublishReviewText.compactValue(value));
            fields.add(label.copy().append(": ").append(display));
        }
        new TreeMap<>(schema.rawConfig()).forEach((key, value) -> {
            if (!described.contains(key) && !key.equals("title") && !value.isBlank())
                fields.add(EditorPublishReviewText.readablePath("config." + key).copy().append(": " + EditorPublishReviewText.compactValue(value)));
        });
    }

    private Translation translation(String path, ResourceLocation id) {
        if (!path.startsWith("localization.")) return null;
        int split = path.indexOf('.', "localization.".length());
        if (split < 0) return null; // fallback_locale is book metadata, not a translated field.
        String key = path.substring(split + 1);
        Translation target = translations.get(key);
        // Author validation uses localization.<locale>.title on the quest instead of a flattened book key.
        if (key.equals("title") && objects.get(id) instanceof QuestDefinition) return new Translation(id, "QUEST", "title");
        return target == null ? new Translation(id, "BOOK", key) : target;
    }

    private static List<Component> translationFields(String path, Translation translation) {
        String locale = path.substring("localization.".length(), path.indexOf('.', "localization.".length()));
        String field = switch (translation.field()) {
            case "quest_subtitle" -> "subtitle";
            case "quest_desc" -> "description";
            case "quest_desc_format" -> "description_format";
            default -> translation.field();
        };
        return List.of(local("locale", locale), EditorPublishReviewText.readablePath(field));
    }

    private static Component objectKind(String kind) {
        return Component.translatable("screen.brnquest.editor.publish.review.diff.object." + kind.toLowerCase(Locale.ROOT));
    }
    private static Component namedHeading(Component name, Component heading) {
        return name.getString().isBlank() ? heading : name.copy().append(" · ").append(heading);
    }
    private static Component join(List<Component> fields) {
        var text = Component.empty();
        for (var field : fields) {
            if (field.getString().isBlank()) continue;
            if (!text.getString().isBlank()) text.append(" · ");
            text.append(field);
        }
        return text;
    }
    private static Component local(String suffix, Object... arguments) { return Component.translatable(PREFIX + suffix, arguments); }
}
