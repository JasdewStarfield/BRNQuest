package yourscraft.jasdewstarfield.brnquest.compat.ftb.text;

import java.util.Objects;

/** Semantic FTB source nodes that never expose an FTB or client class to the importer. */
public sealed interface FtbTextNode permits FtbTextNode.Text, FtbTextNode.Image,
        FtbTextNode.PageBreak, FtbTextNode.Unknown {
    FtbTextSource source();

    record Text(String value, FtbTextStyle style, Action action, FtbTextSource source) implements FtbTextNode {
        public Text {
            value = Objects.requireNonNullElse(value, "");
            style = Objects.requireNonNullElse(style, FtbTextStyle.EMPTY);
            action = Objects.requireNonNullElse(action, Action.NONE);
            source = Objects.requireNonNull(source, "source");
        }
    }

    record Image(String resourceId, String alt, int width, int height, String align,
                 FtbTextSource source) implements FtbTextNode {
        public Image {
            resourceId = Objects.requireNonNullElse(resourceId, "");
            alt = Objects.requireNonNullElse(alt, "");
            width = Math.max(1, width);
            height = Math.max(1, height);
            align = Objects.requireNonNullElse(align, "center");
            source = Objects.requireNonNull(source, "source");
        }
    }

    record PageBreak(FtbTextSource source) implements FtbTextNode {
        public PageBreak { source = Objects.requireNonNull(source, "source"); }
    }

    /** Unknown source remains verbatim so an author can repair it without reopening the FTB files. */
    record Unknown(String sourceText, FtbTextSource source) implements FtbTextNode {
        public Unknown {
            sourceText = Objects.requireNonNullElse(sourceText, "");
            source = Objects.requireNonNull(source, "source");
        }
    }

    record Action(Kind kind, String value) {
        public static final Action NONE = new Action(Kind.NONE, "");

        public Action {
            kind = Objects.requireNonNullElse(kind, Kind.NONE);
            value = Objects.requireNonNullElse(value, "");
        }

        public enum Kind { NONE, OPEN_URL, CHANGE_PAGE, UNSAFE }
    }
}
