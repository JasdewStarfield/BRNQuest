package yourscraft.jasdewstarfield.brnquest.author;

import yourscraft.jasdewstarfield.brnquest.data.BookText;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Pure immutable CRUD operations; no method can partially change a live draft. */
public final class DraftBookEditor {
    private DraftBookEditor() {}

    public static AuthorOperationResult<DraftChange> setBookTitle(QuestBookDefinition book, String title) {
        if (title == null || title.isBlank()) return invalid("BOOK_TITLE_REQUIRED", "Book title is required");
        return changed(new QuestBookDefinition(book.id(), book.schemaVersion(), title,
                book.chapterGroups(), book.chapters(), book.legacyIds(), book.localization(), book.extensions()), book.id());
    }

    public static AuthorOperationResult<DraftChange> addGroup(QuestBookDefinition book, ChapterGroupDefinition group) {
        if (group == null || !group.bookId().equals(book.id())) return invalid("GROUP_BOOK_MISMATCH", "Group belongs to another book");
        if (book.chapterGroups().stream().anyMatch(value -> value.id().equals(group.id()))) return conflict("DUPLICATE_GROUP_ID", group.id());
        List<ChapterGroupDefinition> groups = append(book.chapterGroups(), group);
        return changed(withGroups(book, groups), group.id());
    }

    public static AuthorOperationResult<DraftChange> copyGroup(QuestBookDefinition book, ResourceLocation sourceId,
                                                                ChapterGroupDefinition copy) {
        if (book.chapterGroups().stream().noneMatch(group -> group.id().equals(sourceId))) return notFound("GROUP_NOT_FOUND", sourceId);
        if (copy == null || copy.id().equals(sourceId)) return invalid("COPY_ID_REQUIRED", "Group copy requires a new stable ID");
        return addGroup(book, copy);
    }

    public static AuthorOperationResult<DraftChange> updateGroup(QuestBookDefinition book, ResourceLocation groupId,
                                                                  ChapterGroupDefinition replacement) {
        if (replacement == null || !replacement.id().equals(groupId)) return invalid("STABLE_ID_REQUIRED", "Group ID cannot change in an update");
        return replaceGroup(book, groupId, ignored -> replacement);
    }

    /** A localized property form edits only the visible language, preserving all other translations. */
    public static AuthorOperationResult<DraftChange> updateGroupProperties(QuestBookDefinition book,
            ResourceLocation groupId, ChapterGroupDefinition replacement, String locale) {
        if (locale == null || locale.isBlank()) return updateGroup(book, groupId, replacement);
        ChapterGroupDefinition source = book.chapterGroups().stream().filter(g -> g.id().equals(groupId)).findFirst().orElse(null);
        if (source == null) return notFound("GROUP_NOT_FOUND", groupId);
        String normalized = BookLocalization.normalizeLocale(locale);
        boolean titleChanged = !BookText.structureTitle(book, "chapter_group", groupId, normalized, source.title()).equals(replacement.title());
        boolean fallback = normalized.equals(book.localization().fallbackLocale());
        var metadata = new ChapterGroupDefinition(replacement.bookId(), replacement.id(),
                titleChanged && fallback ? replacement.title() : source.title(), replacement.order(),
                replacement.icon(), replacement.description(), replacement.extensions());
        var updated = updateGroup(book, groupId, metadata);
        if (!updated.success() || !titleChanged) return updated;
        String key = BookText.structureTitleKey(book, "chapter_group", groupId);
        Map<String, Map<String, String>> translations = new java.util.TreeMap<>(book.localization().translations());
        Map<String, String> values = new java.util.TreeMap<>(translations.getOrDefault(normalized, Map.of()));
        // The native title is canonical in the fallback locale, just as for quest text editing.
        if (fallback) values.remove(key);
        else values.put(key, replacement.title());
        if (values.isEmpty()) translations.remove(normalized);
        else translations.put(normalized, values);
        var changedBook = updated.value().book();
        return changed(new QuestBookDefinition(changedBook.id(), changedBook.schemaVersion(), changedBook.title(),
                changedBook.chapterGroups(), changedBook.chapters(), changedBook.legacyIds(),
                new BookLocalization(book.localization().fallbackLocale(), translations), changedBook.extensions()), groupId);
    }

    public static AuthorOperationResult<DraftChange> moveGroup(QuestBookDefinition book, ResourceLocation groupId,
                                                                int targetIndex) {
        List<ChapterGroupDefinition> ordered = book.chapterGroups().stream()
                .sorted(java.util.Comparator.comparingInt(ChapterGroupDefinition::order)
                        .thenComparing(group -> group.id().toString())).toList();
        int source = indexOf(ordered, groupId, ChapterGroupDefinition::id);
        if (source < 0) return notFound("GROUP_NOT_FOUND", groupId);
        List<ChapterGroupDefinition> moved = new ArrayList<>(ordered);
        ChapterGroupDefinition value = moved.remove(source);
        moved.add(Math.max(0, Math.min(targetIndex, moved.size())), value);
        List<ChapterGroupDefinition> normalized = new ArrayList<>();
        for (int index = 0; index < moved.size(); index++) {
            ChapterGroupDefinition group = moved.get(index);
            normalized.add(new ChapterGroupDefinition(group.bookId(), group.id(), group.title(), index,
                    group.icon(), group.description(), group.extensions()));
        }
        return changed(withGroups(book, normalized), groupId);
    }

    public static AuthorOperationResult<DraftChange> removeGroup(QuestBookDefinition book, ResourceLocation groupId) {
        List<ResourceLocation> children = book.chapters().stream().filter(c -> c.groupId().equals(groupId)).map(ChapterDefinition::id).toList();
        if (!children.isEmpty()) return conflict("GROUP_NOT_EMPTY", groupId + " contains " + children);
        return remove(book, book.chapterGroups(), groupId, ChapterGroupDefinition::id, DraftBookEditor::withGroups, "GROUP_NOT_FOUND");
    }

