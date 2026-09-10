package yourscraft.jasdewstarfield.brnquest.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import java.util.*;
import java.util.function.Consumer;

/** Local draft with the same scrolling rows and contextual actions as the quest reward list. */
final class RewardTableEditorScreen extends RewardEditorScreen {
    private final Consumer<String> commit;
    private JsonObject document;
    private final List<JsonObject> entries = new ArrayList<>();
    private final EditorEntryListPanel<String, String> list = new EditorEntryListPanel<>();
    private boolean readOnly;
    private String menuEntry;
    private String editingEntry;
    private List<EditorPopupMenu.Entry> menu = List.of();
    private EditorPopupMenu.CascadeLayout menuLayout;

    RewardTableEditorScreen(Screen parent, String value, Consumer<String> commit) {
        super(parent, Component.translatable("screen.brnquest.reward_table.entries"));
        this.commit = commit;
        try {
            if (value.isBlank()) {
                document = new JsonObject();
                document.addProperty("version", 1);
                document.addProperty("mode", "all");
            } else {
                var tree = RewardTableTree.parse(value);
                document = tree.document();
                entries.addAll(tree.entries());
            }
        } catch (RuntimeException error) {
            readOnly = true;
            issue = Objects.toString(error.getMessage(), "Unsupported table; original preserved");
        }
    }

    @Override protected void init() { super.init(); list.invalidate(); closeMenu(); }

    private UiRect entriesBounds() {
        var b = body();
        return new UiRect(b.left(), b.top() + 26, b.right(), b.bottom() - 26);
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        renderPanel(graphics, x, y, partial);
        graphics.drawString(font,font.plainSubstrByWidth(breadcrumb(),body().width()),body().left(),body().top()-13,0xFF9FB0C2,false);
        var bounds = entriesBounds();
        var hover = list.render(graphics, font, bounds, bounds, bounds.right() + 3,
                32, entries.size(), i -> id(entries.get(i)), frameSeconds(), scrollSpeed(),
                row -> content(find(row.key())), key -> List.of(new EditorActionGroup.Action<>(key,
                        EditorButton.Definition.iconOnly(Component.translatable("screen.brnquest.editor.action.more"),
                                Component.translatable("screen.brnquest.editor.typed.more_hint"), EditorIcon.glyph(Component.literal("…"))),
                        !readOnly, EditorButton.Tone.NEUTRAL, (mx, my) -> openMenu(key, mx.intValue(), my.intValue()))),
                List.of(), Component.translatable("screen.brnquest.editor.typed.empty"), x, y);
        var buttons = new ArrayList<>(footer(!readOnly, this::apply));
        var b = body();
        buttons.add(button("mode", Component.translatable("screen.brnquest.reward_table.mode." + mode()),
                new UiRect(b.left(), b.top(), b.centerX() - 3, b.top() + 20), !readOnly,
                EditorButton.Tone.NEUTRAL, this::openModeMenu));
        buttons.add(button("settings", Component.translatable("screen.brnquest.reward_table.settings"),
                new UiRect(b.centerX() + 3, b.top(), b.right(), b.top() + 20), !readOnly && mode().equals("random"),
                EditorButton.Tone.NEUTRAL, () -> editSettings(null)));
        buttons.add(button("add", Component.translatable("screen.brnquest.reward_table.add"),
                new UiRect(b.left(), b.bottom() - 20, b.right(), b.bottom()),
                !readOnly && entries.size() < RewardTableTree.MAX_NODES - 1, EditorButton.Tone.NEUTRAL, () -> chooseType(null)));
        controls.setActions(buttons);
        controls.render(graphics, font, x, y);
        if (menuLayout != null) EditorPopupMenu.render(graphics, font, menuLayout, menu, x, y);
        else if (!hover.tooltip().isEmpty()) graphics.renderComponentTooltip(font, hover.tooltip(), x, y);
    }

    private EditorEntryListPanel.Content content(JsonObject entry) {
        var type = ResourceLocation.parse(entry.get("type").getAsString());
        var values = RewardTableTree.config(entry);
        var view = new RewardView(type, type, type, values, "manual", false);
        var presentation = ClientRewardPresentationRegistry.get(type);
        ItemStack stack = ItemStack.EMPTY;
        try {
            String snbt = presentation.itemSnbt(view);
            if (!snbt.isBlank() && minecraft.level != null) stack = ItemStack.parseOptional(
                    minecraft.level.registryAccess(), net.minecraft.nbt.TagParser.parseTag(snbt));
        } catch (Exception ignored) { /* Invalid item drafts keep a usable type label. */ }
        Component name = values.getOrDefault("title", "").isBlank()
                ? (stack.isEmpty() ? presentation.typeName(view) : stack.getHoverName()) : Component.literal(values.get("title"));
        EditorIcon icon = stack.isEmpty() ? EditorIcon.glyph(Component.literal(presentation.symbol(view))) : EditorIcon.item(stack);
        return new EditorEntryListPanel.Content(new EditorEntryRow.Content(icon, name,
                mode().equals("random") ? Component.translatable(RewardTableTree.always(entry)
                        ? "screen.brnquest.reward_table.guaranteed" : "screen.brnquest.reward_table.weight_summary",
                        RewardTableTree.weight(entry).toPlainString()) : presentation.typeName(view), 0xFF9FB0C2), stack);
    }

