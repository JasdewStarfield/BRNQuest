package yourscraft.jasdewstarfield.brnquest.runtime;

import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookValidator;
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

    private QuestBookManager() {}
    public static QuestBookManager get() { return INSTANCE; }
    public Optional<QuestBookSnapshot> active() { return Optional.ofNullable(active.get()); }
    public DiagnosticReport lastReport() { return lastReport; }

    public boolean install(QuestBookDefinition book, DiagnosticReport report) {
        if (book != null) QuestBookValidator.validate(book, report);
        lastReport = report;
        if (report.hasFatal()) return false;
        QuestBookSnapshot previous = active.get();
        QuestBookSnapshot current = QuestBookSnapshot.of(book);
        active.set(current);
        BrnQuestEvents.post(new QuestBookReloadedEvent(previous == null ? null : ApiViews.book(previous),
                ApiViews.book(current)));
        return true;
    }
}
