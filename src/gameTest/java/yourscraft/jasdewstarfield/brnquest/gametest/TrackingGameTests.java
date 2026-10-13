package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.progress.QuestProgressData;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import java.util.List;
import java.util.Map;

/** Real completion and reward transactions preserve personal tracking; this small namespace stays separately runnable. */
@GameTestHolder("brnquest_progress")
@PrefixGameTestTemplate(false)
public final class TrackingGameTests {
    @GameTest(template="empty",batch="tracking")
    public static void completionAndRewardClaimKeepTheTrackAndCanStillUntrack(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();var engine=ProgressEngine.get();
        var quest=install();engine.reconcile(player);
        helper.assertTrue(engine.toggleTracked(player,quest.id()).success(),"available quest can be tracked");
        helper.assertTrue(engine.complete(player,quest.id(),true).success(),"checkmark completes normally");
        helper.assertValueEqual(engine.trackedQuest(player),quest.id().toString(),"completion retains focus");
        helper.assertValueEqual(engine.visibleStatuses(player).get(quest.id().toString()),QuestStatus.COMPLETED,"tracking never replaces completion state");
        helper.assertTrue(engine.claim(player,quest.rewards().getFirst().id()).success(),"manual reward receipt commits");
        helper.assertValueEqual(engine.progress(player).status(quest.id().toString()),QuestStatus.REWARD_CLAIMED,"receipt remains terminal");
        helper.assertValueEqual(engine.trackedQuest(player),quest.id().toString(),"reward receipt retains focus");
        helper.assertTrue(engine.toggleTracked(player,quest.id()).success(),"terminal quest can be untracked");
        helper.assertValueEqual(engine.trackedQuest(player),"","explicit untrack clears focus");
        helper.assertTrue(engine.toggleTracked(player,quest.id()).success(),"terminal quest can be tracked again");
        helper.assertValueEqual(engine.progress(player).status(quest.id().toString()),QuestStatus.REWARD_CLAIMED,"tracking never resets receipt");
        helper.assertTrue(engine.toggleTracked(player,id("next")).success(),"tracking another quest replaces the personal focus");
        helper.assertValueEqual(engine.trackedQuest(player),id("next").toString(),"only the new quest is tracked");
        helper.assertValueEqual(engine.progress(player).status(quest.id().toString()),QuestStatus.REWARD_CLAIMED,"switching focus preserves the previous reward receipt");
        helper.succeed();
    }
    @GameTest(template="empty",batch="tracking")
    public static void olderActiveFocusMigratesBeforeCompletionAndRemainsSaved(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();var engine=ProgressEngine.get();
        var quest=install();engine.reconcile(player);
        engine.progress(player).status(quest.id().toString(),QuestStatus.ACTIVE);
        helper.assertValueEqual(QuestProgressData.get(player.getServer()).tracked(player.getUUID()),"","legacy fixture has no separate track");
        helper.assertTrue(engine.complete(player,quest.id(),true).success(),"legacy active quest completes");
        helper.assertValueEqual(QuestProgressData.get(player.getServer()).tracked(player.getUUID()),quest.id().toString(),"migrated track is persisted separately");
        helper.succeed();
    }
    private static QuestDefinition install() {
        var task=new TaskDefinition(id("book"),id("check"),TaskTypes.CHECKMARK,Map.of(),false);
        var reward=new RewardDefinition(id("book"),id("reward"),RewardTypes.XP,Map.of("xp","1"),"manual",false);
        var quest=new QuestDefinition(id("book"),id("quest"),id("chapter"),"Tracked quest","","","",0,0,List.of(),List.of(task),List.of(reward),"");
        var nextTask=new TaskDefinition(id("book"),id("next_check"),TaskTypes.CHECKMARK,Map.of(),false);
        var next=new QuestDefinition(id("book"),id("next"),id("chapter"),"Next quest","","","",1,0,List.of(),List.of(nextTask),List.of(),"");
        var book=new QuestBookDefinition(id("book"),1,"Tracking regression",
                List.of(new ChapterGroupDefinition(id("book"),id("group"),"Group",0)),
                List.of(new ChapterDefinition(id("book"),id("chapter"),id("group"),"Chapter","",0,List.of(quest,next))),Map.of());
        if(!QuestBookManager.get().install(book,new DiagnosticReport())) throw new IllegalStateException("Invalid test book");
        return quest;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("tracking_test",path); }
}
