package yourscraft.jasdewstarfield.brnquest.client;

import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.author.DraftOrigin;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState.CatalogEntry;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState.DraftVersionEntry;
import java.util.*;

/** Auxiliary query results have their own lifecycle and cannot transition an editing lease. */
final class EditorQueryState {
    private static final Gson GSON = new Gson();
    private List<CatalogEntry> catalog = List.of();
    private ResourceLocation versionsBookId;
    private List<DraftVersionEntry> draftVersions = List.of();
    private boolean versionsLoading;
    private String versionsError = "";
    AuthoringNetwork.CatalogResponseWire acceptCatalog(String json) {
        AuthoringNetwork.CatalogResponseWire response = GSON.fromJson(json,
                AuthoringNetwork.CatalogResponseWire.class);
        if (response == null) throw new IllegalArgumentException("Missing catalog response");
        List<CatalogEntry> decoded = new ArrayList<>();
        if (response.entries() != null) {
            for (AuthoringNetwork.CatalogEntryWire entry : response.entries()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.bookId());
                DraftOrigin origin = parseOrigin(entry.origin());
                if (id != null && entry.draftRevision() != null && !entry.draftRevision().isBlank()) {
                decoded.add(new CatalogEntry(id, entry.title() == null ? "" : entry.title(),
                        entry.draftRevision(), origin, entry.importSource() == null ? "" : entry.importSource()));
                }
                if (decoded.size() >= BrnQuestConstants.MAX_EDITOR_CATALOG_ENTRIES) break;
            }
        }
        catalog = List.copyOf(decoded);
        return response;
    }
    void clearCatalog() { catalog = List.of(); }
    List<CatalogEntry> catalog() { return catalog; }
    List<DraftVersionEntry> draftVersions() { return draftVersions; }
    boolean versionsLoading() { return versionsLoading; }
    String versionsError() { return versionsError; }
    void clear() {
        clearCatalog();
        versionsBookId = null;
        draftVersions = List.of();
        versionsLoading = false;
        versionsError = "";
    }
    void saved(ResourceLocation bookId, String savedRevision, String title) {
        List<CatalogEntry> updatedCatalog = new ArrayList<>(catalog.size() + 1);
        boolean found = false;
        for (CatalogEntry entry : catalog) {
            if (entry.bookId().equals(bookId)) {
                updatedCatalog.add(new CatalogEntry(bookId, title == null ? entry.title() : title,
                        savedRevision, entry.origin(), entry.importSource()));
                found = true;
            } else updatedCatalog.add(entry);
        }
        if (!found && title != null) updatedCatalog.add(new CatalogEntry(bookId, title,
                savedRevision, DraftOrigin.UNKNOWN, ""));
        catalog = List.copyOf(updatedCatalog);
    }
    /** The history belongs to one book and is replaced only by its matching server reply. */
    void beginDraftVersions(ResourceLocation targetBookId) {
        versionsBookId = targetBookId;
        draftVersions = List.of();
        versionsError = "";
        versionsLoading = true;
    }

    void acceptDraftVersions(String json) {
        try {
            var response = GSON.fromJson(json, AuthoringNetwork.DraftVersionsResponseWire.class);
            if (response == null || versionsBookId == null
                    || !versionsBookId.equals(ResourceLocation.tryParse(response.bookId()))) return;
            versionsLoading = false;
            if (!"SUCCESS".equals(response.status()) && !"NO_CHANGE".equals(response.status())) {
                versionsError = safe(response.code());
                draftVersions = List.of();
                return;
            }
            List<DraftVersionEntry> decoded = new ArrayList<>();
            if (response.versions() != null) {
                for (var version : response.versions()) {
                    if (version.backupId() == null || version.backupId().isBlank()
                            || version.revision() == null || version.revision().isBlank()) continue;
                    decoded.add(new DraftVersionEntry(version.backupId(), version.revision(),
                            safe(version.title()), Math.max(0L, version.savedAtEpochMillis())));
                    if (decoded.size() >= 40) break;
                }
            }
            draftVersions = List.copyOf(decoded);
            versionsError = "";
        } catch (RuntimeException exception) {
            versionsLoading = false;
            draftVersions = List.of();
            versionsError = "INVALID_DRAFT_VERSIONS";
        }
    }

    /** Keeps the displayed book easy to reach while retaining backup-like drafts at the end. */
    List<CatalogEntry> orderedCatalog(ResourceLocation displayedBookId) {
        return catalog.stream().sorted(Comparator
                .comparingInt((CatalogEntry entry) -> catalogRank(entry, displayedBookId))
                .thenComparing(entry -> catalogLabel(entry).toLowerCase(Locale.ROOT))
                .thenComparing(entry -> entry.bookId().toString()))
                .toList();
    }

    /** Applies the menu's title-or-ID filter after stable relevance ordering. */
    List<CatalogEntry> filteredCatalog(ResourceLocation displayedBookId, String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return orderedCatalog(displayedBookId).stream()
                .filter(entry -> needle.isEmpty() || catalogSearchText(entry).contains(needle))
                .toList();
    }

    boolean contains(ResourceLocation target) {
        return catalog.stream().anyMatch(entry -> entry.bookId().equals(target));
    }

    private static int catalogRank(CatalogEntry entry, ResourceLocation displayedBookId) {
        if (entry.bookId().equals(displayedBookId)) return 0;
        return isBackupLike(entry) ? 2 : 1;
    }

    private static boolean isBackupLike(CatalogEntry entry) {
        String searchable = (entry.bookId() + " " + entry.title()).toLowerCase(Locale.ROOT);
        return searchable.contains("backup") || searchable.contains("备份");
    }

    private static String catalogLabel(CatalogEntry entry) {
        return entry.title().isBlank() ? entry.bookId().toString() : entry.title();
    }

    private static String catalogSearchText(CatalogEntry entry) {
        return (entry.bookId() + " " + entry.title() + " " + entry.importSource()).toLowerCase(Locale.ROOT);
    }

    private static DraftOrigin parseOrigin(String value) {
        try {
            return DraftOrigin.valueOf(value);
        } catch (RuntimeException exception) {
            return DraftOrigin.UNKNOWN;
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
