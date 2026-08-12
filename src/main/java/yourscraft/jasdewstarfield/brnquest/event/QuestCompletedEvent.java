package yourscraft.jasdewstarfield.brnquest.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Server event carrying stable IDs without exposing progress storage internals. */
public final class QuestCompletedEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation bookId;
    private final ResourceLocation questId;
    public QuestCompletedEvent(ServerPlayer player, ResourceLocation bookId, ResourceLocation questId) { this.player = player; this.bookId = bookId; this.questId = questId; }
    public ServerPlayer player() { return player; }
    public ResourceLocation bookId() { return bookId; }
    public ResourceLocation questId() { return questId; }
}
