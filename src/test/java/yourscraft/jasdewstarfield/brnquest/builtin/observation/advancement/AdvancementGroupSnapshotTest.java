package yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement;

import yourscraft.jasdewstarfield.brnquest.builtin.network.AdvancementGroupNetwork;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class AdvancementGroupSnapshotTest {
    @Test void everyMemberArrivesBeforePublicationAndReloadRemovesStaleGroups() {
        var snapshot=new AdvancementGroupSnapshot();
        snapshot.receive(new AdvancementGroupNetwork.Page("one","",0,0,List.of(),"",true));
        var members=IntStream.range(0,150).mapToObj(i -> "test:member_"+i).toList();
        var pages=AdvancementGroupNetwork.pages("one","#test:group",members);
        assertEquals(3,pages.size());
        snapshot.receive(pages.getFirst()); assertNull(snapshot.members("#test:group"));
        snapshot.receive(pages.get(1)); snapshot.receive(pages.get(2));
        assertEquals(members,snapshot.members("#test:group"));
        snapshot.receive(new AdvancementGroupNetwork.Page("two","",0,0,List.of(),"",true));
        snapshot.receive(pages.getFirst()); assertNull(snapshot.members("#test:group"));
        snapshot.receive(new AdvancementGroupNetwork.Page("two","#test:broken",0,0,List.of(),"invalid",false));
        assertEquals(List.of(),snapshot.members("#test:broken"));
    }
    @Test void payloadPreservesAllPageFields() {
        var json=new com.google.gson.Gson();
        var page=AdvancementGroupNetwork.pages("one","#test:group",List.of("test:a","test:b")).getFirst();
        var buffer=io.netty.buffer.Unpooled.buffer();
        try {
            var payload=new AdvancementGroupNetwork.Payload(json.toJson(page));
            AdvancementGroupNetwork.Payload.CODEC.encode(buffer,payload);
            assertEquals(page,json.fromJson(AdvancementGroupNetwork.Payload.CODEC.decode(buffer).json(),AdvancementGroupNetwork.Page.class));
        } finally { buffer.release(); }
    }
}
