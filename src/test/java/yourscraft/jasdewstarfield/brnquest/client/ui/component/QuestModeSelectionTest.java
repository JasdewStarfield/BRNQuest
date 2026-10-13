package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestModeSelectionTest {
    private static final ResourceLocation QUEST = ResourceLocation.fromNamespaceAndPath("test", "quest");

    @Test void keepsTheSameOpenDetailWhenTheTargetModeContainsItsId() {
        QuestModeSelection.Result result = QuestModeSelection.resolve(QUEST, true, Set.of(QUEST), ignored -> true);

        assertEquals(QUEST, result.selectedId());
        assertTrue(result.detailsOpen());
        assertTrue(result.followSelection());
    }

    @Test void keepsACompatibleSelectionWithoutOpeningAClosedDrawer() {
        QuestModeSelection.Result result = QuestModeSelection.resolve(QUEST, false, Set.of(QUEST), ignored -> true);

        assertEquals(QUEST, result.selectedId());
        assertFalse(result.detailsOpen());
        assertFalse(result.followSelection(), "An old task from chapter three must not override a newly selected chapter one");
    }

    @Test void browsingChapterOneSurvivesBothModeChangesWithAnOldSelectionFromChapterThree() {
        var first = ResourceLocation.parse("test:first");
        var second = ResourceLocation.parse("test:second");
        var third = ResourceLocation.parse("test:third");
        var closedSelection = QuestModeSelection.resolve(QUEST, false, Set.of(QUEST), ignored -> true);
        assertFalse(closedSelection.followSelection());
        var runtime = java.util.List.of(first, second, third);
        var draft = java.util.List.of(third, second, first);
        int entering = QuestModeSelection.chapterIndex(first, draft, 0);
        assertEquals(first, draft.get(entering));
        int leaving = QuestModeSelection.chapterIndex(first, runtime, entering);
        assertEquals(first, runtime.get(leaving));
        assertEquals(0, QuestModeSelection.chapterIndex(null, java.util.List.of(), 2));
    }

    @Test void closesDetailsInsteadOfUsingAnotherModesHistoricalSelection() {
        QuestModeSelection.Result result = QuestModeSelection.resolve(QUEST, true, Set.of(), ignored -> true);

        assertNull(result.selectedId());
        assertFalse(result.detailsOpen());
    }

    @Test void closesDetailsWhenAnExistingRuntimeQuestIsHidden() {
        QuestModeSelection.Result result = QuestModeSelection.resolve(QUEST, true, Set.of(QUEST), ignored -> false);

        assertNull(result.selectedId());
        assertFalse(result.detailsOpen());
    }

    @Test void questScreenReconcilesTheModeBeforeRenderingItsSnapshot() throws IOException {
        Path source = Path.of(System.getProperty("brnquest.projectDir"), "src", "main", "java",
                "yourscraft", "jasdewstarfield", "brnquest", "client", "ui", "QuestScreen.java");
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int render = text.indexOf("public void render(GuiGraphics graphics");
        int reconcile = text.indexOf("reconcileModeSelection(ClientEditorState.get());", render);
        int snapshot = text.indexOf("var snapshot = displaySnapshot();", render);

        assertTrue(render >= 0 && reconcile > render && snapshot > reconcile,
                "Mode selection must be transferred before a render frame reads its display snapshot");
    }
}
