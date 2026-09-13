package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.QuestCreationDefaults;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Grouping must not omit an editable override or expose the same field twice. */
class EditorCreationDefaultsScreenTest {
    @Test void groupedFormIncludesEveryTemplateFieldExactlyOnce() {
        var fields = EditorCreationDefaultsScreen.GROUPS.stream().flatMap(List::stream).toList();
        assertEquals(new HashSet<>(QuestCreationDefaults.FIELDS), new HashSet<>(fields));
        assertEquals(QuestCreationDefaults.FIELDS.size(), fields.size());
    }
}
