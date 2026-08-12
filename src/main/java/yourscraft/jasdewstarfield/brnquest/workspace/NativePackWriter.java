package yourscraft.jasdewstarfield.brnquest.workspace;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Writes one deterministic native book as a complete Minecraft data pack. */
public final class NativePackWriter {
    private NativePackWriter() {}

    public static void write(Path pack, String namespace, String bookId, String json, String description) throws IOException {
        if (Files.exists(pack)) throw new java.nio.file.FileAlreadyExistsException(pack.toString());
        Path bookFile = pack.resolve("data").resolve(namespace).resolve("brnquest/books").resolve(bookId + ".json");
        Files.createDirectories(bookFile.getParent());
        Files.writeString(pack.resolve("pack.mcmeta"), "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"" + escape(description) + "\"\n  }\n}\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(bookFile, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);

        var root = JsonParser.parseString(json).getAsJsonObject();
        var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        for (var element : root.getAsJsonArray("chapter_groups")) {
            var value = element.getAsJsonObject();
            Path file = pack.resolve("data").resolve(namespace).resolve("brnquest/chapter_groups")
                    .resolve(resourcePath(value.get("id").getAsString()) + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(value) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        for (var element : root.getAsJsonArray("chapters")) {
            var value = element.getAsJsonObject();
            Path file = pack.resolve("data").resolve(namespace).resolve("brnquest/chapters")
                    .resolve(resourcePath(value.get("id").getAsString()) + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(value) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
    }

    private static String resourcePath(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
