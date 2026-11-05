package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import java.util.Optional;
import java.util.UUID;

/** Immutable session facts for composed forms; only the screen/workflow keeps the mutable facade. */
record EditorSessionView(UUID sessionId, ClientEditorState.Mode mode, boolean editable, boolean busy,
                         String statusCode, Optional<QuestBookSnapshot> draft) {
    static EditorSessionView capture(ClientEditorState editor) {
        return new EditorSessionView(editor.sessionId(), editor.mode(), EditorSessionLifecycle.canEdit(editor),
                editor.busy(), editor.statusCode(), editor.draft());
    }
}
