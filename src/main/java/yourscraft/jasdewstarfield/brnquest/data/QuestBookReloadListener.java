package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookReloadTransaction;

import java.util.Comparator;
import java.util.Map;

/** Loads the deterministic native book file and commits only a fully parsed snapshot. */
public final class QuestBookReloadListener extends SimpleJsonResourceReloadListener {
    private long preparedGeneration;
    public QuestBookReloadListener() { super(new Gson(), "brnquest/books"); }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        preparedGeneration = QuestBookManager.get().liveGeneration();
        return super.prepare(manager, profiler);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> values, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        // A reload prepared before a successful live edit must not restore its older resource snapshot.
        if (preparedGeneration != QuestBookManager.get().liveGeneration()) {
            QuestBookReloadTransaction.abort();
            return;
        }
        DiagnosticReport report = new DiagnosticReport();
        if (values.isEmpty()) {
            report.add(new Diagnostic(Diagnostic.Severity.WARN, "BQV-001", "", "", "", "No BRNQuest book found"));
            QuestBookReloadTransaction.install(new QuestBookDefinition(ResourceLocation.fromNamespaceAndPath(BRNQuest.MOD_ID, "empty"), 1, "BRNQuest", java.util.List.of(), java.util.List.of(), Map.of()), report, null);
            return;
        }
        if (values.size() > 1) report.add(new Diagnostic(Diagnostic.Severity.WARN, "BQV-002", "", "", "", "Only the lexically first book is active in schema 1"));
        Map.Entry<ResourceLocation, JsonElement> selected = values.entrySet().stream().min(Comparator.comparing(e -> e.getKey().toString())).orElseThrow();
        try {
            QuestBookDefinition book = NativeBookJson.decode(selected.getValue().getAsJsonObject());
            if (QuestBookReloadTransaction.install(book, report, selected.getKey())) {
                BRNQuest.LOGGER.debug("[BRNQuest] Loaded {} quests from {}", book.quests().size(), selected.getKey());
            } else {
                BRNQuest.LOGGER.error("[BRNQuest] Retaining previous quest snapshot after validation failure in {}",
                        selected.getKey());
            }
        } catch (Exception exception) {
            report.add(new Diagnostic(Diagnostic.Severity.FATAL, "BQV-003", selected.getKey().toString(), "", "", exception.getMessage()));
            QuestBookReloadTransaction.reject(report);
            BRNQuest.LOGGER.error("[BRNQuest] Retaining previous quest snapshot after reload failure", exception);
        }
    }
}
