package yourscraft.jasdewstarfield.brnquest.task;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/** Structured result returned when a task type handles a client submission intent. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record TaskSubmissionResult(boolean success, String code, String message) {
    public static TaskSubmissionResult accepted() {
        return new TaskSubmissionResult(true, "OK", "Task submission accepted");
    }

    public static TaskSubmissionResult failure(String code, String message) {
        return new TaskSubmissionResult(false, code, message);
    }
}
