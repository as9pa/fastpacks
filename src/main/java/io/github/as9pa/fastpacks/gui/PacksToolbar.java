package io.github.as9pa.fastpacks.gui;

import io.github.as9pa.fastpacks.EntryExtension;
import io.github.as9pa.fastpacks.filter.PackFilter;
import io.github.as9pa.fastpacks.filter.PackResolution;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;

/**
 * The row above the two lists: search box, resolution chips, match counter. Owns the filtered view
 * that the Available list widget reads through. One instance per initGui.
 */
public final class PacksToolbar {
    public static final int TOOLBAR_Y = 28;
    public static final int LIST_TOP = 52;
    public static final int WIDTH = 408;
    public static final int HEIGHT = 20;
    public static final int SEARCH_WIDTH = 140;
    public static final int CHIP_GAP = 2;
    public static final int SEARCH_ID = 199;
    public static final int CHIP_ID_BASE = 100;
    private static final int MAX_QUERY_LENGTH = 40;
    private static final String PLACEHOLDER = "Search packs";
    private static final int PLACEHOLDER_COLOUR = 0x707070;
    private static final int COUNTER_COLOUR = 0xA0A0A0;

    private final FontRenderer font;
    private final int left;
    private final int top;
    private final List<ResourcePackListEntry> source;
    private final List<ResourcePackListEntry> visible = new ArrayList<>();
    private final List<ResourcePackListEntry> snapshot = new ArrayList<>();
    private final List<ChipButton> chips = new ArrayList<>();
    private final GuiTextField search;
    private String query;
    private boolean dirty = true;

    public PacksToolbar(FontRenderer font, int left, int top, List<ResourcePackListEntry> source, String initialQuery) {
        this.font = font;
        this.left = left;
        this.top = top;
        this.source = source;
        // GuiTextField draws its 1 px border outside the box, so inset by 1 to occupy exactly 140x20.
        search = new GuiTextField(SEARCH_ID, font, left + 1, top + 1, SEARCH_WIDTH - 2, HEIGHT - 2);
        search.setMaxStringLength(MAX_QUERY_LENGTH);
        search.setText(initialQuery == null ? "" : initialQuery);
        query = PackFilter.normalizeQuery(search.getText());

        int x = left + SEARCH_WIDTH + 4;
        int id = CHIP_ID_BASE;
        PackFilter.Chip active = PackFilter.activeChip();
        for (PackFilter.Chip chip : PackFilter.Chip.values()) {
            int width = font.getStringWidth(chip.label) + 10;
            ChipButton button = new ChipButton(id++, x, top, width, chip);
            button.setActive(chip == active);
            chips.add(button);
            x += width + CHIP_GAP;
        }
    }

    /** Add these to the screen's buttonList; the screen draws and clicks them. */
    public List<ChipButton> chips() {
        return chips;
    }

    /** The live filtered view. Same instance for the toolbar's lifetime; contents change on refresh(). */
    public List<ResourcePackListEntry> visible() {
        return visible;
    }

    public String query() {
        return search.getText();
    }

    /** Recomputes the view when the filter or the source list changed. Cheap when neither did. */
    public void refresh() {
        if (!dirty && sameAsSnapshot()) {
            return;
        }
        dirty = false;
        snapshot.clear();
        snapshot.addAll(source);
        visible.clear();
        PackFilter.Chip chip = PackFilter.activeChip();
        for (ResourcePackListEntry entry : source) {
            if (matches(chip, entry)) {
                visible.add(entry);
            }
        }
    }

    private boolean sameAsSnapshot() {
        if (snapshot.size() != source.size()) {
            return false;
        }
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i) != source.get(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(PackFilter.Chip chip, ResourcePackListEntry entry) {
        if (!(entry instanceof ResourcePackListEntryFound)) {
            return chip == PackFilter.Chip.ALL && query.isEmpty();
        }
        ResourcePackRepository.Entry pack = ((ResourcePackListEntryFound) entry).func_148318_i();
        // Without the Entry mixin (baseline mode) nothing implements EntryExtension: treat it as still pending.
        PackResolution resolution = pack instanceof EntryExtension ? ((EntryExtension) pack).fastpacks$resolution() : null;
        return PackFilter.matches(chip, query, pack.getResourcePackName(), pack.getTexturePackDescription(), resolution);
    }

    /** True (and the filter updated) when the button is one of our chips. */
    public boolean activate(GuiButton button) {
        if (!(button instanceof ChipButton)) {
            return false;
        }
        PackFilter.Chip chip = ((ChipButton) button).chip;
        PackFilter.setActiveChip(chip);
        for (ChipButton b : chips) {
            b.setActive(b.chip == chip);
        }
        dirty = true;
        return true;
    }

    /** True when the search box consumed the key. */
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!search.isFocused()) {
            return false;
        }
        search.textboxKeyTyped(typedChar, keyCode);
        String normalized = PackFilter.normalizeQuery(search.getText());
        if (!normalized.equals(query)) {
            query = normalized;
            dirty = true;
        }
        return true;
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        search.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void tick() {
        search.updateCursorCounter();
    }

    /** Call after the vanilla screen has drawn (buttons included). */
    public void draw() {
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) {
            font.drawStringWithShadow(PLACEHOLDER, left + 5, top + 6, PLACEHOLDER_COLOUR);
        }
        String counter = visible.size() + " of " + source.size();
        font.drawStringWithShadow(counter, left + WIDTH - font.getStringWidth(counter), top + 6, COUNTER_COLOUR);
    }
}
