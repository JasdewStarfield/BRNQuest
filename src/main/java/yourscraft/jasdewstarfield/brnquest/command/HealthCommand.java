package yourscraft.jasdewstarfield.brnquest.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookHealth;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import java.util.Locale;

/** Administrator-only observation: no reload, sync request, author service or storage write. */
final class HealthCommand {
    private HealthCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("health").requires(source -> source.hasPermission(2))
                .executes(context -> report(context.getSource(), false))
                .then(Commands.literal("details").executes(context -> report(context.getSource(), true)));
    }

    static int report(CommandSourceStack source, boolean details) {
        var health = QuestBookManager.get().health();
        var reload = health.reload();
        // All output uses this captured revision; a read never reruns extension validation.
        if (health.active().isEmpty()) {
            send(source, "no_active");
        } else {
            var active = health.active().orElseThrow();
            String title = active.book().title();
            send(source, "active", title.length() <= 256 ? title : title.substring(0, 256) + "…",
                    active.book().quests().size(), BrnQuestConstants.MAX_QUESTS);
            try {
                var budget = BrnQuestNetwork.bookTransferBudget(active);
                send(source, "budget", budget.encodedBytes(), BrnQuestConstants.MAX_BOOK_BYTES, budget.chunks());
                if (!budget.withinLimits()) send(source, "over_budget");
            } catch (RuntimeException exception) {
                // Observability must not turn an encoding failure into a command exception or state change.
                send(source, "encoding_failed");
                if (details) source.sendSuccess(() -> Component.literal(exception.getClass().getSimpleName()), false);
            }
            if (details) {
                source.sendSuccess(() -> Component.literal("book=" + active.book().id() + " revision=" + active.revision()
                        + " resource=" + health.activeResource().map(Object::toString).orElse("<none>")), false);
            }
        }
        send(source, "reload." + reload.outcome().name().toLowerCase(Locale.ROOT), reload.completedAt(), reload.diagnosticCount());
        if (reload.outcome() == QuestBookHealth.Outcome.REJECTED) {
            send(source, reload.retainedRevision().isBlank() ? "nothing_retained" : "retained");
            boolean capacity = reload.diagnostics().stream().anyMatch(d -> d.code().equals("BQV-124"));
            send(source, capacity ? "reason.capacity" : "reason.validation");
        }
        if (details) {
            source.sendSuccess(() -> Component.literal("candidate=" + reload.candidateBookId()
                    + " quests=" + reload.candidateQuestCount() + " retainedRevision=" + reload.retainedRevision()), false);
            // Bounded technical details stay readable; legacy diagnose retains the complete current report.
            for (var diagnostic : reload.diagnostics().stream().limit(5).toList()) {
                String text = diagnostic.severity() + " " + diagnostic.code() + " " + diagnostic.file()
                        + " " + diagnostic.objectId() + " " + diagnostic.path() + " " + diagnostic.message();
                source.sendSuccess(() -> Component.literal(text.length() <= 1024 ? text : text.substring(0, 1024) + "…"), false);
            }
            if (reload.diagnosticCount() > 5) send(source, "truncated", reload.diagnosticCount());
        } else send(source, "details_hint");
        // Successful inspection is independent of whether the inspected book is healthy.
        return 1;
    }

    private static void send(CommandSourceStack source, String suffix, Object... arguments) {
        source.sendSuccess(() -> Component.translatable("command.brnquest.health." + suffix, arguments), false);
    }
}