    public static AuthorOperationResult<DraftChange> removeGroupWithContents(QuestBookDefinition book,
                                                                              ResourceLocation groupId) {
        if (book.chapterGroups().stream().noneMatch(group -> group.id().equals(groupId))) return notFound("GROUP_NOT_FOUND", groupId);
        Set<ResourceLocation> chapters = book.chapters().stream().filter(chapter -> chapter.groupId().equals(groupId))
                .map(ChapterDefinition::id).collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> quests = book.chapters().stream().filter(chapter -> chapters.contains(chapter.id()))
                .flatMap(chapter -> chapter.quests().stream()).map(QuestDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        QuestBookDefinition pruned = removeQuestSetAndReferences(book, quests);
        List<ChapterDefinition> remainingChapters = pruned.chapters().stream()
                .filter(chapter -> !chapters.contains(chapter.id())).toList();
        List<ChapterGroupDefinition> remainingGroups = pruned.chapterGroups().stream()
                .filter(group -> !group.id().equals(groupId)).toList();
        List<ResourceLocation> affected = new ArrayList<>();
        affected.add(groupId);
        affected.addAll(chapters);
        affected.addAll(quests);
        affected.addAll(dependencyReferrers(book, quests));
        return changed(new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(), remainingGroups,
                remainingChapters, pruned.legacyIds(), book.localization(), book.extensions()),
                affected.toArray(ResourceLocation[]::new));
    }

    public static AuthorOperationResult<DraftChange> addChapter(QuestBookDefinition book, ChapterDefinition chapter) {
        if (chapter == null || !chapter.bookId().equals(book.id()) || !chapter.quests().isEmpty()) {
            return invalid("INVALID_NEW_CHAPTER", "New chapter must belong to the book and contain no quests");
        }
        if (book.chapters().stream().anyMatch(value -> value.id().equals(chapter.id()))) return conflict("DUPLICATE_CHAPTER_ID", chapter.id());
        return changed(withChapters(book, append(book.chapters(), chapter)), chapter.id());
    }

    public static AuthorOperationResult<DraftChange> copyChapter(QuestBookDefinition book, ResourceLocation sourceId,
                                                                  ChapterDefinition copy) {
        if (chapter(book, sourceId) == null) return notFound("CHAPTER_NOT_FOUND", sourceId);
        if (copy == null || copy.id().equals(sourceId) || !copy.bookId().equals(book.id())) {
            return invalid("COPY_ID_REQUIRED", "Chapter copy requires a new stable ID in the same book");
        }
        if (book.chapters().stream().anyMatch(chapter -> chapter.id().equals(copy.id()))) return conflict("DUPLICATE_CHAPTER_ID", copy.id());
        if (copy.quests().stream().anyMatch(quest -> !quest.bookId().equals(book.id()) || !quest.chapterId().equals(copy.id()))) {
            return invalid("QUEST_CONTAINER_MISMATCH", "Copied quests must belong to the copied chapter");
        }
        return changed(withChapters(book, append(book.chapters(), copy)), containedIds(copy));
    }

    public static AuthorOperationResult<DraftChange> updateChapter(QuestBookDefinition book, ResourceLocation chapterId,
                                                                    ChapterDefinition replacement) {
        ChapterDefinition old = chapter(book, chapterId);
        if (old == null) return notFound("CHAPTER_NOT_FOUND", chapterId);
        if (replacement == null || !replacement.id().equals(chapterId) || !replacement.bookId().equals(book.id())) {
            return invalid("STABLE_ID_REQUIRED", "Chapter ID and book ownership cannot change in an update");
        }
        // Child quests are owned by their dedicated operations, not an enclosing overwrite.
        ChapterDefinition safe = new ChapterDefinition(book.id(), chapterId, replacement.groupId(), replacement.title(),
                replacement.icon(), replacement.order(), old.quests(), replacement.extensions());
        return replaceChapter(book, chapterId, ignored -> safe, chapterId);
    }

    public static AuthorOperationResult<DraftChange> moveChapterOrder(QuestBookDefinition book,
                                                                       ResourceLocation chapterId,
                                                                       int targetIndex) {
        ChapterDefinition source = chapter(book, chapterId);
        if (source == null) return notFound("CHAPTER_NOT_FOUND", chapterId);
        List<ChapterDefinition> siblings = book.chapters().stream()
                .filter(chapter -> chapter.groupId().equals(source.groupId()))
                .sorted(java.util.Comparator.comparingInt(ChapterDefinition::order)
                        .thenComparing(chapter -> chapter.id().toString())).toList();
        int sourceIndex = indexOf(siblings, chapterId, ChapterDefinition::id);
        List<ChapterDefinition> moved = new ArrayList<>(siblings);
        ChapterDefinition value = moved.remove(sourceIndex);
        moved.add(Math.max(0, Math.min(targetIndex, moved.size())), value);
        Map<ResourceLocation, Integer> orders = new java.util.HashMap<>();
        for (int index = 0; index < moved.size(); index++) orders.put(moved.get(index).id(), index);
        List<ChapterDefinition> normalized = book.chapters().stream().map(chapter -> {
            Integer order = orders.get(chapter.id());
            return order == null ? chapter : new ChapterDefinition(chapter.bookId(), chapter.id(), chapter.groupId(),
                    chapter.title(), chapter.icon(), order, chapter.quests(), chapter.extensions());
        }).toList();
        return changed(withChapters(book, normalized), chapterId);
    }

    /** Inserts into a group atomically, normalizing both lists so one undo restores the complete move. */
    public static AuthorOperationResult<DraftChange> moveChapterToGroup(QuestBookDefinition book,
            ResourceLocation chapterId, ResourceLocation groupId, int targetIndex) {
        ChapterDefinition source = chapter(book, chapterId);
        if (source == null) return notFound("CHAPTER_NOT_FOUND", chapterId);
        if (book.chapterGroups().stream().noneMatch(group -> group.id().equals(groupId)))
            return notFound("GROUP_NOT_FOUND", groupId);
        if (source.groupId().equals(groupId)) return moveChapterOrder(book, chapterId, targetIndex);
        var order = java.util.Comparator.comparingInt(ChapterDefinition::order).thenComparing(c -> c.id().toString());
        var oldSiblings = book.chapters().stream().filter(c -> c.groupId().equals(source.groupId()) && !c.id().equals(chapterId))
                .sorted(order).toList();
        var newSiblings = new ArrayList<>(book.chapters().stream().filter(c -> c.groupId().equals(groupId)).sorted(order).toList());
        newSiblings.add(Math.max(0, Math.min(targetIndex, newSiblings.size())), source);
        var replacements = new java.util.HashMap<ResourceLocation, ChapterDefinition>();
        for (var siblings : List.of(oldSiblings, newSiblings)) {
            for (int index = 0; index < siblings.size(); index++) {
                var c = siblings.get(index);
                replacements.put(c.id(), new ChapterDefinition(c.bookId(), c.id(),
                        siblings == newSiblings ? groupId : source.groupId(), c.title(), c.icon(), index, c.quests(), c.extensions()));
            }
        }
        return changed(withChapters(book, book.chapters().stream().map(c -> replacements.getOrDefault(c.id(), c)).toList()), chapterId);
    }

