package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.gui.FilteredList;
import java.util.List;
import net.minecraft.client.gui.GuiResourcePackList;
import net.minecraft.client.resources.ResourcePackListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every GuiSlot draw and mouse path goes through getSize()/getListEntry(). When the toolbar attaches a
 * view, answer from it; the vanilla list itself is never mutated. Only the Available list gets a view.
 */
@Mixin(GuiResourcePackList.class)
public abstract class MixinGuiResourcePackList implements FilteredList {
    @Unique private List<ResourcePackListEntry> fastpacks$view;

    @Override
    public void fastpacks$setView(List<ResourcePackListEntry> view) {
        fastpacks$view = view;
    }

    @Inject(method = "getSize", at = @At("HEAD"), cancellable = true)
    private void fastpacks$filteredSize(CallbackInfoReturnable<Integer> cir) {
        if (fastpacks$view != null) {
            cir.setReturnValue(fastpacks$view.size());
        }
    }

    @Inject(method = "getListEntry(I)Lnet/minecraft/client/resources/ResourcePackListEntry;", at = @At("HEAD"), cancellable = true)
    private void fastpacks$filteredEntry(int index, CallbackInfoReturnable<ResourcePackListEntry> cir) {
        if (fastpacks$view != null) {
            cir.setReturnValue(fastpacks$view.get(index));
        }
    }
}
