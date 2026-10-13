package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvents;
import yourscraft.jasdewstarfield.brnquest.event.QuestCompletedEvent;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Real ledger/event regression; a separate template namespace allows this small progress group to run in isolation. */
@GameTestHolder("brnquest_progress")
@PrefixGameTestTemplate(false)
public final class EmptyQuestGameTests {
    private EmptyQuestGameTests() {}
    @GameTest(template="empty",batch="emptyQuest")
    public static void emptyQuestWaitsForObjectivesWithoutResetOrCompletionEvents(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var engine=ProgressEngine.get();
        var empty=quest("unfinished",List.of()); install(empty); engine.reconcile(player);
        var notices=new AtomicInteger();
        try(var listener=BrnQuestEvents.subscribe(QuestCompletedEvent.class,event->{
            if(event.questId().equals(empty.id())) notices.incrementAndGet();
        })) {
            for(boolean intent:new boolean[]{false,true}) {
                var result=engine.complete(player,empty.id(),intent);
                helper.assertTrue(!result.success() && result.code().equals("NO_OBJECTIVES"),"empty quest must reject completion");
            }
            engine.pollTasks(player); engine.submitInventoryTasks(player); engine.tick(player);
            var ledger=engine.progress(player);
            helper.assertValueEqual(ledger.status(empty.id().toString()),QuestStatus.AVAILABLE,"empty authoring placeholder stays available");
            helper.assertValueEqual(ledger.completedAt(empty.id().toString()),0L,"no completion timestamp is written");
            helper.assertValueEqual(notices.get(),0,"no completion event reaches notification listeners");
            // Publishing the configured definition under the same ID needs no manual progress reset.
            var task=new TaskDefinition(id("book"),id("check"),TaskTypes.CHECKMARK,Map.of(),false);
            var configured=quest("unfinished",List.of(task)); install(configured); engine.reconcile(player);
            helper.assertTrue(!engine.complete(player,configured.id(),false).success(),"new checkmark is initially incomplete");
            helper.assertTrue(engine.complete(player,configured.id(),true).success(),"configured objective completes normally");
            helper.assertValueEqual(ledger.status(configured.id().toString()),QuestStatus.COMPLETED,"normal completion is committed");
            helper.assertValueEqual(notices.get(),1,"only real objective completion emits a notice");
        }
        helper.succeed();
    }
    @GameTest(template="empty",batch="emptyQuest")
    public static void explicitForceCompletionStillSupportsObjectiveFreeStoryQuests(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var engine=ProgressEngine.get();
        var quest=quest("story",List.of()); install(quest); engine.reconcile(player);
        helper.assertTrue(engine.forceComplete(player,quest.id()).success(),"explicit authority can still complete a story quest");
        helper.assertValueEqual(engine.progress(player).status(quest.id().toString()),QuestStatus.COMPLETED,"forced completion remains durable");
        helper.assertValueEqual(engine.complete(player,quest.id(),false).code(),"ALREADY_COMPLETED","terminal story history remains unchanged");
        helper.succeed();
    }
    @GameTest(template="empty",batch="emptyQuest")
    public static void optionalOnlyObjectivesKeepTheirExistingCompletionRule(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var engine=ProgressEngine.get();
        var optional=new TaskDefinition(id("book"),id("optional"),TaskTypes.CHECKMARK,Map.of(),true);
        var quest=quest("optional_only",List.of(optional)); install(quest); engine.reconcile(player);
        helper.assertTrue(engine.complete(player,quest.id(),false).success(),"configured optional objectives do not become required");
        helper.assertValueEqual(engine.progress(player).taskProgress(optional.id().toString()),0L,"optional objective is not force-submitted");
        helper.succeed();
    }
    private static QuestDefinition quest(String path,List<TaskDefinition> tasks) {
        return new QuestDefinition(id("book"),id(path),id("chapter"),path,"","","",0,0,List.of(),tasks,List.of(),"");
    }
    private static void install(QuestDefinition quest) {
        var book=new QuestBookDefinition(id("book"),1,"Empty quest regression",
                List.of(new ChapterGroupDefinition(id("book"),id("group"),"Group",0)),
                List.of(new ChapterDefinition(id("book"),id("chapter"),id("group"),"Chapter","",0,List.of(quest))),Map.of());
        if(!QuestBookManager.get().install(book,new DiagnosticReport())) throw new IllegalStateException("Invalid test book");
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("brnquest",path); }
}
