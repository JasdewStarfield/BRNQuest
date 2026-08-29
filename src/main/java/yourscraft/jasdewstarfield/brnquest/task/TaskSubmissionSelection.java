package yourscraft.jasdewstarfield.brnquest.task;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.LinkedHashSet;
import java.util.List;

/** Bounded player intent; task types must still validate every selected inventory slot on the server. */
@ApiStatus(ApiStability.INTERNAL)
public record TaskSubmissionSelection(List<Integer> inventorySlots) {
    public static final int MAX_SELECTED_SLOTS = 36;
    public static final TaskSubmissionSelection AUTOMATIC = new TaskSubmissionSelection(List.of());

    public TaskSubmissionSelection {
        inventorySlots = List.copyOf(inventorySlots);
        if (inventorySlots.size() > MAX_SELECTED_SLOTS
                || new LinkedHashSet<>(inventorySlots).size() != inventorySlots.size()
                || inventorySlots.stream().anyMatch(index -> index == null || index < 0 || index >= 36)) {
            throw new IllegalArgumentException("Task submission selection is invalid");
        }
    }

    public boolean explicit() { return !inventorySlots.isEmpty(); }
}
