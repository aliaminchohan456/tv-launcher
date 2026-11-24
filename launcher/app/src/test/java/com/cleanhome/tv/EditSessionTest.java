package com.cleanhome.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EditSessionTest {
    @Test
    public void loadFillsTheSavedSelectionWhenUntouched() {
        EditSession session = new EditSession();
        int token = session.beginLoad();
        assertNull(session.selectedSpec());

        assertTrue(session.finishLoad(token, "app:com.example:icon"));
        assertEquals("app:com.example:icon", session.selectedSpec());
        assertTrue(session.isReady());
        assertTrue(session.appliesLoadedPreview());
    }

    @Test
    public void missingSavedSelectionBecomesTheOriginalArt() {
        EditSession session = new EditSession();
        int token = session.beginLoad();
        session.finishLoad(token, null);
        assertEquals("", session.selectedSpec());
        assertTrue(session.appliesLoadedPreview());
    }

    @Test
    public void choiceMadeDuringLoadSurvives() {
        EditSession session = new EditSession();
        int token = session.beginLoad();
        session.select("file:icon_example.png");

        assertTrue(session.finishLoad(token, "app:com.example:icon"));
        assertEquals("file:icon_example.png", session.selectedSpec());
        assertFalse(session.appliesLoadedPreview());
    }

    @Test
    public void resetDuringLoadSurvivesAndTakesTheLoadedStandardArt() {
        EditSession session = new EditSession();
        int token = session.beginLoad();
        session.reset();

        assertTrue(session.finishLoad(token, "app:com.example:icon"));
        assertEquals("", session.selectedSpec());
        assertTrue(session.appliesLoadedPreview());
    }

    @Test
    public void olderLoadCannotOverwriteANewerOne() {
        EditSession session = new EditSession();
        int first = session.beginLoad();
        int second = session.beginLoad();

        assertFalse(session.finishLoad(first, "app:old:icon"));
        assertNull(session.selectedSpec());
        assertFalse(session.isReady());

        assertTrue(session.finishLoad(second, "app:new:icon"));
        assertEquals("app:new:icon", session.selectedSpec());
    }

    @Test
    public void invalidatedLoadIsIgnored() {
        EditSession session = new EditSession();
        int token = session.beginLoad();
        session.invalidate();
        assertFalse(session.finishLoad(token, "app:gone:icon"));
        assertNull(session.selectedSpec());
    }

    @Test
    public void saveBeforeLoadKeepsTheUnknownSelection() {
        EditSession session = new EditSession();
        session.beginLoad();
        assertNull(session.selectedSpec());
        assertFalse(session.isTouched());
    }

    @Test
    public void galleryCopyInFlightBlocksTheLoadedPreview() {
        EditSession session = new EditSession();
        int token = session.beginLoad();
        session.beginPendingChoice();

        assertTrue(session.finishLoad(token, "app:com.example:icon"));
        assertNull(session.selectedSpec());
        assertFalse(session.appliesLoadedPreview());
    }

    @Test
    public void selectThenResetEndsAtTheOriginal() {
        EditSession session = new EditSession();
        int token = session.beginLoad();
        session.finishLoad(token, "app:com.example:icon");
        session.select("file:icon_example.png");
        session.reset();

        assertEquals("", session.selectedSpec());
        assertTrue(session.isTouched());
        assertTrue(session.appliesLoadedPreview());
    }
}
