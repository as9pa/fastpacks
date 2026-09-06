package io.github.as9pa.fastpacks;

import io.github.as9pa.fastpacks.dev.DevHarness;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = FastPacks.MODID, useMetadata = true, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class FastPacks {
    public static final String MODID = "fastpacks";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Log.LOG.info("fastpacks initialised");
        if (Boolean.getBoolean("fastpacks.devOpenPacksGui")) {
            Log.LOG.warn("fastpacks: dev harness enabled - will auto-open the Resource Packs screen and exit");
            MinecraftForge.EVENT_BUS.register(new DevHarness());
        }
    }
}
