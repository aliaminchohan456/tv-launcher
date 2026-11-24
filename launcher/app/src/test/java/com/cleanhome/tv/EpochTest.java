package com.cleanhome.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EpochTest {
    @Test
    public void newestTokenIsCurrent() {
        Epoch epoch = new Epoch();
        int first = epoch.next();
        assertTrue(epoch.isCurrent(first));
    }

    @Test
    public void newerTokenMakesOlderOneStale() {
        Epoch epoch = new Epoch();
        int first = epoch.next();
        int second = epoch.next();
        assertFalse(epoch.isCurrent(first));
        assertTrue(epoch.isCurrent(second));
    }

    @Test
    public void invalidateMakesEverythingStale() {
        Epoch epoch = new Epoch();
        int token = epoch.next();
        epoch.invalidate();
        assertFalse(epoch.isCurrent(token));
    }
}
