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
    }

    @Test void keepsACompatibleSelectionWithoutOpeningAClosedDrawer() {
        QuestModeSelection.Result result = QuestModeSelection.resolve(QUEST, false, Set.of(QUEST), ignored -> true);

        assertEquals(QUEST, result.selectedId());
        assertFalse(result.detailsOpen());
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
