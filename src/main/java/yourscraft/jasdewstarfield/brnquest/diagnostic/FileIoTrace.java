package yourscraft.jasdewstarfield.brnquest.diagnostic;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Failure-only filesystem evidence. Never reads file contents, changes retry policy or replaces the original exception. */
public final class FileIoTrace {
    private FileIoTrace() {}
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private record Active(long id, String operation, Path source, Path target, String thread, long started) {}
    private static final ConcurrentHashMap<Long,Active> ACTIVE = new ConcurrentHashMap<>();
    @FunctionalInterface public interface Action<T> { T run() throws IOException; }
    public static <T> T run(String operation, Path source, Path target, Action<T> action) throws IOException {
        return run(operation, source, target, action, (evidence, error) -> LogUtils.getLogger().warn(evidence, error));
    }
    /** Injection keeps diagnostics testable without requiring a real disk failure or changing repository semantics. */
    static <T> T run(String operation, Path source, Path target, Action<T> action,
                     BiConsumer<String,IOException> reporter) throws IOException {
        long started = System.nanoTime();
        long id = SEQUENCE.incrementAndGet();
        var thread = Thread.currentThread();
        var activity = new Active(id,operation,normalized(source),normalized(target),thread.getName()+"#"+thread.threadId(),started);
        ACTIVE.put(id,activity);
        try { return action.run(); }
        catch (IOException error) {
            try {
                String reason = error instanceof FileSystemException fs ? fs.getReason() : error.getMessage();
                reporter.accept("[BRNQuest/FILE_IO] event=" + ProcessHandle.current().pid() + "-" + id
                        + " operation=" + operation + " elapsedMicros=" + (System.nanoTime()-started)/1000
                        + " thread=" + thread.getName() + "#" + thread.threadId()
                        + " exception=" + error.getClass().getName() + " reason=" + reason
                        + " source=" + describe(source) + " target=" + describe(target)
                        + " overlapping=" + overlapping(activity), error);
            } catch (RuntimeException diagnosticFailure) { /* Diagnostics must never mask the original failure. */ }
            throw error;
        } finally { ACTIVE.remove(id); }
    }
    private static Path normalized(Path path) {
        try { return path == null ? null : path.toAbsolutePath().normalize(); }
        catch (RuntimeException unavailable) { return null; }
    }
    private static boolean touches(Path left, Path right) {
        return left != null && right != null && (left.startsWith(right) || right.startsWith(left));
    }
    /** Only currently executing instrumented operations are visible; this is not an OS handle inventory. */
    private static String overlapping(Active failed) {
        var matches = ACTIVE.values().stream().filter(other -> other.id != failed.id &&
                (touches(other.source,failed.source) || touches(other.source,failed.target)
                        || touches(other.target,failed.source) || touches(other.target,failed.target))).toList();
        return "{observed=" + matches.size() + ", operations=" + matches.stream().limit(16).map(other ->
                "[event=" + ProcessHandle.current().pid() + "-" + other.id + ", operation=" + other.operation
                        + ", thread=" + other.thread + ", elapsedMicros=" + (System.nanoTime()-other.started)/1000
                        + ", source=" + other.source + ", target=" + other.target + "]").toList() + "}";
    }
    private static String describe(Path path) {
        if (path == null) return "none";
        try {
            Path absolute = path.toAbsolutePath().normalize();
            return "{path=" + absolute + ", state=" + attributes(absolute)
                    + ", parent=" + attributes(absolute.getParent()) + "}";
        } catch (RuntimeException error) { return "{path=" + path + ", inspection=" + error.getClass().getSimpleName() + "}"; }
    }
    private static String attributes(Path path) {
        if (path == null) return "none";
        try {
            var value = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return "[directory=" + value.isDirectory() + ", symbolicLink=" + value.isSymbolicLink()
                    + ", bytes=" + value.size() + ", modified=" + value.lastModifiedTime()
                    + ", readable=" + Files.isReadable(path) + ", writable=" + Files.isWritable(path) + "]";
        } catch (IOException | RuntimeException error) { return "[inspection=" + error.getClass().getSimpleName() + "]"; }
    }
    public static Path move(Path source, Path target, CopyOption... options) throws IOException {
        return run("move " + java.util.Arrays.toString(options), source, target, () -> Files.move(source,target,options));
    }
    public static String readString(Path path, Charset charset) throws IOException {
        return run("read " + charset.name(), path, null, () -> Files.readString(path,charset));
    }
    public static Path createDirectories(Path path) throws IOException {
        return run("create-directories", null, path, () -> Files.createDirectories(path));
    }
    public static Path copy(Path source, Path target, CopyOption... options) throws IOException {
        return run("copy " + java.util.Arrays.toString(options), source, target, () -> Files.copy(source,target,options));
    }
    public static boolean deleteIfExists(Path path) throws IOException {
        return run("cleanup-delete", path, null, () -> Files.deleteIfExists(path));
    }
    public static Path writeString(Path path, CharSequence content, Charset charset, OpenOption... options) throws IOException {
        return run("write " + charset.name() + " " + java.util.Arrays.toString(options), null, path,
                () -> Files.writeString(path,content,charset,options));
    }
    public static Path createTempFile(Path directory, String prefix, String suffix) throws IOException {
        return run("create-temporary-file", null, directory, () -> Files.createTempFile(directory,prefix,suffix));
    }
}
