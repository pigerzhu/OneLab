package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SamsungRecentsLayoutPolicyTest {
    @Test
    public void firstEnableSeedsBothDisplaysFromHomeUp() {
        SamsungRecentsLayoutPolicy.UpdateResult result = resolve(
                true, false, 0, 2, null, -1, -1);

        assertEquals(Integer.valueOf(2), result.mainWrite);
        assertEquals(Integer.valueOf(2), result.coverWrite);
        assertTrue(result.markInitialized);
        assertEquals(Integer.valueOf(2), result.finalLayout);
        assertEquals(Integer.valueOf(2), result.nextObservedHomeUpLayout);
    }

    @Test
    public void homeUpChangeOnCoverUpdatesOnlyCover() {
        SamsungRecentsLayoutPolicy.UpdateResult result = resolve(
                true, true, 5, 1, 2, 2, 5);

        assertNull(result.mainWrite);
        assertEquals(Integer.valueOf(1), result.coverWrite);
        assertFalse(result.markInitialized);
        assertEquals(Integer.valueOf(1), result.finalLayout);
    }

    @Test
    public void homeUpChangeOnMainUpdatesOnlyMain() {
        SamsungRecentsLayoutPolicy.UpdateResult result = resolve(
                true, true, 0, 4, 2, 1, 5);

        assertEquals(Integer.valueOf(4), result.mainWrite);
        assertNull(result.coverWrite);
        assertEquals(Integer.valueOf(4), result.finalLayout);
    }

    @Test
    public void unchangedHomeUpUsesSavedDisplayValue() {
        SamsungRecentsLayoutPolicy.UpdateResult result = resolve(
                true, true, 5, 2, 2, 1, 5);

        assertNull(result.mainWrite);
        assertNull(result.coverWrite);
        assertEquals(Integer.valueOf(5), result.finalLayout);
    }

    @Test
    public void disabledPreservesSamsungResultAndObservesHomeUp() {
        SamsungRecentsLayoutPolicy.UpdateResult result = resolve(
                false, true, 0, 3, 2, 1, 5);

        assertNull(result.mainWrite);
        assertNull(result.coverWrite);
        assertNull(result.finalLayout);
        assertEquals(Integer.valueOf(3), result.nextObservedHomeUpLayout);
    }

    @Test
    public void reEnableUsesSavedValuesWithoutReseeding() {
        SamsungRecentsLayoutPolicy.UpdateResult result = resolve(
                true, true, 0, 2, 2, 4, 5);

        assertNull(result.mainWrite);
        assertNull(result.coverWrite);
        assertEquals(Integer.valueOf(4), result.finalLayout);
    }

    @Test
    public void invalidHomeUpValueDoesNotSeedOrReplaceSavedValue() {
        SamsungRecentsLayoutPolicy.UpdateResult result = resolve(
                true, false, 0, 99, null, 1, 5);

        assertNull(result.mainWrite);
        assertNull(result.coverWrite);
        assertFalse(result.markInitialized);
        assertEquals(Integer.valueOf(1), result.finalLayout);
        assertNull(result.nextObservedHomeUpLayout);
    }

    @Test
    public void invalidSavedValueDoesNotOverrideSamsung() {
        SamsungRecentsLayoutPolicy.UpdateResult result = resolve(
                true, true, 0, 2, 2, 99, 5);

        assertNull(result.finalLayout);
    }

    @Test
    public void recognizesAllSamsungLayoutsAndCoverDisplay() {
        for (int layout = 0; layout <= 5; layout++) {
            assertTrue(SamsungRecentsLayoutPolicy.isSupportedLayout(layout));
        }
        assertFalse(SamsungRecentsLayoutPolicy.isSupportedLayout(-1));
        assertFalse(SamsungRecentsLayoutPolicy.isSupportedLayout(6));
        assertTrue(SamsungRecentsLayoutPolicy.isCoverDisplay(5));
        assertFalse(SamsungRecentsLayoutPolicy.isCoverDisplay(0));
    }

    @Test
    public void proactiveSelectionFollowsConfigurationWithoutHomeUpWrite() {
        assertEquals(Integer.valueOf(1),
                SamsungRecentsLayoutPolicy.selectSavedLayout(true, false, 5, 2, 1));
        assertEquals(Integer.valueOf(2),
                SamsungRecentsLayoutPolicy.selectSavedLayout(true, false, 0, 2, 1));
    }

    @Test
    public void proactiveSelectionPreservesSamsungDesktopPolicy() {
        assertNull(SamsungRecentsLayoutPolicy.selectSavedLayout(true, true, 0, 2, 1));
    }

    @Test
    public void proactiveSelectionFailsOpenForDisabledOrInvalidState() {
        assertNull(SamsungRecentsLayoutPolicy.selectSavedLayout(false, false, 0, 2, 1));
        assertNull(SamsungRecentsLayoutPolicy.selectSavedLayout(true, false, 0, 9, 1));
        assertNull(SamsungRecentsLayoutPolicy.selectSavedLayout(true, false, 5, 2, -1));
    }

    private static SamsungRecentsLayoutPolicy.UpdateResult resolve(
            boolean enabled,
            boolean initialized,
            int displayType,
            int homeUpLayout,
            Integer lastObservedHomeUpLayout,
            int savedMainLayout,
            int savedCoverLayout) {
        return SamsungRecentsLayoutPolicy.resolve(
                new SamsungRecentsLayoutPolicy.UpdateInput(
                        enabled,
                        initialized,
                        displayType,
                        homeUpLayout,
                        lastObservedHomeUpLayout,
                        savedMainLayout,
                        savedCoverLayout));
    }
}
