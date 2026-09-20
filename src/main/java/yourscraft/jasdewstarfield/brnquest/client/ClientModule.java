package yourscraft.jasdewstarfield.brnquest.client;

/** Internal client assembly SPI, discovered only after the physical-client guard. */
public interface ClientModule {
    void register();

    static void registerInstalled() {
        java.util.ServiceLoader.load(ClientModule.class, ClientModule.class.getClassLoader())
                .forEach(ClientModule::register);
    }
}
