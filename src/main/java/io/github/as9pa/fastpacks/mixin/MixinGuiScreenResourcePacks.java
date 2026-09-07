package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.gui.FilteredList;
import io.github.as9pa.fastpacks.gui.PacksToolbar;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiResourcePackAvailable;
import net.minecraft.client.gui.GuiResourcePackSelected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.ResourcePackListEntry;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the fastpacks toolbar (search, chips, counter) and pushes both lists down to make room. */
@Mixin(GuiScreenResourcePacks.class)
public abstract class MixinGuiScreenResourcePacks extends GuiScreen {
    private static final int LIST_WIDTH = 200;
    private static final int LIST_BOTTOM_INSET = 51;

    @Shadow private List<ResourcePackListEntry> availableResourcePacks;
    @Shadow private GuiResourcePackAvailable availableResourcePacksList;
    @Shadow private GuiResourcePackSelected selectedResourcePacksList;

    @Unique private PacksToolbar fastpacks$toolbar;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void fastpacks$addToolbar(CallbackInfo ci) {
        String keptQuery = fastpacks$toolbar == null ? "" : fastpacks$toolbar.query();   // survives a window resize
        int left = width / 2 - 204;
        fastpacks$toolbar = new PacksToolbar(fontRendererObj, left, PacksToolbar.TOOLBAR_Y, availableResourcePacks, keptQuery);
        buttonList.addAll(fastpacks$toolbar.chips());

        // setDimensions resets left/right, so re-apply the x bounds vanilla set in initGui.
        availableResourcePacksList.setDimensions(LIST_WIDTH, height, PacksToolbar.LIST_TOP, height - LIST_BOTTOM_INSET);
        availableResourcePacksList.setSlotXBoundsFromLeft(left);
        selectedResourcePacksList.setDimensions(LIST_WIDTH, height, PacksToolbar.LIST_TOP, height - LIST_BOTTOM_INSET);
        selectedResourcePacksList.setSlotXBoundsFromLeft(width / 2 + 4);

        ((FilteredList) availableResourcePacksList).fastpacks$setView(fastpacks$toolbar.visible());
        fastpacks$toolbar.refresh();
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void fastpacks$refreshView(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (fastpacks$toolbar != null) {
            fastpacks$toolbar.refresh();
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void fastpacks$drawToolbar(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (fastpacks$toolbar != null) {
            fastpacks$toolbar.draw();
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void fastpacks$onChipPressed(GuiButton button, CallbackInfo ci) {
        if (fastpacks$toolbar != null && fastpacks$toolbar.activate(button)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void fastpacks$focusSearch(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (fastpacks$toolbar != null) {
            fastpacks$toolbar.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    // GuiScreenResourcePacks does not override these two, so nothing vanilla is replaced: the mixin
    // merges them as ordinary overrides of GuiScreen and defers to super.

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (fastpacks$toolbar != null && keyCode != Keyboard.KEY_ESCAPE && fastpacks$toolbar.keyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (fastpacks$toolbar != null) {
            fastpacks$toolbar.tick();
        }
    }
}
