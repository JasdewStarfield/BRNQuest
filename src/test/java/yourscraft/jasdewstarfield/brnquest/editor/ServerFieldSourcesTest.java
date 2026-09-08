package yourscraft.jasdewstarfield.brnquest.editor;

import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ServerFieldSourcesTest {
    @Test void existingSmallSourceInterfaceSupportsEveryPageOfItsCompleteCollection() {
        ServerFieldSources.Source source=(player,filter,selected) -> new ServerFieldSources.Result(
                IntStream.range(0,200).mapToObj(i -> new ServerFieldSources.Entry("test:"+i,1)).toList(),200,0,"","");
        assertEquals("test:64",source.queryPage(null,"","",64).entries().getFirst().value());
        assertEquals("test:199",source.queryPage(null,"","",192).entries().getLast().value());
        assertEquals(8,source.queryPage(null,"","",192).entries().size());
    }
}
