package io.github.as9pa.fastpacks;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = FastPacks.MODID, useMetadata = true, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class FastPacks {
    public static final String MODID = "fastpacks";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Log.LOG.info("fastpacks initialised");
    }
}
