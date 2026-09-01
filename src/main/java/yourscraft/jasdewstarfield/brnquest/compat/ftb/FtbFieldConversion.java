package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import java.util.Objects;

/** One machine-readable import decision so authors can audit mappings without reading logs. */
public record FtbFieldConversion(String file, String path, String sourceField, String targetField,
                                 Status status, String detail) {
    public FtbFieldConversion {
        file = Objects.requireNonNullElse(file, "");
        path = Objects.requireNonNullElse(path, "");
        sourceField = Objects.requireNonNullElse(sourceField, "");
        targetField = Objects.requireNonNullElse(targetField, "");
        detail = Objects.requireNonNullElse(detail, "");
    }

    public enum Status { MAPPED, DEFAULTED, PRESERVED_EXTENSION, UNSUPPORTED }
}
