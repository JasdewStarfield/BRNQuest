package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One page owns one intent; stale pages and duplicate callbacks cannot enqueue another request. */
final class TaskSubmissionDispatch implements Consumer<TaskSubmissionSelection> {
    private final BooleanSupplier valid;
    private final Consumer<TaskSubmissionSelection> send;
    private boolean sent;

    TaskSubmissionDispatch(BooleanSupplier valid, Consumer<TaskSubmissionSelection> send) {
        this.valid = valid;
        this.send = send;
    }

    @Override public void accept(TaskSubmissionSelection selection) {
        Objects.requireNonNull(selection, "selection");
        if (sent || !valid.getAsBoolean()) return;
        sent = true;
        send.accept(selection);
    }
}
