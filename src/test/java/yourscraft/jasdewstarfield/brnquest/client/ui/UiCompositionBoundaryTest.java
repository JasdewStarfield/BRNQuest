package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Prevent presentation modules from slowly regaining direct protocol access or a whole-Screen backdoor. */
class UiCompositionBoundaryTest {
    @Test void composedViewsHaveNoNetworkOrMutableClientSingletonDependency() throws Exception {
        Path ui = Path.of(System.getProperty("brnquest.projectDir"),
                "src/main/java/yourscraft/jasdewstarfield/brnquest/client/ui");
        for (String name : List.of("QuestNavigationPanel.java", "QuestDetailsPanel.java", "QuestDetailRows.java",
                "QuestNodeDrag.java", "QuestTypedEntryListSection.java", "QuestTypePickerModel.java",
                "QuestTypedEntryKind.java", "QuestTypedPropertyFormModel.java", "QuestScreenFrameIdentity.java",
                "component/EditorPropertyPanel.java", "component/EditorFormFields.java",
                "component/EditorEntryListPanel.java", "component/EditorSelectionFocus.java")) {
            String source = Files.readString(ui.resolve(name), StandardCharsets.UTF_8);
            assertFalse(source.contains("import yourscraft.jasdewstarfield.brnquest.network."), name);
            assertFalse(source.contains("ClientEditorState.get()"), name);
            assertFalse(source.contains("ClientQuestState.get()"), name);
            assertFalse(source.contains("QuestScreen screen"), name);
        }
    }
}
