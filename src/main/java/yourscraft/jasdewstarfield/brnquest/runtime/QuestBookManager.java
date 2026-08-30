package yourscraft.jasdewstarfield.brnquest.runtime;

import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookValidator;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvents;
import yourscraft.jasdewstarfield.brnquest.event.QuestBookReloadedEvent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the one active book and atomically preserves the last valid snapshot. */
public final class QuestBookManager {
    private static final QuestBookManager INSTANCE = new QuestBookManager();
    private final AtomicReference<QuestBookSnapshot> active = new AtomicReference<>();
    private volatile DiagnosticReport lastReport = new DiagnosticReport();
    private volatile long liveGeneration;
    private volatile net.minecraft.resources.ResourceLocation activeResource;

    private QuestBookManager() {}
    public static QuestBookManager get() { return INSTANCE; }
    public Optional<QuestBookSnapshot> active() { return Optional.ofNullable(active.get()); }
    public DiagnosticReport lastReport() { return lastReport.copy(); }
    public long liveGeneration() { return liveGeneration; }
    public Optional<net.minecraft.resources.ResourceLocation> activeResource() { return Optional.ofNullable(activeResource); }

    /** Internal live-edit path: full validation and durable replacement must finish before this call. */
    public synchronized void installLiveValidated(QuestBookSnapshot current, DiagnosticReport report) {
        QuestBookSnapshot previous = active.get();
        liveGeneration++;
        lastReport = report.copy();
        active.set(current);
        BrnQuestEvents.post(new QuestBookReloadedEvent(previous == null ? null : ApiViews.book(previous), ApiViews.book(current)));
    }

    public synchronized boolean install(QuestBookDefinition book, DiagnosticReport report) {
        return install(book, report, null);
    }

    /** Remember the actual resource key: a book ID is not required to match its source filename. */
    public synchronized boolean install(QuestBookDefinition book, DiagnosticReport report,
                                        net.minecraft.resources.ResourceLocation resource) {
        DiagnosticReport working = report == null ? new DiagnosticReport() : report;
        if (book == null) {
            working.add(new Diagnostic(Diagnostic.Severity.FATAL, "BQV-004", "", "", "",
                    "Reload produced no task book"));
        } else {
            try {
                // Decode and every registered extension validation finish before active changes.
                QuestBookValidator.validate(book, working);
            } catch (RuntimeException | LinkageError exception) {
                working.add(new Diagnostic(Diagnostic.Severity.FATAL, "BQV-005", "", "", "",
                        "Extension validation failed: " + exception.getClass().getSimpleName()));
            }
        }
        lastReport = working.copy();
        if (working.hasFatal()) return false;
        QuestBookSnapshot previous = active.get();
        QuestBookSnapshot current = QuestBookSnapshot.of(book);
        // One atomic pointer write is the only moment the new revision becomes visible.
        active.set(current);
        activeResource = resource;
        BrnQuestEvents.post(new QuestBookReloadedEvent(previous == null ? null : ApiViews.book(previous),
                ApiViews.book(current)));
        return true;
    }
}
