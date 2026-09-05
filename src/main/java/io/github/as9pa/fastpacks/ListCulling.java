package io.github.as9pa.fastpacks;

/** Off-screen test for list rows. Mirrors vanilla GuiSlot's own check so partially visible rows still draw. */
public final class ListCulling {
    private ListCulling() {}

    public static boolean isOffscreen(int y, int height, int top, int bottom) {
        return y > bottom || y + height < top;
    }
}
