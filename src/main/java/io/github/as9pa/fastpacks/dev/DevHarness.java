package io.github.as9pa.fastpacks.dev;

import io.github.as9pa.fastpacks.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Dev-only. Opens the Resource Packs screen as soon as the main menu appears, measures whole-frame
 * render time (RenderTickEvent START to END, which excludes the frame-rate cap wait) while that screen
 * is showing, logs the average after FRAMES frames and exits the game.
 */
public class DevHarness {
    private static final int FRAMES = 120;

    private boolean opened;
    private long frameStartNanos;
    private long totalNanos;
    private int frames;
    private boolean reported;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (opened || !(event.gui instanceof GuiMainMenu)) {
            return;
        }
        opened = true;
        final GuiScreen menu = event.gui;
        final Minecraft mc = Minecraft.getMinecraft();
        // Let the main menu finish showing, then replace it on the next tick.
        mc.addScheduledTask(() -> {
            Log.LOG.info("fastpacks dev: opening GuiScreenResourcePacks");
            mc.displayGuiScreen(new GuiScreenResourcePacks(menu));
        });
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (reported || !(mc.currentScreen instanceof GuiScreenResourcePacks)) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            frameStartNanos = System.nanoTime();
            return;
        }
        if (frameStartNanos == 0L) {
            return;
        }
        totalNanos += System.nanoTime() - frameStartNanos;
        frames++;
        if (frames >= FRAMES) {
            reported = true;
            int packs = mc.getResourcePackRepository().getRepositoryEntriesAll().size();
            Log.LOG.info("fastpacks dev: {} packs, avg frame {} ms over {} frames",
                    packs, String.format("%.2f", totalNanos / 1_000_000.0 / frames), frames);
            mc.shutdown();
        }
    }
}
