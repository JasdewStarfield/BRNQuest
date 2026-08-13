package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import java.util.Map;

/** Decodes the schema-1 string map through a registered task or reward codec. */
public final class StringMapConfigCodec {
    private StringMapConfigCodec() {}

    public static <T> DataResult<T> decode(Codec<T> codec, Map<String, String> values) {
        JsonObject json = new JsonObject();
        // Schema 1 deliberately stores every leaf as a string; codecs provide typed runtime views
        // without changing the on-disk representation during the stage-2 compatibility window.
        values.forEach(json::addProperty);
        return codec.parse(JsonOps.INSTANCE, json);
    }
}