    public static AuthorOperationResult<DraftChange> removeChapter(QuestBookDefinition book, ResourceLocation chapterId) {
        ChapterDefinition old = chapter(book, chapterId);
        if (old == null) return notFound("CHAPTER_NOT_FOUND", chapterId);
        if (!old.quests().isEmpty()) return conflict("CHAPTER_NOT_EMPTY", chapterId + " contains quests");
        return remove(book, book.chapters(), chapterId, ChapterDefinition::id, DraftBookEditor::withChapters, "CHAPTER_NOT_FOUND");
    }

    public static AuthorOperationResult<DraftChange> removeChapterWithContents(QuestBookDefinition book,
                                                                                ResourceLocation chapterId) {
        ChapterDefinition chapter = chapter(book, chapterId);
        if (chapter == null) return notFound("CHAPTER_NOT_FOUND", chapterId);
        Set<ResourceLocation> quests = chapter.quests().stream().map(QuestDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        QuestBookDefinition pruned = removeQuestSetAndReferences(book, quests);
        List<ChapterDefinition> chapters = pruned.chapters().stream().filter(value -> !value.id().equals(chapterId)).toList();
        List<ResourceLocation> affected = new ArrayList<>();
        affected.add(chapterId);
        affected.addAll(quests);
        affected.addAll(dependencyReferrers(book, quests));
        return changed(withChapters(pruned, chapters), affected.toArray(ResourceLocation[]::new));
    }

    public static AuthorOperationResult<DraftChange> addQuest(QuestBookDefinition book, ResourceLocation chapterId,
                                                               QuestDefinition quest) {
        if (quest == null || !quest.bookId().equals(book.id()) || !quest.chapterId().equals(chapterId)) {
            return invalid("QUEST_CONTAINER_MISMATCH", "Quest ownership does not match its chapter");
        }
        if (quest(book, quest.id()) != null) return conflict("DUPLICATE_QUEST_ID", quest.id());
        if (questLegacySourceExists(book, quest.id())) return retiredQuestId(quest.id());
        return replaceChapter(book, chapterId, chapter -> new ChapterDefinition(chapter.bookId(), chapter.id(),
                chapter.groupId(), chapter.title(), chapter.icon(), chapter.order(), append(chapter.quests(), quest),
                chapter.extensions()),
                questObjectIds(quest));
    }

    public static AuthorOperationResult<DraftChange> copyQuest(QuestBookDefinition book, ResourceLocation sourceId,
                                                                QuestDefinition copy) {
        if (quest(book, sourceId) == null) return notFound("QUEST_NOT_FOUND", sourceId);
        if (copy == null || copy.id().equals(sourceId)) return invalid("COPY_ID_REQUIRED", "Quest copy requires a new stable ID");
        AuthorOperationResult<DraftChange> added = addQuest(book, copy.chapterId(), copy);
        if (!added.success()) return added;
        // Copy every locale and extension text suffix to the new identity; later edits stay independent.
        String sourcePrefix = BookText.questPrefix(quest(book, sourceId));
        String targetPrefix = BookText.questPrefix(copy);
        Map<String, Map<String, String>> translations = new java.util.TreeMap<>();
        book.localization().translations().forEach((locale, source) -> {
            Map<String, String> values = new java.util.TreeMap<>(source);
            source.forEach((key, value) -> {
                if (key.startsWith(sourcePrefix)) values.put(targetPrefix + key.substring(sourcePrefix.length()), value);
            });
            translations.put(locale, values);
        });
        QuestBookDefinition candidate = added.value().book();
        return AuthorOperationResult.success(added.code(), added.message(), new DraftChange(
                new QuestBookDefinition(candidate.id(), candidate.schemaVersion(), candidate.title(),
                        candidate.chapterGroups(), candidate.chapters(), candidate.legacyIds(),
                        new BookLocalization(book.localization().fallbackLocale(), translations), candidate.extensions()),
                added.value().affectedObjects()));
    }

    public static AuthorOperationResult<DraftChange> updateQuest(QuestBookDefinition book, ResourceLocation questId,
                                                                  QuestDefinition replacement) {
        QuestLocation location = questLocation(book, questId);
        if (location == null) return notFound("QUEST_NOT_FOUND", questId);
        if (replacement == null || !replacement.id().equals(questId)
                || !replacement.bookId().equals(book.id()) || !replacement.chapterId().equals(location.chapter.id())) {
            return invalid("STABLE_ID_REQUIRED", "Quest ID and container cannot change in an update");
        }
        // Basic properties may change here; dependency/task/reward collections have
        // their own stable-ID operations and cannot be overwritten accidentally.
        QuestDefinition safe = new QuestDefinition(book.id(), questId, location.chapter.id(), replacement.title(),
                replacement.subtitle(), replacement.description(), replacement.icon(), replacement.x(), replacement.y(),
                location.chapter.quests().get(location.index).dependencies(),
                location.chapter.quests().get(location.index).tasks(),
                location.chapter.quests().get(location.index).rewards(), replacement.legacyId(),
                replacement.appearance(), replacement.behavior(), replacement.extensions());
        return replaceQuest(book, location, ignored -> safe, questId);
    }

    /**
     * Applies basic properties and an optional stable-ID rename as one draft revision.
     * The serialized sibling position is deliberately preserved because graph coordinates,
     * rather than file order, define the author- and player-facing layout.
     */
    public static AuthorOperationResult<DraftChange> updateQuestBasics(QuestBookDefinition book,
                                                                        ResourceLocation questId,
                                                                        QuestDefinition replacement) {
        QuestLocation location = questLocation(book, questId);
        if (location == null) return notFound("QUEST_NOT_FOUND", questId);
        if (replacement == null || !replacement.bookId().equals(book.id())
                || !replacement.chapterId().equals(location.chapter.id())) {
            return invalid("QUEST_CONTAINER_MISMATCH", "Quest ownership cannot change in a basic-property update");
        }
        ResourceLocation replacementId = replacement.id();
        if (!replacementId.equals(questId) && quest(book, replacementId) != null) {
            return conflict("DUPLICATE_QUEST_ID", replacementId);
        }
        ResourceLocation previousAliasTarget = book.legacyIds().get(replacementId.toString());
        if (!replacementId.equals(questId) && previousAliasTarget != null && !previousAliasTarget.equals(questId)) {
            // Renaming a quest back through its own alias chain is safe; assigning another
            // quest's retired source would attach historical player state to the wrong node.
            return retiredQuestId(replacementId);
        }

        List<ResourceLocation> affected = new ArrayList<>();
        affected.add(questId);
        if (!replacementId.equals(questId)) affected.add(replacementId);
        List<ChapterDefinition> chapters = new ArrayList<>();
        for (ChapterDefinition chapter : book.chapters()) {
            List<QuestDefinition> quests = new ArrayList<>();
            for (QuestDefinition current : chapter.quests()) {
                List<ResourceLocation> dependencies = current.dependencies().stream()
                        .map(id -> id.equals(questId) ? replacementId : id).toList();
                if (!dependencies.equals(current.dependencies()) && !affected.contains(current.id())) {
                    affected.add(current.id());
                }
                if (current.id().equals(questId)) {
                    quests.add(new QuestDefinition(book.id(), replacementId, chapter.id(), replacement.title(),
                            replacement.subtitle(), replacement.description(), replacement.icon(),
                            current.x(), current.y(), dependencies, current.tasks(), current.rewards(),
                            current.legacyId(), replacement.appearance(), replacement.behavior(), replacement.extensions()));
                } else {
                    quests.add(copyQuest(current, dependencies, current.tasks(), current.rewards()));
                }
            }
            chapters.add(withQuests(chapter, quests));
        }

        Map<String, ResourceLocation> aliases = new java.util.TreeMap<>(book.legacyIds());
        if (!replacementId.equals(questId)) {
            // Typed aliases use an explicit prefix and must not be rewritten by an unrelated quest rename.
            aliases.replaceAll((alias, target) -> !alias.startsWith("@task:") && !alias.startsWith("@reward:")
                    && target.equals(questId) ? replacementId : target);
            // Renaming back to a former alias must not leave a meaningless A -> A mapping.
            aliases.entrySet().removeIf(entry -> entry.getKey().equals(entry.getValue().toString()));
            aliases.put(questId.toString(), replacementId);
        }
        BookLocalization localization = replacementId.equals(questId) || !location.chapter.quests().get(location.index)
                .legacyId().isBlank() ? book.localization()
                : renameQuestTranslationPrefix(book.localization(), questId.toString(), replacementId.toString());
        QuestBookDefinition changed = new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(),
                book.chapterGroups(), chapters, aliases, localization, book.extensions());
        return changed(changed, affected.toArray(ResourceLocation[]::new));
    }