    private void openMenu(String key, int x, int y) {
        menuEntry = key;
        int index = index(key);
        menu = EditorPopupMenu.menu(builder -> builder
                .action("edit", Component.translatable("screen.brnquest.config.edit"), false)
                .action("type", Component.translatable("screen.brnquest.reward_table.change_type"), false)
                .action("weight", Component.translatable("screen.brnquest.reward_table.entry_settings"), false, mode().equals("random"))
                .action("up", Component.translatable("screen.brnquest.editor.context.move_up"), false, index > 0)
                .action("down", Component.translatable("screen.brnquest.editor.context.move_down"), false, index + 1 < entries.size())
                .action("copy", Component.translatable("screen.brnquest.reward_table.copy"), false, entries.size() < RewardTableTree.MAX_NODES - 1)
                .action("delete", Component.translatable("screen.brnquest.editor.context.delete"), true));
        var root = EditorPopupMenu.layout(x, y, width, panel().top(), panel().bottom(), 150, menu.size());
        menuLayout = EditorPopupMenu.cascadeLayout(root, menu, -1, width, panel().top(), panel().bottom(), 150);
    }

    private void closeMenu() { menuEntry = null; menu = List.of(); menuLayout = null; }

    private void act(String key, String action) {
        int index = index(key);
        if (index < 0) return;
        switch (action) {
            case "edit" -> edit(key);
            case "type" -> chooseType(key);
            case "weight" -> editSettings(key);
            case "up" -> { if (index > 0) Collections.swap(entries, index, index - 1); }
            case "down" -> { if (index + 1 < entries.size()) Collections.swap(entries, index, index + 1); }
            case "copy" -> entries.add(index + 1, RewardTableTree.copyEntry(entries.get(index)));
            case "delete" -> entries.remove(index);
            default -> { }
        }
        // A reordered row must not reuse yesterday's index for another click in the same frame.
        list.invalidate();
    }

    private void chooseType(String key) {
        editingEntry=key;
        minecraft.setScreen(new RewardTypePickerScreen(this, type -> {
            JsonObject previous = key == null ? null : find(key);
            // Switching types starts from that type's defaults; cancelling leaves the old entry intact.
            Map<String, String> initial = previous != null && previous.get("type").getAsString().equals(type.toString())
                    ? editorConfig(previous) : Map.of();
            minecraft.setScreen(new RewardLeafEditorScreen(this, type, initial, values -> {
                JsonObject changed = previous == null ? new JsonObject() : previous.deepCopy();
                if (previous == null) {
                    changed.addProperty("entry_id", "entry_" + UUID.randomUUID().toString().replace("-", ""));
                    changed.addProperty("weight", 1);
                    changed.addProperty("always", false);
                }
                changed.addProperty("type", type.toString());
                writeEntryConfig(changed,values);
                if (previous == null) entries.add(changed);
                else entries.set(index(key), changed);
            }));
        }));
    }

    private void edit(String key) {
        editingEntry=key;
        var entry = find(key);
        minecraft.setScreen(new RewardLeafEditorScreen(this, ResourceLocation.parse(entry.get("type").getAsString()),
                editorConfig(entry), values -> {
            var changed = entry.deepCopy();
            writeEntryConfig(changed,values);
            entries.set(index(key), changed);
        }));
    }

    /** The form uses a string map; the stored tree has exactly one structured child document. */
    private static Map<String,String> editorConfig(JsonObject entry) {
        var values=new LinkedHashMap<>(RewardTableTree.config(entry));
        if(entry.has("table"))values.put("table",entry.get("table").toString());
        return values;
    }
    private static void writeEntryConfig(JsonObject entry,Map<String,String> values) {
        var fields=new LinkedHashMap<>(values);entry.remove("table");
        if(entry.get("type").getAsString().equals("brnquest:reward_table"))
            entry.add("table",RewardTableTree.parse(fields.remove("table")).document());
        entry.add("config",config(fields));
    }
    @Override protected UiRect body() {
        var b=super.body();return new UiRect(b.left(),b.top()+14,b.right(),b.bottom());
    }
    private String breadcrumb() {
        var labels=new ArrayList<String>();Screen screen=parent;
        while(screen instanceof RewardEditorScreen editor) {
            if(screen instanceof RewardTableEditorScreen table) {
                String key=table.editingEntry;
                var values=key!=null && table.index(key)>=0?RewardTableTree.config(table.find(key)):Map.<String,String>of();
                labels.add(values.getOrDefault("title","").isBlank()?(key==null?"+":key):values.get("title"));
            }
            screen=editor.parent;
        }
        Collections.reverse(labels);return "root"+(labels.isEmpty()?"":" / "+String.join(" / ",labels));
    }
    private void apply() {
        try {
            JsonObject result = document.deepCopy();
            JsonArray children = new JsonArray();
            entries.forEach(e -> children.add(e.deepCopy()));
            result.add("entries", children);
            var tree = RewardTableTree.parse(result.toString());
            commit.accept(tree.encode());
            onClose();
        } catch (RuntimeException error) { issue = Objects.toString(error.getMessage(), "Invalid table"); }
    }

