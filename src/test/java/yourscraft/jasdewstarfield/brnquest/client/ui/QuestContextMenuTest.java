package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPopupMenu;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Covers the editor behavior layered on top of the shared popup geometry. */
class QuestContextMenuTest {
    private static final EditorSessionLifecycle.Scope SCOPE =
            new EditorSessionLifecycle.Scope(UUID.randomUUID(), id("book"), true);
    private static final QuestContextMenu.Viewport VIEWPORT = new QuestContextMenu.Viewport(400, 20, 300);
    private static final QuestContextMenu.Options OPTIONS =
            new QuestContextMenu.Options(false, false, false, Component.empty(), false, false, false);

    @Test
    void parentRowKeepsTheMenuOpenAndOnlyTheLeafProducesAnIntent() {
        var menu = new QuestContextMenu();
        menu.open(context(QuestContextMenu.Kind.GROUP, Set.of()));
        int parentY = 50 + 2 * EditorPopupMenu.ROW_HEIGHT + 5;

        assertTrue(menu.click(VIEWPORT, OPTIONS, SCOPE, 105, parentY, 0).isEmpty());
        assertNotNull(menu.context());
        var intent = menu.click(VIEWPORT, OPTIONS, SCOPE, 245, parentY, 0).orElseThrow();

        assertEquals(QuestContextMenu.Action.MOVE_GROUP_UP, intent.action());
        assertEquals(id("target"), intent.context().target());
        assertNull(menu.context(), "A leaf consumes the open menu");
        assertTrue(menu.click(VIEWPORT, OPTIONS, SCOPE, 245, parentY, 0).isEmpty());
    }

    @Test
    void selectionAndCoordinatesRemainBoundToTheOpenedMenu() {
        var selection = new HashSet<>(Set.of(id("quest"), id("art")));
        var menu = new QuestContextMenu();
        menu.open(context(QuestContextMenu.Kind.SELECTION, selection));
        selection.clear();
        selection.add(id("different"));

        var intent = menu.click(VIEWPORT, OPTIONS, SCOPE, 105, 55, 0).orElseThrow();
        assertEquals(QuestContextMenu.Action.COPY_SELECTION_SNAPSHOT, intent.action());
        assertEquals(Set.of(id("quest"), id("art")), intent.context().selection());
        assertEquals(id("parent"), intent.context().parent());
        assertEquals(2.25, intent.context().graphX());
        assertEquals(-3.5, intent.context().graphY());
    }

    @Test
    void staleOrRevokedScopesCloseThePopupWithoutDispatch() {
        for (var current : List.of(
                new EditorSessionLifecycle.Scope(UUID.randomUUID(), SCOPE.book(), true),
                new EditorSessionLifecycle.Scope(SCOPE.session(), id("other_book"), true),
                new EditorSessionLifecycle.Scope(SCOPE.session(), SCOPE.book(), false))) {
            var menu = new QuestContextMenu();
            menu.open(context(QuestContextMenu.Kind.GROUP, Set.of()));

            assertTrue(menu.click(VIEWPORT, OPTIONS, current, 105, 55, 0).isEmpty());
            assertNull(menu.context());
        }
    }

    @Test
    void progressActionsFollowPermissionAndAppearOnlyForQuestsAndTasks() {
        var menu = new QuestContextMenu();
        var progress = new QuestContextMenu.Options(false, false, true, Component.literal("Manage"), false, true, false);
        menu.open(context(QuestContextMenu.Kind.NODE, Set.of()));
        assertFalse(menu.entries(OPTIONS).stream().anyMatch(entry -> entry.action().equals("ADMIN_QUEST")));
        assertTrue(menu.entries(progress).stream().anyMatch(entry -> entry.action().equals("ADMIN_QUEST")));
        assertTrue(menu.entries(progress).stream().filter(entry -> entry.action().equals("ADMIN_QUEST"))
                .noneMatch(EditorPopupMenu.Entry::enabled));

        menu.open(new QuestContextMenu.Context(SCOPE, QuestContextMenu.Kind.TYPED_ENTRY, id("target"), id("parent"),
                QuestTypedEntryKind.TASK, Set.of(), 100, 50, 0, 0));
        assertTrue(menu.entries(progress).stream().anyMatch(entry -> entry.action().equals("ADMIN_TASK")));
        menu.open(new QuestContextMenu.Context(SCOPE, QuestContextMenu.Kind.TYPED_ENTRY, id("target"), id("parent"),
                QuestTypedEntryKind.REWARD, Set.of(), 100, 50, 0, 0));
        assertFalse(menu.entries(progress).stream().anyMatch(entry -> entry.action().equals("ADMIN_TASK")));
    }

    @Test
    void typedRowUsesTheCurrentPositionOfItsStableId() {
        var target = new TaskDefinition(id("book"), id("target"), id("type"), Map.of(), false);
        var other = new TaskDefinition(id("book"), id("other"), id("type"), Map.of(), false);
        var context = new QuestContextMenu.Context(SCOPE, QuestContextMenu.Kind.TYPED_ENTRY, target.id(),
                id("quest"), QuestTypedEntryKind.TASK, Set.of(), 100, 50, 0, 0);

        assertEquals(1, context.typedIndex(quest(id("quest"), List.of(other, target))));
        assertEquals(0, context.typedIndex(quest(id("quest"), List.of(target, other))));
        assertEquals(-1, context.typedIndex(quest(id("quest"), List.of(other))));
        assertEquals(-1, context.typedIndex(quest(id("other_quest"), List.of(target, other))));
    }

    private static QuestDefinition quest(ResourceLocation quest, List<TaskDefinition> tasks) {
        return new QuestDefinition(id("book"), quest, id("chapter"), "Quest", "", "", "", 0, 0,
                List.of(), tasks, List.of(), "");
    }

    private static QuestContextMenu.Context context(QuestContextMenu.Kind kind, Set<ResourceLocation> selection) {
        return new QuestContextMenu.Context(SCOPE, kind, id("target"), id("parent"), null,
                selection, 100, 50, 2.25, -3.5);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