    /** Commits a multi-selection drag as one revision instead of one stale-prone request per node. */
    public static AuthorOperationResult<DraftChange> updateQuestPositions(QuestBookDefinition book,
                                                                           Map<ResourceLocation, Position> positions) {
        if (positions == null || positions.isEmpty()) return invalid("POSITIONS_REQUIRED", "At least one position is required");
        if (positions.values().stream().anyMatch(position -> position == null
                || !Double.isFinite(position.x()) || !Double.isFinite(position.y()))) {
            return invalid("INVALID_QUEST_POSITION", "Quest coordinates must be finite");
        }
        if (positions.keySet().stream().anyMatch(id -> quest(book, id) == null)) {
            return invalid("QUEST_NOT_FOUND", "Every moved quest must exist in the current draft");
        }
        List<ChapterDefinition> chapters = book.chapters().stream().map(chapter -> new ChapterDefinition(
                chapter.bookId(), chapter.id(), chapter.groupId(), chapter.title(), chapter.icon(), chapter.order(),
                chapter.quests().stream().map(quest -> {
                    Position position = positions.get(quest.id());
                    return position == null ? quest : new QuestDefinition(quest.bookId(), quest.id(), quest.chapterId(),
                            quest.title(), quest.subtitle(), quest.description(), quest.icon(), position.x(), position.y(),
                            quest.dependencies(), quest.tasks(), quest.rewards(), quest.legacyId(),
                            quest.appearance(), quest.behavior(), quest.extensions());
                }).toList(), chapter.extensions())).toList();
        return changed(withChapters(book, chapters), positions.keySet().toArray(ResourceLocation[]::new));
    }

