package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPopupMenu;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Owns the contextual popup and translates its input into one action on the captured target. */
final class QuestContextMenu {
    private static final int WIDTH = 142;

    enum Kind { CANVAS, NODE, DECORATION, SELECTION, GROUP, CHAPTER, TYPED_ENTRY, DEPENDENCY_ENTRY }

    enum Action {
        ADD_QUEST, ADD_DECORATION, BOOK_PROPERTIES, PASTE_QUEST_SNAPSHOT,
        DECORATION_PROPERTIES, COPY_SELECTION_SNAPSHOT, COPY_SELECTION, DELETE_SELECTION, CLEAR_SELECTION,
        SELF_FORCE_QUEST, SELF_RESET_QUEST, ADMIN_QUEST, SET_CHAPTER_AUTOFOCUS, COPY_QUEST_SNAPSHOT, COPY_QUEST,
        DELETE_QUEST, ADD_CHAPTER, RENAME_GROUP, MOVE_GROUP_UP, MOVE_GROUP_DOWN, DELETE_GROUP,
        COPY_CHAPTER, RENAME_CHAPTER, MOVE_CHAPTER_UP, MOVE_CHAPTER_DOWN, DELETE_CHAPTER,
        SELF_FORCE_TASK, SELF_RESET_TASK, ADMIN_TASK, EDIT_TYPED, COPY_TYPED_SNAPSHOT, COPY_TYPED,
        MOVE_TYPED_UP, MOVE_TYPED_DOWN, COPY_TYPED_ID, COPY_TYPED_TYPE, DELETE_TYPED,
        COPY_DEPENDENCY_ID, REMOVE_DEPENDENCY
    }

    // Canvas actions bind to a chapter; row actions bind to the quest that owns the row.
    record Context(EditorSessionLifecycle.Scope scope, Kind kind, ResourceLocation target, ResourceLocation parent,
                   QuestTypedEntryKind entryKind, Set<ResourceLocation> selection, int x, int y,
                   double graphX, double graphY) {
        Context {
            selection = Set.copyOf(selection);
        }

        int typedIndex(QuestDefinition quest) {
            if (entryKind == null || quest == null || !quest.id().equals(parent)) return -1;
            for (int index = 0; index < entryKind.size(quest); index++) {
                if (entryKind.value(quest, index).id().equals(target)) return index;
            }
            return -1;
        }
    }

    record Intent(Action action, Context context) {}
    record Viewport(int width, int top, int bottom) {}
    record Options(boolean canPaste, boolean hasChapter, boolean showProgress, Component progressLabel,
                   boolean canManageProgress, boolean canMoveUp, boolean canMoveDown) {}

    private Context context;
    private int submenu = -1;

    void open(Context context) {
        this.context = context;
        submenu = -1;
    }

    Context context() {
        return context;
    }

    void close() {
        context = null;
        submenu = -1;
    }

    void render(GuiGraphics graphics, Font font, Viewport viewport, Options options, int mouseX, int mouseY) {
        if (context == null) return;
        List<EditorPopupMenu.Entry> entries = entries(options);
        var layout = layout(viewport, entries);
        submenu = EditorPopupMenu.resolveSubmenu(layout.root(), entries, layout, mouseX, mouseY);
        EditorPopupMenu.render(graphics, font, layout(viewport, entries), entries, mouseX, mouseY);
    }

    Optional<Intent> click(Viewport viewport, Options options, EditorSessionLifecycle.Scope scope,
                           double mouseX, double mouseY, int button) {
        if (context == null) return Optional.empty();
        if (!scope.editing() || !scope.equals(context.scope()) || button != 0) {
            close();
            return Optional.empty();
        }
        List<EditorPopupMenu.Entry> entries = entries(options);
        var layout = layout(viewport, entries);
        if (!layout.contains(mouseX, mouseY)) {
            close();
            return Optional.empty();
        }
        String action = EditorPopupMenu.actionAt(layout, entries, mouseX, mouseY);
        if (action.isEmpty()) {
            // A click can enter the submenu before the next hover/render pass.
            int row = EditorPopupMenu.rowAt(layout.root(), entries.size(), mouseX, mouseY);
            if (row >= 0 && entries.get(row).submenu()) submenu = row;
            return Optional.empty();
        }
        var intent = new Intent(Action.valueOf(action), context);
        close();
        return Optional.of(intent);
    }

