package yourscraft.jasdewstarfield.brnquest.client.ui;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

/** Child results belong to one opening, including when the same object is reopened in the same lease. */
final class EditorFormIdentity {
    record Token(long generation, UUID session, ResourceLocation target) {}
    private long generation;
    private Token current;
    void open(UUID session, ResourceLocation target) { current = new Token(++generation, session, target); }
    void close() { generation++; current = null; }
    Token token() { return current; }
    boolean matches(Token token, UUID session, ResourceLocation target) {
        return token != null && token.equals(current) && Objects.equals(token.session(), session)
                && Objects.equals(token.target(), target);
    }
}
