package io.github.as9pa.fastpacks.dev;

import io.github.as9pa.fastpacks.EntryExtension;
import io.github.as9pa.fastpacks.Log;
import io.github.as9pa.fastpacks.filter.PackResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.ResourcePackRepository;
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

    private boolean menuSeen;
    private boolean opened;
    private long frameStartNanos;
    private long totalNanos;
    private int frames;
    private boolean reported;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui instanceof GuiMainMenu) {
            menuSeen = true;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (menuSeen && !opened) {
            // Deliberately not done from GuiOpenEvent: on the client thread addScheduledTask runs the
            // task inline, so displayGuiScreen from inside that event is immediately overwritten by
            // the displayGuiScreen call that fired it and the main menu wins.
            if (event.phase == TickEvent.Phase.END && mc.currentScreen instanceof GuiMainMenu) {
                opened = true;
                Log.LOG.info("fastpacks dev: opening GuiScreenResourcePacks");
                mc.displayGuiScreen(new GuiScreenResourcePacks(mc.currentScreen));
            }
            return;
        }
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
            int r16 = 0, r32 = 0, r64 = 0, r128 = 0, overlay = 0, unknown = 0, pending = 0;
            for (ResourcePackRepository.Entry entry : mc.getResourcePackRepository().getRepositoryEntriesAll()) {
                // In baseline mode the Entry mixin is off, so no entry implements EntryExtension: count them pending.
                PackResolution r = entry instanceof EntryExtension ? ((EntryExtension) entry).fastpacks$resolution() : null;
                if (r == null) { pending++; }
                else if (r.kind == PackResolution.Kind.OVERLAY) { overlay++; }
                else if (r.kind == PackResolution.Kind.UNKNOWN) { unknown++; }
                else if (r.res == 16) { r16++; } else if (r.res == 32) { r32++; } else if (r.res == 64) { r64++; } else { r128++; }
            }
            Log.LOG.info("fastpacks dev: resolutions 16x={} 32x={} 64x={} 128x+={} overlay={} unknown={} pending={}",
                    r16, r32, r64, r128, overlay, unknown, pending);
            mc.shutdown();
        }
    }
}
