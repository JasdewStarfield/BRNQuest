package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ServerFieldPagesTest {
    @Test void pagesReachTheLastResultWithoutLoadingAllPrecedingPages() {
        var pages=new ServerFieldPages();
        assertTrue(pages.receive(pages.begin(0),page(0,10001)));
        assertEquals(10001,pages.total());
        assertNull(pages.entry(64));
        assertTrue(pages.needs(10000));
        assertTrue(pages.receive(pages.begin(9984),page(9984,10001)));
        assertEquals("test:10000",pages.entry(10000).value());
        assertFalse(pages.needs(10000));
        assertNull(pages.entry(5000),"skipped pages need not be allocated");
    }
    @Test void duplicateAndOldSearchResponsesCannotOverwriteTheNewResultSet() {
        var pages=new ServerFieldPages();
        String old=pages.begin(64);
        pages.reset();
        String fresh=pages.begin(0);
        assertFalse(pages.receive(old,page(64,200)));
        assertTrue(pages.receive(fresh,page(0,2)));
        assertFalse(pages.receive(fresh,page(0,200)));
        assertEquals(2,pages.total());
    }
    @Test void selectionRefreshRetainsVisiblePagesAndRejectsObsoleteReplies() {
        var pages=new ServerFieldPages();
        pages.receive(pages.begin(0),page(0,200));
        pages.receive(pages.begin(128),page(128,200));
        String old=pages.begin(64);
        pages.cancelPending();
        assertEquals(200,pages.total());
        assertEquals("test:150",pages.entry(150).value());
        assertFalse(pages.receive(old,page(64,200)));
        pages.receive(pages.begin(0),page(0,200));
        assertEquals("test:150",pages.entry(150).value(),"metadata refresh does not evict a scrolled page");
    }
    private static ServerFieldSources.Result page(int offset,int total) {
        return new ServerFieldSources.Result(IntStream.range(offset,Math.min(offset+64,total))
                .mapToObj(i -> new ServerFieldSources.Entry("test:"+i,1)).toList(),total,1,"","");
    }
}
