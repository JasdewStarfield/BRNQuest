package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ServiceLoader;

/** Internal bridge preserves the historic public facade while isolating the concrete converter. */
public interface FtbImportBackend {
    FtbImportResult importBook(Path source, String namespace, String bookPath) throws IOException;

    /** A missing adapter is an explicit import error; it never changes the source or creates a draft. */
    static FtbImportBackend installed() throws IOException {
        var providers = ServiceLoader.load(FtbImportBackend.class, FtbImportBackend.class.getClassLoader())
                .stream().toList();
        if (providers.size() != 1) throw new IOException("Expected one installed FTB import adapter, found " + providers.size());
        return providers.getFirst().get();
    }
}
