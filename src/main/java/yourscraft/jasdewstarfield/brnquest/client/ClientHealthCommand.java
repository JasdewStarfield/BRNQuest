package yourscraft.jasdewstarfield.brnquest.client;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import java.util.ArrayList;
import java.util.List;

/** Local-only diagnostics: no packet sends and no access to another player's state. */
public final class ClientHealthCommand {
    private ClientHealthCommand() {}
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("brnquest_client")
                .then(Commands.literal("health").executes(context -> report(context.getSource(), false))
                        .then(Commands.literal("details").executes(context -> report(context.getSource(), true)))));
    }
    private static int report(net.minecraft.commands.CommandSourceStack source, boolean details) {
        messages(ClientQuestState.get().syncHealth(), details).forEach(message -> source.sendSuccess(() -> message, false));
        return 1;
    }
    /** Pure formatting keeps local commands testable without creating a Minecraft client. */
    static List<Component> messages(ClientQuestState.SyncHealth health, boolean details) {
        List<Component> messages = new ArrayList<>();
        String status = !health.receivingRevision().isBlank() ? "receiving"
                : health.activeRevision().isBlank() ? "empty"
                : !health.advertisedRevision().isBlank() && !health.activeRevision().equals(health.advertisedRevision()) ? "behind" : "applied";
        messages.add(Component.translatable("command.brnquest.client_health." + status));
        messages.add(Component.translatable("command.brnquest.client_health.progress", health.receivedChunks(),
                health.expectedChunks(), health.receivedBytes(), health.expectedBytes(), health.ignoredChunks()));
        if (!health.lastRejectedCode().isBlank()) {
            messages.add(Component.translatable("command.brnquest.client_health.rejected"));
        }
        if (details) {
            messages.add(Component.literal("active=" + health.activeRevision() + " advertised=" + health.advertisedRevision()));
            messages.add(Component.literal("receiving=" + health.receivingRevision() + " lastApplied=" + health.lastAppliedRevision()));
            messages.add(Component.literal("lastRejected=" + health.lastRejectedRevision() + " code=" + health.lastRejectedCode()));
        } else messages.add(Component.translatable("command.brnquest.client_health.details_hint"));
        return List.copyOf(messages);
    }
}
