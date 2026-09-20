package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Performs only syntax parsing; FTB-specific interpretation belongs to the mapper. */
public final class SnbtReader {
    public CompoundTag read(Path path) throws IOException, CommandSyntaxException {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        try {
            return TagParser.parseTag(source);
        } catch (CommandSyntaxException strictFailure) {
            // FTB's writer omits commas between line-delimited fields. Normalize only structural
            // line boundaries, then still delegate all value parsing to Minecraft's TagParser.
            return TagParser.parseTag(addStructuralCommas(source));
        }
    }

    static String addStructuralCommas(String source) {
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder result = new StringBuilder(source.length() + lines.length);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String current = line.trim();
            String next = "";
            for (int j = i + 1; j < lines.length; j++) {
                next = lines[j].trim();
                if (!next.isEmpty()) break;
            }
            result.append(line);
            if (needsComma(current, next)) result.append(',');
            if (i + 1 < lines.length) result.append('\n');
        }
        return result.toString();
    }

    private static boolean needsComma(String current, String next) {
        if (current.isEmpty() || next.isEmpty() || current.endsWith(",") || current.endsWith("{") || current.endsWith("[")) return false;
        return !next.startsWith("}") && !next.startsWith("]");
    }
}
