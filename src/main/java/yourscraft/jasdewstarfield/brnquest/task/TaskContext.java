package yourscraft.jasdewstarfield.brnquest.task;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;

import java.util.Objects;

/** Immutable task evaluation context; mutable progress storage never crosses the SPI boundary. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record TaskContext(ServerPlayer player, ResourceLocation bookId, ResourceLocation questId,
                          TaskView task, long progress) {
    public TaskContext {
        Objects.requireNonNull(bookId, "bookId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(task, "task");
        if (!bookId.equals(task.bookId())) throw new IllegalArgumentException("Task belongs to a different book");
    }
}