    private static String id(JsonObject entry) { return entry.get("entry_id").getAsString(); }
    private int index(String key) {
        for (int i = 0; i < entries.size(); i++) if (id(entries.get(i)).equals(key)) return i;
        return -1;
    }
    private JsonObject find(String key) { return entries.get(index(key)); }
    private static JsonObject config(Map<String, String> values) {
        JsonObject object = new JsonObject();
        values.forEach(object::addProperty);
        return object;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (menuLayout != null) {
            String key = menuEntry;
            String action = button == 0 ? EditorPopupMenu.actionAt(menuLayout, menu, x, y) : "";
            closeMenu();
            if (!action.isBlank()) {
                if (key.isEmpty()) { document.addProperty("mode", action); list.invalidate(); }
                else act(key, action);
            }
            return true;
        }
        if (list.actions().mouseClicked(x, y, button) || list.list().mouseClicked(x, y, button)) return true;
        var row = list.list().rowAt(x, y);
        if (!readOnly && row.isPresent()) {
            if (button == 0) edit(row.orElseThrow().key());
            else if (button == 1) openMenu(row.orElseThrow().key(), (int) x, (int) y);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    private String mode() { return document == null || !document.has("mode") ? "all" : document.get("mode").getAsString(); }

    private void openModeMenu() {
        menuEntry = "";
        menu = EditorPopupMenu.menu(builder -> {
            for (String mode : List.of("all", "random", "choice")) builder.action(mode,
                    Component.translatable("screen.brnquest.reward_table.mode." + mode), false);
        });
        var root = EditorPopupMenu.layout(body().left(), body().top() + 20, width, panel().top(), panel().bottom(), 150, menu.size());
        menuLayout = EditorPopupMenu.cascadeLayout(root, menu, -1, width, panel().top(), panel().bottom(), 150);
    }

    /** Root and entry metadata stay outside the leaf's reward config, preserving both on mode changes. */
    private void editSettings(String key) {
        JsonObject source = key == null ? document : find(key);
        List<ConfigFieldDescriptor> fields = key == null ? List.of(
                ConfigFieldDescriptor.field("rolls", ConfigValueType.INTEGER).asRequired().withDefault("1").withRange(1, 64).withLabel("screen.brnquest.reward_table.rolls"),
                ConfigFieldDescriptor.field("replacement", ConfigValueType.BOOLEAN).withDefault("true").withLabel("screen.brnquest.reward_table.replacement"),
                ConfigFieldDescriptor.field("empty_weight", ConfigValueType.DECIMAL).asRequired().withDefault("0").withRange(0, Double.MAX_VALUE).withLabel("screen.brnquest.reward_table.empty_weight"))
                : List.of(ConfigFieldDescriptor.field("weight", ConfigValueType.DECIMAL).asRequired().withDefault("1").withLabel("screen.brnquest.reward_table.weight"),
                ConfigFieldDescriptor.field("always", ConfigValueType.BOOLEAN).withDefault("false").withLabel("screen.brnquest.reward_table.guaranteed"));
        Map<String, String> values = new LinkedHashMap<>();
        fields.forEach(field -> { if (source.has(field.key())) values.put(field.key(), source.get(field.key()).getAsString()); });
        var schema = new ConfigEditorSchema(ConfigEditorSchema.Kind.REWARD, ResourceLocation.parse("brnquest:reward_table"), fields, values, List.of(), false);
        minecraft.setScreen(new RewardLeafEditorScreen(this, Component.translatable(key == null
                ? "screen.brnquest.reward_table.settings" : "screen.brnquest.reward_table.entry_settings"), schema, changed -> {
            JsonObject candidate = settings(source, changed);
            JsonObject check = key == null ? candidate.deepCopy() : document.deepCopy();
            JsonArray children = new JsonArray();
            for (var entry : entries) children.add(key != null && id(entry).equals(key) ? candidate : entry.deepCopy());
            // Allow configuring a new empty draft before its first real entry; this placeholder is validation-only.
            if (children.isEmpty()) {
                JsonObject placeholder = new JsonObject();
                placeholder.addProperty("entry_id", "validation"); placeholder.addProperty("type", "brnquest:custom");
                children.add(placeholder);
            }
            check.add("entries", children);
            RewardTableTree.parse(check.toString()).requireSingleLayer();
            return changed;
        }, changed -> {
            if (key == null) document = settings(source, changed);
            else entries.set(index(key), settings(source, changed));
        }));
    }

    private static JsonObject settings(JsonObject source, Map<String, String> values) {
        var changed = source.deepCopy();
        values.forEach((key, value) -> {
            if (key.equals("always") || key.equals("replacement")) changed.addProperty(key, Boolean.parseBoolean(value));
            else changed.addProperty(key, new java.math.BigDecimal(value));
        });
        return changed;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (menuLayout != null) return true;
        return list.list().mouseScrolled(x, y, vertical, scrollStep()) || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256 && menuLayout != null) { closeMenu(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
