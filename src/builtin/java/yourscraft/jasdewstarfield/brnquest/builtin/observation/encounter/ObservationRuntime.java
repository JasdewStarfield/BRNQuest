package yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.builtin.network.ObservationNetwork;
import java.util.*;

/** Transient timing is player-local even for shared quests; only a full gaze reaches the shared ledger. */
public final class ObservationRuntime {
    private record Key(ResourceLocation book,ResourceLocation task) {}
    private record Attempt(yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId owner,ResourceLocation dimension,EncounterConfig config) {}
    private record State(Attempt attempt,GazeTimer timer) {}
    private static final Map<ServerPlayer,Map<Key,State>> STATES=new WeakHashMap<>();
    private ObservationRuntime() {}
    public static void clear() { STATES.clear(); }
    public static void clear(ServerPlayer player) { STATES.remove(player); }
    /** Explicit reset must also discard a gaze that has not written any permanent progress yet. */
    public static void clearTask(TaskContext context) {
        var states=STATES.get(context.player());
        if(states!=null) states.remove(new Key(context.bookId(),context.task().id()));
        ObservationNetwork.send(context.player(),context.bookId(),context.task().id(),0);
    }
    /** Read-only diagnostic for the current player's transient timer. */
    public static int ticks(TaskContext context) {
        var states=STATES.get(context.player()); var state=states==null ? null : states.get(new Key(context.bookId(),context.task().id()));
        return state==null ? 0 : state.timer().ticks();
    }
    public static long sample(TaskContext context,EncounterConfig config) {
        var player=context.player();
        var owner=BrnQuestApi.getProgressOwner(player).orElseThrow().id();
        var key=new Key(context.bookId(),context.task().id());
        // Owner changes discard local timing; resetTransientState and reload/logout hooks clear both partial and completed attempts.
        var attempt=new Attempt(owner,player.level().dimension().location(),config);
        var states=STATES.computeIfAbsent(player,ignored->new HashMap<>());
        var state=states.get(key);
        if(state==null || !state.attempt().equals(attempt)) { state=new State(attempt,new GazeTimer()); states.put(key,state); }
        var hit=ObservationRay.hit(player,config);
        int previous=state.timer().ticks();
        int ticks=state.timer().sample(player.server.getTickCount(),hit==null ? null : hit.identity());
        boolean complete=hit!=null && ticks>=config.duration();
        // Small display-only updates replace per-tick full-book progress writes. Idle misses send nothing.
        if(complete || ticks>0 && (ticks==1 || player.server.getTickCount()%4==0) || ticks==0 && previous>0)
            ObservationNetwork.send(player,key.book(),key.task(),ticks);
        if(complete) states.remove(key);
        return complete ? 1 : 0;
    }
}
