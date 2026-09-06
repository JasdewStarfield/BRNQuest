package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;

import java.util.Objects;

/** Identifies the immutable data and logical viewport used to draw one interactive screen frame. */
record QuestScreenFrameIdentity(ResourceLocation bookId, String revision, boolean editing, int width, int height) {
    QuestScreenFrameIdentity {
        Objects.requireNonNull(bookId, "bookId");
        Objects.requireNonNull(revision, "revision");
        if (width < 0 || height < 0) throw new IllegalArgumentException("Frame dimensions must be non-negative");
    }

    static QuestScreenFrameIdentity of(QuestBookSnapshot snapshot, boolean editing, int width, int height) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new QuestScreenFrameIdentity(snapshot.book().id(), snapshot.revision(), editing, width, height);
    }
}
