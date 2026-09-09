package yourscraft.jasdewstarfield.brnquest.editor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.*;
import java.util.*;

/** Read-only server-backed field choices. Sources never mutate drafts or trust client registry results. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ServerFieldSources {
    private ServerFieldSources() {}
    public static final int PAGE_SIZE = 64;
    @ApiStatus(ApiStability.EXPERIMENTAL)
    public record Entry(String value, int count) {}
    @ApiStatus(ApiStability.EXPERIMENTAL)
    public record Result(List<Entry> entries, int total, int selectedCount, String error, String current, String detail, String previewSource) {
        public Result { entries = List.copyOf(entries); }
        /** Older providers need not offer a read-only member preview. */
        public Result(List<Entry> entries, int total, int selectedCount, String error, String current, String detail) {
            this(entries,total,selectedCount,error,current,detail,"");
        }
        public Result(List<Entry> entries, int total, int selectedCount, String error, String current) {
            this(entries, total, selectedCount, error, current, "");
        }
    }
    @ApiStatus(ApiStability.EXPERIMENTAL)
    @FunctionalInterface public interface Source {
        Result query(ServerPlayer player, String filter, String selected);
        /** Context is untrusted editor input, used only to resolve dependent choices. */
        default Result queryPage(ServerPlayer player, String filter, String selected, int offset, Map<String,String> context) {
            return queryPage(player,filter,selected,offset);
        }
        /** Small existing sources can return their full collection; expensive sources override this page hook. */
        default Result queryPage(ServerPlayer player, String filter, String selected, int offset) {
            Result result = query(player, filter, selected);
            return new Result(result.entries().stream().skip(offset).limit(PAGE_SIZE).toList(), result.total(),
                    result.selectedCount(), result.error(), result.current(), result.detail(), result.previewSource());
        }
    }
    private static final Map<ResourceLocation, Source> SOURCES = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean frozen;
    public static synchronized void register(ResourceLocation id, Source source) {
        Objects.requireNonNull(id, "id");
        if (frozen || SOURCES.putIfAbsent(id, Objects.requireNonNull(source)) != null) throw new IllegalStateException("Field source already registered/frozen: " + id);
    }
    @ApiStatus(ApiStability.INTERNAL)
    public static synchronized void freeze() { frozen = true; }
    /** Batch registration preflight; callers hold the registry class lock until commit. */
    @ApiStatus(ApiStability.INTERNAL)
    public static synchronized void requireAvailable(ResourceLocation id) {
        if (frozen || SOURCES.containsKey(id)) throw new IllegalStateException("Field source already registered/frozen: " + id);
    }
    public static Result query(ServerPlayer player, ResourceLocation source, String filter, String selected) {
        return query(player, source, filter, selected, 0);
    }
    public static Result query(ServerPlayer player, ResourceLocation source, String filter, String selected, int offset) {
        return query(player,source,filter,selected,offset,Map.of());
    }
    public static Result query(ServerPlayer player, ResourceLocation source, String filter, String selected, int offset, Map<String,String> context) {
        if (offset < 0) throw new IllegalArgumentException("Negative field page offset");
        if (!player.hasPermissions(2)) return new Result(List.of(),0,0,"permission", "");
        var provider = SOURCES.get(source);
        return provider == null ? new Result(List.of(),0,0,"unknown_source", "") : provider.queryPage(player, filter, selected, offset, Map.copyOf(context));
    }
}
