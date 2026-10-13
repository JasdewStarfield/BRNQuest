package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.*;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.gametest.*;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics.*;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.progress.*;
import yourscraft.jasdewstarfield.brnquest.task.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static yourscraft.jasdewstarfield.brnquest.gametest.StatisticsLifecycleGameTests.*;

/** Quantitative sampling evidence, independent of client FPS and the external modpack. */
@GameTestHolder("brnquest_statistics")
@SuppressWarnings("removal")
public final class StatisticsBudgetGameTests {
    @GameTest(template="empty",batch="statisticBudget",timeoutTicks=100) @PrefixGameTestTemplate(false)
    public static void onePassCachesReadsAndIdleHundredsOfGoalsStayClean(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();
        set(player,Stats.JUMP,7);
        var goal=goal("cache","jump","total",1000);
        var type=new StatisticsTask();
        var decoded=new StatisticsTask.Config(StatisticsConfig.parse(goal.config()),goal.config());
        var values=new TaskSampleValues();var reads=new AtomicInteger();
        String key="brnquest:stat/"+Stats.CUSTOM.get(Stats.JUMP).getName();
        values.get(key,()->{reads.incrementAndGet();return player.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));});
        set(player,Stats.JUMP,8);
        for(int i=0;i<100;i++) {
            var context=new TaskContext(player,goal.bookId(),id("cache"),ApiViews.task(goal),0);
            helper.assertTrue(type.sample(new TaskSamplingContext(context,TaskSamplingState.EMPTY,false,values),decoded).progress()==7,"a shared pass keeps one immutable native reading");
        }
        helper.assertTrue(reads.get()==1,"read supplier runs once");
        helper.runAfterDelay(due(player),()->{
            var engine=ProgressEngine.get();
            for(int size:List.of(1,128,512)) {
                var goals=new ArrayList<yourscraft.jasdewstarfield.brnquest.data.TaskDefinition>();
                for(int i=0;i<size;i++)goals.add(goal("budget_"+size+"_"+i,"jump","total",1000));
                var quest=quest("budget_"+size,goals,List.of(),behavior(false,false,false));
                install(List.of(quest));engine.reconcile(player);
                long coldStart=System.nanoTime();engine.pollTasks(player);long cold=System.nanoTime()-coldStart;
                var data=QuestProgressData.get(player.server);data.setDirty(false);
                // Warm the engine path before measuring steady-state costs; the cold pass remains separate.
                for(int warm=0;warm<128;warm++)engine.pollTasks(player);
                long[] elapsed=new long[20];
                for(int i=0;i<elapsed.length;i++){long start=System.nanoTime();engine.pollTasks(player);elapsed[i]=System.nanoTime()-start;}
                Arrays.sort(elapsed);
                BRNQuest.LOGGER.info("[BRNQuest/STAT-BUDGET] goals={} cold_ms={} median_ms={} p95_ms={}",size,cold/1_000_000.0,elapsed[10]/1_000_000.0,elapsed[18]/1_000_000.0);
                helper.assertTrue(!data.isDirty(),"idle samples write no persistent state and trigger no progress synchronization");
                helper.assertTrue(engine.taskProgress(player,quest,goals.getLast())==8,"every goal sees the same current native value");
            }
            helper.succeed();
        });
    }
}
