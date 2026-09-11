package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.*;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise every display page and wire limits, including choices beyond the first 64 entries. */
class RewardTableChoiceProtocolTest {
    @Test void allOptionsRemainReachableAndExecutionConfigIsNotSent() {
        List<JsonObject> entries=new ArrayList<>();
        for(int i=0;i<255;i++) {
            var entry=new JsonObject();entry.addProperty("entry_id","e"+i);entry.addProperty("type","brnquest:command");
            var config=new JsonObject();config.addProperty("title","Option "+i);config.addProperty("command","secret execution text");
            config.addProperty("xp_levels","5");
            config.addProperty("item","x".repeat(3000));entry.add("config",config);entries.add(entry);
        }
        List<String> ids=new ArrayList<>();
        while(ids.size()<entries.size()) {
            String text=RewardTableChoiceNetwork.displayPage(entries,ids.size());
            assertTrue(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=32768);
            assertFalse(text.contains("secret execution"));
            assertTrue(text.contains("xp_levels"),"experience-level amounts remain visible");
            var page=JsonParser.parseString(text).getAsJsonArray();assertTrue(page.size()>0 && page.size()<=64);
            page.forEach(e->ids.add(e.getAsJsonObject().get("id").getAsString()));
        }
        assertEquals(255,new HashSet<>(ids).size());assertEquals("e254",ids.getLast());
        assertThrows(IllegalArgumentException.class,()->RewardTableChoiceNetwork.displayPage(entries,-1));
        assertThrows(IllegalArgumentException.class,()->RewardTableChoiceNetwork.displayPage(entries,255));
    }
    @Test void advancementDisplayKeepsSelectorButNotExecutionConfiguration() {
        var source = JsonParser.parseString("""
                {"entry_id":"progress","type":"brnquest:advancement","config":{
                  "advancement":"minecraft:story/mine_stone","criterion":"stone",
                  "command":"must not be sent","permission_level":"4"}}
                """).getAsJsonObject();
        var display = JsonParser.parseString(RewardTableChoiceNetwork.displayPage(List.of(source), 0))
                .getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("config");
        assertEquals(Set.of("advancement"), display.keySet());
        assertEquals("minecraft:story/mine_stone", display.get("advancement").getAsString());
        source.getAsJsonObject("config").addProperty("advancement", "x".repeat(257));
        assertFalse(JsonParser.parseString(RewardTableChoiceNetwork.displayPage(List.of(source), 0))
                .getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("config").has("advancement"));
    }

    @Test void boundedIdentityAndDisplayCodecsRoundTrip() {
        var request=new RewardTableChoiceNetwork.Request("revision","test:reward","attempt","root/choice/0",0,"e200",0);
        var buffer=Unpooled.buffer();
        try {
            RewardTableChoiceNetwork.Request.CODEC.encode(buffer,request);
            assertEquals(request,RewardTableChoiceNetwork.Request.CODEC.decode(buffer));
            var page=new RewardTableChoiceNetwork.Page("revision","test:reward","attempt",248,255,"[]","AWAITING_CHOICE");
            RewardTableChoiceNetwork.Page.CODEC.encode(buffer,page);
            assertEquals(page,RewardTableChoiceNetwork.Page.CODEC.decode(buffer));
            assertThrows(RuntimeException.class,()->RewardTableChoiceNetwork.Request.CODEC.encode(buffer,
                    new RewardTableChoiceNetwork.Request("x".repeat(257),"r","a","p",0,"e",0)));
        } finally { buffer.release(); }
    }
    @Test void nestedChoicePathsSurviveWireEncoding() {
        String path="root"+("/"+"a".repeat(64)+"/63").repeat(7)+"/choice/0";
        var page=new RewardTableChoiceNetwork.Page("rev","test:r","attempt",0,1,"[]","AWAITING_CHOICE",path,0);
        var buffer=Unpooled.buffer();
        try {
            RewardTableChoiceNetwork.Page.CODEC.encode(buffer,page);
            assertEquals(page,RewardTableChoiceNetwork.Page.CODEC.decode(buffer));
            var request=new RewardTableChoiceNetwork.Request("rev","test:r","attempt",path,0,"a",0);
            RewardTableChoiceNetwork.Request.CODEC.encode(buffer,request);
            assertEquals(request,RewardTableChoiceNetwork.Request.CODEC.decode(buffer));
        } finally {buffer.release();}
    }
}
