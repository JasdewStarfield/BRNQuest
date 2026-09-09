package yourscraft.jasdewstarfield.brnquest.gametest;

import yourscraft.jasdewstarfield.brnquest.network.AdvancementGroupNetwork;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.*;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
import yourscraft.jasdewstarfield.brnquest.progress.*;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.advancement.*;
import java.util.*;

/** Vanilla impossible-trigger fixtures make criterion, side-effect and owner-ledger checks deterministic. */
@GameTestHolder("brnquest")
@SuppressWarnings("removal")
public final class AdvancementGameTests {
    @GameTest(template="empty", batch="advancementLatch")
    @PrefixGameTestTemplate(false)
    public static void existingCriteriaLatchPersistAndResetWithoutRevokingVanilla(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();
        var first=holder(player,"first");
        player.getAdvancements().award(first,"a");
        var task=task("criterion",Map.of("advancement",id("first").toString(),"criterion","a"));
        var quest=quest("latch",List.of(task),List.of(),List.of(),false);
        install(List.of(quest));
        var engine=ProgressEngine.get(); engine.reconcile(player); engine.pollTasks(player);
        helper.assertTrue(engine.progress(player).taskProgress(task.id().toString())==1,"pre-existing criterion counts");
        helper.assertTrue(!player.getAdvancements().getOrStartProgress(first).isDone(),"criterion is not the whole advancement");
        player.getAdvancements().revoke(first,"a"); engine.pollTasks(player);
        helper.assertTrue(engine.progress(player).taskProgress(task.id().toString())==1,"vanilla revoke never rolls back the quest ledger");
        var saved=QuestProgressData.get(player.server).save(new net.minecraft.nbt.CompoundTag(),player.registryAccess());
        player.server.overworld().getDataStorage().set("brnquest_progress",QuestProgressData.load(saved,player.registryAccess()));
        helper.assertTrue(engine.progress(player).taskProgress(task.id().toString())==1,"SavedData roundtrip retains completion");
        engine.reset(player,quest.id()); engine.pollTasks(player);
        helper.assertTrue(engine.progress(player).taskProgress(task.id().toString())==0,"revoked criterion stays incomplete after reset");
        player.getAdvancements().award(first,"a"); engine.pollTasks(player);
        helper.assertTrue(engine.progress(player).taskProgress(task.id().toString())==1,"grant event invalidates the cached false result");
        engine.reset(player,quest.id()); engine.pollTasks(player);
        helper.assertTrue(engine.progress(player).taskProgress(task.id().toString())==1,"reset counts still-granted vanilla progress again");
        helper.succeed();
    }

    @GameTest(template="empty", batch="advancementGroups")
    @PrefixGameTestTemplate(false)
    public static void groupModesAndEventInvalidationUseNativeProgress(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();
        var any=new AdvancementConfig("#brnquest_f3:nested","",false);
        var all=new AdvancementConfig("#brnquest_f3:pair","",true);
        helper.assertTrue(!AdvancementTargets.matches(player,any),"ANY starts false");
        grant(player,"first");
        helper.assertTrue(AdvancementTargets.matches(player,any),"ANY sees the grant event");
        helper.assertTrue(!AdvancementTargets.matches(player,all),"ALL needs both members");
        grant(player,"second");
        helper.assertTrue(AdvancementTargets.matches(player,all),"ALL sees both members");
        player.getAdvancements().revoke(holder(player,"second"),"only");
        helper.assertTrue(!AdvancementTargets.matches(player,all),"revoke clears cached true reads for future tasks");
        for(String group:List.of("empty","missing","cycle"))
            helper.assertTrue(!AdvancementTargets.matches(player,new AdvancementConfig("#brnquest_f3:"+group,"",true)),"invalid ALL group never succeeds: "+group);
        helper.assertTrue(!AdvancementTargets.matches(player,new AdvancementConfig(id("first").toString(),"unknown",false)),"missing criterion never succeeds");
        AdvancementTargets.invalidate();
        helper.assertTrue(AdvancementTargets.matches(player,any),"resource-cache invalidation rebuilds nested groups");
        helper.succeed();
    }

