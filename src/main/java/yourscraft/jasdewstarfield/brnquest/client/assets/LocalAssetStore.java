package yourscraft.jasdewstarfield.brnquest.client.assets;

import net.minecraft.resources.ResourceLocation;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Local files only: native books and authoring packets continue to contain resource IDs, never image bytes. */
public final class LocalAssetStore {
    public static final String NAMESPACE = "brnquest_local";
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_DIMENSION = 4096;
    private static final byte[] PNG = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
    private final Path root;
    public record Imported(ResourceLocation id, Path file, boolean reused) {}
    public enum Problem { INVALID_PNG, TOO_LARGE, DIMENSIONS }
    public static final class InvalidAsset extends IOException {
        private final Problem problem;
        public InvalidAsset(Problem problem) { super(problem.name()); this.problem = problem; }
        public Problem problem() { return problem; }
    }
    public LocalAssetStore(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public Path root() { return root; }
    public Path textures() { return root.resolve("assets").resolve(NAMESPACE).resolve("textures/imported"); }
    /** Create the namespace before ResourceManager builds its namespace index, even for an empty library. */
    public void initialize() throws IOException { Files.createDirectories(textures()); }

    public Imported importPng(Path source) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("Not a regular image file");
        byte[] input;
        try (var stream = Files.newInputStream(source)) {
            // Bound the actual read too: a file may grow after the size check.
            if (Files.size(source) > MAX_BYTES) throw new InvalidAsset(Problem.TOO_LARGE);
            input = stream.readNBytes(MAX_BYTES + 1);
        }
        if (input.length > MAX_BYTES) throw new InvalidAsset(Problem.TOO_LARGE);
        byte[] bytes = normalize(input);
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        var id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "textures/imported/" + hash + ".png");
        initialize();
        Path destination = textures().resolve(hash + ".png");
        if (Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) && Files.size(destination) == bytes.length
                && Arrays.equals(Files.readAllBytes(destination), bytes)) return new Imported(id, destination, true);
        // Publish only complete, validated images. A failed import never leaves a partial .png visible to the pack.
        Path temporary = Files.createTempFile(textures(), ".import-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
        return new Imported(id, destination, false);
    }

    /** Decode before publishing, stripping metadata and normalizing to the RGBA PNG format used by the game. */
    private static byte[] normalize(byte[] bytes) throws IOException {
        if (bytes.length < PNG.length || !Arrays.equals(Arrays.copyOf(bytes, PNG.length), PNG))
            throw new InvalidAsset(Problem.INVALID_PNG);
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReadersByFormatName("png");
            if (!readers.hasNext()) throw new IOException("PNG decoder unavailable");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION)
                    throw new InvalidAsset(Problem.DIMENSIONS);
                var decoded = reader.read(0);
                var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                try {
                    // Copy one row at a time to avoid a second full-image temporary pixel array.
                    for (int y = 0; y < height; y++) image.setRGB(0, y, width, 1, decoded.getRGB(0, y, width, 1, null, 0, width), 0, width);
                    var output = new ByteArrayOutputStream();
                    if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder unavailable");
                    if (output.size() > MAX_BYTES) throw new InvalidAsset(Problem.TOO_LARGE);
                    return output.toByteArray();
                } finally { image.flush(); decoded.flush(); }
            } catch (InvalidAsset invalid) { throw invalid; }
            catch (IOException | RuntimeException malformed) { throw new InvalidAsset(Problem.INVALID_PNG); }
            finally { reader.dispose(); }
        }
    }
}
