package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.EntryExtension;
import io.github.as9pa.fastpacks.Log;
import java.util.List;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Refreshes every cached entry key once per scan (keeps vanilla semantics) and logs the scan time. */
@Mixin(ResourcePackRepository.class)
public abstract class MixinResourcePackRepository {

    @Shadow private List<ResourcePackRepository.Entry> repositoryEntriesAll;

    @Unique private long fastpacks$scanStartNanos;

    @Inject(method = "updateRepositoryEntriesAll", at = @At("HEAD"))
    private void fastpacks$beforeScan(CallbackInfo ci) {
        fastpacks$scanStartNanos = System.nanoTime();
        for (Object entry : repositoryEntriesAll) {
            if (entry instanceof EntryExtension) {
                ((EntryExtension) entry).fastpacks$refreshKey();
            }
        }
    }

    @Inject(method = "updateRepositoryEntriesAll", at = @At("RETURN"))
    private void fastpacks$afterScan(CallbackInfo ci) {
        long ms = (System.nanoTime() - fastpacks$scanStartNanos) / 1_000_000L;
        Log.LOG.info("fastpacks: rescanned {} packs in {} ms", repositoryEntriesAll.size(), ms);
    }
}