    @GameTest(template="empty", batch="advancementRewards")
    @PrefixGameTestTemplate(false)
    public static void rewardsPreflightAllTargetsAndUseNativeSideEffectsOnce(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();
        var reward=new AdvancementReward();
        var context=context(player,Map.of("advancement","#brnquest_f3:missing"));
        helper.assertTrue(!reward.execute(context,context.reward().config()).success(),"missing member rejects whole reward");
        helper.assertTrue(!player.getAdvancements().getOrStartProgress(holder(player,"first")).isDone(),"valid member was not partially granted");
        helper.assertTrue(player.totalExperience==0,"invalid group has no native rewards");
        var a=Map.of("advancement",id("first").toString(),"criterion","a");
        helper.assertTrue(reward.execute(context(player,a),a).success(),"single criterion is granted");
        helper.assertTrue(player.totalExperience==0,"incomplete advancement has no completion rewards");
        var b=Map.of("advancement",id("first").toString(),"criterion","b");
        helper.assertTrue(reward.execute(context(player,b),b).success(),"last criterion completes advancement");
        helper.assertTrue(player.totalExperience==10,"vanilla experience and function both execute");
        reward.execute(context(player,b),b);
        helper.assertTrue(player.totalExperience==10,"already granted criterion is a no-op");
        var other=helper.makeMockServerPlayerInLevel();
        var second=Map.of("advancement",id("second").toString());
        reward.execute(context(other,second),second);
        helper.assertTrue(!other.getAdvancements().getOrStartProgress(holder(other,"first")).isDone(),"granting a child never grants its parent");
        helper.assertTrue(other.totalExperience==5,"only the child's reward executes");
        helper.succeed();
    }

    @GameTest(template="empty", batch="advancementClaim")
    @PrefixGameTestTemplate(false)
    public static void claimFailureStaysUnclaimedAndRewardProgressWaitsForPolling(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();
        var gate=new TaskDefinition(id("book"),id("check"),ResourceLocation.parse("brnquest:checkmark"),Map.of(),false);
        var valid=reward("grant",Map.of("advancement",id("first").toString()));
        var missing=reward("missing_reward",Map.of("advancement",id("first").toString(),"criterion","missing"));
        var first=quest("claim",List.of(gate),List.of(valid,missing),List.of(),false);
        var follow=task("follow",Map.of("advancement",id("first").toString()));
        var next=quest("next",List.of(follow),List.of(),List.of(first.id()),false);
        install(List.of(first,next));
        var engine=ProgressEngine.get(); engine.reconcile(player);
        engine.progress(player).addTaskProgress(gate.id().toString(),1); engine.complete(player,first.id(),false);
        helper.assertTrue(!engine.claim(player,missing.id()).success(),"invalid criterion cannot be claimed");
        helper.assertTrue(!engine.rewardClaimed(player,missing),"failed preflight retains the reward");
        helper.assertTrue(engine.claim(player,valid.id()).success(),"valid advancement reward is claimable");
        helper.assertTrue(engine.progress(player).taskProgress(follow.id().toString())==0,"native award must not recursively enter the progress engine");
        engine.pollTasks(player);
        helper.assertTrue(engine.progress(player).taskProgress(follow.id().toString())==1,"next poll completes newly eligible advancement task");
        engine.claim(player,valid.id());
        helper.assertTrue(player.totalExperience==10,"duplicate claim cannot replay vanilla effects");
        helper.succeed();
    }

