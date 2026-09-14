package yourscraft.jasdewstarfield.brnquest.client.assets;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.author.CanvasEdits;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real file and ResourceManager checks: no GPU or file-dialog success is inferred from these tests. */
class LocalAssetStoreTest {
    @TempDir Path temporary;
    private Path png(String filename, int width, int height, int color) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        Path path = temporary.resolve(filename);
        assertTrue(ImageIO.write(image, "png", path.toFile()));
        image.flush();
        return path;
    }
    @Test void importsAreOwnedCopiesWithStableContentIdsAcrossNamesAndRestarts() throws Exception {
        var store = new LocalAssetStore(temporary.resolve("library"));
        Path original = png("中文 image.PNG", 3, 2, 0x7F123456);
        byte[] originalBytes = Files.readAllBytes(original);
        var first = store.importPng(original);
        assertFalse(first.reused());
        assertEquals(LocalAssetStore.NAMESPACE, first.id().getNamespace());
        assertTrue(first.id().getPath().matches("textures/imported/[a-f0-9]{64}\\.png"));
        assertArrayEquals(originalBytes, Files.readAllBytes(original));
        Path renamed = temporary.resolve("renamed.png"); Files.copy(original, renamed);
        var second = new LocalAssetStore(store.root()).importPng(renamed);
        assertTrue(second.reused()); assertEquals(first.id(), second.id());
        Files.delete(original); Files.delete(renamed);
        var decoded = ImageIO.read(first.file().toFile());
        assertEquals(3, decoded.getWidth()); assertEquals(2, decoded.getHeight());
        assertEquals(0x7F123456, decoded.getRGB(0, 0));
        assertEquals(0, decoded.getRGB(1, 1)); decoded.flush();
        var changed = store.importPng(png("different.png", 3, 2, 0xFFABCDEF));
        assertNotEquals(first.id(), changed.id());
        try (var files = Files.list(store.textures())) { assertEquals(2, files.count()); }
    }
    @Test void rejectedFilesNeverBecomePackResources() throws Exception {
        var store = new LocalAssetStore(temporary.resolve("library")); store.initialize();
        Path text = temporary.resolve("fake.png"); Files.writeString(text, "not a PNG", java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(LocalAssetStore.Problem.INVALID_PNG, assertThrows(LocalAssetStore.InvalidAsset.class, () -> store.importPng(text)).problem());
        Path broken = temporary.resolve("truncated.png"); Files.write(broken, Arrays.copyOf(Files.readAllBytes(png("valid.png", 2, 2, 1)), 28));
        assertThrows(LocalAssetStore.InvalidAsset.class, () -> store.importPng(broken));
        Path wide = png("wide.png", LocalAssetStore.MAX_DIMENSION + 1, 1, 1);
        assertEquals(LocalAssetStore.Problem.DIMENSIONS, assertThrows(LocalAssetStore.InvalidAsset.class, () -> store.importPng(wide)).problem());
        Path huge = temporary.resolve("huge.png"); Files.write(huge, new byte[LocalAssetStore.MAX_BYTES + 1]);
        assertEquals(LocalAssetStore.Problem.TOO_LARGE, assertThrows(LocalAssetStore.InvalidAsset.class, () -> store.importPng(huge)).problem());
        assertThrows(IOException.class, () -> store.importPng(temporary.resolve("missing.png")));
        assertThrows(IOException.class, () -> store.importPng(temporary));
        try (var files = Files.list(store.textures())) { assertEquals(0, files.count()); }
    }
    @Test void mountedEmptyPackSeesNewImagesImmediatelyAndAfterReload() throws Exception {
        Path source = png("image.png", 4, 2, 0xFFFFFFFF);
        var known = new LocalAssetStore(temporary.resolve("other-library")).importPng(source).id();
        var store = new LocalAssetStore(temporary.resolve("library"));
        var pack = LocalAssetPack.create(store);
        assertTrue(pack.isRequired()); assertTrue(pack.isHidden());
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack.open()))) {
            assertTrue(manager.getNamespaces().contains(LocalAssetStore.NAMESPACE));
            assertTrue(manager.getResource(known).isEmpty());
            var imported = store.importPng(source);
            assertEquals(known, imported.id());
            assertTrue(manager.listResources("textures", id -> id.getPath().endsWith(".png")).containsKey(known));
            try (var input = manager.getResource(known).orElseThrow().open()) {
                assertArrayEquals(Files.readAllBytes(imported.file()), input.readAllBytes());
            }
        }
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(LocalAssetPack.create(new LocalAssetStore(store.root())).open()))) {
            assertTrue(manager.getResource(known).isPresent());
        }
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack.open()))) {
            assertTrue(manager.getNamespaces().isEmpty());
        }
    }
    @Test void bookSerializationContainsOnlyResourceLocationNotFileOrImageBytes() throws Exception {
        var imported = new LocalAssetStore(temporary.resolve("library")).importPng(png("private-file.png", 2, 2, 0xFFABCDEF));
        var id = ResourceLocation.parse("test:book");
        var book = new QuestBookDefinition(id, 1, "Book", List.of(), List.of(), Map.of());
        var scene = new CanvasScene(List.of(), new CanvasScene.Background(imported.id().toString(), CanvasScene.Fit.TILE, 0.5), null);
        var updated = CanvasEdits.replace(book, id, scene).value().book();
        String json = NativeBookJson.encode(updated);
        assertTrue(json.contains(imported.id().toString()));
        assertFalse(json.contains("private-file")); assertFalse(json.contains("iVBOR"));
        assertFalse(json.contains(temporary.toString()));
        assertEquals(scene, NativeBookJson.decode(com.google.gson.JsonParser.parseString(json).getAsJsonObject()).canvasScene());
    }
}
