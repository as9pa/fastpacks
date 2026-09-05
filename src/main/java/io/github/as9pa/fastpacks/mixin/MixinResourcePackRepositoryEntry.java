package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.PackKey;
import java.awt.image.BufferedImage;
import java.io.File;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla Entry.toString() stats the file twice on every call, and equals()/hashCode() call toString().
 * updateRepositoryEntriesAll() compares entries O(N^2) times, so with hundreds of packs that is hundreds of
 * thousands of filesystem calls per screen open. We cache the string; the repository mixin refreshes it once per scan.
 */
@Mixin(ResourcePackRepository.Entry.class)
public abstract class MixinResourcePackRepositoryEntry implements EntryExtension {

    @Shadow @Final private File resourcePackFile;

    @Unique private String fastpacks$key;
    @Unique private volatile BufferedImage fastpacks$pendingIcon;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fastpacks$onConstructed(CallbackInfo ci) {
        fastpacks$refreshKey();
    }

    @Override
    public void fastpacks$refreshKey() {
        fastpacks$key = PackKey.compute(resourcePackFile);
    }

    @Override
    public void acceptIcon(BufferedImage image) {
        fastpacks$pendingIcon = image;
    }

    @Inject(method = "toString", at = @At("HEAD"), cancellable = true)
    private void fastpacks$cachedToString(CallbackInfoReturnable<String> cir) {
        if (fastpacks$key == null) {
            fastpacks$refreshKey();
        }
        cir.setReturnValue(fastpacks$key);
    }
}
