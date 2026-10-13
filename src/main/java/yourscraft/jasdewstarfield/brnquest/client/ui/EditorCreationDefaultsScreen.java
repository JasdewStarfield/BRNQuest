package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.screens.Screen;
import yourscraft.jasdewstarfield.brnquest.data.QuestCreationDefaults;
import java.util.function.Consumer;

/** Book/chapter scope adapter; all field generation belongs to the shared registered-property form. */
final class EditorCreationDefaultsScreen extends EditorRegisteredPropertiesScreen {
    EditorCreationDefaultsScreen(Screen parent, QuestCreationDefaults initial,
            Consumer<QuestCreationDefaults> selection) {
        super(parent, EditorPropertyValues.Scope.BOOK, initial, QuestCreationDefaults.EMPTY, selection,
                false, null, null, false, null);
    }
    EditorCreationDefaultsScreen(Screen parent, QuestCreationDefaults initial, QuestCreationDefaults inherited,
            Consumer<QuestCreationDefaults> selection, boolean hideDependencyLines, Consumer<Boolean> hideLinesSelection,
            Boolean consumeItems, boolean inheritedConsumeItems, Consumer<Boolean> consumeSelection) {
        super(parent, EditorPropertyValues.Scope.CHAPTER, initial, inherited, selection,
                hideDependencyLines, hideLinesSelection, consumeItems, inheritedConsumeItems, consumeSelection);
    }
}
