package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.progress.AdminProgressAction;
import yourscraft.jasdewstarfield.brnquest.progress.AdminProgressService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminProgressNetworkTest {
    @Test void requestAndPreviewReplyRoundTripWithoutLosingActionOrConfirmation() {
        Gson gson = new Gson();
        var intent = new AdminProgressService.Intent("uuid", "test:book", "revision", "test:quest",
                "test:task", AdminProgressAction.RESET_TASK);
        var request = new AdminProgressNetwork.Request("request", "PREVIEW", intent, "", "玩家");
        var buffer = Unpooled.buffer();
        try {
            AdminProgressNetwork.RequestPayload.CODEC.encode(buffer, new AdminProgressNetwork.RequestPayload(gson.toJson(request)));
            var decoded = gson.fromJson(AdminProgressNetwork.RequestPayload.CODEC.decode(buffer).json(), AdminProgressNetwork.Request.class);
            assertEquals(request, decoded);
            var view = new AdminProgressService.View("玩家", "目标", "COMPLETED", "personal/uuid", 1,
                    1, 1, 1, 1, 0, false, false);
            var response = new AdminProgressNetwork.Response("request", new AdminProgressService.Reply(
                    OperationResult.noChange("OK", "Preview"), List.of(), view, "server-issued-ticket"));
            AdminProgressNetwork.ResponsePayload.CODEC.encode(buffer, new AdminProgressNetwork.ResponsePayload(gson.toJson(response)));
            assertEquals(response, gson.fromJson(AdminProgressNetwork.ResponsePayload.CODEC.decode(buffer).json(), AdminProgressNetwork.Response.class));
        } finally { buffer.release(); }
    }

    @Test void oversizedAdminIntentIsRejectedAtTheCodecBoundary() {
        var buffer = Unpooled.buffer();
        try {
            assertThrows(EncoderException.class, () -> AdminProgressNetwork.RequestPayload.CODEC.encode(buffer,
                    new AdminProgressNetwork.RequestPayload("x".repeat(16_385))));
        } finally { buffer.release(); }
    }
}
