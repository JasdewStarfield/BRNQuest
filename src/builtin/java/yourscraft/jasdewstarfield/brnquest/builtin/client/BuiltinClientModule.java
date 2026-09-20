package yourscraft.jasdewstarfield.brnquest.builtin.client;

import yourscraft.jasdewstarfield.brnquest.client.ClientModule;
import yourscraft.jasdewstarfield.brnquest.builtin.basic.client.BuiltinBasicClient;

/** Client provider keeps presentation classes out of the common provider's linkage graph. */
public final class BuiltinClientModule implements ClientModule {
    @Override public void register() {
        BuiltinBasicClient.register();
        BuiltinClientTypes.register();
    }
}
