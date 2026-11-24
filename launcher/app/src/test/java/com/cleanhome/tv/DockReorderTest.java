package com.cleanhome.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DockReorderTest {
    @Test
    public void startsIdle() {
        DockReorder reorder = new DockReorder();
        assertFalse(reorder.isActive());
        assertEquals(-1, reorder.finish());
    }

    @Test
    public void beginAndFinishRoundTripTheOrigin() {
        DockReorder reorder = new DockReorder();
        reorder.begin(2);
        assertTrue(reorder.isActive());
        assertEquals(2, reorder.origin());
        assertEquals(2, reorder.finish());
        assertFalse(reorder.isActive());
        assertEquals(-1, reorder.finish());
    }

    @Test
    public void cancelLeavesNoOriginBehind() {
        DockReorder reorder = new DockReorder();
        reorder.begin(1);
        reorder.cancel();
        assertFalse(reorder.isActive());
        assertEquals(-1, reorder.finish());
    }

    @Test
    public void aSecondBeginKeepsTheFirstOrigin() {
        DockReorder reorder = new DockReorder();
        reorder.begin(1);
        reorder.begin(3);
        assertEquals(1, reorder.origin());
        assertEquals(1, reorder.finish());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNegativeOriginIsRejected() {
        new DockReorder().begin(-1);
    }
}
