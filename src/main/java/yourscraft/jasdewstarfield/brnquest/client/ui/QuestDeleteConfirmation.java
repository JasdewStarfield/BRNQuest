package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.author.CanvasSelectionKey;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorConfirmDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.data.CanvasScene;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Owns the destructive prompt until it is dismissed or returns one confirmed edit. */
final class QuestDeleteConfirmation {
    enum Kind { GROUP, CHAPTER, QUEST, CANVAS_SELECTION }

    record Intent(Kind kind, ResourceLocation target, Set<ResourceLocation> selection) {
        Intent {
            selection = Set.copyOf(selection);
        }

        String action() {
            return "DELETE_" + kind.name();
        }
    }

    // The warning and the eventual edit describe the same draft revision and target.
    private record Request(EditorSessionLifecycle.Scope scope, String revision, Intent intent, Component impact) {}

    private Request pending;

    boolean openTarget(EditorSessionLifecycle.Scope scope, String revision, QuestBookDefinition book,
                       Kind kind, ResourceLocation target) {
        if (!canOpen(scope, book) || target == null) return false;
        Component impact = switch (kind) {
            case GROUP -> {
                var chapters = book.chapters().stream().filter(chapter -> chapter.groupId().equals(target)).toList();
                long quests = chapters.stream().mapToLong(chapter -> chapter.quests().size()).sum();
                yield Component.translatable("screen.brnquest.editor.delete.group_impact", chapters.size(), quests);
            }
            case CHAPTER -> {
                int quests = book.chapters().stream().filter(chapter -> chapter.id().equals(target))
                        .findFirst().map(chapter -> chapter.quests().size()).orElse(0);
                yield Component.translatable("screen.brnquest.editor.delete.chapter_impact", quests);
            }
            case QUEST -> {
                long references = book.quests().stream().filter(quest -> quest.dependencies().contains(target)).count();
                yield Component.translatable("screen.brnquest.editor.delete.quest_impact", references);
            }
            case CANVAS_SELECTION -> throw new IllegalArgumentException("Canvas deletion requires a selection");
        };
        pending = new Request(scope, revision, new Intent(kind, target, Set.of()), impact);
        return true;
    }

    /** Locked artwork remains selected, but the destructive request never includes it. */
    boolean openSelection(EditorSessionLifecycle.Scope scope, String revision, QuestBookDefinition book,
                          ChapterDefinition chapter, Set<ResourceLocation> selected) {
        if (!canOpen(scope, book) || chapter == null) return false;
        var locked = chapter.canvasScene().decorations().stream().filter(CanvasScene.Decoration::locked)
                .map(decoration -> CanvasSelectionKey.decoration(decoration.id())).collect(Collectors.toSet());
        var removable = selected.stream().filter(key -> !locked.contains(key)).collect(Collectors.toUnmodifiableSet());
        if (removable.isEmpty()) return false;
        var nodes = CanvasSelectionKey.ids(removable, false);
        long references = book.quests().stream().filter(quest -> !nodes.contains(quest.id()))
                .filter(quest -> quest.dependencies().stream().anyMatch(nodes::contains)).count();
        var impact = Component.translatable("screen.brnquest.selection.delete_warning", removable.size(), references);
        pending = new Request(scope, revision, new Intent(Kind.CANVAS_SELECTION, chapter.id(), removable), impact);
        return true;
    }

    private static boolean canOpen(EditorSessionLifecycle.Scope scope, QuestBookDefinition book) {
        return scope.editing() && scope.session() != null && book.id().equals(scope.book());
    }

    boolean isOpen() {
        return pending != null;
    }

    void close() {
        pending = null;
    }

    void render(GuiGraphics graphics, Font font, QuestScreenLayout layout, EditorButtonInput buttons,
                int mouseX, int mouseY) {
        if (pending == null) return;
        EditorConfirmDialog.render(graphics, font, layout, buttons,
                Component.translatable("screen.brnquest.editor.delete.warning"), pending.impact(),
                0xFFFFC07A, Component.translatable("gui.cancel"),
                Component.translatable("screen.brnquest.editor.delete.confirm"), mouseX, mouseY);
    }

    Optional<Intent> click(QuestScreenLayout layout, EditorSessionLifecycle.Scope scope, String revision,
                           double mouseX, double mouseY, int button) {
        var request = pending;
        if (request == null) return Optional.empty();
        if (!request.scope().equals(scope) || !Objects.equals(request.revision(), revision)) {
            close();
            return Optional.empty();
        }
        if (button != 0) return Optional.empty();
        var action = EditorConfirmDialog.actionAt(layout, mouseX, mouseY);
        if (action != EditorConfirmDialog.Action.NONE) close();
        return action == EditorConfirmDialog.Action.CONFIRM ? Optional.of(request.intent()) : Optional.empty();
    }
}
