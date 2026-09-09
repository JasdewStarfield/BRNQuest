package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
import java.util.*;

/** Sparse page cache: the scrollbar represents the full result set, while only visible pages are queried. */
final class ServerFieldPages {
    private final Map<Integer, ServerFieldSources.Entry> entries = new HashMap<>();
    private final Set<Integer> receivedPages = new HashSet<>();
    private String pending = "";
    private int offset;
    private int total;
    void reset() { entries.clear(); receivedPages.clear(); pending = ""; total = 0; }
    /** Selection metadata can change without discarding the already displayed search pages. */
    void cancelPending() { pending = ""; }
    String begin(int nextOffset) { offset = nextOffset; pending = UUID.randomUUID().toString(); return pending; }
    boolean waiting() { return !pending.isEmpty(); }
    int total() { return total; }
    ServerFieldSources.Entry entry(int index) { return entries.get(index); }
    boolean needs(int index) { return !receivedPages.contains(index / ServerFieldSources.PAGE_SIZE); }
    boolean receive(String id, ServerFieldSources.Result result) {
        if (pending.isEmpty() || !pending.equals(id) || result == null) return false;
        pending = "";
        if (total != result.total()) { entries.clear(); receivedPages.clear(); }
        total = Math.max(0, result.total());
        for (int i = 0; i < result.entries().size() && offset + i < total; i++) entries.put(offset+i,result.entries().get(i));
        receivedPages.add(offset / ServerFieldSources.PAGE_SIZE);
        return true;
    }
}
