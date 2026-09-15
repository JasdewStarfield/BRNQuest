package yourscraft.jasdewstarfield.brnquest.compat.ftb.text;

/** Fully resolved FTB run style; reset and JSON false values therefore need no renderer state. */
public record FtbTextStyle(Integer color, boolean rainbow, boolean bold, boolean italic,
                           boolean underlined, boolean strikethrough, boolean obfuscated) {
    public static final FtbTextStyle EMPTY = new FtbTextStyle(null, false, false, false, false, false, false);

    public FtbTextStyle {
        if (color != null) color &= 0xFFFFFF;
        if (rainbow) color = null;
    }

    public FtbTextStyle withColor(Integer value, boolean dynamicRainbow) {
        return new FtbTextStyle(value, dynamicRainbow, bold, italic, underlined, strikethrough, obfuscated);
    }

    public FtbTextStyle withBold(boolean value) {
        return new FtbTextStyle(color, rainbow, value, italic, underlined, strikethrough, obfuscated);
    }

    public FtbTextStyle withItalic(boolean value) {
        return new FtbTextStyle(color, rainbow, bold, value, underlined, strikethrough, obfuscated);
    }

    public FtbTextStyle withUnderlined(boolean value) {
        return new FtbTextStyle(color, rainbow, bold, italic, value, strikethrough, obfuscated);
    }

    public FtbTextStyle withStrikethrough(boolean value) {
        return new FtbTextStyle(color, rainbow, bold, italic, underlined, value, obfuscated);
    }

    public FtbTextStyle withObfuscated(boolean value) {
        return new FtbTextStyle(color, rainbow, bold, italic, underlined, strikethrough, value);
    }
}
