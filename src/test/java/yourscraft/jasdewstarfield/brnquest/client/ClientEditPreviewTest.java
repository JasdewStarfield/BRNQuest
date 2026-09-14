package yourscraft.jasdewstarfield.brnquest.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.author.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the display projector using the actual client wire shape, without server/world/texture access. */
class ClientEditPreviewTest {
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:"+path); }
    private static QuestBookDefinition book() {
        var task = new TaskDefinition(id("book"), id("task"), id("opaque"), Map.of("value","old"), false);
        var reward = new RewardDefinition(id("book"), id("reward"), id("opaque"), Map.of("value","old"), "manual", false);
        var q = new QuestDefinition(id("book"),id("q"),id("c"),"Quest","Sub","Body","",1,2,List.of(),List.of(task),List.of(reward),"");
        var art = new CanvasScene.Decoration(id("art"), "brnquest_local:textures/imported/local.png",3,4,2,1,true,2,false);
        return new QuestBookDefinition(id("book"),1,"Book",List.of(new ChapterGroupDefinition(id("book"),id("g"),"Group",0)),
                List.of(new ChapterDefinition(id("book"),id("c"),id("g"),"Chapter","",0,List.of(q),new CanvasScene(List.of(art),null,null).write(Map.of("custom","keep"))),
                        new ChapterDefinition(id("book"),id("dest"),id("g"),"Destination","",1,List.of())),Map.of());
    }
    private static AuthoringNetwork.EditorMutationWire wire(String action, String target, String parent, String source,
            String title, int index, Map<String,String> config, List<AuthoringNetwork.PositionWire> positions) {
        return new AuthoringNetwork.EditorMutationWire("session","test:book","revision",action,
                target.isBlank()?"":id(target).toString(),parent.isBlank()?"":id(parent).toString(),source.isBlank()?"":id(source).toString(),title,index,10,20,positions,config);
    }
    @Test void mixedMoveResizeDeleteAndPasteShowNewGeometryWithoutTouchingTheOriginal() {
        var original=book();
        var positions=List.of(new AuthoringNetwork.PositionWire(CanvasSelectionKey.quest(id("q")).toString(),5,6),
                new AuthoringNetwork.PositionWire(CanvasSelectionKey.decoration(id("art")).toString(),7,8));
        var moved=ClientEditPreview.mutation(original,wire("MOVE_CANVAS_SELECTION","c","","","",0,Map.of(),positions));
        assertEquals(5,moved.quests().getFirst().x()); assertEquals(7,moved.chapters().getFirst().canvasScene().decorations().getFirst().x());
        assertEquals(1,original.quests().getFirst().x()); assertEquals(3,original.chapters().getFirst().canvasScene().decorations().getFirst().x());
        var resized=new CanvasScene(List.of(new CanvasScene.Decoration(id("art"),"test:textures/new.png",7,8,4,2,true,5,false)),null,null);
        var shown=ClientEditPreview.mutation(moved,wire("UPDATE_CANVAS","c","","","",0,Map.of("scene",resized.encode()),List.of()));
        assertEquals(4,shown.chapters().getFirst().canvasScene().decorations().getFirst().width());
        var copied=QuestClipboardSnapshot.capture(shown,id("c"),Set.of(id("q")),Set.of(id("art")));
        var pasted=ClientEditPreview.mutation(shown,wire("PASTE_QUESTS","dest","","","",0,Map.of("snapshot",copied.encode()),List.of()));
        assertEquals(10,pasted.chapters().get(1).quests().getFirst().x()); assertEquals(12,pasted.chapters().get(1).canvasScene().decorations().getFirst().x());
        var deleted=ClientEditPreview.mutation(shown,wire("DELETE_CANVAS_SELECTION","c","","","",0,Map.of(),positions));
        assertTrue(deleted.quests().isEmpty()); assertTrue(deleted.chapters().getFirst().canvasScene().decorations().isEmpty());
        assertEquals("keep",deleted.chapters().getFirst().extensions().get("custom"));
    }
    @Test void metadataProjectionUsesWireDefaultsAndPreservesUnrelatedBackgroundAndLocaleData() {
        var original=book();
        var background=new CanvasScene(List.of(),new CanvasScene.Background("test:textures/map.png",CanvasScene.Fit.TILE,0.5),null,true);
        var c=Map.of("quest_defaults","{\"size\":2}","default_consume_items","false","text_field","title","text_locale.zh_cn","章节名",
                "backgrounds",background.encode(),"icon","test:textures/icon.png");
        var shown=ClientEditPreview.mutation(original,wire("UPDATE_CHAPTER","c","g","","",0,c,List.of()));
        var chapter=shown.chapters().getFirst();
        assertEquals("2.0",chapter.questDefaults().values().get("size")); assertEquals(false,chapter.consumeItems());
        assertEquals("test:textures/icon.png",chapter.icon()); assertEquals(1,chapter.canvasScene().decorations().size());
        assertEquals(background.canvas(),chapter.canvasScene().canvas()); assertEquals("keep",chapter.extensions().get("custom"));
        assertTrue(shown.localization().translations().get("zh_cn").containsValue("章节名"));
        var group=ClientEditPreview.mutation(shown,wire("UPDATE_GROUP","g","","","",0,Map.of("text_field","title","text_locale.zh_cn","组名","description","Description"),List.of()));
        assertEquals("Description",group.chapterGroups().getFirst().description());
        var updated=ClientEditPreview.mutation(group,wire("UPDATE_BOOK_PROPERTIES","book","","","",0,
                Map.of("quest_defaults","{\"size\":3}","backgrounds",background.encode(),"text_locale.zh_cn","书名"),List.of()));
        assertEquals("3.0",updated.questDefaults().values().get("size")); assertEquals(background,updated.canvasScene());
        assertEquals("Book",original.title()); assertEquals(CanvasScene.EMPTY,original.canvasScene());
    }
    @Test void typedAndQuestPropertyChangesAreVisibleBeforeTheirServerReply() {
        var original=book();
        var shown=ClientEditPreview.mutation(original,wire("UPDATE_TASK","new_task","q","task","",1,Map.of("value","new"),List.of()));
        var task=shown.quests().getFirst().tasks().getFirst(); assertEquals(id("new_task"),task.id()); assertTrue(task.optional()); assertEquals("new",task.config().get("value"));
        shown=ClientEditPreview.mutation(shown,wire("UPDATE_REWARD","new_reward","q","reward","auto_hidden",1,Map.of("value","new"),List.of()));
        var reward=shown.quests().getFirst().rewards().getFirst(); assertTrue(reward.teamReward()); assertEquals("auto_hidden",reward.claimPolicy());
        var q=shown.quests().getFirst();
        var replacement=new QuestDefinition(q.bookId(),q.id(),q.chapterId(),q.title(),q.subtitle(),q.description(),QuestIconValue.texture(id("textures/node.png")),
                9,10,q.dependencies(),q.tasks(),q.rewards(),q.legacyId(),new QuestAppearance("circle",2,0.5,4),q.behavior(),q.extensions());
        var changed=ClientEditPreview.questProperties(shown,q.id(),replacement);
        assertEquals(replacement,changed.quests().getFirst());
        var copy=ClientEditPreview.mutation(changed,wire("COPY_QUEST","copy","","q","Quest",0,Map.of(),List.of()));
        assertEquals(2,copy.quests().size()); assertNotEquals(q.tasks().getFirst().id(),copy.quests().get(1).tasks().getFirst().id());
        assertEquals("old",original.quests().getFirst().tasks().getFirst().config().get("value"));
    }
}
