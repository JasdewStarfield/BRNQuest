package yourscraft.jasdewstarfield.brnquest.platform;

/** Internal assembly SPI. A source module supplies its provider only when installed. */
public interface RuntimeModule {
    void register();

    /** Use the mod class loader so dedicated servers discover only installed providers. */
    static void registerInstalled() {
        java.util.ServiceLoader.load(RuntimeModule.class, RuntimeModule.class.getClassLoader())
                .forEach(RuntimeModule::register);
    }
}
