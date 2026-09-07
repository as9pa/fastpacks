package io.github.as9pa.fastpacks.gui;

import io.github.as9pa.fastpacks.filter.PackFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/** A vanilla-looking button that keeps the hovered look while its chip is the active filter. */
public final class ChipButton extends GuiButton {
    public static final int HEIGHT = 20;
    private static final int TEXT_NORMAL = 0xE0E0E0;
    private static final int TEXT_HOVER = 0xFFFFA0;

    public final PackFilter.Chip chip;
    private boolean active;

    public ChipButton(int id, int x, int y, int width, PackFilter.Chip chip) {
        super(id, x, y, width, HEIGHT, chip.label);
        this.chip = chip;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    protected int getHoverState(boolean mouseOver) {
        return active || mouseOver ? 2 : 1;
    }

    /** Vanilla GuiButton.drawButton (1.8.9), except the text colour also follows {@link #active}. */
    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        FontRenderer font = mc.fontRendererObj;
        mc.getTextureManager().bindTexture(buttonTextures);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
        int state = getHoverState(hovered);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.blendFunc(770, 771);
        drawTexturedModalRect(xPosition, yPosition, 0, 46 + state * 20, width / 2, height);
        drawTexturedModalRect(xPosition + width / 2, yPosition, 200 - width / 2, 46 + state * 20, width / 2, height);
        mouseDragged(mc, mouseX, mouseY);
        int colour = active || hovered ? TEXT_HOVER : TEXT_NORMAL;
        drawCenteredString(font, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, colour);
    }
}
