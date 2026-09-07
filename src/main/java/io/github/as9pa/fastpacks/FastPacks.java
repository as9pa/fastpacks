package io.github.as9pa.fastpacks;

import io.github.as9pa.fastpacks.dev.DevHarness;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = FastPacks.MODID, version = FastPacks.VERSION, useMetadata = true, clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]")
public class FastPacks {
    public static final String MODID = "fastpacks";
    /** Keep in step with gradle.properties. */
    public static final String VERSION = "0.2.0";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Log.LOG.info("fastpacks initialised");
        if (Boolean.getBoolean("fastpacks.devOpenPacksGui")) {
            Log.LOG.warn("fastpacks: dev harness enabled - will auto-open the Resource Packs screen and exit");
            MinecraftForge.EVENT_BUS.register(new DevHarness());
        }
    }
}
