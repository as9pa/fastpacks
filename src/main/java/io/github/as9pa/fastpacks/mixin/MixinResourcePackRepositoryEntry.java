package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.PackKey;
import io.github.as9pa.fastpacks.icon.IconLoader;
import java.awt.image.BufferedImage;
import java.io.File;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Shadow private BufferedImage texturePackIcon;
    @Shadow private ResourceLocation locationTexturePackIcon;

    /**
     * Vanilla decodes pack.png synchronously here (for every pack, at startup). Both getPackImage() call sites in
     * updateResourcePack() hit this redirect: the pack's own icon and the default-pack fallback. We hand back the
     * shared placeholder at once and decode the real icon in the background.
     */
    @Redirect(
            method = "updateResourcePack",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/IResourcePack;getPackImage()Ljava/awt/image/BufferedImage;"))
    private BufferedImage fastpacks$deferIcon(IResourcePack pack) {
        if (!(pack instanceof DefaultResourcePack)) {
            IconLoader.submit(resourcePackFile, this);
        }
        return IconLoader.placeholder();
    }

    /** Client thread: swap a finished background icon in before vanilla binds (and lazily uploads) the texture. */
    @Inject(method = "bindTexturePackIcon", at = @At("HEAD"))
    private void fastpacks$swapInLoadedIcon(TextureManager textureManager, CallbackInfo ci) {
        BufferedImage ready = fastpacks$pendingIcon;
        if (ready == null) {
            return;
        }
        fastpacks$pendingIcon = null;
        texturePackIcon = ready;
        if (locationTexturePackIcon != null) {
            textureManager.deleteTexture(locationTexturePackIcon);
            locationTexturePackIcon = null;
        }
    }

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
