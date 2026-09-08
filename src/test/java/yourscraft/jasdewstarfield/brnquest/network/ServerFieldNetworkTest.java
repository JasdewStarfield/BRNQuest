package yourscraft.jasdewstarfield.brnquest.network;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Raw selector requests cannot grow into unrestricted author payloads. */
class ServerFieldNetworkTest {
    @Test void boundedRequestRoundTripsAndRejectsOversizedInput() {
        var buffer=Unpooled.buffer();
        try {
            var request=new ServerFieldNetwork.Request("{\"selected\":\"#test:group\"}");
            ServerFieldNetwork.Request.CODEC.encode(buffer,request);
            assertEquals(request,ServerFieldNetwork.Request.CODEC.decode(buffer));
            buffer.clear();
            assertThrows(io.netty.handler.codec.EncoderException.class,()->ServerFieldNetwork.Request.CODEC.encode(buffer,new ServerFieldNetwork.Request("x".repeat(2049))));
        } finally { buffer.release(); }
    }
}