    @GameTest(template="empty", batch="advancementSequence")
    @PrefixGameTestTemplate(false)
    public static void preexistingAdvancementHonorsSequentialEligibility(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); grant(player,"first");
        var gate=new TaskDefinition(id("book"),id("sequence_gate"),ResourceLocation.parse("brnquest:checkmark"),Map.of(),false);
        var follow=task("sequence_follow",Map.of("advancement",id("first").toString()));
        var quest=quest("sequence",List.of(gate,follow),List.of(),List.of(),true);
        install(List.of(quest));
        var engine=ProgressEngine.get();engine.reconcile(player);engine.pollTasks(player);
        helper.assertTrue(engine.progress(player).taskProgress(follow.id().toString())==0,"future task cannot record preexisting vanilla progress");
        engine.progress(player).addTaskProgress(gate.id().toString(),1);engine.pollTasks(player);
        helper.assertTrue(engine.progress(player).taskProgress(follow.id().toString())==1,"eligible task counts preexisting progress");
        helper.succeed();
    }

    @GameTest(template="empty", batch="advancementPicker")
    @PrefixGameTestTemplate(false)
    public static void dependentChoicesAndNestedPreviewUseServerData(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();
        // GameTestServer uses a zero default operator level, so give this fixture an explicit author level.
        player.server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(),2,false));
        try {
            var first=Map.of("advancement",id("first").toString());
            var criteria=ServerFieldSources.query(player,AdvancementFieldSource.CRITERIA,"","",0,first);
            helper.assertTrue(criteria.entries().stream().map(ServerFieldSources.Entry::value).toList().equals(List.of("a","b")),"criteria come from selected native advancement: "+criteria);
            var second=ServerFieldSources.query(player,AdvancementFieldSource.CRITERIA,"","",0,Map.of("advancement",id("second").toString()));
            helper.assertTrue(second.entries().getFirst().value().equals("only"),"changing the advancement changes its choices");
            helper.assertTrue(ServerFieldSources.query(player,AdvancementFieldSource.CRITERIA,"","",0,Map.of("advancement","#brnquest_f3:pair")).error().equals("single_advancement"),"groups cannot expose ambiguous criteria");
            var group=ServerFieldSources.query(player,AdvancementConfig.ID,"","#brnquest_f3:nested");
            helper.assertTrue(group.previewSource().equals(AdvancementFieldSource.MEMBERS.toString()),"valid group advertises its registered preview");
            var members=ServerFieldSources.query(player,AdvancementFieldSource.MEMBERS,"","#brnquest_f3:nested");
            helper.assertTrue(members.entries().stream().map(ServerFieldSources.Entry::value).toList().equals(List.of(id("first").toString(),id("second").toString())),"nested preview expands actual group members");
            helper.assertTrue(!ServerFieldSources.query(player,AdvancementFieldSource.MEMBERS,"","#brnquest_f3:missing").error().isEmpty(),"missing references report an error");
            helper.assertTrue(!player.getAdvancements().getOrStartProgress(holder(player,"first")).isDone(),"read-only queries never grant advancements");
        } finally { player.server.getPlayerList().deop(player.getGameProfile()); }
        // Ordinary-player tooltip data follows the same resolved groups without author permissions.
        var snapshot=new AdvancementGroupSnapshot();
        AdvancementGroupNetwork.snapshot(player.server).forEach(snapshot::receive);
        helper.assertTrue(snapshot.members("#brnquest_f3:nested").equals(List.of(id("first").toString(),id("second").toString())),"public snapshot includes every nested member");
        helper.assertTrue(snapshot.members("#brnquest_f3:missing").isEmpty(),"invalid group never publishes a partial member list");
        helper.succeed();
    }

    private static net.minecraft.advancements.AdvancementHolder holder(ServerPlayer player,String name) {
        return Objects.requireNonNull(player.server.getAdvancements().get(id(name)),"Missing isolated advancement fixture: "+name);
    }
    private static void grant(ServerPlayer player,String name) {
        var holder=holder(player,name); holder.value().criteria().keySet().forEach(criterion->player.getAdvancements().award(holder,criterion));
    }
    private static TaskDefinition task(String name,Map<String,String> values) { return new TaskDefinition(id("book"),id(name),AdvancementConfig.ID,values,false); }
    private static RewardDefinition reward(String name,Map<String,String> values) { return new RewardDefinition(id("book"),id(name),AdvancementConfig.ID,values,"manual",false); }
    private static RewardContext context(ServerPlayer player,Map<String,String> values) { return new RewardContext(player,id("book"),id("quest"),ApiViews.reward(reward("reward",values))); }
    private static QuestDefinition quest(String name,List<TaskDefinition> tasks,List<RewardDefinition> rewards,List<ResourceLocation> dependencies,boolean sequential) {
        var behavior=new QuestBehavior(false,false,false,0,false,false,false,DependencyRequirement.ALL_COMPLETED,0,sequential,false,0,false);
        return new QuestDefinition(id("book"),id(name),id("chapter"),name,"","","",0,0,dependencies,tasks,rewards,"",QuestAppearance.DEFAULT,behavior,Map.of());
    }
    private static void install(List<QuestDefinition> quests) {
        var book=new QuestBookDefinition(id("book"),1,"Advancement tests",List.of(new ChapterGroupDefinition(id("book"),id("group"),"Group",0)),
                List.of(new ChapterDefinition(id("book"),id("chapter"),id("group"),"Chapter","",0,quests)),Map.of());
        if(!QuestBookManager.get().install(book,new DiagnosticReport())) throw new IllegalStateException("Advancement fixture rejected");
    }
    private static ResourceLocation id(String path) { return ResourceLocation.parse("brnquest_f3:"+path); }
}
