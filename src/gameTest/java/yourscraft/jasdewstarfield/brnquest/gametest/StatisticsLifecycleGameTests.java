package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.gametest.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.progress.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.*;
import java.util.*;

/** Real server statistics exercise activation, saved baselines, configuration changes and repeat boundaries. */
@GameTestHolder("brnquest_statistics")
@SuppressWarnings("removal")
public final class StatisticsLifecycleGameTests {
    @GameTest(template="empty", batch="statisticDeltaLifecycle", timeoutTicks=80) @PrefixGameTestTemplate(false)
    public static void sequentialDeltaSaveEditAndResetKeepPreciseBoundaries(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var engine = ProgressEngine.get();
        var gate = new TaskDefinition(id("book"), id("gate"), TaskTypes.CHECKMARK, Map.of(), false);
        var goal = goal("delta", "jump", "since_available", 20);
        var quest = quest("sequence", List.of(gate, goal), List.of(), behavior(true,false,false));
        set(player, Stats.JUMP, 80); install(List.of(quest)); engine.reconcile(player);
        helper.assertTrue(engine.progress(player).sampleState(goal.id().toString(), player.getUUID()).values().isEmpty(), "future sequential goal has no baseline");
        set(player, Stats.JUMP, 85); engine.complete(player, quest.id(), true);
        helper.assertTrue(engine.progress(player).sampleState(goal.id().toString(), player.getUUID()).values().get("last")==85, "baseline captured synchronously at predecessor submission");
        helper.runAfterDelay(due(player), () -> {
            set(player, Stats.JUMP, 92); engine.pollTasks(player);
            helper.assertTrue(engine.taskProgress(player, quest, goal)==7, "actions immediately after activation survive the first poll");
            restore(player);
            set(player, Stats.JUMP, 95); engine.pollTasks(player);
            helper.assertTrue(engine.taskProgress(player, quest, goal)==10, "save/reload retains the read position and accumulated count");
            var edited = new TaskDefinition(goal.bookId(), goal.id(), goal.typeId(), Map.of("stat","minecraft:jump","mode","since_available","count","30","title","Changed","format","raw"), false);
            var updated = quest("sequence", List.of(gate, edited), List.of(), behavior(true,false,false));
            install(List.of(updated)); engine.reconcile(player);
            helper.assertTrue(engine.taskProgress(player, updated, edited)==10, "threshold/title/format edits retain progress");
            var replaced = goal("delta", "drop", "since_available", 30);
            var changed = quest("sequence", List.of(gate,replaced), List.of(), behavior(true,false,false));
            set(player, Stats.DROP, 30); install(List.of(changed)); engine.reconcile(player);
            helper.assertTrue(engine.taskProgress(player, changed, replaced)==0, "different statistic does not borrow the old count");
            set(player, Stats.DROP, 33); engine.pollTasks(player);
            set(player, Stats.DROP, 0); engine.pollTasks(player);
            set(player, Stats.DROP, 5); engine.pollTasks(player);
            helper.assertTrue(engine.taskProgress(player, changed, replaced)==8, "native reset preserves 3 and counts 5 new actions");
            engine.reset(player, changed.id()); set(player, Stats.DROP, 20); engine.complete(player, changed.id(), true);
            set(player, Stats.DROP, 23); engine.pollTasks(player);
            helper.assertTrue(engine.taskProgress(player, changed, replaced)==3, "explicit reset starts a new baseline at renewed eligibility");
            helper.succeed();
        });
    }
    @GameTest(template="empty", batch="statisticUnlock", timeoutTicks=80) @PrefixGameTestTemplate(false)
    public static void dependencyUnlockAndRepeatCaptureBaselineBeforeNextAction(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var engine = ProgressEngine.get();
        var gate = new TaskDefinition(id("book"),id("unlock_gate"),TaskTypes.CHECKMARK,Map.of(),false);
        var parent = quest("parent",List.of(gate),List.of(),behavior(false,false,false));
        var goal = goal("repeat_delta","jump","since_available",2);
        var child = quest("child",List.of(goal),List.of(parent.id()),behavior(false,true,false));
        set(player,Stats.JUMP,80); install(List.of(parent,child)); engine.reconcile(player);
        set(player,Stats.JUMP,90); engine.complete(player,parent.id(),true);
        helper.assertTrue(engine.progress(player).sampleState(goal.id().toString(),player.getUUID()).values().get("last")==90,"dependency completion establishes baseline before returning");
        helper.runAfterDelay(due(player),()->{
            set(player,Stats.JUMP,92); engine.pollTasks(player);
            helper.assertTrue(engine.progress(player).completionCycles(child.id().toString())==1,"first increment completes one cycle");
            helper.assertTrue(engine.taskProgress(player,child,goal)==0,"zero-cooldown repeat clears the old count");
            helper.assertTrue(engine.progress(player).sampleState(goal.id().toString(),player.getUUID()).values().get("last")==92,"new repeat cycle pins its own baseline");
            set(player,Stats.JUMP,93); engine.pollTasks(player);
            helper.assertTrue(engine.taskProgress(player,child,goal)==1,"new cycle counts only the next action");
            helper.succeed();
        });
    }
    @GameTest(template="empty", batch="statisticRelock", timeoutTicks=80) @PrefixGameTestTemplate(false)
    public static void relockedDependencyPausesDeltaAndTrackingDoesNotResetIt(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var engine = ProgressEngine.get();
        var gate = new TaskDefinition(id("book"), id("relock_gate"), TaskTypes.CHECKMARK, Map.of(), false);
        var parent = quest("relock_parent", List.of(gate), List.of(), behavior(false, false, false));
        var goal = goal("relock_delta", "jump", "since_available", 10);
        var child = quest("relock_child", List.of(goal), List.of(parent.id()), behavior(false, false, false));
        set(player, Stats.JUMP, 40); install(List.of(parent, child)); engine.reconcile(player);
        engine.complete(player, parent.id(), true);
        helper.runAfterDelay(due(player), () -> {
            set(player, Stats.JUMP, 42); engine.pollTasks(player);
            // A display selection must never become the activation boundary of a server counter.
            engine.toggleTracked(player, child.id()); engine.toggleTracked(player, child.id());
            set(player, Stats.JUMP, 43); engine.pollTasks(player);
            helper.assertTrue(engine.taskProgress(player, child, goal) == 3, "tracking leaves the baseline and contribution intact");
            engine.reset(player, parent.id());
            set(player, Stats.JUMP, 50); engine.pollTasks(player);
            helper.assertTrue(engine.taskProgress(player, child, goal) == 3, "locked actions do not increase progress");
            restore(player); engine.reconcile(player);
            engine.complete(player, parent.id(), true);
            helper.assertTrue(engine.progress(player).sampleState(goal.id().toString(), player.getUUID()).values().get("last") == 50,
                    "unlock resumes at the current statistic after a persisted pause");
            set(player, Stats.JUMP, 52); engine.pollTasks(player);
            helper.assertTrue(engine.taskProgress(player, child, goal) == 5, "renewed eligibility preserves old contribution and counts only new actions");
            helper.succeed();
        });
    }
    @GameTest(template="empty", batch="statisticImportRuntime", timeoutTicks=80) @PrefixGameTestTemplate(false)
    public static void importedSnbtGoalRunsAsCustomHistoryAndKeepsRawDisplay(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();
        var config=new HashMap<String,String>();
        try {
            var raw=net.minecraft.nbt.TagParser.parseTag("{stat:'minecraft:jump',value:12}");
            helper.assertTrue(yourscraft.jasdewstarfield.brnquest.compat.ftb.FtbStatisticFields.apply(StatisticsConfig.ID,raw,config).isEmpty(),"SNBT mapping succeeds");
        } catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new IllegalStateException(error);}
        var goal=new TaskDefinition(id("book"),id("imported"),StatisticsConfig.ID,config,false);
        var quest=quest("imported",List.of(goal),List.of(),behavior(false,false,false));
        set(player,Stats.JUMP,12); install(List.of(quest)); var engine=ProgressEngine.get();engine.reconcile(player);
        helper.runAfterDelay(due(player),()->{
            engine.pollTasks(player);
            helper.assertTrue(engine.progress(player).status(quest.id().toString())==QuestStatus.COMPLETED,"imported history completes through the normal transaction");
            helper.assertTrue(StatisticsTargets.format(StatisticsConfig.parse(config),1200).equals("1200"),"import retains raw integer display");
            var legacy=StatisticsConfig.parse(Map.of("stat","oldmod:jump","ftb.stat_namespace_fallback","true"));
            helper.assertTrue(StatisticsTargets.resolve(legacy).orElseThrow()==Stats.CUSTOM.get(Stats.JUMP),"legacy namespace fallback resolves the canonical existing statistic");
            helper.assertTrue(StatisticsTargets.resolve(StatisticsConfig.parse(Map.of("stat","oldmod:jump"))).isEmpty(),"new native goals keep strict identities");
            helper.succeed();
        });
    }
    /** Fixture constructors stay independent of deployed user books and never touch their source files. */
    static ResourceLocation id(String path){return ResourceLocation.parse("brnquest_statistics:"+path);}
    static TaskDefinition goal(String name,String target,String mode,int count){return new TaskDefinition(id("book"),id(name),StatisticsConfig.ID,Map.of("stat","minecraft:"+target,"mode",mode,"count",Integer.toString(count)),false);}
    static QuestBehavior behavior(boolean sequential,boolean repeat,boolean members){return new QuestBehavior(false,false,false,0,false,false,false,DependencyRequirement.ALL_COMPLETED,0,sequential,repeat,0,false,members);}
    static QuestDefinition quest(String name,List<TaskDefinition> tasks,List<ResourceLocation> deps,QuestBehavior behavior){return new QuestDefinition(id("book"),id(name),id("chapter"),"Statistics","","","",0,0,deps,tasks,List.of(),"",QuestAppearance.DEFAULT,behavior,Map.of());}
    static void install(List<QuestDefinition> quests){
        var book=new QuestBookDefinition(id("book"),1,"Statistics",List.of(new ChapterGroupDefinition(id("book"),id("group"),"Group",0)),List.of(new ChapterDefinition(id("book"),id("chapter"),id("group"),"Chapter","",0,quests)),Map.of());
        if(!QuestBookManager.get().install(book,new DiagnosticReport()))throw new IllegalStateException("Statistics fixture rejected");
    }
    static void set(ServerPlayer player,ResourceLocation stat,int value){player.getStats().setValue(player,Stats.CUSTOM.get(stat),value);}
    static int due(ServerPlayer player){int delay=(3-player.server.getTickCount()%3)%3;return delay==0?3:delay;}
    static void restore(ServerPlayer player){var data=QuestProgressData.get(player.server).save(new net.minecraft.nbt.CompoundTag(),player.registryAccess());player.server.overworld().getDataStorage().set("brnquest_progress",QuestProgressData.load(data,player.registryAccess()));}
}
