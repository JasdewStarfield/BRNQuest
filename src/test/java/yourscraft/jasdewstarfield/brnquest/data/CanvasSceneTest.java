package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.author.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Artwork must survive the existing schema, metadata edits and chapter copy without acquiring quest semantics. */
class CanvasSceneTest {
    private static final ResourceLocation BOOK = id("book"), CHAPTER = id("chapter"), GROUP = id("group");
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
    private static CanvasScene.Decoration decoration(String path) {
        return new CanvasScene.Decoration(id(path), "pack:textures/missing.png", -2.5, 4.25, 2, 1, true, -3, true);
    }
    private static CanvasScene scene() {
        return new CanvasScene(List.of(decoration("art")), new CanvasScene.Background("pack:textures/bg.png", CanvasScene.Fit.TILE, 0.4), null);
    }
    private static QuestBookDefinition book() {
        var quest = new QuestDefinition(BOOK, id("q"), CHAPTER, "Q", "", "", "", 0, 0, List.of(), List.of(), List.of(), "");
        return new QuestBookDefinition(BOOK, 1, "B", List.of(new ChapterGroupDefinition(BOOK, GROUP, "G", 0)),
                List.of(new ChapterDefinition(BOOK, CHAPTER, GROUP, "C", "", 0, List.of(quest), Map.of("addon:opaque", "原样"))), Map.of());
    }
    @Test void nativeRoundTripPreservesArtworkAndOpaqueDataWithoutQuestChanges() {
        var original = book();
        var result = CanvasEdits.replace(original, CHAPTER, scene());
        assertTrue(result.success());
        var restored = NativeBookJson.decode(JsonParser.parseString(NativeBookJson.encode(result.value().book())).getAsJsonObject());
        assertEquals(scene(), restored.chapters().getFirst().canvasScene());
        assertEquals("原样", restored.chapters().getFirst().extensions().get("addon:opaque"));
        assertEquals(original.quests(), restored.quests());
        assertEquals(CanvasScene.EMPTY, original.chapters().getFirst().canvasScene());
        var reset = CanvasEdits.replace(restored, CHAPTER, CanvasScene.EMPTY).value().book();
        assertEquals(original, reset);
    }
    @Test void wholeChapterCopyAllocatesIndependentArtworkIdsAndKeepsSettings() {
        var source = CanvasEdits.replace(book(), CHAPTER, scene()).value().book();
        var copy = ChapterCopyEdits.copy(source, CHAPTER);
        assertTrue(copy.success());
        var target = copy.value().book().chapters().stream().filter(c -> !c.id().equals(CHAPTER)).findFirst().orElseThrow().canvasScene();
        assertNotEquals(scene().decorations().getFirst().id(), target.decorations().getFirst().id());
        assertEquals(scene().decorations().getFirst(), target.decorations().getFirst().placed(id("art"), -2.5, 4.25, 2, 1));
        assertEquals(scene().canvas(), target.canvas());
        assertEquals(scene(), source.chapters().getFirst().canvasScene());
    }
    @Test void backgroundPropertiesPreserveDecorationsAndRejectObjectInjection() {
        var source = scene();
        var backgrounds = new CanvasScene(List.of(), null, source.canvas());
        var changed = source.withBackgrounds(backgrounds);
        assertEquals(source.decorations(), changed.decorations());
        assertEquals(backgrounds, changed.backgrounds());
        assertEquals(source, scene());
        assertThrows(IllegalArgumentException.class, () -> source.withBackgrounds(source));
    }
    @Test void backgroundOrderInheritsAndSurvivesCopyAndMetadataEdits() {
        var top = new CanvasScene(List.of(), null, null, true);
        assertTrue(CanvasScene.EMPTY.screenAbove(top));
        assertFalse(CanvasScene.EMPTY.screenAbove(CanvasScene.EMPTY));
        var override = new CanvasScene(List.of(), null, null, false);
        assertFalse(override.screenAbove(top));
        var decorated = scene().withBackgrounds(top);
        assertEquals(top, decorated.backgrounds());
        assertEquals(Boolean.TRUE, decorated.copied().screenAbove());
        assertEquals(decorated, CanvasScene.decode(decorated.encode()));
        assertNull(CanvasScene.decode("{\"decorations\":[]}").screenAbove());
        assertThrows(IllegalArgumentException.class, () -> CanvasScene.decode("{\"decorations\":[],\"screen_above\":\"true\"}"));
        assertEquals(scene().decorations(), decorated.decorations());
    }
    @Test void backgroundScaleRoundTripsAndLegacyDefaultsToOne() {
        var bg = new CanvasScene.Background("pack:textures/bg.png", CanvasScene.Fit.TILE, 0.5, 2);
        var value = new CanvasScene(List.of(), bg, null);
        assertEquals(value, CanvasScene.decode(value.encode()));
        assertEquals(1, CanvasScene.decode(value.encode().replace(",\"scale\":2.0", "")).canvas().scale());
        for (double invalid : new double[]{0, 0.24, 8.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new CanvasScene.Background(bg.texture(), bg.fit(), bg.opacity(), invalid));
    }
    @Test void backgroundInheritanceDistinguishesResetFromExplicitDisable() {
        var bookBackground = scene().canvas();
        var off = new CanvasScene.Background("", CanvasScene.Fit.CONTAIN, 0);
        assertEquals(bookBackground, CanvasScene.effective(null, bookBackground));
        assertEquals(off, CanvasScene.effective(off, bookBackground));
        var settings = new CanvasScene(List.of(), null, off);
        assertEquals(settings, CanvasScene.decode(settings.encode()));
        assertEquals(settings, CanvasEdits.replace(book(), BOOK, settings).value().book().canvasScene());
        assertThrows(IllegalArgumentException.class, () -> CanvasEdits.replace(book(), BOOK, scene()));
    }
    @Test void adversarialGeometryIdentityAndResourceInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CanvasScene(List.of(decoration("a"), decoration("a")), null, null));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 1_000_001})
            assertThrows(IllegalArgumentException.class, () -> decoration("a").placed(id("a"), invalid, 0, 1, 1));
        for (double invalid : new double[]{0, -1, Double.NaN, 1025})
            assertThrows(IllegalArgumentException.class, () -> decoration("a").placed(id("a"), 0, 0, invalid, 1));
        for (String invalid : List.of("https://example.com/image.png", "pack:../image.png", "pack:models/file.json"))
            assertThrows(IllegalArgumentException.class, () -> new CanvasScene.Background(invalid, CanvasScene.Fit.TILE, 1));
        assertThrows(IllegalArgumentException.class, () -> CanvasScene.decode(scene().encode().replace("\"locked\":true", "\"locked\":\"true\"")));
        assertThrows(IllegalArgumentException.class, () -> CanvasScene.decode(scene().encode().replace("\"layer\":-3", "\"layer\":1.5")));
    }
    @Test void limitsAndMissingTargetsCannotProducePartialBooks() {
        var many = new ArrayList<CanvasScene.Decoration>();
        for (int i = 0; i <= CanvasScene.MAX_DECORATIONS; i++) many.add(decoration("d"+i));
        assertThrows(IllegalArgumentException.class, () -> new CanvasScene(many, null, null));
        var source = book();
        assertFalse(CanvasEdits.replace(source, id("missing"), scene()).success());
        assertEquals(CanvasScene.EMPTY, source.chapters().getFirst().canvasScene());
    }
    @Test void decorationsCannotAliasQuestOrOtherChapterIdentities() {
        var source = book();
        var aliased = new CanvasScene(List.of(decoration("q")), null, null);
        assertThrows(IllegalArgumentException.class, () -> CanvasEdits.replace(source, CHAPTER, aliased));
        var decorated = CanvasEdits.replace(source, CHAPTER, scene()).value().book();
        var duplicate = new ChapterDefinition(BOOK, id("other"), GROUP, "Other", "", 1, List.of(), scene().write(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> DraftBookEditor.addChapter(decorated, duplicate));
    }
    @Test void semanticReviewReportsIndividualArtworkChanges() {
        var source = CanvasEdits.replace(book(), CHAPTER, scene()).value().book();
        var moved = scene().decorations().getFirst().placed(id("art"), 8, 4.25, 2, 1);
        var target = CanvasEdits.replace(source, CHAPTER, new CanvasScene(List.of(moved), scene().canvas(), null)).value().book();
        var entries = QuestBookDiffer.diff(source, target).entries();
        assertEquals(1, entries.size());
        assertEquals("canvas.decorations.test:art.x", entries.getFirst().path());
        assertEquals("-2.5", entries.getFirst().before()); assertEquals("8.0", entries.getFirst().after());
    }
}
