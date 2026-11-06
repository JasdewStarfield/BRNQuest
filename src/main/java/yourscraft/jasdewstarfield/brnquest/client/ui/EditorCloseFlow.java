package yourscraft.jasdewstarfield.brnquest.client.ui;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** One explicit continuation survives confirmation and the asynchronous lease-close acknowledgement. */
final class EditorCloseFlow {
    enum Step { IDLE, CONFIRM_DISCARD, CLOSING }
    enum AfterClose { NONE, COPY_CURRENT, SHOW_VERSIONS }
    record Plan(ResourceLocation target, String revision, boolean closeScreen, AfterClose after) {}
    private Step step = Step.IDLE;
    private Plan plan;
    Step step() { return step; }
    Plan plan() { return plan; }
    void request(Plan plan, boolean confirm) { this.plan = plan; step = confirm ? Step.CONFIRM_DISCARD : Step.CLOSING; }
    Optional<Plan> confirm() {
        if (step != Step.CONFIRM_DISCARD) return Optional.empty();
        step = Step.CLOSING;
        return Optional.of(plan);
    }
    Optional<Plan> closed() {
        if (step != Step.CLOSING) return Optional.empty();
        var result = plan;
        cancel();
        return Optional.of(result);
    }
    void cancel() { step = Step.IDLE; plan = null; }
}
