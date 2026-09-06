package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.ListCulling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiResourcePackList;
import net.minecraft.client.gui.GuiSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla GuiSlot.drawSelectionBox() calls drawSlot() for every row every frame, on-screen or not.
 * Skip rows entirely outside the list area, but only for the two resource pack lists so other
 * mods' lists keep vanilla behaviour. OptiFine already does this; with it present this is a no-op.
 */
@Mixin(GuiListExtended.class)
public abstract class MixinGuiListExtended extends GuiSlot {

    private MixinGuiListExtended(Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
        super(mc, width, height, top, bottom, slotHeight);
    }

    @Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
    private void fastpacks$cullOffscreenRows(int entryId, int x, int y, int height, int mouseX, int mouseY, CallbackInfo ci) {
        if ((Object) this instanceof GuiResourcePackList && ListCulling.isOffscreen(y, height, this.top, this.bottom)) {
            ci.cancel();
        }
    }
}
