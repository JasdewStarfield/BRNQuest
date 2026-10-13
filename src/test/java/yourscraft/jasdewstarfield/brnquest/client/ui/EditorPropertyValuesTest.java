package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.QuestCreationDefaults;
import yourscraft.jasdewstarfield.brnquest.editor.QuestPropertySchema;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Empty UI inputs must preserve the distinction between concrete task values and sparse defaults. */
class EditorPropertyValuesTest {
    private static final QuestPropertySchema.Property SIZE = QuestPropertySchema.CATALOG.property("size");
    private static final QuestPropertySchema.Property COUNT = QuestPropertySchema.CATALOG.property("visible_after_tasks");

    @Test void savedInstanceNumbersRemainConcreteEvenWhenEqualToDefaults() {
        var form = new EditorPropertyValues(EditorPropertyValues.Scope.INSTANCE, List.of(SIZE, COUNT),
                Map.of("size", "1.0", "visible_after_tasks", "0"), Map.of());
        assertEquals("1.0", form.value("size"));
        assertEquals("0", form.value("visible_after_tasks"));
        assertEquals(EditorPropertyValues.Source.SAVED, form.source(SIZE));
        assertEquals("1.0", form.fallback(SIZE));
        assertEquals(Map.of("size", "1.0", "visible_after_tasks", "0"), form.confirmed());
        form.edit(SIZE, "2");
        assertEquals("2.0", form.confirmed().get("size"));
        form.edit(SIZE, "");
        assertEquals(EditorPropertyValues.Source.SYSTEM, form.source(SIZE));
        assertEquals("screen.brnquest.defaults.hint.system", form.hintKey(SIZE));
        assertEquals("1.0", form.confirmed().get("size"));
        var reopened = new EditorPropertyValues(EditorPropertyValues.Scope.INSTANCE, List.of(SIZE, COUNT), form.confirmed(), Map.of("size", "8.0"));
        assertEquals("1.0", reopened.value("size"));
        assertEquals(EditorPropertyValues.Source.SAVED, reopened.source(SIZE));
    }

    @Test void chapterWithAnEmptyBookStillHasAnInheritanceScope() {
        var form = new EditorPropertyValues(EditorPropertyValues.Scope.CHAPTER, List.of(SIZE), Map.of(), Map.of());
        assertEquals("1.0", form.fallback(SIZE));
        assertEquals(EditorPropertyValues.Source.BOOK_SYSTEM, form.source(SIZE));
        assertEquals("screen.brnquest.defaults.hint.book_system", form.hintKey(SIZE));
        assertEquals(Map.of(), form.confirmed());
        assertEquals("screen.brnquest.defaults.inherit", EditorPropertyValues.Scope.CHAPTER.defaultLabelKey());
        assertEquals("screen.brnquest.defaults.system", EditorPropertyValues.Scope.BOOK.defaultLabelKey());
    }

    @Test void clearingChapterOverrideRestoresTheBookDefaultWithoutMaterializingIt() {
        var form = new EditorPropertyValues(EditorPropertyValues.Scope.CHAPTER, List.of(SIZE, COUNT),
                Map.of("size", "3.0", "visible_after_tasks", "0"), Map.of("size", "2.0", "visible_after_tasks", "5"));
        form.edit(SIZE, " ");
        assertNull(form.value("size"));
        assertEquals("2.0", form.fallback(SIZE));
        assertEquals(EditorPropertyValues.Source.BOOK, form.source(SIZE));
        assertEquals("screen.brnquest.defaults.hint.book", form.hintKey(SIZE));
        assertEquals(Map.of("visible_after_tasks", "0"), form.confirmed());
        var effective = new QuestCreationDefaults(Map.of("size", "2.0", "visible_after_tasks", "5"))
                .overlay(new QuestCreationDefaults(form.confirmed()));
        assertEquals("2.0", effective.values().get("size"));
        assertEquals("0", effective.values().get("visible_after_tasks"));
    }

    @Test void equalTemplateOverridesSurviveAndRemainIndependentOfFutureBookChanges() {
        var original = Map.of("size", "1.0");
        var form = new EditorPropertyValues(EditorPropertyValues.Scope.CHAPTER, List.of(SIZE), original, original);
        assertEquals("1.0", form.value("size"));
        assertEquals(EditorPropertyValues.Source.SAVED, form.source(SIZE));
        var reopened = new EditorPropertyValues(EditorPropertyValues.Scope.CHAPTER, List.of(SIZE), form.confirmed(), Map.of("size", "2.0"));
        assertEquals("1.0", reopened.value("size"));
        assertEquals("1.0", reopened.confirmed().get("size"));
        form.edit(SIZE, "4.0");
        assertEquals(Map.of("size", "1.0"), original);
    }

    @Test void bookHintsOnlyUseTheSystemDefaultAndClearingKeepsTheTemplateSparse() {
        var form = new EditorPropertyValues(EditorPropertyValues.Scope.BOOK, List.of(SIZE),
                Map.of("size", "2.0"), Map.of("size", "3.0"));
        assertEquals("1.0", form.fallback(SIZE));
        form.edit(SIZE, "");
        assertEquals(EditorPropertyValues.Source.SYSTEM, form.source(SIZE));
        assertEquals(Map.of(), form.confirmed());
    }

    @Test void invalidTypedValuesStillRejectConfirmationInEveryScope() {
        for (var scope : EditorPropertyValues.Scope.values()) {
            var form = new EditorPropertyValues(scope, List.of(SIZE, COUNT), Map.of(), Map.of());
            for (String invalid : List.of("0", "-1", "NaN", "abc")) {
                form.edit(SIZE, invalid);
                assertThrows(IllegalArgumentException.class, form::confirmed);
            }
            form.edit(SIZE, "");
            form.edit(COUNT, "-1");
            assertThrows(IllegalArgumentException.class, form::confirmed);
            form.edit(COUNT, "");
            assertDoesNotThrow(form::confirmed);
        }
    }
}
