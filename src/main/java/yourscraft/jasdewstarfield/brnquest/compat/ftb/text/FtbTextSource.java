package yourscraft.jasdewstarfield.brnquest.compat.ftb.text;

import java.util.Objects;

/** Exact source location retained while FTB text is converted away from its runtime syntax. */
public record FtbTextSource(String file, String key, int line, int column, int length) {
    public FtbTextSource {
        file = Objects.requireNonNullElse(file, "");
        key = Objects.requireNonNullElse(key, "");
        line = Math.max(1, line);
        column = Math.max(1, column);
        length = Math.max(0, length);
    }

    public FtbTextSource slice(int columnOffset, int sliceLength) {
        return new FtbTextSource(file, key, line, column + Math.max(0, columnOffset), sliceLength);
    }
}
