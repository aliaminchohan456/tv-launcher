package com.cleanhome.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IconPickerStateTest {
    @Test
    public void startsOnTheAppList() {
        IconPickerState state = new IconPickerState();
        assertTrue(state.isShowingApps());
        assertFalse(state.isShowingIcons());
        assertNull(state.packageName());
        assertEquals(0, state.returnPosition());
    }

    @Test
    public void openingAnAppShowsItsArtworkAndRemembersItsPlace() {
        IconPickerState state = new IconPickerState();
        state.showIcons("com.example", 7);

        assertTrue(state.isShowingIcons());
        assertEquals("com.example", state.packageName());
        assertEquals(7, state.returnPosition());
    }

    @Test
    public void backFromArtworkReturnsToTheAppList() {
        IconPickerState state = new IconPickerState();
        state.showIcons("com.example", 7);

        assertTrue(state.backToApps());
        assertTrue(state.isShowingApps());
        assertNull(state.packageName());
        assertEquals(7, state.returnPosition());
    }

    @Test
    public void backFromTheAppListIsNotConsumed() {
        IconPickerState state = new IconPickerState();
        assertFalse(state.backToApps());
        assertTrue(state.isShowingApps());
    }

    @Test
    public void negativePositionIsClampedToTheFirstCard() {
        IconPickerState state = new IconPickerState();
        state.showIcons("com.example", -3);
        assertEquals(0, state.returnPosition());
    }
}
