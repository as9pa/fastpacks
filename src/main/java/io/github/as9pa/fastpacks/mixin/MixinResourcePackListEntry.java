package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.gui.RowBadge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.ResourcePackListEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the resolution badge on every row and shortens the name trim so the two never overlap. */
@Mixin(ResourcePackListEntry.class)
public abstract class MixinResourcePackListEntry {
    @Shadow @Final protected Minecraft mc;

    /**
     * drawEntry uses the literal 157 three times: twice for the name ("if (width > 157)" and
     * "trimStringToWidth(s, 157 - ...)"), once for the description. Only the first two change.
     */
    @ModifyConstant(method = "drawEntry",
            constant = {@Constant(intValue = 157, ordinal = 0), @Constant(intValue = 157, ordinal = 1)},
            require = 2)
    private int fastpacks$nameWidthWithBadge(int vanillaWidth) {
        return RowBadge.NAME_WIDTH;
    }

    @Inject(method = "drawEntry", at = @At("RETURN"))
    private void fastpacks$drawBadge(int slotIndex, int x, int y, int listWidth, int slotHeight,
                                     int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
        String badge = RowBadge.text((ResourcePackListEntry) (Object) this);
        if (badge.isEmpty()) {
            return;
        }
        FontRenderer font = mc.fontRendererObj;
        font.drawStringWithShadow(badge, x + RowBadge.RIGHT_EDGE - font.getStringWidth(badge), y + 1, RowBadge.COLOUR);
    }
}