    private EditorPopupMenu.CascadeLayout layout(Viewport viewport, List<EditorPopupMenu.Entry> entries) {
        UiRect root = EditorPopupMenu.layout(context.x(), context.y(), viewport.width(), viewport.top(),
                viewport.bottom(), WIDTH, entries.size());
        return EditorPopupMenu.cascadeLayout(root, entries, submenu, viewport.width(), viewport.top(),
                viewport.bottom(), WIDTH);
    }

    List<EditorPopupMenu.Entry> entries(Options options) {
        if (context == null) return List.of();
        return switch (context.kind()) {
            case CANVAS -> EditorPopupMenu.menu(menu -> menu.action(Action.ADD_QUEST.name(),
                    Component.translatable("screen.brnquest.editor.context.add_quest"), false)
                    .action(Action.PASTE_QUEST_SNAPSHOT.name(), Component.translatable("screen.brnquest.quest_clipboard.paste"), false, options.canPaste())
                    .action(Action.ADD_DECORATION.name(), EditorCanvasScreen.label("add_here"), false, options.hasChapter())
                    .action(Action.BOOK_PROPERTIES.name(), Component.translatable("screen.brnquest.book.properties"), false));
            case DECORATION -> EditorPopupMenu.menu(menu -> menu.action(Action.DECORATION_PROPERTIES.name(), EditorCanvasScreen.label("properties"), false)
                    .action(Action.COPY_SELECTION_SNAPSHOT.name(), Component.translatable("screen.brnquest.quest_clipboard.copy"), false)
                    .action(Action.COPY_SELECTION.name(), Component.translatable("screen.brnquest.selection.copy", context.selection().size()), false)
                    .action(Action.PASTE_QUEST_SNAPSHOT.name(), Component.translatable("screen.brnquest.quest_clipboard.paste"), false, options.canPaste())
                    .action(Action.DELETE_SELECTION.name(), Component.translatable("screen.brnquest.selection.delete", context.selection().size()), true));
            case SELECTION -> EditorPopupMenu.menu(menu -> menu
                    .action(Action.COPY_SELECTION_SNAPSHOT.name(), Component.translatable("screen.brnquest.quest_clipboard.copy"), false)
                    .action(Action.PASTE_QUEST_SNAPSHOT.name(), Component.translatable("screen.brnquest.quest_clipboard.paste"), false, options.canPaste())
                    .action(Action.COPY_SELECTION.name(), Component.translatable("screen.brnquest.selection.copy", context.selection().size()), false)
                    .action(Action.DELETE_SELECTION.name(), Component.translatable("screen.brnquest.selection.delete", context.selection().size()), true)
                    .action(Action.CLEAR_SELECTION.name(), Component.translatable("screen.brnquest.selection.clear"), false));
            case NODE -> EditorPopupMenu.menu(menu -> menu
                    .action(Action.SELF_FORCE_QUEST.name(), Component.translatable("screen.brnquest.admin.self_force"), false,
                            options.canManageProgress())
                    .action(Action.SELF_RESET_QUEST.name(), Component.translatable("screen.brnquest.admin.self_reset"), true,
                            options.canManageProgress())
                    .action(Action.ADMIN_QUEST.name(), options.progressLabel(), false,
                            options.canManageProgress())
                    .action(Action.SET_CHAPTER_AUTOFOCUS.name(), Component.translatable("screen.brnquest.chapter.autofocus.set_here"), false)
                    .action(Action.COPY_QUEST_SNAPSHOT.name(), Component.translatable("screen.brnquest.quest_clipboard.copy"), false)
                    .action(Action.COPY_QUEST.name(), Component.translatable("screen.brnquest.editor.context.copy_quest"), false)
                    .action(Action.DELETE_QUEST.name(), Component.translatable(
                            "screen.brnquest.editor.context.delete_quest"), true)).stream()
                    .filter(entry -> !(entry.action().equals(Action.ADMIN_QUEST.name()) || entry.action().startsWith("SELF_"))
                            || options.showProgress()).toList();
            case GROUP -> EditorPopupMenu.menu(menu -> menu
                    .action(Action.ADD_CHAPTER.name(), Component.translatable(
                            "screen.brnquest.editor.context.add_chapter"), false)
                    .action(Action.RENAME_GROUP.name(), Component.translatable("screen.brnquest.editor.structure.group_properties"), false)
                    .submenu(Component.translatable("screen.brnquest.editor.context.move"), move -> move
                            .action(Action.MOVE_GROUP_UP.name(), Component.translatable(
                                    "screen.brnquest.editor.context.move_up"), false)
                            .action(Action.MOVE_GROUP_DOWN.name(), Component.translatable(
                                    "screen.brnquest.editor.context.move_down"), false))
                    .action(Action.DELETE_GROUP.name(), Component.translatable("screen.brnquest.editor.context.delete"), true));
            case CHAPTER -> EditorPopupMenu.menu(menu -> menu
                    .action(Action.COPY_CHAPTER.name(), Component.translatable("screen.brnquest.chapter.copy"), false)
                    .action(Action.RENAME_CHAPTER.name(), Component.translatable(
                            "screen.brnquest.editor.structure.chapter_properties"), false)
                    .submenu(Component.translatable("screen.brnquest.editor.context.move"), move -> move
                            .action(Action.MOVE_CHAPTER_UP.name(), Component.translatable(
                                    "screen.brnquest.editor.context.move_up"), false)
                            .action(Action.MOVE_CHAPTER_DOWN.name(), Component.translatable(
                                    "screen.brnquest.editor.context.move_down"), false))
                    .action(Action.DELETE_CHAPTER.name(), Component.translatable(
                            "screen.brnquest.editor.context.delete"), true));
            case TYPED_ENTRY -> EditorPopupMenu.menu(menu -> menu
                    .action(Action.SELF_FORCE_TASK.name(), Component.translatable("screen.brnquest.admin.self_force"), false,
                            options.canManageProgress())
                    .action(Action.SELF_RESET_TASK.name(), Component.translatable("screen.brnquest.admin.self_reset"), true,
                            options.canManageProgress())
                    .action(Action.ADMIN_TASK.name(), options.progressLabel(), false,
                            options.canManageProgress())
                    .action(Action.EDIT_TYPED.name(), Component.translatable(
                            "screen.brnquest.editor.action.edit"), false)
                    .action(Action.COPY_TYPED_SNAPSHOT.name(), Component.translatable("screen.brnquest.clipboard.copy"), false)
                    .action(Action.COPY_TYPED.name(), Component.translatable(
                            "screen.brnquest.editor.action.copy"), false)
                    .submenu(Component.translatable("screen.brnquest.editor.context.move"), move -> move
                            .action(Action.MOVE_TYPED_UP.name(), Component.translatable(
                                    "screen.brnquest.editor.context.move_up"), false,
                                    options.canMoveUp())
                            .action(Action.MOVE_TYPED_DOWN.name(), Component.translatable(
                                    "screen.brnquest.editor.context.move_down"), false,
                                    options.canMoveDown()))
                    .submenu(Component.translatable("screen.brnquest.editor.context.technical"), technical -> technical
                            .action(Action.COPY_TYPED_ID.name(), Component.translatable(
                                    "screen.brnquest.editor.context.copy_stable_id"), false)
                            .action(Action.COPY_TYPED_TYPE.name(), Component.translatable(
                                    "screen.brnquest.editor.context.copy_type_id"), false))
                    .action(Action.DELETE_TYPED.name(), Component.translatable(
                            "screen.brnquest.editor.context.delete"), true)).stream()
                    .filter(entry -> !(entry.action().equals(Action.ADMIN_TASK.name()) || entry.action().startsWith("SELF_"))
                            || options.showProgress() && context.entryKind() == QuestTypedEntryKind.TASK).toList();
            case DEPENDENCY_ENTRY -> EditorPopupMenu.menu(menu -> menu
                    .submenu(Component.translatable("screen.brnquest.editor.context.technical"), technical -> technical
                            .action(Action.COPY_DEPENDENCY_ID.name(), Component.translatable(
                                    "screen.brnquest.editor.context.copy_quest_id"), false))
                    .action(Action.REMOVE_DEPENDENCY.name(), Component.translatable(
                            "screen.brnquest.editor.dependency.remove"), true));
        };
    }
}
