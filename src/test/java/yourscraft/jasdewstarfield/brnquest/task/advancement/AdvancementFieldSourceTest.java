package yourscraft.jasdewstarfield.brnquest.task.advancement;

import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class AdvancementFieldSourceTest {
    @Test void previewAndCriteriaKeepLastPageAndFilterCounts() {
        var values=IntStream.range(0,150).mapToObj(i -> "test:entry_"+i).toList();
        var page=AdvancementFieldSource.page(values,"","",128,true);
        assertEquals(150,page.total()); assertEquals(150,page.selectedCount());
        assertEquals(22,page.entries().size()); assertEquals("test:entry_149",page.entries().getLast().value());
        var filtered=AdvancementFieldSource.page(values,"ENTRY_149","test:entry_149",0,false);
        assertEquals(1,filtered.total()); assertEquals(1,filtered.selectedCount());
        assertTrue(AdvancementFieldSource.page(values,"absent","",0,false).entries().isEmpty());
    }
}
