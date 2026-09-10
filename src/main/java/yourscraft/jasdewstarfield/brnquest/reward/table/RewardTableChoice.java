package yourscraft.jasdewstarfield.brnquest.reward.table;

import com.google.gson.JsonParser;
import java.util.List;
import static yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableJournal.*;

/** Single-layer choice decisions are immutable: version zero waits, version one has exactly one selected leaf. */
public final class RewardTableChoice {
    private RewardTableChoice() {}
    public static final String PATH = "root/choice/0";
    public record Confirmation(String attempt, String occurrence, int version, String entry) {}
    public static Attempt select(Attempt current, Confirmation request) {
        var snapshot = JsonParser.parseString(current.snapshot()).getAsJsonObject();
        if (!current.attemptId().equals(request.attempt()) || !PATH.equals(request.occurrence())
                || request.version() != 0 || !snapshot.get("mode").getAsString().equals("choice"))
            throw new IllegalArgumentException("Stale or invalid choice confirmation");
        if (snapshot.has("brnquest.selected")) {
            if (!snapshot.get("brnquest.selected").getAsString().equals(request.entry()))
                throw new IllegalArgumentException("Choice already confirmed with another entry");
            return current; // An identical retry observes the original decision, including its execution state.
        }
        if (current.state() != State.AWAITING_CHOICE) throw new IllegalArgumentException("Choice is not awaiting confirmation");
        if (current.leaves().stream().anyMatch(leaf -> leaf.state() != LeafState.NOT_STARTED))
            throw new IllegalArgumentException("Unconfirmed choice contains execution evidence; manual review required");
        var selected = current.leaves().stream().filter(leaf -> leaf.path().equals("root/" + request.entry()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Entry is not in the frozen choice"));
        snapshot.addProperty("brnquest.selected", request.entry());
        return new Attempt(current.version(), current.key(), current.attemptId(), current.executor(), snapshot.toString(),
                current.resources(), State.READY, List.of(selected), System.currentTimeMillis(), "Choice confirmed; no effects yet");
    }
}
