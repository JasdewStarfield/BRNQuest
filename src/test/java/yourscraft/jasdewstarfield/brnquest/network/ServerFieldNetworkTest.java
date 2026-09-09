package yourscraft.jasdewstarfield.brnquest.network;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Raw selector requests cannot grow into unrestricted author payloads. */
class ServerFieldNetworkTest {
    @Test void dependentContextRoundTripsAndHasIndependentBounds() {
        var json=new com.google.gson.Gson();
        var query=new ServerFieldNetwork.Query("id","test:source","","a",64,java.util.Map.of("advancement","test:first"));
        assertEquals(query,json.fromJson(json.toJson(query),ServerFieldNetwork.Query.class));
        assertTrue(ServerFieldNetwork.validContext(query.context()));
        assertFalse(ServerFieldNetwork.validContext(java.util.Map.of("advancement","x".repeat(257))));
        var tooMany=new java.util.HashMap<String,String>();
        for(int i=0;i<65;i++) tooMany.put("field"+i,"");
        assertFalse(ServerFieldNetwork.validContext(tooMany));
    }
    @Test void boundedRequestRoundTripsAndRejectsOversizedInput() {
        var buffer=Unpooled.buffer();
        try {
            var request=new ServerFieldNetwork.Request("{\"selected\":\"#test:group\"}");
            ServerFieldNetwork.Request.CODEC.encode(buffer,request);
            assertEquals(request,ServerFieldNetwork.Request.CODEC.decode(buffer));
            buffer.clear();
            assertThrows(io.netty.handler.codec.EncoderException.class,()->ServerFieldNetwork.Request.CODEC.encode(buffer,new ServerFieldNetwork.Request("x".repeat(65537))));
        } finally { buffer.release(); }
    }
}
