package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.data.QuestNotificationPolicy;
import yourscraft.jasdewstarfield.brnquest.editor.QuestPropertySchema;
import java.util.Map;
import java.util.function.Consumer;

/** Legacy policy adapter; its controls are generated from registered notification properties. */
final class EditorQuestNotificationsScreen extends EditorRegisteredPropertiesScreen {
    EditorQuestNotificationsScreen(Screen parent, QuestNotificationPolicy initial, Consumer<QuestNotificationPolicy> apply) {
        super(parent, Component.translatable("screen.brnquest.notifications.title"), initial.fields(),
                QuestPropertySchema.instance("notifications"),
                values -> apply.accept(QuestNotificationPolicy.read(QuestPropertySchema.CATALOG.extensions(Map.of(), values))));
    }
}
