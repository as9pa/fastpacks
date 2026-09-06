package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.Log;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Dev switch: -Dfastpacks.baseline=true keeps the timing mixin but disables the optimisations,
 * so before/after can be measured in the same environment. Must not reference Minecraft classes.
 */
public class FastPacksMixinPlugin implements IMixinConfigPlugin {
    private static final boolean BASELINE = Boolean.getBoolean("fastpacks.baseline");
    private static final Set<String> OPTIMISATIONS = new HashSet<>(Arrays.asList(
            "io.github.as9pa.fastpacks.mixin.MixinResourcePackRepositoryEntry",
            "io.github.as9pa.fastpacks.mixin.MixinGuiListExtended"));

    @Override
    public void onLoad(String mixinPackage) {
        if (BASELINE) {
            Log.LOG.warn("fastpacks: BASELINE mode - optimisations disabled, timing only");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !(BASELINE && OPTIMISATIONS.contains(mixinClassName));
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
