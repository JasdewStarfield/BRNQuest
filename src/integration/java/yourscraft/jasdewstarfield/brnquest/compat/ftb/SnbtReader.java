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
            // Normalize FTB's omitted commas before decoding quoted newline escapes, so generated
            // text line breaks never participate in structural comma insertion.
            return TagParser.parseTag(decodeQuotedNewlines(addStructuralCommas(source)));
        }
    }

    /** Accepts FTB newline escapes while preserving escaped backslashes, quotes and invalid escapes. */
    private static String decodeQuotedNewlines(String source) {
        StringBuilder result = new StringBuilder(source.length());
        char quote = 0;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (quote != 0 && current == '\\' && i + 1 < source.length()) {
                char escaped = source.charAt(++i);
                // Consume each escape once: a literal \\n must not become a line break.
                if (escaped == 'n') result.append('\n');
                else result.append(current).append(escaped);
            } else {
                result.append(current);
                if (quote == 0 && (current == '"' || current == '\'')) quote = current;
                else if (current == quote) quote = 0;
            }
        }
        return result.toString();
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
        // A typed-array header opens its elements, including nested forms such as [[I;.
        // Inserting a separator here would turn valid FTB output into the invalid [I;,.
        if (current.endsWith("[I;") || current.endsWith("[B;") || current.endsWith("[L;")) return false;
        return !next.startsWith("}") && !next.startsWith("]");
    }
}