    /** Updates one locale without rebuilding or trusting any client-owned quest structure. */
    public static AuthorOperationResult<DraftChange> updateQuestTranslation(QuestBookDefinition book,
                                                                             ResourceLocation questId,
                                                                             String locale, String title,
                                                                             String subtitle, String description) {
        QuestDefinition quest = quest(book, questId);
        if (quest == null) return notFound("QUEST_NOT_FOUND", questId);
        String normalizedLocale = BookLocalization.normalizeLocale(locale);
        String sourceId = quest.legacyId().isBlank() ? quest.id().toString() : quest.legacyId();
        String prefix = "quest." + sourceId + ".";
        if (normalizedLocale.equals(book.localization().fallbackLocale())) {
            // The native quest fields are the canonical fallback text. Keeping a second fallback-locale
            // copy in translations would let it shadow property and quick edits indefinitely.
            QuestDefinition replacement = new QuestDefinition(quest.bookId(), quest.id(), quest.chapterId(),
                    title, subtitle, description, quest.icon(), quest.x(), quest.y(), quest.dependencies(),
                    quest.tasks(), quest.rewards(), quest.legacyId(), quest.appearance(), quest.behavior(), quest.extensions());
            AuthorOperationResult<DraftChange> updated = updateQuest(book, questId, replacement);
            if (!updated.success()) return updated;
            QuestBookDefinition changedBook = updated.value().book();
            Map<String, Map<String, String>> translations = new java.util.TreeMap<>(
                    changedBook.localization().translations());
            Map<String, String> fallbackValues = new java.util.TreeMap<>(
                    translations.getOrDefault(normalizedLocale, Map.of()));
            fallbackValues.keySet().removeAll(List.of(prefix + "title", prefix + "quest_subtitle",
                    prefix + "quest_desc"));
            if (fallbackValues.isEmpty()) translations.remove(normalizedLocale);
            else translations.put(normalizedLocale, fallbackValues);
            QuestBookDefinition normalized = new QuestBookDefinition(changedBook.id(), changedBook.schemaVersion(),
                    changedBook.title(), changedBook.chapterGroups(), changedBook.chapters(), changedBook.legacyIds(),
                    new BookLocalization(changedBook.localization().fallbackLocale(), translations),
                    changedBook.extensions());
            return changed(normalized, questId);
        }
        Map<String, Map<String, String>> translations = new java.util.TreeMap<>(book.localization().translations());
        Map<String, String> values = new java.util.TreeMap<>(translations.getOrDefault(normalizedLocale, Map.of()));
        values.put(prefix + "title", title == null ? "" : title);
        values.put(prefix + "quest_subtitle", subtitle == null ? "" : subtitle);
        values.put(prefix + "quest_desc", description == null ? "" : description);
        translations.put(normalizedLocale, values);
        QuestBookDefinition changed = new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(),
                book.chapterGroups(), book.chapters(), book.legacyIds(),
                new BookLocalization(book.localization().fallbackLocale(), translations), book.extensions());
        return changed(changed, questId);
    }

    public record Position(double x, double y) {}

    public static AuthorOperationResult<DraftChange> moveQuest(QuestBookDefinition book, ResourceLocation questId,
                                                                ResourceLocation targetChapterId, int targetIndex) {
        QuestLocation source = questLocation(book, questId);
        ChapterDefinition target = chapter(book, targetChapterId);
        if (source == null) return notFound("QUEST_NOT_FOUND", questId);
        if (target == null) return notFound("CHAPTER_NOT_FOUND", targetChapterId);
        List<ChapterDefinition> chapters = new ArrayList<>(book.chapters());
        List<QuestDefinition> sourceQuests = new ArrayList<>(source.chapter.quests());
        QuestDefinition moved = sourceQuests.remove(source.index);
        chapters.set(source.chapterIndex, withQuests(source.chapter, sourceQuests));
        int targetChapterIndex = indexOf(chapters, targetChapterId, ChapterDefinition::id);
        List<QuestDefinition> targetQuests = source.chapter.id().equals(targetChapterId)
                ? new ArrayList<>(sourceQuests) : new ArrayList<>(chapters.get(targetChapterIndex).quests());
        int insertion = Math.max(0, Math.min(targetIndex, targetQuests.size()));
        QuestDefinition relocated = new QuestDefinition(book.id(), moved.id(), targetChapterId, moved.title(), moved.subtitle(),
                moved.description(), moved.icon(), moved.x(), moved.y(), moved.dependencies(), moved.tasks(), moved.rewards(),
                moved.legacyId(), moved.appearance(), moved.behavior(), moved.extensions());
        targetQuests.add(insertion, relocated);
        chapters.set(targetChapterIndex, withQuests(chapters.get(targetChapterIndex), targetQuests));
        return changed(withChapters(book, chapters), questId, source.chapter.id(), targetChapterId);
    }

    public static AuthorOperationResult<DraftChange> removeQuest(QuestBookDefinition book, ResourceLocation questId) {
        QuestLocation location = questLocation(book, questId);
        if (location == null) return notFound("QUEST_NOT_FOUND", questId);
        List<ResourceLocation> referrers = book.quests().stream().filter(q -> q.dependencies().contains(questId)).map(QuestDefinition::id).toList();
        if (!referrers.isEmpty()) return conflict("QUEST_IS_DEPENDENCY", questId + " is referenced by " + referrers);
        List<QuestDefinition> quests = new ArrayList<>(location.chapter.quests());
        quests.remove(location.index);
        return replaceChapter(book, location.chapter.id(), chapter -> withQuests(chapter, quests), questId);
    }

    public static AuthorOperationResult<DraftChange> removeQuestAndReferences(QuestBookDefinition book,
                                                                               ResourceLocation questId) {
        if (quest(book, questId) == null) return notFound("QUEST_NOT_FOUND", questId);
        List<ResourceLocation> referrers = book.quests().stream().filter(q -> q.dependencies().contains(questId))
                .map(QuestDefinition::id).toList();
        QuestBookDefinition changed = removeQuestSetAndReferences(book, Set.of(questId));
        List<ResourceLocation> affected = new ArrayList<>();
        affected.add(questId);
        affected.addAll(referrers);
        return changed(changed, affected.toArray(ResourceLocation[]::new));
    }

    public static AuthorOperationResult<DraftChange> addDependency(QuestBookDefinition book, ResourceLocation questId,
                                                                    ResourceLocation dependencyId) {
        if (quest(book, dependencyId) == null) return notFound("DEPENDENCY_NOT_FOUND", dependencyId);
        return editQuest(book, questId, quest -> {
            if (quest.dependencies().contains(dependencyId)) return quest;
            return copyQuest(quest, append(quest.dependencies(), dependencyId), quest.tasks(), quest.rewards());
        }, dependencyId);
    }

    public static AuthorOperationResult<DraftChange> removeDependency(QuestBookDefinition book, ResourceLocation questId,
                                                                       ResourceLocation dependencyId) {
        return editQuest(book, questId, quest -> copyQuest(quest,
                quest.dependencies().stream().filter(id -> !id.equals(dependencyId)).toList(), quest.tasks(), quest.rewards()), dependencyId);
    }

    public static AuthorOperationResult<DraftChange> addTask(QuestBookDefinition book, ResourceLocation questId, TaskDefinition task) {
        if (task == null || !task.bookId().equals(book.id())) return invalid("TASK_BOOK_MISMATCH", "Task belongs to another book");
        if (typedIdExists(book, task.id())) return conflict("DUPLICATE_TYPED_ID", task.id());
        if (typedLegacySourceExists(book, task.id())) return retiredTypedId(task.id());
        return editQuest(book, questId, quest -> copyQuest(quest, quest.dependencies(), append(quest.tasks(), normalizeTask(task)), quest.rewards()), task.id());
    }

    public static AuthorOperationResult<DraftChange> copyTask(QuestBookDefinition book, ResourceLocation questId,
                                                               ResourceLocation sourceId, TaskDefinition copy) {
        QuestDefinition quest = quest(book, questId);
        if (quest == null) return notFound("QUEST_NOT_FOUND", questId);
        if (quest.tasks().stream().noneMatch(task -> task.id().equals(sourceId))) return notFound("TASK_NOT_FOUND", sourceId);
        if (copy == null || copy.id().equals(sourceId)) return invalid("COPY_ID_REQUIRED", "Task copy requires a new stable ID");
        return addTask(book, questId, copy);
    }

    public static AuthorOperationResult<DraftChange> updateTask(QuestBookDefinition book, ResourceLocation questId,
                                                                 ResourceLocation taskId, TaskDefinition replacement) {
        if (replacement == null || !replacement.bookId().equals(book.id())) {
            return invalid("TASK_BOOK_MISMATCH", "Task belongs to another book");
        }
        if (!replacement.id().equals(taskId) && typedIdExists(book, replacement.id())) {
            return conflict("DUPLICATE_TYPED_ID", replacement.id());
        }
        if (!replacement.id().equals(taskId) && typedLegacySourceExists(book, replacement.id())) {
            return retiredTypedId(replacement.id());
        }
        AuthorOperationResult<DraftChange> updated = editTyped(book, questId, taskId, normalizeTask(replacement), true);
        return renamedTyped(updated, "@task:", taskId, replacement.id());
    }

    public static AuthorOperationResult<DraftChange> removeTask(QuestBookDefinition book, ResourceLocation questId, ResourceLocation taskId) {
        return prunedAliases(editTyped(book, questId, taskId, null, true));
    }

    public static AuthorOperationResult<DraftChange> moveTask(QuestBookDefinition book, ResourceLocation questId,
                                                               ResourceLocation taskId, int targetIndex) {
        return moveTyped(book, questId, taskId, targetIndex, true);
    }

    public static AuthorOperationResult<DraftChange> addReward(QuestBookDefinition book, ResourceLocation questId, RewardDefinition reward) {
        if (reward == null || !reward.bookId().equals(book.id())) return invalid("REWARD_BOOK_MISMATCH", "Reward belongs to another book");
        if (typedIdExists(book, reward.id())) return conflict("DUPLICATE_TYPED_ID", reward.id());
        if (typedLegacySourceExists(book, reward.id())) return retiredTypedId(reward.id());
        return editQuest(book, questId, quest -> copyQuest(quest, quest.dependencies(), quest.tasks(), append(quest.rewards(), normalizeReward(reward))), reward.id());
    }

    /** Types normalize only their own fields; opaque keys remain available for future extensions. */
    private static RewardDefinition normalizeReward(RewardDefinition reward) {
        var type = yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry.get(reward.typeId());
        if (type == null) return reward;
        Map<String, String> config = new java.util.TreeMap<>(reward.config());
        config.putAll(type.normalizeConfig(reward.config()));
        return new RewardDefinition(reward.bookId(), reward.id(), reward.typeId(), config, reward.claimPolicy(), reward.teamReward());
    }

    private static TaskDefinition normalizeTask(TaskDefinition task) {
        var type = yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry.get(task.typeId());
        if (type == null) return task;
        Map<String, String> config = new java.util.TreeMap<>(task.config());
        config.putAll(type.normalizeConfig(task.config()));
        return new TaskDefinition(task.bookId(), task.id(), task.typeId(), config, task.optional());
    }

    public static AuthorOperationResult<DraftChange> copyReward(QuestBookDefinition book, ResourceLocation questId,
                                                                 ResourceLocation sourceId, RewardDefinition copy) {
        QuestDefinition quest = quest(book, questId);
        if (quest == null) return notFound("QUEST_NOT_FOUND", questId);
        if (quest.rewards().stream().noneMatch(reward -> reward.id().equals(sourceId))) return notFound("REWARD_NOT_FOUND", sourceId);
        if (copy == null || copy.id().equals(sourceId)) return invalid("COPY_ID_REQUIRED", "Reward copy requires a new stable ID");
        return addReward(book, questId, copy);
    }

    public static AuthorOperationResult<DraftChange> updateReward(QuestBookDefinition book, ResourceLocation questId,
                                                                   ResourceLocation rewardId, RewardDefinition replacement) {
        if (replacement == null || !replacement.bookId().equals(book.id())) {
            return invalid("REWARD_BOOK_MISMATCH", "Reward belongs to another book");
        }
        if (!replacement.id().equals(rewardId) && typedIdExists(book, replacement.id())) {
            return conflict("DUPLICATE_TYPED_ID", replacement.id());
        }
        if (!replacement.id().equals(rewardId) && typedLegacySourceExists(book, replacement.id())) {
            return retiredTypedId(replacement.id());
        }
        AuthorOperationResult<DraftChange> updated = editTyped(book, questId, rewardId, normalizeReward(replacement), false);
        return renamedTyped(updated, "@reward:", rewardId, replacement.id());
    }

    public static AuthorOperationResult<DraftChange> removeReward(QuestBookDefinition book, ResourceLocation questId, ResourceLocation rewardId) {
        return prunedAliases(editTyped(book, questId, rewardId, null, false));
    }

    public static AuthorOperationResult<DraftChange> moveReward(QuestBookDefinition book, ResourceLocation questId,
                                                                 ResourceLocation rewardId, int targetIndex) {
        return moveTyped(book, questId, rewardId, targetIndex, false);
    }

    private static AuthorOperationResult<DraftChange> editTyped(QuestBookDefinition book, ResourceLocation questId,
                                                                 ResourceLocation typedId, Object replacement, boolean task) {
        QuestDefinition quest = quest(book, questId);
        if (quest == null) return notFound("QUEST_NOT_FOUND", questId);
        if (task) {
            int index = indexOf(quest.tasks(), typedId, TaskDefinition::id);
            if (index < 0) return notFound("TASK_NOT_FOUND", typedId);
            List<TaskDefinition> values = new ArrayList<>(quest.tasks());
            if (replacement == null) values.remove(index); else values.set(index, (TaskDefinition) replacement);
            return editQuest(book, questId, value -> copyQuest(value, value.dependencies(), values, value.rewards()), typedId);
        }
        int index = indexOf(quest.rewards(), typedId, RewardDefinition::id);
        if (index < 0) return notFound("REWARD_NOT_FOUND", typedId);
        List<RewardDefinition> values = new ArrayList<>(quest.rewards());
        if (replacement == null) values.remove(index); else values.set(index, (RewardDefinition) replacement);
        return editQuest(book, questId, value -> copyQuest(value, value.dependencies(), value.tasks(), values), typedId);
    }

    private static AuthorOperationResult<DraftChange> moveTyped(QuestBookDefinition book, ResourceLocation questId,
                                                                 ResourceLocation typedId, int targetIndex, boolean task) {
        QuestDefinition quest = quest(book, questId);
        if (quest == null) return notFound("QUEST_NOT_FOUND", questId);
        if (task) {
            List<TaskDefinition> values = move(quest.tasks(), typedId, targetIndex, TaskDefinition::id);
            if (values == null) return notFound("TASK_NOT_FOUND", typedId);
            return editQuest(book, questId, value -> copyQuest(value, value.dependencies(), values, value.rewards()), typedId);
        }
        List<RewardDefinition> values = move(quest.rewards(), typedId, targetIndex, RewardDefinition::id);
        if (values == null) return notFound("REWARD_NOT_FOUND", typedId);
        return editQuest(book, questId, value -> copyQuest(value, value.dependencies(), value.tasks(), values), typedId);
    }

    private static AuthorOperationResult<DraftChange> editQuest(QuestBookDefinition book, ResourceLocation questId,
                                                                 UnaryOperator<QuestDefinition> operation,
                                                                 ResourceLocation... affected) {
        QuestLocation location = questLocation(book, questId);
        if (location == null) return notFound("QUEST_NOT_FOUND", questId);
        return replaceQuest(book, location, operation, concat(questId, affected));
    }

    private static AuthorOperationResult<DraftChange> replaceQuest(QuestBookDefinition book, QuestLocation location,
                                                                    UnaryOperator<QuestDefinition> operation,
                                                                    ResourceLocation... affected) {
        List<QuestDefinition> quests = new ArrayList<>(location.chapter.quests());
        quests.set(location.index, operation.apply(quests.get(location.index)));
        return replaceChapter(book, location.chapter.id(), chapter -> withQuests(chapter, quests), affected);
    }

    private static AuthorOperationResult<DraftChange> replaceGroup(QuestBookDefinition book, ResourceLocation id,
                                                                    UnaryOperator<ChapterGroupDefinition> operation) {
        int index = indexOf(book.chapterGroups(), id, ChapterGroupDefinition::id);
        if (index < 0) return notFound("GROUP_NOT_FOUND", id);
        List<ChapterGroupDefinition> values = new ArrayList<>(book.chapterGroups());
        values.set(index, operation.apply(values.get(index)));
        return changed(withGroups(book, values), id);
    }

    private static AuthorOperationResult<DraftChange> replaceChapter(QuestBookDefinition book, ResourceLocation id,
                                                                      UnaryOperator<ChapterDefinition> operation,
                                                                      ResourceLocation... affected) {
        int index = indexOf(book.chapters(), id, ChapterDefinition::id);
        if (index < 0) return notFound("CHAPTER_NOT_FOUND", id);
        List<ChapterDefinition> values = new ArrayList<>(book.chapters());
        values.set(index, operation.apply(values.get(index)));
        return changed(withChapters(book, values), concat(id, affected));
    }

    private static <T> AuthorOperationResult<DraftChange> remove(QuestBookDefinition book, List<T> source,
                                                                  ResourceLocation id,
                                                                  java.util.function.Function<T, ResourceLocation> idGetter,
                                                                  java.util.function.BiFunction<QuestBookDefinition, List<T>, QuestBookDefinition> replacer,
                                                                  String notFoundCode) {
        int index = indexOf(source, id, idGetter);
        if (index < 0) return notFound(notFoundCode, id);
        List<T> values = new ArrayList<>(source);
        values.remove(index);
        return changed(replacer.apply(book, values), id);
    }

    private static QuestBookDefinition withGroups(QuestBookDefinition book, List<ChapterGroupDefinition> groups) {
        return new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(), groups, book.chapters(),
                book.legacyIds(), book.localization(), book.extensions());
    }

    private static QuestBookDefinition withChapters(QuestBookDefinition book, List<ChapterDefinition> chapters) {
        return new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(), book.chapterGroups(), chapters,
                book.legacyIds(), book.localization(), book.extensions());
    }

    private static ChapterDefinition withQuests(ChapterDefinition chapter, List<QuestDefinition> quests) {
        return new ChapterDefinition(chapter.bookId(), chapter.id(), chapter.groupId(), chapter.title(), chapter.icon(),
                chapter.order(), quests, chapter.extensions());
    }

    private static QuestDefinition copyQuest(QuestDefinition quest, List<ResourceLocation> dependencies,
                                             List<TaskDefinition> tasks, List<RewardDefinition> rewards) {
        return new QuestDefinition(quest.bookId(), quest.id(), quest.chapterId(), quest.title(), quest.subtitle(),
                quest.description(), quest.icon(), quest.x(), quest.y(), dependencies, tasks, rewards, quest.legacyId(),
                quest.appearance(), quest.behavior(), quest.extensions());
    }

    private static QuestBookDefinition removeQuestSetAndReferences(QuestBookDefinition book,
                                                                    Set<ResourceLocation> removedQuestIds) {
        List<ChapterDefinition> chapters = book.chapters().stream().map(chapter -> {
            List<QuestDefinition> quests = chapter.quests().stream()
                    .filter(quest -> !removedQuestIds.contains(quest.id()))
                    .map(quest -> copyQuest(quest, quest.dependencies().stream()
                            .filter(id -> !removedQuestIds.contains(id)).toList(), quest.tasks(), quest.rewards()))
                    .toList();
            return withQuests(chapter, quests);
        }).toList();
        Map<String, ResourceLocation> aliases = new java.util.TreeMap<>(book.legacyIds());
        Set<ResourceLocation> remainingTasks = chapters.stream().flatMap(chapter -> chapter.quests().stream())
                .flatMap(quest -> quest.tasks().stream()).map(TaskDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> remainingRewards = chapters.stream().flatMap(chapter -> chapter.quests().stream())
                .flatMap(quest -> quest.rewards().stream()).map(RewardDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        aliases.entrySet().removeIf(entry -> removedQuestIds.contains(entry.getValue())
                || entry.getKey().startsWith("@task:") && !remainingTasks.contains(entry.getValue())
                || entry.getKey().startsWith("@reward:") && !remainingRewards.contains(entry.getValue()));
        return new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(), book.chapterGroups(), chapters,
                aliases, book.localization(), book.extensions());
    }

    private static List<ResourceLocation> dependencyReferrers(QuestBookDefinition book, Set<ResourceLocation> removedIds) {
        return book.quests().stream().filter(quest -> !removedIds.contains(quest.id())
                && quest.dependencies().stream().anyMatch(removedIds::contains)).map(QuestDefinition::id).toList();
    }

    private static BookLocalization renameQuestTranslationPrefix(BookLocalization localization,
                                                                  String oldId, String newId) {
        String oldPrefix = "quest." + oldId + ".";
        String newPrefix = "quest." + newId + ".";
        Map<String, Map<String, String>> translations = new java.util.TreeMap<>();
        localization.translations().forEach((locale, source) -> {
            Map<String, String> values = new java.util.TreeMap<>();
            source.forEach((key, value) -> values.put(key.startsWith(oldPrefix)
                    ? newPrefix + key.substring(oldPrefix.length()) : key, value));
            translations.put(locale, values);
        });
        return new BookLocalization(localization.fallbackLocale(), translations);
    }

    private static ResourceLocation[] containedIds(ChapterDefinition chapter) {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(chapter.id());
        chapter.quests().forEach(quest -> ids.addAll(List.of(questObjectIds(quest))));
        return ids.toArray(ResourceLocation[]::new);
    }

    private static ResourceLocation[] questObjectIds(QuestDefinition quest) {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(quest.id());
        quest.tasks().forEach(task -> ids.add(task.id()));
        quest.rewards().forEach(reward -> ids.add(reward.id()));
        return ids.toArray(ResourceLocation[]::new);
    }

    private static ChapterDefinition chapter(QuestBookDefinition book, ResourceLocation id) {
        return book.chapters().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    private static QuestDefinition quest(QuestBookDefinition book, ResourceLocation id) {
        return book.quests().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    private static QuestLocation questLocation(QuestBookDefinition book, ResourceLocation id) {
        for (int chapterIndex = 0; chapterIndex < book.chapters().size(); chapterIndex++) {
            ChapterDefinition chapter = book.chapters().get(chapterIndex);
            for (int questIndex = 0; questIndex < chapter.quests().size(); questIndex++) {
                if (chapter.quests().get(questIndex).id().equals(id)) return new QuestLocation(chapterIndex, chapter, questIndex);
            }
        }
        return null;
    }

    private static boolean typedIdExists(QuestBookDefinition book, ResourceLocation id) {
        return book.quests().stream().anyMatch(quest -> quest.tasks().stream().anyMatch(task -> task.id().equals(id))
                || quest.rewards().stream().anyMatch(reward -> reward.id().equals(id)));
    }

    /** Legacy source IDs are retired permanently because player ledgers may still contain them. */
    private static boolean typedLegacySourceExists(QuestBookDefinition book, ResourceLocation id) {
        return book.legacyIds().containsKey("@task:" + id)
                || book.legacyIds().containsKey("@reward:" + id);
    }

    private static boolean questLegacySourceExists(QuestBookDefinition book, ResourceLocation id) {
        return book.legacyIds().containsKey(id.toString());
    }

    /** Records typed-object renames in schema-1 legacy_ids so reload can migrate player ledgers. */
    private static AuthorOperationResult<DraftChange> renamedTyped(AuthorOperationResult<DraftChange> updated,
                                                                    String prefix, ResourceLocation oldId,
                                                                    ResourceLocation newId) {
        if (!updated.success() || oldId.equals(newId)) return updated;
        QuestBookDefinition book = updated.value().book();
        Map<String, ResourceLocation> aliases = new java.util.TreeMap<>(book.legacyIds());
        aliases.replaceAll((alias, target) -> alias.startsWith(prefix) && target.equals(oldId) ? newId : target);
        aliases.entrySet().removeIf(entry -> entry.getKey().equals(prefix + entry.getValue()));
        aliases.put(prefix + oldId, newId);
        QuestBookDefinition renamed = new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(),
                book.chapterGroups(), book.chapters(), aliases, book.localization(), book.extensions());
        return changed(renamed, oldId, newId);
    }

    private static AuthorOperationResult<DraftChange> prunedAliases(AuthorOperationResult<DraftChange> updated) {
        if (!updated.success()) return updated;
        DraftChange change = updated.value();
        QuestBookDefinition book = change.book();
        Set<ResourceLocation> quests = book.quests().stream().map(QuestDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> tasks = book.quests().stream().flatMap(quest -> quest.tasks().stream())
                .map(TaskDefinition::id).collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> rewards = book.quests().stream().flatMap(quest -> quest.rewards().stream())
                .map(RewardDefinition::id).collect(java.util.stream.Collectors.toSet());
        Map<String, ResourceLocation> aliases = new java.util.TreeMap<>(book.legacyIds());
        aliases.entrySet().removeIf(entry -> entry.getKey().startsWith("@task:")
                ? !tasks.contains(entry.getValue()) : entry.getKey().startsWith("@reward:")
                ? !rewards.contains(entry.getValue()) : !quests.contains(entry.getValue()));
        if (aliases.equals(book.legacyIds())) return updated;
        QuestBookDefinition pruned = new QuestBookDefinition(book.id(), book.schemaVersion(), book.title(),
                book.chapterGroups(), book.chapters(), aliases, book.localization(), book.extensions());
        return AuthorOperationResult.success("DRAFT_CHANGED", "Draft candidate changed",
                new DraftChange(pruned, change.affectedObjects()));
    }

    private static <T> List<T> append(List<T> source, T value) {
        List<T> result = new ArrayList<>(source);
        result.add(value);
        return result;
    }

    private static <T> List<T> move(List<T> source, ResourceLocation id, int targetIndex,
                                    java.util.function.Function<T, ResourceLocation> idGetter) {
        int index = indexOf(source, id, idGetter);
        if (index < 0) return null;
        List<T> result = new ArrayList<>(source);
        T value = result.remove(index);
        result.add(Math.max(0, Math.min(targetIndex, result.size())), value);
        return result;
    }

    private static <T> int indexOf(List<T> source, ResourceLocation id,
                                   java.util.function.Function<T, ResourceLocation> idGetter) {
        for (int i = 0; i < source.size(); i++) if (idGetter.apply(source.get(i)).equals(id)) return i;
        return -1;
    }

    private static ResourceLocation[] concat(ResourceLocation first, ResourceLocation... rest) {
        ResourceLocation[] result = new ResourceLocation[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    private static AuthorOperationResult<DraftChange> changed(QuestBookDefinition book, ResourceLocation... affected) {
        return AuthorOperationResult.success("DRAFT_CHANGED", "Draft candidate changed", new DraftChange(book, List.of(affected)));
    }

    private static AuthorOperationResult<DraftChange> invalid(String code, String message) {
        return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, code, message);
    }

    private static AuthorOperationResult<DraftChange> conflict(String code, Object detail) {
        return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, code, detail.toString());
    }

    private static AuthorOperationResult<DraftChange> retiredTypedId(ResourceLocation id) {
        return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "RETIRED_TYPED_ID",
                "Stable ID " + id + " is reserved by a player-ledger migration");
    }

    private static AuthorOperationResult<DraftChange> retiredQuestId(ResourceLocation id) {
        return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "RETIRED_QUEST_ID",
                "Quest ID " + id + " is reserved by a player-ledger migration");
    }

    private static AuthorOperationResult<DraftChange> notFound(String code, ResourceLocation id) {
        return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, code, id.toString());
    }

    private record QuestLocation(int chapterIndex, ChapterDefinition chapter, int index) {}
}
