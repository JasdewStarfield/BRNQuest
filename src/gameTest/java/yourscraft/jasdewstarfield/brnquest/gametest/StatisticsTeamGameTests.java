package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.gametest.*;
import yourscraft.jasdewstarfield.brnquest.owner.*;
import yourscraft.jasdewstarfield.brnquest.progress.*;
import java.util.*;
import static yourscraft.jasdewstarfield.brnquest.gametest.StatisticsLifecycleGameTests.*;

/** Real player counters and engine transactions with a disposable owner-provider fixture, independent of optional mods. */
@GameTestHolder("brnquest_statistics")
@SuppressWarnings("removal")
public final class StatisticsTeamGameTests {
    @GameTest(template="empty",batch="statisticTeamHistory",timeoutTicks=80) @PrefixGameTestTemplate(false)
    public static void historicalTeamUsesHighestMemberAndOwnerHistoriesStaySeparate(GameTestHelper helper) {
        var a=helper.makeMockServerPlayerInLevel();var b=helper.makeMockServerPlayerInLevel();
        helper.runAfterDelay(due(a),()->{
            try(var teams=new Teams()) {
                UUID team=UUID.randomUUID();teams.join(a,team);teams.join(b,team);
                var goal=goal("team_history","jump","total",100);
                var quest=quest("team_history",List.of(goal),List.of(),behavior(false,false,false));
                set(a,Stats.JUMP,8);set(b,Stats.JUMP,12);install(List.of(quest));
                var engine=ProgressEngine.get();engine.reconcile(a);engine.reconcile(b);engine.pollTasks(a);engine.pollTasks(b);
                helper.assertTrue(engine.taskProgress(a,quest,goal)==12,"history is the highest member value, not summed");
                restore(a);teams.leave(b);engine.pollTasks(a);
                helper.assertTrue(engine.taskProgress(a,quest,goal)==12,"departure and SavedData restore retain team history");
                teams.join(b,team);engine.reconcile(b);engine.pollTasks(b);
                helper.assertTrue(engine.taskProgress(a,quest,goal)==12,"rejoining cannot add the same history again");
                teams.join(b,UUID.randomUUID());engine.reconcile(b);
                helper.assertTrue(engine.taskProgress(b,quest,goal)==0,"another owner starts an independent ledger");
                engine.pollTasks(b);
                helper.assertTrue(engine.taskProgress(b,quest,goal)==12,"new owner can read that player's historical maximum");
                helper.succeed();
            }
        });
    }
    @GameTest(template="empty",batch="statisticTeamDelta",timeoutTicks=80) @PrefixGameTestTemplate(false)
    public static void incrementalMembersDoNotReplayOrAttributeActionsWhileAway(GameTestHelper helper) {
        var a=helper.makeMockServerPlayerInLevel();var b=helper.makeMockServerPlayerInLevel();var c=helper.makeMockServerPlayerInLevel();
        helper.runAfterDelay(due(a),()->{
            try(var teams=new Teams()) {
                UUID team=UUID.randomUUID();teams.join(a,team);teams.join(b,team);
                var goal=goal("team_delta","jump","since_available",100);
                var quest=quest("team_delta",List.of(goal),List.of(),behavior(false,false,false));
                set(a,Stats.JUMP,80);set(b,Stats.JUMP,100);set(c,Stats.JUMP,500);install(List.of(quest));
                var engine=ProgressEngine.get();engine.reconcile(a);engine.reconcile(b);
                set(a,Stats.JUMP,82);set(b,Stats.JUMP,103);engine.pollTasks(a);engine.pollTasks(b);engine.pollTasks(b);
                helper.assertTrue(engine.taskProgress(a,quest,goal)==5,"two independent contributors add 2 and 3 once");
                restore(a);teams.leave(b);engine.reconcile(b);engine.pollTasks(a);
                set(b,Stats.JUMP,150);teams.join(b,team);engine.reconcile(b);engine.pollTasks(b);
                helper.assertTrue(engine.taskProgress(a,quest,goal)==5,"actions while away are excluded on rejoin");
                helper.assertTrue(engine.progress(a).sampleState(goal.id().toString(),b.getUUID()).values().get("contribution")==3,"old member contribution survives rebasing");
                set(b,Stats.JUMP,151);engine.pollTasks(b);
                helper.assertTrue(engine.taskProgress(a,quest,goal)==6,"returning member resumes with only new actions");
                teams.join(c,team);engine.reconcile(c);engine.pollTasks(c);
                helper.assertTrue(engine.taskProgress(a,quest,goal)==6,"new member's earlier 500 actions do not count");
                set(c,Stats.JUMP,502);engine.pollTasks(c);
                helper.assertTrue(engine.taskProgress(a,quest,goal)==8,"new member contributes after joining");
                helper.succeed();
            }
        });
    }
    @GameTest(template="empty",batch="statisticAllMembers",timeoutTicks=80) @PrefixGameTestTemplate(false)
    public static void allMembersUseIndividualThresholdsIncludingOfflineRoster(GameTestHelper helper) {
        var a=helper.makeMockServerPlayerInLevel();var b=helper.makeMockServerPlayerInLevel();
        helper.runAfterDelay(due(a),()->{
            try(var teams=new Teams()) {
                UUID team=UUID.randomUUID();teams.join(a,team);teams.join(b,team);
                UUID offline=UUID.randomUUID();teams.memberships.put(offline,team);
                var goal=goal("all_members","jump","total",3);
                var quest=quest("all_members",List.of(goal),List.of(),behavior(false,false,true));
                set(a,Stats.JUMP,3);set(b,Stats.JUMP,1);install(List.of(quest));
                var engine=ProgressEngine.get();engine.reconcile(a);engine.reconcile(b);engine.pollTasks(a);engine.pollTasks(b);
                helper.assertTrue(engine.taskProgress(a,quest,goal)==3 && engine.taskProgress(b,quest,goal)==1,"member counters remain independent");
                helper.assertTrue(engine.progress(a).status(quest.id().toString())!=QuestStatus.COMPLETED,"one member cannot complete for the whole roster");
                restore(a);set(b,Stats.JUMP,3);engine.pollTasks(b);
                helper.assertTrue(engine.progress(a).status(quest.id().toString())!=QuestStatus.COMPLETED,"offline roster member still needs an individual receipt");
                teams.memberships.remove(offline);engine.complete(a,quest.id(),false);
                helper.assertTrue(engine.progress(a).status(quest.id().toString())==QuestStatus.COMPLETED,"all members reaching their thresholds complete the shared quest");
                helper.succeed();
            }
        });
    }
    /** Fixture replaces only the isolated test registry entry and always restores the exact preceding provider. */
    private static final class Teams implements AutoCloseable,ProgressOwnerProvider {
        private final ResourceLocation id=ResourceLocation.parse("brnquest:openpac");
        private final Map<UUID,UUID> memberships=new HashMap<>();
        private final Map<ResourceLocation,ProgressOwnerProvider> providers;
        private final ProgressOwnerProvider old;
        @SuppressWarnings("unchecked") Teams(){
            try {
                var field=ProgressOwnerProviderRegistry.class.getDeclaredField("PROVIDERS");field.setAccessible(true);
                providers=(Map<ResourceLocation,ProgressOwnerProvider>)field.get(null);old=providers.put(id,this);
            }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        }
        void join(ServerPlayer player,UUID team){memberships.put(player.getUUID(),team);}
        void leave(ServerPlayer player){memberships.remove(player.getUUID());}
        public ResourceLocation id(){return id;}
        public Optional<ProgressOwnerId> resolve(ServerPlayer player){return Optional.ofNullable(memberships.get(player.getUUID())).map(team->new ProgressOwnerId(id,team));}
        public Set<UUID> members(MinecraftServer server,ProgressOwnerId owner){var result=new HashSet<UUID>();memberships.forEach((player,team)->{if(team.equals(owner.ownerId()))result.add(player);});return Set.copyOf(result);}
        public ProgressOwnerLifecycle lifecycle(MinecraftServer server,ProgressOwnerId owner){return ProgressOwnerLifecycle.ACTIVE;}
        public Optional<ProgressOwnerArchive> archivedSnapshot(MinecraftServer server,ProgressOwnerId owner){return Optional.empty();}
        public void close(){if(old==null)providers.remove(id,this);else providers.put(id,old);}
    }
}
