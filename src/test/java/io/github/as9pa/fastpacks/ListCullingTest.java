package io.github.as9pa.fastpacks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ListCullingTest {
    private static final int TOP = 32;
    private static final int BOTTOM = 200;
    private static final int H = 32;

    @Test public void fullyAboveIsOffscreen()        { assertTrue(ListCulling.isOffscreen(-40, H, TOP, BOTTOM)); }
    @Test public void justAboveByOnePixelIsOffscreen(){ assertTrue(ListCulling.isOffscreen(-1, H, TOP, BOTTOM)); }
    @Test public void touchingTopIsVisible()         { assertFalse(ListCulling.isOffscreen(0, H, TOP, BOTTOM)); }
    @Test public void straddlingTopIsVisible()       { assertFalse(ListCulling.isOffscreen(20, H, TOP, BOTTOM)); }
    @Test public void middleIsVisible()              { assertFalse(ListCulling.isOffscreen(100, H, TOP, BOTTOM)); }
    @Test public void straddlingBottomIsVisible()    { assertFalse(ListCulling.isOffscreen(190, H, TOP, BOTTOM)); }
    @Test public void touchingBottomIsVisible()      { assertFalse(ListCulling.isOffscreen(200, H, TOP, BOTTOM)); }
    @Test public void justBelowIsOffscreen()         { assertTrue(ListCulling.isOffscreen(201, H, TOP, BOTTOM)); }
    @Test public void farBelowIsOffscreen()          { assertTrue(ListCulling.isOffscreen(5000, H, TOP, BOTTOM)); }
}
