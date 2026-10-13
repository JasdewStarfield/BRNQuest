package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.editor.QuestPropertySchema;
import java.util.*;
import java.util.function.Consumer;

/** Compatibility entry point backed by the same registered property form as creation defaults. */
public final class EditorQuestBehaviorScreen extends EditorRegisteredPropertiesScreen {
    public EditorQuestBehaviorScreen(Screen parent, QuestBehavior value, Consumer<QuestBehavior> consumer) {
        this(parent, value, consumer, null, null);
    }
    public EditorQuestBehaviorScreen(Screen parent, QuestBehavior value, Consumer<QuestBehavior> consumer,
            Boolean hideDependencyLines, Consumer<Boolean> hideLinesSelection) {
        super(parent, Component.translatable("screen.brnquest.editor.behavior.title"),
                initial(value, hideDependencyLines), QuestPropertySchema.CATALOG.properties().stream()
                        .filter(property -> property.storage() == QuestPropertySchema.Storage.BEHAVIOR
                                || hideLinesSelection != null && property.key().equals("hide_dependency_lines")).toList(),
                values -> {
                    consumer.accept(QuestPropertySchema.behavior(values));
                    if (hideLinesSelection != null) hideLinesSelection.accept(QuestPropertySchema.appearance(values).hideDependencyLines());
                });
    }
    private static Map<String, String> initial(QuestBehavior behavior, Boolean hideLines) {
        var appearance = new QuestAppearance(QuestAppearance.DEFAULT.shape(), 1, 1, 0, hideLines);
        return QuestPropertySchema.values(appearance, behavior, Map.of());
    }
}
